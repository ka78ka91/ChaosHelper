package com.chaoshelper.world;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.data.ShipPoint;
import com.chaoshelper.minimap.XaeroWaypoints;
import com.chaoshelper.module.modules.TravelModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Finds End Ships from the air and remembers where they are.
 *
 * <p>Shulkers are useless while flying: they sit inside the city below and never
 * spawn on a ship. The reliable client-visible signals are the blocks that only
 * an End Ship contains:
 *
 * <ul>
 *   <li>the dragon head mounted on the bow,</li>
 *   <li>the brewing stand in the treasure room,</li>
 *   <li>the item frame that holds the elytra.</li>
 * </ul>
 *
 * <p>Chunk columns inside the render distance are scanned a few at a time and
 * only re-read after {@code rescanTicks}, so the cost stays flat. Nothing is
 * sent to the server; the ship list is local ({@code data.json}) and optionally
 * mirrored to a Xaero waypoint.
 */
public final class EndShipFinder {
	private static final Predicate<BlockState> SIGNAL = state ->
			state.isOf(Blocks.DRAGON_HEAD) || state.isOf(Blocks.BREWING_STAND);
	/** A frame this close to a known ship is treated as its treasure room. */
	private static final int FRAME_MATCH_RADIUS = 32;

	private final List<int[]> offsets = new ArrayList<>();
	private final Map<Long, Integer> scannedAt = new HashMap<>();
	private int offsetRadius = -1;
	private int cursor;
	private int tickCounter;
	private int chainCountdown = -1;
	private ShipPoint pendingChain;
	private String lastDimension = "";

