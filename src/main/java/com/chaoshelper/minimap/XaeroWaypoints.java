package com.chaoshelper.minimap;

import com.chaoshelper.ChaosHelperClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * File-based integration with Xaero's Minimap (and XaeroPlus builds).
 *
 * <p>The on-disk format was verified against real files written by Xaero's
 * Minimap 26.6.0:
 *
 * <pre>
 * &lt;gameDir&gt;/xaero/minimap/&lt;world&gt;/dim%0/waypoints.txt
 * &lt;gameDir&gt;/xaero/minimap/&lt;world&gt;/dim%0/mw$default_1.txt
 * waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination
 * </pre>
 *
 * Dimension folders: {@code dim%0} overworld, {@code dim%-1} nether, {@code dim%1}
 * the end, {@code dim%<namespace>$<path>} for custom dimensions.
 *
 * <p>Writing never touches existing waypoint lines: it appends to a dedicated
 * {@code mw$<set>.txt} (or to {@code waypoints.txt} when the world does not use
 * multiworld sets) and keeps a one-time {@code .chaoshelper.bak} copy.
 */
public final class XaeroWaypoints {
	private static final String HEADER = "#\n"
			+ "#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination\n"
			+ "#\n";

	public record Waypoint(String name, int x, int y, int z, String set, Path file) {
	}

	private XaeroWaypoints() {
	}

