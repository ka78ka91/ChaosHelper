package com.chaoshelper.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Health bookkeeping for tracked players.
 *
 * <p>Rules (deliberately conservative, never bypasses hidden health):
 * <ul>
 *   <li>While a player is visible the value comes from the client entity and is
 *       labelled as synced. That is the only authoritative source.</li>
 *   <li>After they leave view the last value is kept and labelled "可能过期".</li>
 *   <li>After {@code resetSeconds} the value resets to full health and is
 *       labelled "估算" — a guess, not a measurement.</li>
 *   <li>A value that only exists in the tab list (never seen in world) is shown
 *       as a full-health estimate as well, because the client has no other data.</li>
 * </ul>
 */
public final class HealthEstimator {
	public record Estimate(float health, float maxHealth, String source, String confidence,
			double error, boolean estimated, long firstSeenMillis, long lastVisibleMillis) {
		public static final Estimate UNKNOWN = new Estimate(-1.0F, -1.0F, "未加载", "无", -1.0, true, 0L, 0L);
	}

	private static final float DEFAULT_MAX_HEALTH = 20.0F;
	private static final long VISIBLE_GRACE_MILLIS = 1500L;

	private final Map<UUID, State> states = new HashMap<>();

	public Estimate observe(UUID uuid, float health, float maxHealth, long now) {
		State state = states.computeIfAbsent(uuid, ignored -> new State());
		if (state.firstSeenMillis <= 0) {
			state.firstSeenMillis = now;
		}
		if (maxHealth > 0.0F) {
			state.maxHealth = maxHealth;
		}
		if (health >= 0.0F) {
			state.health = health;
		} else if (state.health < 0.0F) {
			state.health = state.maxHealth;
		}
		state.lastVisibleMillis = now;
		state.everSeen = true;
		// A visible player is always a synced value: the server sent the entity data.
		return new Estimate(state.health, state.maxHealth, "客户端同步", "高", 0.5, false,
				state.firstSeenMillis, state.lastVisibleMillis);
	}

	public Estimate absent(UUID uuid, long now, int resetSeconds, double errorBase) {
		State state = states.get(uuid);
		if (state == null) {
			return Estimate.UNKNOWN;
		}
		if (!state.everSeen) {
			return new Estimate(DEFAULT_MAX_HEALTH, DEFAULT_MAX_HEALTH, "估算", "低",
					DEFAULT_MAX_HEALTH / 2.0, true, 0L, 0L);
		}
		long away = Math.max(0L, now - state.lastVisibleMillis);
		long resetMillis = Math.max(5, resetSeconds) * 1000L;
		if (away <= resetMillis) {
			double error = Math.min(20.0, errorBase + (away / 1000.0) * 0.5);
			return new Estimate(state.health, state.maxHealth, "最后可见", "可能过期", error, false,
					state.firstSeenMillis, state.lastVisibleMillis);
		}
		return new Estimate(state.maxHealth, state.maxHealth, "估算 (重置)", "低",
				state.maxHealth / 2.0, true, state.firstSeenMillis, state.lastVisibleMillis);
	}

	public boolean everSeen(UUID uuid) {
		State state = states.get(uuid);
		return state != null && state.everSeen;
	}

	public void markInvisible(UUID uuid, long now) {
		State state = states.get(uuid);
		if (state != null && state.lastVisibleMillis <= 0) {
			state.lastVisibleMillis = now;
		}
	}

	public void forget(UUID uuid) {
		states.remove(uuid);
	}

	public void clear() {
		states.clear();
	}

	private static final class State {
		float health = -1.0F;
		float maxHealth = DEFAULT_MAX_HEALTH;
		long firstSeenMillis;
		long lastVisibleMillis;
		boolean everSeen;
	}
}
