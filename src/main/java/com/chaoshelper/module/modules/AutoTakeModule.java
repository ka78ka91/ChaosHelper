package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;

/**
 * Takes an elytra out of a nearby item frame, with or without a travel goal.
 *
 * <p>Split out of {@code travel} in 1.0.2: it is useful on its own (End city
 * looting) and should not require a navigation target. Only vanilla attack
 * input is used; nothing is simulated or sent beyond that.
 */
public final class AutoTakeModule extends Module {
	private int cooldown;

	public AutoTakeModule() {
		super("autotake", "自动取鞘翅", false, true, true);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	protected void onDisable() {
	}

	@Override
	protected void tick() {
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = mod.client();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null) {
			return;
		}
		if (client.currentScreen != null) {
			return;
		}
		ChaosConfig.AutoTakeConfig config = mod.config().autotake;
		if (config.onlyWhenMissing
				&& player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
			return;
		}
		double rangeSquared = config.range * config.range;
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof ItemFrameEntity frame)) {
				continue;
			}
			if (!frame.getHeldItemStack().isOf(Items.ELYTRA)) {
				continue;
			}
			if (frame.squaredDistanceTo(player) > rangeSquared) {
				continue;
			}
			client.interactionManager.attackEntity(player, frame);
			cooldown = config.cooldownTicks;
			mod.notifications().info("已取下展示框中的鞘翅");
			// Tell the ship finder: this marks the ship as looted and, when
			// enabled, schedules the flight to the next unlooted one.
			mod.endShipFinder().lootNear(frame.getBlockPos());
			return;
		}
	}

	@Override
	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		return mod == null ? "" : "范围 " + mod.config().autotake.range + " 格";
	}
}
