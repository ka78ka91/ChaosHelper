package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;
import com.chaoshelper.util.InputWatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Minimal survival auto-eat. It only selects visible hotbar food, starts use,
 * and restores the original slot. Any player input cancels immediately.
 */
public final class AutoEatModule extends Module {
	private int cooldownTicks;
	private int originalSlot = -1;
	private int eatingTicks;
	private boolean startedUsing;
	private boolean eating;

	public AutoEatModule() {
		super("autoeat", "自动进食", false, true, true);
	}

	@Override
	protected void onEnable() {
		resetState();
	}

	@Override
	protected void onDisable() {
		stopAndRestore(true);
		resetState();
	}

	@Override
	protected void tick() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = mod.client();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			resetState();
			return;
		}
		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}
		if (client.currentScreen != null) {
			stopAndRestore(false);
			return;
		}
		if (InputWatcher.hasInterruptingInput(client)) {
			if (eating) {
				stopAndRestore(true);
				mod.notifications().info("检测到玩家输入，自动进食已取消");
			}
			cooldownTicks = mod.config().autoEat.cooldownTicks;
			return;
		}
		if (player.isSpectator() || !player.isAlive()) {
			stopAndRestore(true);
			return;
		}

		if (eating) {
			eatingTicks++;
			if (player.isUsingItem()) {
				startedUsing = true;
			}
			if (startedUsing && !player.isUsingItem()) {
				finishEating();
				return;
			}
			if (player.getMainHandStack().isEmpty() || eatingTicks > 120) {
				finishEating();
			}
			return;
		}

		int foodLevel = player.getHungerManager().getFoodLevel();
		if (foodLevel >= mod.config().autoEat.threshold) {
			return;
		}
		int foodSlot = findFoodSlot(player, mod.config().autoEat.preferHighestNutrition);
		if (foodSlot < 0) {
			cooldownTicks = mod.config().autoEat.cooldownTicks;
			mod.notifications().info("自动进食: 快捷栏没有可用食物");
			return;
		}
		originalSlot = player.getInventory().getSelectedSlot();
		if (originalSlot != foodSlot) {
			switchSlot(player, foodSlot);
		}
		ActionResult result = client.interactionManager == null ? ActionResult.FAIL
				: client.interactionManager.interactItem(player, Hand.MAIN_HAND);
		if (result.isAccepted() || player.isUsingItem()) {
			eating = true;
			eatingTicks = 0;
			startedUsing = player.isUsingItem();
		} else {
			stopAndRestore(true);
			cooldownTicks = 20;
		}
	}

	private int findFoodSlot(ClientPlayerEntity player, boolean preferNutrition) {
		int bestSlot = -1;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}
			FoodComponent food = stack.get(DataComponentTypes.FOOD);
			if (food == null || player.getItemCooldownManager().isCoolingDown(stack)) {
				continue;
			}
			double score = preferNutrition
					? food.nutrition() * 100.0 + food.saturation()
					: slot == player.getInventory().getSelectedSlot() ? 1.0 : 0.0;
			if (score > bestScore) {
				bestScore = score;
				bestSlot = slot;
			}
		}
		return bestSlot;
	}

	private void switchSlot(ClientPlayerEntity player, int slot) {
		player.getInventory().setSelectedSlot(slot);
		if (player.networkHandler != null) {
			player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
		}
	}

	private void finishEating() {
		stopAndRestore(false);
		cooldownTicks = 10;
	}

	private void stopAndRestore(boolean stopUsing) {
		MinecraftClient client = ChaosHelperClient.get().client();
		ClientPlayerEntity player = client.player;
		if (stopUsing && player != null && player.isUsingItem() && client.interactionManager != null) {
			client.interactionManager.stopUsingItem(player);
		}
		if (player != null && originalSlot >= 0 && originalSlot < 9
				&& ChaosHelperClient.get().config().autoEat.restoreSlot
				&& player.getInventory().getSelectedSlot() != originalSlot) {
			switchSlot(player, originalSlot);
		}
		originalSlot = -1;
		eating = false;
		startedUsing = false;
		eatingTicks = 0;
	}

	private void resetState() {
		eating = false;
		startedUsing = false;
		eatingTicks = 0;
		originalSlot = -1;
		cooldownTicks = 0;
	}

	@Override
	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return "";
		}
		return (eating ? "进食中" : "待机") + " | 阈值 " + mod.config().autoEat.threshold;
	}
}
