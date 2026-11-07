package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.util.InputWatcher;
import com.chaoshelper.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Unified travel: walking (Baritone custom goal), direct elytra flight (Baritone
 * elytra process) and local glide steering (no fireworks).
 *
 * <p>Every mode is interruptible: any player movement/attack input, opening a
 * screen, panic, death or a dimension change stops it immediately.
 */
public final class TravelModule extends Module {
	public enum Mode {
		/** Pick walk or elytra from the current state and distance. */
		AUTO,
		/** Baritone path finding on foot. */
		WALK,
		/** Baritone elytra process: straight flight to the goal (may use fireworks). */
		ELYTRA,
		/** Local steering only: aim at the goal and glide, never uses fireworks. */
		GLIDE
	}

	private boolean hasTarget;
	private Mode activeMode = Mode.WALK;
	private int targetX;
	private int targetY;
	private int targetZ;
	private String targetLabel = "";
	private int ticksSinceStart;
	private int ticksSinceArrive;
	private boolean tookElytra;

	/** Both assists are opt-in: they only run when the matching config flag is on. */
	private final GlideClimbAssist climb = new GlideClimbAssist();
	private final ElytraSwapAssist swap = new ElytraSwapAssist();
	private boolean climbOptionLast;
	private boolean swapOptionLast;
	private int ticksNotGliding;

	public TravelModule() {
		super("travel", "旅行 (步行/鞘翅)", false, true, false);
	}

	public boolean hasTarget() {
		return hasTarget;
	}

	public String targetText() {
		return hasTarget ? targetX + ", " + targetY + ", " + targetZ : "无";
	}

	public Mode activeMode() {
		return activeMode;
	}

