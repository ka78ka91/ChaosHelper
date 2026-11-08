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
import net.minecraft.entity.EquipmentSlot;
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
 * Finds End Ships from the air, then runs the whole looting loop: Baritone flies
 * to the ship, the player is walked to the treasure room, the elytra is taken,
 * the ship is marked as looted and the next one is selected.
 *
 * <p>Shulkers are useless for detection while flying: they sit inside the city
 * below and never spawn on a ship. The reliable client-visible signals are the
 * blocks only an End Ship contains: the dragon head on the bow, the brewing
 * stand in the treasure room, and the item frame holding the elytra.
 *
 * <p>Everything is client-side: chunk reads, local {@code data.json}, optional
 * Xaero waypoints, plus the same Baritone API the player can call by hand.
 */
public final class EndShipFinder {
	private enum Phase {
		IDLE, SWEEP, FLY, APPROACH, MANUAL, WAIT_LOOT, EXIT
	}

	private static final Predicate<BlockState> SIGNAL = state ->
			state.isOf(Blocks.DRAGON_HEAD) || state.isOf(Blocks.BREWING_STAND);
	/** A frame this close to a known ship is treated as its treasure room. */
	private static final int FRAME_MATCH_RADIUS = 32;
	/** Ticks to wait after the walk path ends before falling back to manual. */
	private static final int PATH_GIVE_UP_TICKS = 40;
	/** Retry delay after a failed flight/hand-off, so we do not loop on it. */
	private static final int RETRY_COOLDOWN_TICKS = 400;
	/** A leg that gains less than this is treated as "stuck on the ground". */
	private static final double LEG_STUCK_BLOCKS = 32.0;
	/** Give up cruising after this many legs in a row made no progress. */
	private static final int MAX_STUCK_LEGS = 3;
	/** A leg counts as reached this many blocks before its exact waypoint. */
	private static final double LEG_TOLERANCE = 24.0;

	private final List<int[]> offsets = new ArrayList<>();
	private final Map<Long, Integer> scannedAt = new HashMap<>();
	private int offsetRadius = -1;
	private int cursor;
	private int tickCounter;
	private String lastDimension = "";

	private Phase phase = Phase.IDLE;
	private ShipPoint target;
	private int phaseTicks;
	private int chainDelay;
	private int retryCooldown;
	private boolean pathIssued;
	/** Loop stopped until the player re-arms it (failure or chainNext=false). */
	private boolean paused;
	/** The pause came from chainNext=false, so flipping it back on resumes. */
	private boolean chainStop;

