package com.chaoshelper.world;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.minimap.XaeroWaypoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Marks End cities on the minimap while the player is in the End.
 *
 * <p>Shulkers only spawn in End cities, so they are a cheap beacon for the city
 * itself. End Ships are handled by {@link EndShipFinder}: shulkers never spawn
 * on a ship, and while flying you are usually too high to see one.
 */
public final class EndMarkerService {
	private static final int SCAN_INTERVAL_TICKS = 40;
	/** Two markers within this grid cell are considered the same structure. */
	private static final int CELL = 96;

	private final Set<String> marked = new HashSet<>();
	private int tickCounter;

	public void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			marked.clear();
			return;
		}
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || !mod.config().xaero.enabled || !mod.config().xaero.autoMarkEnd) {
			return;
		}
		if (!client.world.getRegistryKey().equals(World.END)) {
			return;
		}
		tickCounter++;
		if (tickCounter < SCAN_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;
		for (Entity entity : client.world.getEntities()) {
			if (entity instanceof ShulkerEntity shulker) {
				mark(mod, "末地城", shulker.getBlockPos());
			}
		}
	}

	private void mark(ChaosHelperClient mod, String name, BlockPos pos) {
		String key = name + ":" + Math.floorDiv(pos.getX(), CELL) + ":" + Math.floorDiv(pos.getZ(), CELL);
		if (marked.contains(key)) {
			return;
		}
		// Remember attempts too: a write failure must not retry (and log) every scan.
		marked.add(key);
		if (XaeroWaypoints.mark(name, pos)) {
			mod.notifications().info("已在小地图标记 " + name + " @ " + pos.getX() + ", " + pos.getZ());
		}
	}

	public void clear() {
		marked.clear();
		tickCounter = 0;
	}
}