	public static boolean isInstalled() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded("xaerominimap") || loader.isModLoaded("xaeroplus")
				|| loader.isModLoaded("xaeroworldmap");
	}

	public static String statusLine() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return "Xaero: 未初始化";
		}
		if (!mod.config().xaero.enabled) {
			return "Xaero: 已禁用";
		}
		if (!isInstalled()) {
			return "Xaero: 未安装";
		}
		Path dir = currentDimDir();
		if (dir == null) {
			return "Xaero: 未找到当前世界目录";
		}
		String mode = XaeroApi.available() ? "API" : "文件";
		return "Xaero: " + dir.getParent().getFileName() + "/" + dir.getFileName()
				+ " (" + readAll().size() + " 个路点, " + mode + ")";
	}

	public static Path minimapRoot() {
		return FabricLoader.getInstance().getGameDir().resolve("xaero").resolve("minimap");
	}

	/** Resolves the folder Xaero uses for the current save/server. */
	public static Path currentWorldDir() {
		Path root = minimapRoot();
		if (!Files.isDirectory(root)) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		List<String> candidates = new ArrayList<>();
		ServerInfo info = client.getCurrentServerEntry();
		if (info != null && info.address != null && !info.address.isBlank()) {
			candidates.add("Multiplayer_" + info.address);
			int colon = info.address.lastIndexOf(':');
			if (colon > 0) {
				candidates.add("Multiplayer_" + info.address.substring(0, colon));
			}
		}
		if (client.getServer() != null) {
			try {
				Path save = client.getServer().getSavePath(WorldSavePath.ROOT);
				if (save != null && save.getFileName() != null) {
					String name = save.getFileName().toString();
					candidates.add(name);
					candidates.add(name.replace("_", "%us%"));
				}
			} catch (Throwable ignored) {
				// Older/odd servers may not expose a save path; fall back below.
			}
		}
		for (String candidate : candidates) {
			Path dir = root.resolve(candidate);
			if (Files.isDirectory(dir)) {
				return dir;
			}
		}
		// Fallback: the most recently modified world folder that looks like a save.
		try (Stream<Path> stream = Files.list(root)) {
			return stream.filter(Files::isDirectory)
					.filter(path -> Files.isRegularFile(path.resolve("config.txt")))
					.max(Comparator.comparingLong(XaeroWaypoints::lastModified))
					.orElse(null);
		} catch (IOException exception) {
			return null;
		}
	}

	private static long lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path.resolve("config.txt")).toMillis();
		} catch (IOException exception) {
			return 0L;
		}
	}

	public static Path currentDimDir() {
		return currentDimDir(false);
	}

	/**
	 * @param create when true, creates the dimension folder if Xaero has not written
	 *               there yet (e.g. the player entered the End without opening the map)
	 */
	private static Path currentDimDir(boolean create) {
		Path world = currentWorldDir();
		if (world == null) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return null;
		}
		String folder = dimensionFolder(client.world);
		Path dir = world.resolve(folder);
		if (Files.isDirectory(dir)) {
			return dir;
		}
		if (!create) {
			return null;
		}
		try {
			Files.createDirectories(dir);
			return dir;
		} catch (IOException exception) {
			ChaosHelperClient.LOGGER.warn("创建 Xaero 维度目录失败: {}", exception.getMessage());
			return null;
		}
	}

	static String dimensionFolder(World world) {
		var key = world.getRegistryKey().getValue();
		if (key.equals(World.OVERWORLD.getValue())) {
			return "dim%0";
		}
		if (key.equals(World.NETHER.getValue())) {
			return "dim%-1";
		}
		if (key.equals(World.END.getValue())) {
			return "dim%1";
		}
		return "dim%" + key.getNamespace() + "$" + key.getPath();
	}

	public static List<Waypoint> readAll() {
		Path dir = currentDimDir();
		if (dir == null) {
			return List.of();
		}
		List<Waypoint> result = new ArrayList<>();
		try (Stream<Path> stream = Files.list(dir)) {
			for (Path file : stream.filter(Files::isRegularFile).toList()) {
				String name = file.getFileName().toString();
				if (!name.equals("waypoints.txt") && !name.startsWith("mw$")) {
					continue;
				}
				result.addAll(read(file));
			}
		} catch (IOException exception) {
			ChaosHelperClient.LOGGER.warn("读取 Xaero 路点失败: {}", exception.getMessage());
		}
		return result;
	}

	private static List<Waypoint> read(Path file) {
		List<Waypoint> result = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (line == null || !line.startsWith("waypoint:")) {
					continue;
				}
				String[] parts = line.split(":", -1);
				if (parts.length < 6) {
					continue;
				}
				try {
					int x = (int) Math.round(Double.parseDouble(parts[3]));
					int y = (int) Math.round(Double.parseDouble(parts[4]));
					int z = (int) Math.round(Double.parseDouble(parts[5]));
					String set = parts.length > 8 ? parts[8] : "";
					result.add(new Waypoint(parts[1], x, y, z, set, file));
				} catch (NumberFormatException ignored) {
					// Skip malformed lines instead of losing the whole file.
				}
			}
		} catch (IOException exception) {
			ChaosHelperClient.LOGGER.warn("读取 Xaero 路点文件失败 {}: {}", file.getFileName(), exception.getMessage());
		}
		return result;
	}

	public static Waypoint find(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		for (Waypoint waypoint : readAll()) {
			if (waypoint.name().equalsIgnoreCase(name)) {
				return waypoint;
			}
		}
		return null;
	}

	/**
	 * Adds one marker to a dedicated set. Returns false only when the Xaero folder
	 * could not be resolved or writing is disabled.
	 */
	public static boolean mark(String name, BlockPos pos) {
		return mark(name, pos.getX(), pos.getY(), pos.getZ());
	}

	public static boolean mark(String name, int x, int y, int z) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || !mod.config().xaero.enabled || !mod.config().xaero.allowWrite) {
			return false;
		}
		String safeName = sanitize(name);
		// Prefer Xaero's own in-memory API: it persists the marker and shows it
		// immediately. Only edit the waypoint file when the reflection bridge is
		// unavailable (different Xaero version, or no world loaded yet).
		if (XaeroApi.add(safeName, x, y, z)) {
			return true;
		}
		Path dir = currentDimDir(true);
		if (dir == null) {
			return false;
		}
		String set = mod.config().xaero.setName;
		for (Waypoint existing : readAll()) {
			if (existing.name().equalsIgnoreCase(safeName)
					&& Math.abs(existing.x() - x) <= 32
					&& Math.abs(existing.z() - z) <= 32) {
				return true;
			}
		}
		Path target = pickTargetFile(dir, set);
		try {
			if (Files.notExists(target)) {
				Files.writeString(target, HEADER, StandardCharsets.UTF_8);
			} else if (!backedUp(target)) {
				Files.copy(target, target.resolveSibling(target.getFileName() + ".chaoshelper.bak"),
						StandardCopyOption.REPLACE_EXISTING);
			}
			String line = String.format(Locale.ROOT,
					"waypoint:%s:%s:%d:%d:%d:0:false:0:%s:false:0:1:false%n",
					safeName, initials(safeName), x, y, z, set);
			Files.writeString(target, line, StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.APPEND);
			return true;
		} catch (IOException exception) {
			ChaosHelperClient.LOGGER.warn("写入 Xaero 路点失败: {}", exception.getMessage());
			mod.errorLog().add(com.chaoshelper.util.ErrorLog.Level.WARN, "xaero",
					"写入 Xaero 路点失败: " + exception.getMessage());
			return false;
		}
	}

	/**
	 * Xaero names multiworld set files {@code mw$<set>_<index>.txt} (observed:
	 * {@code mw$default_1.txt}), so the dedicated set follows the same pattern
	 * instead of inventing a file name Xaero would not load.
	 */
	private static Path pickTargetFile(Path dir, String set) {
		try (Stream<Path> stream = Files.list(dir)) {
			boolean multiworld = stream.anyMatch(path -> path.getFileName().toString().startsWith("mw$"));
			if (multiworld) {
				return dir.resolve("mw$" + set + "_1.txt");
			}
		} catch (IOException ignored) {
			// Fall through to the legacy file.
		}
		return dir.resolve("waypoints.txt");
	}

	private static final java.util.Set<String> BACKED_UP = new java.util.HashSet<>();

	private static boolean backedUp(Path file) {
		String key = file.toAbsolutePath().toString();
		if (BACKED_UP.contains(key)) {
			return true;
		}
		if (Files.exists(file.resolveSibling(file.getFileName() + ".chaoshelper.bak"))) {
			BACKED_UP.add(key);
			return true;
		}
		return false;
	}

	private static String sanitize(String name) {
		if (name == null || name.isBlank()) {
			return "marker";
		}
		String cleaned = name.replace(':', ' ').replace('\n', ' ').replace('\r', ' ').trim();
		return cleaned.isEmpty() ? "marker" : cleaned;
	}

	private static String initials(String name) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < name.length() && builder.length() < 2; index++) {
			char c = name.charAt(index);
			if (Character.isLetterOrDigit(c)) {
				builder.append(Character.toUpperCase(c));
			}
		}
		return builder.length() == 0 ? "C" : builder.toString();
	}
}
