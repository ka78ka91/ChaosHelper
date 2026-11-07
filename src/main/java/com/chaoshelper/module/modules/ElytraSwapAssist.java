package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * "Armour swap" (甲飞): briefly move the elytra out of the chest slot just before
 * the server's one-second durability tick, then put it back and re-open it.
 *
 * <p>Why this works on 1.21.11: {@code LivingEntity#tickGliding} damages the
 * glider when {@code glidingTicks % 20 == 0}, and {@code LivingEntity#tick}
 * resets {@code glidingTicks} to 0 on any tick where {@code isGliding()} is
 * false. Taking the elytra off makes the server's {@code canGlide()} false, so
 * it drops the gliding flag and clears the counter; re-equipping plus a normal
 * {@code START_FALL_FLYING} command restarts the count from zero.
 *
 * <p>Everything here is a packet a human sends by clicking their inventory or
 * pressing jump. No position, velocity or state is fabricated, and the server
 * remains authoritative. Because the counter only resets on a server tick, the
 * swap MUST span a tick: we wait until the server has synced
 * {@code !isGliding()} back before re-equipping, instead of firing two clicks
 * in the same tick (which would silently do nothing).
 *
 * <p>Disabled by default ({@code travel.elytraSwap}); the player opts in.
 */
public final class ElytraSwapAssist {
	private enum Phase {
		IDLE, WAIT_STOPPED, WAIT_RESUME
	}

	/** Chest slot inside PlayerScreenHandler: EQUIPMENT_START is the helmet. */
	private static final int CHEST_SLOT_ID = PlayerScreenHandler.EQUIPMENT_START + 1;
	private static final int MAX_WAIT_TICKS = 15;
	private static final int COOLDOWN_TICKS = 10;

	private Phase phase = Phase.IDLE;
	private int waitTicks;
	private int cooldownTicks;
	private int swapSlot = -1;
	private int swapCount;
	private int failures;
	private boolean swappedOut;
	private boolean aborted;
	private boolean warnedNoSlot;

	/** Full reset after landing / re-enabling the option. */
	public void resetRun() {
		phase = Phase.IDLE;
		waitTicks = 0;
		cooldownTicks = 0;
		swapSlot = -1;
		swapCount = 0;
		failures = 0;
		aborted = false;
		warnedNoSlot = false;
	}

	public boolean isAborted() {
		return aborted;
	}

	public boolean hasPendingSwap() {
		return swappedOut;
	}

	/** True while a swap cycle is waiting for the server, i.e. not safely interruptible. */
	public boolean isMidCycle() {
		return phase != Phase.IDLE;
	}

	public int swapCount() {
		return swapCount;
	}

	public String status() {
		if (aborted) {
			return "甲飞[已放弃]";
		}
		return switch (phase) {
			case IDLE -> "甲飞[待机]";
			case WAIT_STOPPED -> "甲飞[等待停飞]";
			case WAIT_RESUME -> "甲飞[等待展开]";
		};
	}

	/**
	 * Safety net, called every tick by the module even when the option is off:
	 * if a swap was interrupted (GUI, input, panic, option toggled off), put the
	 * elytra back as soon as the player's own inventory is current again.
	 */
	public void ensureRestored(MinecraftClient client, ClientPlayerEntity player) {
		if (!swappedOut || player == null || client.interactionManager == null) {
			return;
		}
		if (client.currentScreen != null || !(player.currentScreenHandler instanceof PlayerScreenHandler)) {
			return;
		}
		if (!swap(client, player, swapSlot)) {
			return;
		}
		swappedOut = false;
		swapSlot = -1;
		if (!player.isGliding() && !player.isOnGround()) {
			player.networkHandler.sendPacket(new ClientCommandC2SPacket(player,
					ClientCommandC2SPacket.Mode.START_FALL_FLYING));
		}
		ChaosHelperClient.get().notifications().info("甲飞已中断，鞘翅已装回");
	}

	/** Immediately stop cycling and restore equipment; keeps the abort latch. */
	public void abort(MinecraftClient client, ClientPlayerEntity player, String reason) {
		phase = Phase.IDLE;
		waitTicks = 0;
		boolean wasSwapped = swappedOut;
		if (swappedOut) {
			ensureRestored(client, player);
		}
		// Avoid spamming the HUD when the interrupting input is held down.
		if (reason != null && (wasSwapped || !aborted)) {
			ChaosHelperClient.get().notifications().error(reason);
		}
		aborted = true;
	}

	/**
	 * @param clearance blocks of free air below the player (computed by the caller)
	 */
	public void tick(MinecraftClient client, ClientPlayerEntity player, int clearance,
			ChaosConfig.TravelConfig config) {
		if (client.interactionManager == null) {
			return;
		}
		// Landing (or manually stopping the glide) clears the latch for the next flight.
		if (phase == Phase.IDLE && !player.isGliding()) {
			if (swapCount > 0 || cooldownTicks > 0 || failures > 0 || aborted) {
				resetRun();
			}
			return;
		}
		if (aborted) {
			return;
		}
		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}

		switch (phase) {
			case IDLE -> {
				if (!canRun(client, player, clearance, config)) {
					return;
				}
				int trigger = Math.max(4, 20 - config.elytraSwapLeadTicks);
				if (player.getGlidingTicks() < trigger) {
					return;
				}
				int slot = findSwapSlot(player);
				if (slot < 0) {
					if (!warnedNoSlot) {
						warnedNoSlot = true;
						ChaosHelperClient.get().notifications().error(
								"甲飞：快捷栏需要一格空位或一件备用胸甲，本次已跳过");
					}
					return;
				}
				warnedNoSlot = false;
				if (!swap(client, player, slot)) {
					return;
				}
				swappedOut = true;
				swapSlot = slot;
				phase = Phase.WAIT_STOPPED;
				waitTicks = 0;
			}
			case WAIT_STOPPED -> {
				waitTicks++;
				if (!player.isGliding()) {
					// Server dropped the flag (and zeroed glidingTicks) and told us so.
					if (!swapBack(client, player)) {
						return;
					}
					phase = Phase.WAIT_RESUME;
					waitTicks = 0;
					return;
				}
				if (waitTicks >= MAX_WAIT_TICKS) {
					fail(client, player, "甲飞未生效（服务端未同步停飞），已恢复装备");
				}
			}
			case WAIT_RESUME -> {
				waitTicks++;
				if (player.isGliding()) {
					swapCount++;
					failures = 0;
					cooldownTicks = COOLDOWN_TICKS;
					phase = Phase.IDLE;
					waitTicks = 0;
					if (config.elytraSwapMaxSwaps > 0 && swapCount >= config.elytraSwapMaxSwaps) {
						aborted = true;
						ChaosHelperClient.get().notifications().info(
								"甲飞已达本轮切换上限 " + swapCount + " 次，落地后自动重置");
					}
					return;
				}
				if (waitTicks >= MAX_WAIT_TICKS) {
					fail(client, player, "甲飞：鞘翅未重新展开，请手动按空格（装备已恢复）");
				}
			}
			default -> {
				resetRun();
			}
		}
	}

	private boolean canRun(MinecraftClient client, ClientPlayerEntity player, int clearance,
			ChaosConfig.TravelConfig config) {
		if (client.currentScreen != null || !(player.currentScreenHandler instanceof PlayerScreenHandler)) {
			return false;
		}
		if (!player.isGliding() || !player.isAlive()) {
			return false;
		}
		if (clearance < config.elytraSwapClearance) {
			return false;
		}
		ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
		if (!chest.contains(DataComponentTypes.GLIDER) || chest.willBreakNextUse()) {
			return false;
		}
		int limit = config.elytraSwapDurabilityLimit;
		if (limit > 0 && chest.getMaxDamage() - chest.getDamage() > limit) {
			return false;
		}
		// Sanity check that slot 6 really is the chest slot before clicking it.
		ItemStack inSlot = player.currentScreenHandler.getSlot(CHEST_SLOT_ID).getStack();
		return ItemStack.areEqual(chest, inSlot);
	}

	/**
	 * Prefer a spare chestplate (the literal "switch to armour"), then an empty
	 * hotbar slot. Never disturb the currently held slot unless nothing else works.
	 */
	private static int findSwapSlot(ClientPlayerEntity player) {
		int selected = player.getInventory().getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			if (i == selected) {
				continue;
			}
			if (isSpareChestArmour(player.getInventory().getStack(i))) {
				return i;
			}
		}
		for (int i = 0; i < 9; i++) {
			if (i == selected) {
				continue;
			}
			if (player.getInventory().getStack(i).isEmpty()) {
				return i;
			}
		}
		return player.getInventory().getStack(selected).isEmpty() ? selected : -1;
	}

	private static boolean isSpareChestArmour(ItemStack stack) {
		if (stack.isEmpty() || stack.contains(DataComponentTypes.GLIDER)) {
			return false;
		}
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
	}

	/** One real inventory click: moves the elytra out of (or back into) the chest slot. */
	private boolean swap(MinecraftClient client, ClientPlayerEntity player, int hotbarSlot) {
		if (hotbarSlot < 0 || hotbarSlot > 8) {
			return false;
		}
		if (!(player.currentScreenHandler instanceof PlayerScreenHandler)) {
			return false;
		}
		client.interactionManager.clickSlot(player.currentScreenHandler.syncId, CHEST_SLOT_ID,
				hotbarSlot, SlotActionType.SWAP, player);
		return true;
	}

	private boolean swapBack(MinecraftClient client, ClientPlayerEntity player) {
		if (!swap(client, player, swapSlot)) {
			return false;
		}
		swappedOut = false;
		swapSlot = -1;
		player.networkHandler.sendPacket(new ClientCommandC2SPacket(player,
				ClientCommandC2SPacket.Mode.START_FALL_FLYING));
		return true;
	}

	private void fail(MinecraftClient client, ClientPlayerEntity player, String reason) {
		failures++;
		if (swappedOut) {
			ensureRestored(client, player);
		}
		phase = Phase.IDLE;
		waitTicks = 0;
		cooldownTicks = COOLDOWN_TICKS * 2;
		if (failures >= 2) {
			aborted = true;
			ChaosHelperClient.get().notifications().error(
					"甲飞连续失败，已自动放弃（改用耐久III+经验修补更稳妥）: " + reason);
		} else {
			ChaosHelperClient.get().notifications().error(reason);
		}
	}
}
