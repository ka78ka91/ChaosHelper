package com.chaoshelper.world;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.minimap.XaeroWaypoints;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Exports a structural/terrain fingerprint for external seed-cracking tools.
 *
 * <p>This does <b>not</b> crack seeds. Real seed cracking needs to re-run world
 * generation for millions of candidates against the server's own generator,
 * which a client cannot do on a multiplayer server. What this class does is
 * collect the observations a cracker needs (biome ids on a grid, named waypoints
 * such as end cities) so they can be fed into a dedicated tool.
 */
public final class SeedEvidence {
	private SeedEvidence() {
	}

	/**
	 * Reports what is actually knowable about the world seed, with confidence.
	 * <p>Singleplayer: exact, read from the integrated server. Multiplayer: the
	 * seed is server-side only; no client can obtain it, so this says so instead
	 * of inventing a guess.
	 */
	public static String describe() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return "当前不在世界中";
		}
		if (client.isInSingleplayer() && client.getServer() != null) {
			long seed = client.getServer().getOverworld().getSeed();
			return "种子: " + seed + " | 来源: 单人存档 IntegratedServer | 置信度: 精确";
		}
		return "多人服务器不下发种子，客户端无法获取 | 置信度: 无 | 可用 seed export 导出结构/地形证据交给外部工具";
	}

	public static String export() {		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return "当前不在世界中";
		}
		JsonObject root = new JsonObject();
		root.addProperty("exportedAt", Instant.now().toString());
		root.addProperty("dimension", client.world.getRegistryKey().getValue().toString());
		root.addProperty("multiplayer", client.getCurrentServerEntry() != null);
		root.addProperty("playerX", player.getX());
		root.addProperty("playerY", player.getY());
		root.addProperty("playerZ", player.getZ());

		// Singleplayer: the integrated server is right here, so the seed is read
		// exactly instead of guessed. Multiplayer: the seed lives on the server
		// and is not sent to clients, so only evidence can be collected.
		String seedSummary;
		if (client.isInSingleplayer() && client.getServer() != null) {
			long seed = client.getServer().getOverworld().getSeed();
			String text = Long.toString(seed);
			root.addProperty("seed", seed);
			root.addProperty("seedSource", "单人存档 IntegratedServer.getOverworld().getSeed()");
			root.addProperty("seedAccuracy", "精确（客户端只读，不修改存档）");
			root.addProperty("seedText", text);
			seedSummary = "种子 " + text + "（单人存档直读，精确）";
		} else {
			root.addProperty("seedSource", "不可获取：多人服务器不下发种子");
			root.addProperty("seedAccuracy", "无");
			seedSummary = "多人服务器无法获取种子，仅导出证据";
		}
		root.addProperty("note", "单人=直读种子；多人=观察证据，需外部工具解算");

		JsonArray biomes = new JsonArray();
		BlockPos origin = player.getBlockPos();
		int step = 32;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				BlockPos pos = origin.add(dx * step, 0, dz * step);
				try {
					String id = client.world.getBiome(pos).getKey()
							.map(RegistryKey::getValue)
							.map(Object::toString)
							.orElse("unknown");
					JsonObject sample = new JsonObject();
					sample.addProperty("x", pos.getX());
					sample.addProperty("z", pos.getZ());
					sample.addProperty("biome", id);
					biomes.add(sample);
				} catch (Throwable ignored) {
					// Unloaded chunk: skip the sample instead of failing the export.
				}
			}
		}
		root.add("biomeSamples", biomes);

		JsonArray waypoints = new JsonArray();
		for (XaeroWaypoints.Waypoint waypoint : XaeroWaypoints.readAll()) {
			if (waypoint.name().startsWith("gui.xaero_")) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("name", waypoint.name());
			entry.addProperty("x", waypoint.x());
			entry.addProperty("y", waypoint.y());
			entry.addProperty("z", waypoint.z());
			waypoints.add(entry);
		}
		root.add("waypoints", waypoints);

		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve(ChaosHelperClient.MOD_ID);
			Files.createDirectories(dir);
			Path file = dir.resolve("seed-evidence-" + client.world.getRegistryKey().getValue().getPath() + ".json");
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root),
					StandardCharsets.UTF_8);
			return seedSummary + " → " + file.getFileName() + " (生物群系 " + biomes.size()
					+ " 点, 路点 " + waypoints.size() + " 个)";
		} catch (Exception exception) {
			ChaosHelperClient.LOGGER.error("导出种子证据失败", exception);
			return "导出失败: " + exception.getMessage();
		}
	}
}