	/**
	 * @return null on success, or a user-facing error message
	 */
	public String start(int x, int y, int z, Mode requested, String label) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = mod.client();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return "当前不在世界中";
		}
		double distance = Math.sqrt(player.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5));
		if (distance > mod.config().travel.maxDistance) {
			return String.format(Locale.ROOT, "目标距离 %.0f 超过上限 %.0f", distance,
					mod.config().travel.maxDistance);
		}
		Mode mode = resolveMode(requested, player, distance);
		if (mode == Mode.GLIDE) {
			if (!player.isGliding()) {
				return "滑翔模式需要先处于滑翔状态（或改用 elytra 直飞）";
			}
		}
		if (mode == Mode.ELYTRA) {
			if (!mod.baritoneBridge().isElytraAvailable()) {
				return "ElytraFly 不可用: " + mod.baritoneBridge().elytraUnavailableReason();
			}
			// The Nether has a low ceiling, so a minimum altitude actually matters
			// there. Overworld/End flights start from any height.
			boolean nether = client.world.getRegistryKey().equals(net.minecraft.world.World.NETHER);
			if (nether && player.getY() < mod.config().travel.minY) {
				return String.format(Locale.ROOT, "下界 Y=%.0f 低于安全下限 %.0f", player.getY(),
						mod.config().travel.minY);
			}
			if (!player.isGliding()) {
				mod.notifications().info("鞘翅飞行已设定：跳下并展开鞘翅后会自动接管");
			}
		}
		stop(false);
		if (mode == Mode.WALK) {
			String error = mod.baritoneBridge().startPath(x, y, z);
			if (error != null) {
				return error;
			}
		} else if (mode == Mode.ELYTRA) {
			String error = mod.baritoneBridge().startElytra(x, z);
			if (error != null) {
				return error;
			}
		}
		targetX = x;
		targetY = y;
		targetZ = z;
		targetLabel = label == null ? "" : label;
		activeMode = mode;
		hasTarget = true;
		ticksSinceStart = 0;
		ticksSinceArrive = 0;
		tookElytra = false;
		mod.notifications().info("开始旅行(" + modeLabel(mode) + "): " + targetText()
				+ (targetLabel.isBlank() ? "" : " " + targetLabel));
		return null;
	}

	private Mode resolveMode(Mode requested, ClientPlayerEntity player, double distance) {
		if (requested != null && requested != Mode.AUTO) {
			return requested;
		}
		ChaosHelperClient mod = ChaosHelperClient.get();
		boolean canFly = mod.baritoneBridge().isElytraAvailable()
				&& (player.isGliding() || player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA));
		if (mod.config().travel.glide && player.isGliding()) {
			return Mode.GLIDE;
		}
		String configured = mod.config().travel.mode;
		if ("walk".equals(configured)) {
			return Mode.WALK;
		}
		if ("elytra".equals(configured)) {
			return canFly ? Mode.ELYTRA : Mode.WALK;
		}
		if (canFly && distance > 96.0) {
			return Mode.ELYTRA;
		}
		return Mode.WALK;
	}

	public void stop(boolean notify) {
		boolean wasActive = hasTarget;
		hasTarget = false;
		ticksSinceArrive = 0;
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod != null) {
			mod.baritoneBridge().cancel();
		}
		climb.reset();
		restoreIfNeeded();
		if (notify && wasActive && mod != null) {
			mod.notifications().info("旅行已停止");
		}
	}

	@Override
	protected void onEnable() {
		hasTarget = false;
		resetAssists();
	}

	@Override
	protected void onDisable() {
		stop(false);
	}

	@Override
	protected void tick() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = mod.client();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			restoreIfNeeded();
			if (hasTarget) {
				stop(true);
			}
			return;
		}

		// Safety net: outside a healthy in-flight swap cycle, always try to put
		// the elytra back. It must NOT run during WAIT_STOPPED - doing so would
		// restore the chest slot before the server tick we are waiting for and
		// silently defeat the whole durability-reset trick.
		ChaosConfig.TravelConfig travel = mod.config().travel;
		syncOptionEdges(travel);

		boolean interrupted = (travel.cancelOnScreen && client.currentScreen != null)
				|| (travel.cancelOnInput && InputWatcher.hasInterruptingInput(client))
				|| !player.isAlive();
		boolean swapRunning = travel.elytraSwap && !interrupted && !swap.isAborted()
				&& client.currentScreen == null && player.isAlive();
		if (!swapRunning || (!player.isGliding() && !swap.isMidCycle())) {
			swap.ensureRestored(client, player);
		}

		if (interrupted) {
			interruptAssists(client, player);
			if (hasTarget) {
				stop(true);
			}
			return;
		}

		boolean gliding = player.isGliding();
		if (!gliding) {
			// A swap briefly stops the glide. Give the climb state machine a grace
			// window so the two assists do not reset each other every second.
			if (++ticksNotGliding >= 15) {
				climb.reset();
			}
		} else {
			ticksNotGliding = 0;
		}

		int clearance = Integer.MAX_VALUE;
		if ((gliding || swap.isMidCycle()) && (travel.glideClimb || travel.elytraSwap)) {
			int cap = Math.max(travel.glideClimbClearance, travel.elytraSwapClearance) + 8;
			clearance = clearanceBelow(client.world, player, cap);
		}

		// The two assists are independent from having a navigation goal.
		// Do NOT gate the swap on isGliding(): its WAIT_STOPPED phase is precisely
		// the tick where the server reports !isGliding(), and skipping it would
		// strand the elytra in the hotbar forever.
		if (travel.elytraSwap) {
			swap.tick(client, player, clearance, travel);
		}
		Float climbPitch = null;
		if (gliding && travel.glideClimb && !climb.isAborted()) {
			climbPitch = climb.tick(player, client.world, clearance, travel);
		}

		if (!hasTarget) {
			// Opt-in climb also works while free-flying: yaw is left untouched.
			if (climbPitch != null) {
				player.setPitch(climbPitch);
			}
			return;
		}

		ticksSinceStart++;
		double dx = targetX + 0.5 - player.getX();
		double dy = targetY + 0.5 - player.getY();
		double dz = targetZ + 0.5 - player.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		int arrive = mod.config().travel.arriveRadius;

		if (activeMode == Mode.GLIDE) {
			// A swap briefly stops the glide; only a sustained stop means landing.
			if (ticksNotGliding >= 15) {
				hasTarget = false;
				mod.notifications().info("滑翔结束（已落地），旅行已停止");
				return;
			}
			if (gliding) {
				steerGlide(player, dx, dy, dz, horizontal, climbPitch);
			}
			if (horizontal <= arrive + 2 && Math.abs(dy) <= 8) {
				arrive(player);
			}
			return;
		}
		if (horizontal <= arrive && Math.abs(dy) <= Math.max(3, arrive)) {
			arrive(player);
			return;
		}
		if (activeMode == Mode.WALK) {
			if (!mod.baritoneBridge().isPathing() && ticksSinceStart > 60) {
				hasTarget = false;
				// Drop any residual Baritone goal so nothing keeps running behind our back.
				mod.baritoneBridge().cancel();
				mod.notifications().info("步行寻路已结束");
			}
		} else if (activeMode == Mode.ELYTRA) {
			if (!mod.baritoneBridge().isElytraActive() && ticksSinceStart > 100) {
				// Baritone may finish slightly above the goal; keep the marker until arrival check.
				if (horizontal > arrive + 4) {
					hasTarget = false;
					mod.baritoneBridge().cancel();
					mod.notifications().info("鞘翅飞行已结束");
				}
			}
		}
	}

	/** Local steering: aim at the goal, no fireworks, no extra packets. */
	private void steerGlide(ClientPlayerEntity player, double dx, double dy, double dz, double horizontal,
			Float climbPitch) {
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch;
		if (climbPitch != null) {
			pitch = climbPitch;
		} else {
			pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.max(1.0, horizontal)));
			pitch = Math.max(-55.0F, Math.min(55.0F, pitch));
		}
		player.setYaw(yaw);
		player.setPitch(pitch);
	}

	/** Called by the options screen and commands when the player flips a switch. */
	public void resetAssists() {
		climb.reset();
		swap.resetRun();
	}

	public String assistStatus() {
		return climb.status() + " / " + swap.status() + " (本轮切换 " + swap.swapCount() + " 次)";
	}

	private void syncOptionEdges(ChaosConfig.TravelConfig travel) {
		if (travel.glideClimb && !climbOptionLast) {
			climb.reset();
		}
		if (travel.elytraSwap && !swapOptionLast) {
			swap.resetRun();
		}
		climbOptionLast = travel.glideClimb;
		swapOptionLast = travel.elytraSwap;
	}

	private void interruptAssists(MinecraftClient client, ClientPlayerEntity player) {
		climb.reset();
		swap.abort(client, player, swap.hasPendingSwap() ? "鞘翅辅助已打断，鞘翅已装回" : null);
	}

	private void restoreIfNeeded() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return;
		}
		MinecraftClient client = mod.client();
		if (client != null && client.player != null) {
			swap.ensureRestored(client, client.player);
		}
	}

	/** Blocks of free air directly below the player; the scan is capped on purpose. */
	private static int clearanceBelow(ClientWorld world, ClientPlayerEntity player, int cap) {
		int x = player.getBlockX();
		int z = player.getBlockZ();
		int startY = player.getBlockY() - 1;
		int bottom = Math.max(world.getBottomY(), startY - cap);
		for (int y = startY; y >= bottom; y--) {
			if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) {
				return startY - y;
			}
		}
		return cap;
	}

	private void arrive(ClientPlayerEntity player) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		ticksSinceArrive++;
		if (ticksSinceArrive == 1) {
			mod.notifications().info("已到达 " + (targetLabel.isBlank() ? targetText() : targetLabel));
			stop(false);
			if (mod.config().travel.autoTakeElytra) {
				takeElytra(player);
			}
		}
	}

	/** Breaks a nearby elytra item frame once; the drop is picked up normally. */
	private void takeElytra(ClientPlayerEntity player) {
		if (tookElytra) {
			return;
		}
		MinecraftClient client = ChaosHelperClient.get().client();
		if (client.world == null || client.interactionManager == null) {
			return;
		}
		Vec3d eye = player.getEyePos();
		for (Entity entity : client.world.getEntities()) {
			if (entity instanceof ItemFrameEntity frame && frame.getHeldItemStack().isOf(Items.ELYTRA)
					&& frame.squaredDistanceTo(eye) <= 16.0) {
				client.interactionManager.attackEntity(player, frame);
				tookElytra = true;
				ChaosHelperClient.get().notifications().info("已取下物品展示框中的鞘翅");
				return;
			}
		}
	}

	@Override
	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return "";
		}
		String suffix = (mod.config().travel.glideClimb || mod.config().travel.elytraSwap)
				? " | " + assistStatus() : "";
		if (!mod.baritoneBridge().isAvailable()) {
			return "Baritone 不可用（滑翔仍可用）" + suffix;
		}
		return (hasTarget ? modeLabel(activeMode) + " → " + targetText() : "无目标") + suffix;
	}

	public static String modeLabel(Mode mode) {
		return switch (mode) {
			case AUTO -> "自动";
			case WALK -> "步行";
			case ELYTRA -> "鞘翅直飞";
			case GLIDE -> "滑翔";
		};
	}
}
