package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Leaves the server before a death that would drop the inventory.
 *
 * <p>Only fires when the player is already at or below the configured health
 * and has no totem of undying anywhere in the inventory (main slots, hotbar,
 * armour or offhand). It never touches the server: {@code MinecraftClient
 * .disconnect} closes our own connection the same way the "disconnect" button
 * does.
 */
public final class AutoLogModule extends Module {
	private int counter;
	private boolean triggered;

	public AutoLogModule() {
		super("autolog", "自动退服", false, true, true);
	}

	@Override
	protected void onEnable() {
		counter = 0;
		triggered = false;
	}

	@Override
	protected void onDisable() {
		triggered = false;
	}

	@Override
	protected void tick() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return;
		}
		MinecraftClient client = mod.client();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			// Back at the title screen / server list: arm again for the next join.
			triggered = false;
			return;
		}
		if (triggered) {
			return;
		}
		ChaosConfig.AutoLogConfig config = mod.config().autolog;
		if (config.onlyMultiplayer && client.isInSingleplayer()) {
			return;
		}
		counter++;
		if (counter < Math.max(1, config.checkIntervalTicks)) {
			return;
		}
		counter = 0;
		if (!player.isAlive()) {
			return;
		}
		float health = player.getHealth() + player.getAbsorptionAmount();
		if (health > config.healthThreshold) {
			return;
		}
		if (config.requireNoTotem && hasTotem(player)) {
			return;
		}
		triggered = true;
		String reason = String.format(Locale.ROOT, "AutoLog: 生命值 %.1f 且没有不死图腾，已自动退服", health);
		mod.notifications().error(reason);
		ChaosHelperClient.LOGGER.warn(reason);
		client.disconnect(Text.literal(reason));
	}

	private static boolean hasTotem(ClientPlayerEntity player) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return "";
		}
		ChaosConfig.AutoLogConfig config = mod.config().autolog;
		return String.format(Locale.ROOT, "≤%.0f 血%s", config.healthThreshold,
				config.requireNoTotem ? " 且无图腾" : "");
	}
}
