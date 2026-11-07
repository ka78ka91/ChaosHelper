package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Unpowered elytra climb: a periodic pitch cycle (dive - level - pull - climb)
 * that trades speed for height so a glide does not steadily lose altitude.
 *
 * <p>This class only ever calls {@code setPitch}/{@code getVelocity}/{@code getY}.
 * It never touches position, velocity or packets: the server remains fully
 * authoritative over the flight. It is disabled by default ({@code
 * travel.glideClimb}) and must be opted into by the player.
 *
 * <p>The {@link #tick} result is the pitch to apply, or {@code null} when the
 * assist has handed control back to ordinary goal steering (unsafe altitude,
 * obstruction, stall, or repeated failed cycles).
 */
public final class GlideClimbAssist {
	private enum Phase {
		DIVE, LEVEL, PULL, CLIMB, HOLD
	}

	private static final float DIVE_PITCH = 46.0F;
	private static final float LEVEL_PITCH = 2.0F;
	private static final float PULL_PITCH = -70.0F;
	private static final float EXIT_PITCH = -15.0F;
	private static final int LEVEL_TICKS = 20;
	private static final int PULL_TICKS = 6;
	private static final int HOLD_TICKS = 4;
	private static final int MAX_DIVE_TICKS = 200;
	private static final int MAX_CLIMB_TICKS = 160;
	/** Below this speed the glide is stalling; steering cannot recover it. */
	private static final double STALL_SPEED = 0.32;

	private Phase phase = Phase.DIVE;
	private int phaseTicks;
	private double diveStartY;
	private double cycleStartY;
	private double apexY;
	private int failedCycles;
	private boolean aborted;

	/** Full reset, e.g. after landing or when the player re-enables the option. */
	public void reset() {
		resetCycle();
		failedCycles = 0;
		aborted = false;
	}

	/** Restart the current cycle without clearing the abort latch. */
	private void resetCycle() {
		phase = Phase.DIVE;
		phaseTicks = 0;
		diveStartY = Double.NaN;
		cycleStartY = Double.NaN;
		apexY = Double.NaN;
	}

	public boolean isAborted() {
		return aborted;
	}

	public String status() {
		if (aborted) {
			return "爬升[已放弃]";
		}
		return switch (phase) {
			case DIVE -> "爬升[俯冲]";
			case LEVEL -> "爬升[拉平]";
			case PULL -> "爬升[拉杆]";
			case CLIMB -> "爬升[爬升]";
			case HOLD -> "爬升[顶点]";
		};
	}

	/**
	 * @param clearance blocks of free air below the player (computed by the caller)
	 * @return target pitch, or null to fall back to normal steering
	 */
	public Float tick(ClientPlayerEntity player, ClientWorld world, int clearance,
			ChaosConfig.TravelConfig config) {
		if (aborted) {
			return null;
		}
		Vec3d velocity = player.getVelocity();
		if (velocity.lengthSquared() < STALL_SPEED * STALL_SPEED) {
			// A stall cannot be steered out of; stop cycling rather than flailing.
			abort("无动力爬升：速度过低，已退回普通滑翔（可重新展开后自动恢复）");
			return null;
		}
		if (clearance < config.glideClimbClearance) {
			resetCycle();
			return null;
		}
		if (isObstructed(world, player, velocity)) {
			resetCycle();
			return null;
		}

		double y = player.getY();
		phaseTicks++;
		float current = player.getPitch();

		switch (phase) {
			case DIVE -> {
				if (phaseTicks == 1) {
					diveStartY = y;
					cycleStartY = y;
					apexY = y;
				}
				float pitch = approach(current, DIVE_PITCH, 1.5F);
				if (y <= diveStartY - config.glideClimbDiveBlocks || phaseTicks >= MAX_DIVE_TICKS) {
					next(Phase.LEVEL);
				}
				return pitch;
			}
			case LEVEL -> {
				float pitch = approach(current, LEVEL_PITCH, 4.0F);
				if (phaseTicks >= LEVEL_TICKS) {
					next(Phase.PULL);
				}
				return pitch;
			}
			case PULL -> {
				float pitch = approach(current, PULL_PITCH, 12.0F);
				if (phaseTicks >= PULL_TICKS) {
					next(Phase.CLIMB);
				}
				return pitch;
			}
			case CLIMB -> {
				apexY = Math.max(apexY, y);
				// Ease the nose down from -70 toward -15 so speed is not dumped at once.
				float target = PULL_PITCH + (EXIT_PITCH - PULL_PITCH)
						* (float) Math.min(1.0, phaseTicks / 60.0);
				float pitch = approach(current, target, 2.0F);
				boolean peaked = apexY > cycleStartY + 2.0 && velocity.y <= 0.0 && phaseTicks > 10;
				if (peaked || phaseTicks >= MAX_CLIMB_TICKS) {
					next(Phase.HOLD);
				}
				return pitch;
			}
			case HOLD -> {
				float pitch = approach(current, EXIT_PITCH, 3.0F);
				if (phaseTicks >= HOLD_TICKS) {
					if (apexY - cycleStartY <= 0.0) {
						failedCycles++;
					} else {
						failedCycles = 0;
					}
					if (failedCycles >= 3) {
						abort("无动力爬升未生效（连续 3 次循环无净上升），已退回普通滑翔");
						return null;
					}
					next(Phase.DIVE);
				}
				return pitch;
			}
			default -> {
				return null;
			}
		}
	}

	private void next(Phase next) {
		phase = next;
		phaseTicks = 0;
	}

	private void abort(String message) {
		aborted = true;
		resetCycle();
		ChaosHelperClient.get().notifications().error(message);
	}

	private static float approach(float current, float target, float step) {
		return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
	}

	/** Cheap forward probe: three samples along the current velocity direction. */
	private static boolean isObstructed(ClientWorld world, ClientPlayerEntity player, Vec3d velocity) {
		double horizontal = Math.hypot(velocity.x, velocity.z);
		if (horizontal < 0.01) {
			return false;
		}
		double nx = velocity.x / horizontal;
		double nz = velocity.z / horizontal;
		Vec3d eye = player.getEyePos();
		for (int distance = 6; distance <= 18; distance += 6) {
			int bx = (int) Math.floor(eye.x + nx * distance);
			int by = (int) Math.floor(eye.y + velocity.y * 0.3);
			int bz = (int) Math.floor(eye.z + nz * distance);
			if (!world.getBlockState(new BlockPos(bx, by, bz)).isAir()) {
				return true;
			}
		}
		return false;
	}
}
