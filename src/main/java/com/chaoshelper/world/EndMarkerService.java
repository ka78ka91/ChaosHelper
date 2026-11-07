package com.chaoshelper.world;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.minimap.XaeroWaypoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Marks End cities and elytra frames on the minimap while the player is in the End.
 *
 * <p>Detection is purely client-visible: shulkers only spawn in End cities, and the
 * elytra is read from the item frame's held stack. No world generation or seed
 * access is involved.
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
			if (entity instanceof ItemFrameEntity frame && frame.getHeldItemStack().isOf(Items.ELYTRA)) {
				mark(mod, "鞘翅", frame.getBlockPos());
			} else if (entity instanceof ShulkerEntity shulker) {
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