	// Cruise state: a fixed bearing from an anchor point, one leg at a time.
	private boolean sweepArmed;
	private double sweepOriginX;
	private double sweepOriginZ;
	/** Bearing the anchor was taken with; a config change re-anchors. */
	private double sweepBearing;
	/** Highest distance along the bearing that has already been planned. */
	private double sweepProgress;
	private double legStartProgress;
	private int legX;
	private int legZ;
	private int sweepLegs;
	private int stuckLegs;
	private boolean warnedLegError;
	/** The elytra was actually deployed during this ship flight. */
	private boolean flewThisLeg;

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
		ChaosConfig.EndShipConfig config = mod.config().endship;
		String dimension = world.getRegistryKey().getValue().toString();
		if (!dimension.equals(lastDimension)) {
			lastDimension = dimension;
			scannedAt.clear();
			cursor = 0;
			resetAutomation();
		}
		if (config.onlyEnd && !world.getRegistryKey().equals(World.END)) {
			return;
		}
		tickCounter++;
		autoLootTick(mod, client, config);
		if (tickCounter % Math.max(1, config.intervalTicks) != 0) {
			return;
		}
		ensureOffsets(config.scanRadiusChunks);
		scanFrames(mod, client, dimension, config);
		scanChunks(mod, client, dimension, config);
	}

	// ---------------------------------------------------------------- looting

	private void autoLootTick(ChaosHelperClient mod, MinecraftClient client, ChaosConfig.EndShipConfig config) {
		if (retryCooldown > 0) {
			retryCooldown--;
		}
		if (chainDelay > 0) {
			chainDelay--;
		}
		if (!config.autoLoot) {
			return;
		}
		if (paused) {
			if (!chainStop || !config.chainNext) {
				return;
			}
			// The player re-enabled chaining; arm the loop again.
			paused = false;
			chainStop = false;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		if (target != null && target.looted && phase != Phase.IDLE && phase != Phase.EXIT) {
			beginExit(mod, player, config);
			return;
		}
		switch (phase) {
			case IDLE -> {
				if (chainDelay > 0 || retryCooldown > 0 || client.currentScreen != null) {
					return;
				}
				if (mod.travel().hasTarget()) {
					// The player (or another module) is already travelling.
					return;
				}
				ShipPoint next = config.sweep
						? nextAheadShip(mod, player, config)
						: nextUnlooted(mod, null);
				if (next != null) {
					if (horizontalDistance(player, next) <= config.approachRadius) {
						target = next;
						enterApproach(mod);
					} else {
						startFlight(mod, player, next, config);
					}
					return;
				}
				if (config.sweep) {
					// Nothing in the corridor: keep going the same way.
					startSweepLeg(mod, player, config);
				}
			}
			case SWEEP -> {
				if (mod.travel().hasTarget() && !mod.travel().isTarget(legX, legZ)) {
					// The player (or another module) replaced our leg: yield instead
					// of hijacking their destination.
					phase = Phase.IDLE;
					target = null;
					return;
				}
				// A ship showed up inside the corridor: break off and loot it now.
				ShipPoint found = nextAheadShip(mod, player, config);
				if (found != null) {
					mod.travel().stop(false);
					if (horizontalDistance(player, found) <= config.approachRadius) {
						target = found;
						enterApproach(mod);
					} else {
						startFlight(mod, player, found, config);
					}
					return;
				}
				// Leg done once we pass the planned distance. This is what keeps a
				// pure glide from turning around to hit an altitude it already left.
				boolean legReached = progress(player, config) >= sweepProgress - LEG_TOLERANCE;
				if (legReached && mod.travel().hasTarget()) {
					mod.travel().stop(false);
				}
				if (!mod.travel().hasTarget()) {
					// Leg finished (arrived, landed, or Baritone gave up): plan the next.
					if (!progressGained(player, config)) {
						if (++stuckLegs >= MAX_STUCK_LEGS) {
							abort(mod, "连续多段没有前进，请先跳下展开鞘翅再继续");
							return;
						}
					} else {
						stuckLegs = 0;
					}
					startSweepLeg(mod, player, config);
				}
			}
			case FLY -> {
				if (target == null) {
					phase = Phase.IDLE;
					return;
				}
				phaseTicks++;
				double horizontal = horizontalDistance(player, target);
				if (player.isGliding()) {
					flewThisLeg = true;
				}
				if (horizontal <= config.approachRadius) {
					mod.travel().stop(false);
					enterApproach(mod);
					return;
				}
				if (flewThisLeg && !player.isGliding() && phaseTicks > 40) {
					// Touched down. With an exact-Y goal Baritone often lands on the
					// ship itself; either way the rest is walking.
					mod.travel().stop(false);
					if (horizontal <= Math.max(config.approachRadius * 4.0, 160.0)) {
						enterApproach(mod);
					} else {
						abort(mod, "已落地但离船太远");
					}
					return;
				}
				if (!mod.travel().hasTarget()) {
					// Baritone may pick a safe landing spot well away from the ship.
					if (horizontal <= Math.max(config.approachRadius * 2.0, 128.0)) {
						enterApproach(mod);
					} else {
						abort(mod, "航线结束但还没到船附近");
					}
				}
			}
			case APPROACH -> approachTick(mod, client, player, config);
			case MANUAL -> manualTick(mod, client, player, config);
			case WAIT_LOOT -> waitLootTick(mod, client, player, config);
			case EXIT -> exitTick(mod, player, config);
		}
	}

	// ----------------------------------------------------------------- cruise

	private double dirX(ChaosConfig.EndShipConfig config) {
		return Math.sin(Math.toRadians(config.sweepBearing));
	}

	private double dirZ(ChaosConfig.EndShipConfig config) {
		return -Math.cos(Math.toRadians(config.sweepBearing));
	}

	/** Distance travelled along the cruise bearing, measured from the anchor. */
	private double progress(ClientPlayerEntity player, ChaosConfig.EndShipConfig config) {
		return (player.getX() - sweepOriginX) * dirX(config)
				+ (player.getZ() - sweepOriginZ) * dirZ(config);
	}

	private boolean progressGained(ClientPlayerEntity player, ChaosConfig.EndShipConfig config) {
		return progress(player, config) - legStartProgress >= LEG_STUCK_BLOCKS;
	}

	private void armSweep(ClientPlayerEntity player, ChaosConfig.EndShipConfig config) {
		sweepArmed = true;
		sweepBearing = config.sweepBearing;
		sweepOriginX = player.getX();
		sweepOriginZ = player.getZ();
		sweepProgress = 0.0;
		legStartProgress = 0.0;
		sweepLegs = 0;
		stuckLegs = 0;
		warnedLegError = false;
	}

	private boolean sweepNeedsArming(ChaosConfig.EndShipConfig config) {
		return !sweepArmed || Math.abs(sweepBearing - config.sweepBearing) > 1e-6;
	}

	/** Flies the next leg along the cruise bearing. */
	private void startSweepLeg(ChaosHelperClient mod, ClientPlayerEntity player,
			ChaosConfig.EndShipConfig config) {
		if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
			// Not geared up yet: stay idle instead of complaining every tick.
			return;
		}
		if (sweepNeedsArming(config)) {
			armSweep(player, config);
		}
		double dx = dirX(config);
		double dz = dirZ(config);
		double ahead = Math.max(sweepProgress, progress(player, config)) + Math.max(64, config.sweepStep);
		sweepProgress = ahead;
		legStartProgress = progress(player, config);
		int x = (int) Math.round(sweepOriginX + dx * ahead);
		int z = (int) Math.round(sweepOriginZ + dz * ahead);
		int y = (int) Math.round(player.getY());
		mod.moduleManager().setEnabled("travel", true);
		String error = mod.travel().start(x, y, z, TravelModule.Mode.ELYTRA, "巡航", true);
		if (error != null) {
			if (!warnedLegError) {
				warnedLegError = true;
				mod.notifications().error("自动巡航无法起飞：" + error);
			}
			paused = true;
			chainStop = false;
			retryCooldown = RETRY_COOLDOWN_TICKS;
			return;
		}
		warnedLegError = false;
		legX = x;
		legZ = z;
		phase = Phase.SWEEP;
		phaseTicks = 0;
		sweepLegs++;
	}

	/**
	 * Closest unlooted ship that still lies inside the cruise corridor. Ships are
	 * taken in flight order (smallest progress first) so the sweep never zigzags.
	 */
	private ShipPoint nextAheadShip(ChaosHelperClient mod, ClientPlayerEntity player,
			ChaosConfig.EndShipConfig config) {
		if (mod.client().world == null) {
			return null;
		}
		if (!sweepArmed) {
			if (phase != Phase.IDLE) {
				// Mid-run without an anchor; the next leg will establish one.
				return null;
			}
			armSweep(player, config);
		}
		String dimension = mod.client().world.getRegistryKey().getValue().toString();
		double dx = dirX(config);
		double dz = dirZ(config);
		double base = progress(player, config);
		int range = config.chainRange;
		double rangeSquared = range <= 0 ? Double.MAX_VALUE : (double) range * range;
		ShipPoint best = null;
		double bestProgress = Double.MAX_VALUE;
		for (ShipPoint ship : mod.dataStore().ships()) {
			if (ship.looted || !ship.dimension.equals(dimension)) {
				continue;
			}
			if (ship.distanceSquaredTo(player.getX(), player.getY(), player.getZ()) > rangeSquared) {
				continue;
			}
			double sx = ship.x + 0.5 - sweepOriginX;
			double sz = ship.z + 0.5 - sweepOriginZ;
			double along = sx * dx + sz * dz;
			if (along < base - config.sweepBacktrack) {
				// Flown past already; a later pass can pick it up.
				continue;
			}
			if (Math.abs(sx * dz - sz * dx) > config.sweepCorridor) {
				continue;
			}
			if (along < bestProgress) {
				bestProgress = along;
				best = ship;
			}
		}
		return best;
	}

	/** Compass label for a bearing in degrees. */
	public static String bearingName(double bearing) {
		String[] names = { "北 (-Z)", "东北", "东 (+X)", "东南", "南 (+Z)", "西南", "西 (-X)", "西北" };
		int index = (int) Math.round((((bearing % 360.0) + 360.0) % 360.0) / 45.0) % 8;
		return names[index];
	}

	private void startFlight(ChaosHelperClient mod, ClientPlayerEntity player, ShipPoint ship,
			ChaosConfig.EndShipConfig config) {
		mod.moduleManager().setEnabled("travel", true);
		String error = config.flyExact
				// GoalBlock: Baritone keeps the ship's Y instead of cruising at 64
				// and looking for a landing spot on the island far below.
				? mod.travel().startExact(ship.x, ship.y, ship.z, "末地船", false)
				: mod.travel().start(ship.x, ship.y, ship.z, TravelModule.Mode.ELYTRA, "末地船");
		if (error != null) {
			mod.notifications().error("自动搜船：飞往末地船失败 - " + error);
			target = null;
			retryCooldown = RETRY_COOLDOWN_TICKS;
			return;
		}
		target = ship;
		phase = Phase.FLY;
		phaseTicks = 0;
		flewThisLeg = false;
	}

	private void enterApproach(ChaosHelperClient mod) {
		phase = Phase.APPROACH;
		phaseTicks = 0;
		pathIssued = false;
		mod.notifications().info("已到末地船附近，正在接近展示框");
	}

	private void approachTick(ChaosHelperClient mod, MinecraftClient client, ClientPlayerEntity player,
			ChaosConfig.EndShipConfig config) {
		if (target == null) {
			phase = Phase.IDLE;
			return;
		}
		phaseTicks++;
		if (tryTake(client, player, target, config.takeRange)) {
			phase = Phase.WAIT_LOOT;
			phaseTicks = 0;
			return;
		}
		BlockPos goal = target.hasFrame()
				? new BlockPos(target.frameX, target.frameY, target.frameZ)
				: new BlockPos(target.x, target.y, target.z);
		if (!pathIssued) {
			pathIssued = true;
			phaseTicks = 0;
			String error = mod.baritoneBridge().startPath(goal.getX(), goal.getY(), goal.getZ());
			if (error != null) {
				toManual(mod, error);
			}
			return;
		}
		if (!mod.baritoneBridge().isPathing()) {
			if (phaseTicks > PATH_GIVE_UP_TICKS) {
				toManual(mod, "步行路径无法到达展示框");
			}
			return;
		}
		if (phaseTicks > config.approachTimeoutTicks) {
			toManual(mod, "接近超时");
		}
	}

	private void manualTick(ChaosHelperClient mod, MinecraftClient client, ClientPlayerEntity player,
			ChaosConfig.EndShipConfig config) {
		if (target == null) {
			phase = Phase.IDLE;
			return;
		}
		phaseTicks++;
		if (tryTake(client, player, target, config.takeRange)) {
			phase = Phase.WAIT_LOOT;
			phaseTicks = 0;
			return;
		}
		if (phaseTicks > config.manualTimeoutTicks) {
			abort(mod, "等待手动接近超时");
		}
	}

	private void waitLootTick(ChaosHelperClient mod, MinecraftClient client, ClientPlayerEntity player,
			ChaosConfig.EndShipConfig config) {
		if (target == null) {
			phase = Phase.IDLE;
			return;
		}
		phaseTicks++;
		if (target.looted) {
			beginExit(mod, player, config);
			return;
		}
		if (phaseTicks % 20 == 0) {
			// Still in range: re-send the attack, the server may have rejected the first one.
			tryTake(client, player, target, config.takeRange);
		}
		if (phaseTicks > 120) {
			toManual(mod, "展示框没有变空");
		}
	}

	/**
	 * The elytra is taken. An End Ship's treasure room is fully enclosed, so there
	 * is nowhere to deploy a wing: walk back out to the bow (dragon head) first,
	 * otherwise the next flight just stands inside a wall and times out.
	 */
	private void beginExit(ChaosHelperClient mod, ClientPlayerEntity player, ChaosConfig.EndShipConfig config) {
		if (!config.chainNext || target == null || !target.hasDeck()) {
			finishShip(mod, config);
			return;
		}
		phase = Phase.EXIT;
		phaseTicks = 0;
		pathIssued = false;
	}

	private void exitTick(ChaosHelperClient mod, ClientPlayerEntity player, ChaosConfig.EndShipConfig config) {
		if (target == null || !target.hasDeck()) {
			finishShip(mod, config);
			return;
		}
		phaseTicks++;
		double dx = target.deckX + 0.5 - player.getX();
		double dz = target.deckZ + 0.5 - player.getZ();
		double dy = target.deckY + 0.5 - player.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal <= 4.0 && Math.abs(dy) <= 3.0) {
			finishShip(mod, config);
			return;
		}
		if (!pathIssued) {
			pathIssued = true;
			phaseTicks = 0;
			String error = mod.baritoneBridge().startPath(target.deckX, target.deckY, target.deckZ);
			if (error != null) {
				mod.notifications().info("无法寻路回甲板（" + error + "），请自己走出舱门");
				finishShip(mod, config);
			}
			return;
		}
		if (!mod.baritoneBridge().isPathing() && phaseTicks > PATH_GIVE_UP_TICKS) {
			// Standing on the hull but a few blocks from the bow is fine.
			if (horizontal > 12.0) {
				mod.notifications().info("没能走到甲板，请自己走出舱门后继续");
			}
			finishShip(mod, config);
			return;
		}
		if (phaseTicks > config.approachTimeoutTicks) {
			mod.baritoneBridge().cancel();
			if (horizontal > 12.0) {
				mod.notifications().info("回甲板超时，请自己走出去；已直接开始下一段");
			}
			finishShip(mod, config);
		}
	}

	private void finishShip(ChaosHelperClient mod, ChaosConfig.EndShipConfig config) {
		phase = Phase.IDLE;
		target = null;
		pathIssued = false;
		phaseTicks = 0;
		if (!config.chainNext) {
			paused = true;
			chainStop = true;
			mod.notifications().info("链式转场已关闭，自动搜船暂停");
			return;
		}
		chainDelay = Math.max(1, config.chainDelayTicks);
	}

	private void toManual(ChaosHelperClient mod, String reason) {
		mod.baritoneBridge().cancel();
		phase = Phase.MANUAL;
		phaseTicks = 0;
		mod.notifications().info("自动搜船：请手动飞进船内（" + reason + "），靠近展示框 3 格会自动取");
	}

	private void abort(ChaosHelperClient mod, String reason) {
		mod.notifications().info("自动搜船暂停：" + reason + "（/chaoshelper ship goto 继续）");
		mod.baritoneBridge().cancel();
		phase = Phase.IDLE;
		target = null;
		pathIssued = false;
		paused = true;
		chainStop = false;
	}

	/** Attacks the known elytra frame when it is within reach. */
	private boolean tryTake(MinecraftClient client, ClientPlayerEntity player, ShipPoint ship, double range) {
		if (client.interactionManager == null || client.currentScreen != null
				|| client.world == null || !ship.hasFrame()) {
			return false;
		}
		BlockPos pos = new BlockPos(ship.frameX, ship.frameY, ship.frameZ);
		double limit = range * range;
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof ItemFrameEntity frame)) {
				continue;
			}
			if (!frame.getBlockPos().equals(pos) || !frame.getHeldItemStack().isOf(Items.ELYTRA)) {
				continue;
			}
			if (player.squaredDistanceTo(frame) > limit) {
				continue;
			}
			client.interactionManager.attackEntity(player, frame);
			return true;
		}
		return false;
	}

	private static double horizontalDistance(ClientPlayerEntity player, ShipPoint ship) {
		double dx = ship.x + 0.5 - player.getX();
		double dz = ship.z + 0.5 - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
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
				register(mod, pos, pos, dimension, config, false);
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
								dimension, config, state.isOf(Blocks.DRAGON_HEAD));
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
			ChaosConfig.EndShipConfig config, boolean bow) {
		ShipPoint existing = findNear(dimension, pos, config.dedupeRadius);
		if (existing != null) {
			boolean changed = false;
			if (bow && !existing.hasDeck()) {
				// Dragon head: the only reliably "outside the cabin" anchor.
				existing.setDeck(pos.getX(), pos.getY(), pos.getZ());
				changed = true;
			}
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
		if (bow) {
			ship.setDeck(pos.getX(), pos.getY(), pos.getZ());
		}
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
		resetAutomation();
	}

	/**
	 * Called when the module is switched off. Only our own cruise leg is dropped;
	 * a trip the player started by hand is left alone.
	 */
	public void cancelOwnLeg() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return;
		}
		if (phase == Phase.SWEEP && mod.travel().isTarget(legX, legZ)) {
			mod.travel().stop(false);
		}
		if (phase == Phase.FLY || phase == Phase.APPROACH) {
			mod.baritoneBridge().cancel();
		}
	}

	/** Re-arms the loop after the player starts the flow again. */
	public void resumeAutomation() {		paused = false;
		chainStop = false;
		retryCooldown = 0;
		chainDelay = 0;
		phase = Phase.IDLE;
		target = null;
		// Re-anchor the cruise line on the player instead of resuming the old one.
		sweepArmed = false;
		sweepProgress = 0;
		legStartProgress = 0;
		sweepLegs = 0;
		stuckLegs = 0;
		warnedLegError = false;
	}

	public void resetForWorldChange() {
		scannedAt.clear();
		cursor = 0;
		tickCounter = 0;
		lastDimension = "";
		resetAutomation();
	}

	private void resetAutomation() {
		phase = Phase.IDLE;
		target = null;
		phaseTicks = 0;
		chainDelay = 0;
		retryCooldown = 0;
		pathIssued = false;
		paused = false;
		chainStop = false;
		sweepArmed = false;
		sweepProgress = 0;
		legStartProgress = 0;
		sweepLegs = 0;
		stuckLegs = 0;
		warnedLegError = false;
	}

	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || mod.client().world == null) {
			return "未在世界中";
		}
		String dimension = mod.client().world.getRegistryKey().getValue().toString();
		ChaosConfig.EndShipConfig config = mod.config().endship;
		String state = switch (phase) {
			case IDLE -> paused ? "已暂停" : "待机";
			case SWEEP -> {
				ClientPlayerEntity player = mod.client().player;
				long left = player == null ? 0
						: Math.round(Math.max(0, sweepProgress - progress(player, config)));
				yield "巡航 " + bearingName(config.sweepBearing) + "（第 " + sweepLegs + " 段，剩 " + left + " 格）";
			}
			case FLY -> "飞往 " + (target == null ? "?" : target.x + ", " + target.z);
			case APPROACH -> "接近展示框";
			case MANUAL -> "等待手动接近";
			case WAIT_LOOT -> "取鞘翅中";
			case EXIT -> "走回甲板";
		};
		return state + " · 已发现 " + shipsIn(dimension).size() + "（未搜 " + unlootedCount(dimension) + "）";
	}
}