	/** Called once per client tick by the {@code endship} module. */
	public void tick(MinecraftClient client) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return;
		}
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			resetForWorldChange();
			return;
		}
		if (chainCountdown > 0 && --chainCountdown == 0) {
			startChain(mod);
		}

		ChaosConfig.EndShipConfig config = mod.config().endship;
		if (config.onlyEnd && !world.getRegistryKey().equals(World.END)) {
			return;
		}
		String dimension = world.getRegistryKey().getValue().toString();
		if (!dimension.equals(lastDimension)) {
			lastDimension = dimension;
			scannedAt.clear();
			cursor = 0;
		}
		tickCounter++;
		if (tickCounter % Math.max(1, config.intervalTicks) != 0) {
			return;
		}
		ensureOffsets(config.scanRadiusChunks);
		scanFrames(mod, client, dimension, config);
		scanChunks(mod, client, dimension, config);
	}

	// ---------------------------------------------------------------- scanning

	private void scanFrames(ChaosHelperClient mod, MinecraftClient client, String dimension,
			ChaosConfig.EndShipConfig config) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		double range = config.scanRadiusChunks * 16.0;
		double rangeSquared = range * range;
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof ItemFrameEntity frame)) {
				continue;
			}
			if (frame.squaredDistanceTo(player) > rangeSquared) {
				continue;
			}
			BlockPos pos = frame.getBlockPos();
			if (frame.getHeldItemStack().isOf(Items.ELYTRA)) {
				// The frame is the best navigation anchor: it is inside the ship.
				register(mod, pos, pos, dimension, config);
				continue;
			}
			ShipPoint known = findNear(dimension, pos, FRAME_MATCH_RADIUS);
			if (known != null && known.hasFrame()
					&& known.frameX == pos.getX() && known.frameY == pos.getY() && known.frameZ == pos.getZ()) {
				markLooted(mod, known, "展示框已空");
			}
		}
	}

	private void scanChunks(ChaosHelperClient mod, MinecraftClient client, String dimension,
			ChaosConfig.EndShipConfig config) {
		ClientPlayerEntity player = client.player;
		if (player == null || offsets.isEmpty()) {
			return;
		}
		ChunkPos center = player.getChunkPos();
		int processed = 0;
		int attempts = 0;
		int total = offsets.size();
		while (processed < config.chunksPerScan && attempts < total) {
			int[] offset = offsets.get(cursor);
			cursor = (cursor + 1) % total;
			attempts++;
			int chunkX = center.x + offset[0];
			int chunkZ = center.z + offset[1];
			long key = ChunkPos.toLong(chunkX, chunkZ);
			int last = scannedAt.getOrDefault(key, 0);
			if (last != 0 && tickCounter - last < config.rescanTicks) {
				continue;
			}
			scannedAt.put(key, tickCounter);
			processed++;
			WorldChunk chunk = client.world.getChunkManager()
					.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
			if (chunk != null) {
				scanChunk(mod, chunk, dimension, config);
			}
		}
		if (scannedAt.size() > 8192) {
			scannedAt.clear();
		}
	}

	private void scanChunk(ChaosHelperClient mod, WorldChunk chunk, String dimension,
			ChaosConfig.EndShipConfig config) {
		ChunkSection[] sections = chunk.getSectionArray();
		int bottom = chunk.getBottomY();
		int startX = chunk.getPos().getStartX();
		int startZ = chunk.getPos().getStartZ();
		for (int index = 0; index < sections.length; index++) {
			ChunkSection section = sections[index];
			if (section == null || section.isEmpty() || !section.hasAny(SIGNAL)) {
				continue;
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (!state.isOf(Blocks.DRAGON_HEAD) && !state.isOf(Blocks.BREWING_STAND)) {
							continue;
						}
						register(mod, new BlockPos(startX + x, bottom + index * 16 + y, startZ + z), null,
								dimension, config);
					}
				}
			}
		}
	}

	private void ensureOffsets(int radius) {
		if (radius == offsetRadius) {
			return;
		}
		offsetRadius = radius;
		offsets.clear();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius + radius) {
					offsets.add(new int[] { dx, dz });
				}
			}
		}
		offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
		cursor = 0;
	}

	// ------------------------------------------------------------- registration

	private void register(ChaosHelperClient mod, BlockPos pos, BlockPos frame, String dimension,
			ChaosConfig.EndShipConfig config) {
		ShipPoint existing = findNear(dimension, pos, config.dedupeRadius);
		if (existing != null) {
			boolean changed = false;
			if (frame != null && (!existing.hasFrame() || existing.frameX != frame.getX()
					|| existing.frameY != frame.getY() || existing.frameZ != frame.getZ())) {
				// Prefer the treasure room over the bow once it is visible.
				existing.setFrame(frame.getX(), frame.getY(), frame.getZ());
				existing.x = frame.getX();
				existing.y = frame.getY();
				existing.z = frame.getZ();
				changed = true;
			}
			if (changed) {
				mod.dataStore().saveQuietly();
			}
			return;
		}
		ShipPoint ship = new ShipPoint(dimension, pos.getX(), pos.getY(), pos.getZ());
		if (frame != null) {
			ship.setFrame(frame.getX(), frame.getY(), frame.getZ());
		}
		mod.dataStore().ships().add(ship);
		mod.dataStore().saveQuietly();
		boolean marked = false;
		if (config.markXaero) {
			marked = XaeroWaypoints.mark("末地船", pos);
		}
		if (config.notify) {
			ClientPlayerEntity player = mod.client().player;
			double distance = player == null ? 0
					: Math.sqrt(ship.distanceSquaredTo(player.getX(), player.getY(), player.getZ()));
			String suffix = !config.markXaero ? "，仅本地记录"
					: marked ? "，已写入路点" : "，Xaero 路点写入失败";
			mod.notifications().info(String.format(Locale.ROOT, "发现末地船 %d, %d, %d（%.0f 格%s）",
					pos.getX(), pos.getY(), pos.getZ(), distance, suffix));
		}
	}

	private ShipPoint findNear(String dimension, BlockPos pos, double radius) {
		double squared = radius * radius;
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return null;
		}
		for (ShipPoint ship : mod.dataStore().ships()) {
			if (!ship.dimension.equals(dimension)) {
				continue;
			}
			double dx = ship.x - pos.getX();
			double dy = ship.y - pos.getY();
			double dz = ship.z - pos.getZ();
			if (dx * dx + dy * dy + dz * dz <= squared) {
				return ship;
			}
		}
		return null;
	}

	/** Called by the auto-take module right after it attacks an elytra frame. */
	public void lootNear(BlockPos framePos) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || mod.client().world == null) {
			return;
		}
		String dimension = mod.client().world.getRegistryKey().getValue().toString();
		ShipPoint ship = findNear(dimension, framePos, FRAME_MATCH_RADIUS);
		if (ship != null) {
			if (!ship.hasFrame()) {
				ship.setFrame(framePos.getX(), framePos.getY(), framePos.getZ());
			}
			markLooted(mod, ship, "已取走鞘翅");
		}
	}

	private void markLooted(ChaosHelperClient mod, ShipPoint ship, String reason) {
		if (ship.looted) {
			return;
		}
		ship.looted = true;
		mod.dataStore().saveQuietly();
		if (mod.config().endship.notify) {
			mod.notifications().info("末地船已搜（" + reason + "），" + ship.x + ", " + ship.z);
		}
		if (!mod.config().endship.chainNext) {
			return;
		}
		ShipPoint next = nextUnlooted(mod, ship);
		if (next == null) {
			if (mod.config().endship.notify) {
				mod.notifications().info("没有其他已发现的未搜末地船，转场结束");
			}
			return;
		}
		pendingChain = next;
		chainCountdown = Math.max(1, mod.config().endship.chainDelayTicks);
		if (mod.config().endship.notify) {
			mod.notifications().info(String.format(Locale.ROOT, "%d tick 后飞往下一艘 %d, %d",
					chainCountdown, next.x, next.z));
		}
	}

	private void startChain(ChaosHelperClient mod) {
		ShipPoint target = pendingChain;
		pendingChain = null;
		ChaosConfig.EndShipConfig config = mod.config().endship;
		if (target == null || !config.chainNext) {
			return;
		}
		ClientPlayerEntity player = mod.client().player;
		if (player == null || mod.client().world == null) {
			return;
		}
		if (!target.dimension.equals(mod.client().world.getRegistryKey().getValue().toString())) {
			return;
		}
		mod.moduleManager().setEnabled("travel", true);
		String error = mod.travel().start(target.x, target.y, target.z, TravelModule.Mode.ELYTRA, "末地船");
		if (error != null) {
			mod.notifications().error("飞往下一艘末地船失败: " + error);
		}
	}

	// ------------------------------------------------------------------ queries

	public ShipPoint nextUnlooted(ChaosHelperClient mod, ShipPoint exclude) {
		ClientPlayerEntity player = mod.client().player;
		if (player == null || mod.client().world == null) {
			return null;
		}
		String dimension = mod.client().world.getRegistryKey().getValue().toString();
		int range = mod.config().endship.chainRange;
		double rangeSquared = range <= 0 ? Double.MAX_VALUE : (double) range * range;
		ShipPoint best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ShipPoint ship : mod.dataStore().ships()) {
			if (ship.looted || ship == exclude || !ship.dimension.equals(dimension)) {
				continue;
			}
			double distance = ship.distanceSquaredTo(player.getX(), player.getY(), player.getZ());
			if (distance > rangeSquared || distance >= bestDistance) {
				continue;
			}
			bestDistance = distance;
			best = ship;
		}
		return best;
	}

	public List<ShipPoint> shipsIn(String dimension) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		List<ShipPoint> result = new ArrayList<>();
		if (mod == null) {
			return result;
		}
		for (ShipPoint ship : mod.dataStore().ships()) {
			if (ship.dimension.equals(dimension)) {
				result.add(ship);
			}
		}
		return result;
	}

	public int unlootedCount(String dimension) {
		int count = 0;
		for (ShipPoint ship : shipsIn(dimension)) {
			if (!ship.looted) {
				count++;
			}
		}
		return count;
	}

	public void clearFound() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return;
		}
		mod.dataStore().ships().clear();
		mod.dataStore().saveQuietly();
		scannedAt.clear();
	}

	public void resetForWorldChange() {
		scannedAt.clear();
		cursor = 0;
		tickCounter = 0;
		chainCountdown = -1;
		pendingChain = null;
		lastDimension = "";
	}

	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || mod.client().world == null) {
			return "未在世界中";
		}
		String dimension = mod.client().world.getRegistryKey().getValue().toString();
		return "已发现 " + shipsIn(dimension).size() + " 艘（未搜 " + unlootedCount(dimension) + "）";
	}
}
