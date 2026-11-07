package com.chaoshelper.api;

/**
 * A player observed through the client. Stale entries keep the last visible
 * position and the last known health value.
 *
 * <p>{@code healthSource} / {@code healthConfidence} describe where the value
 * came from: entity sync, last-visible snapshot, or a full-health estimate after
 * the reset window. Nothing is ever inferred from packets.
 */
public record PlayerData(
		String uuid,
		String name,
		double x,
		double y,
		double z,
		double distance,
		float health,
		float maxHealth,
		float absorption,
		int armor,
		int ping,
		String gameMode,
		String healthSource,
		String healthConfidence,
		double healthError,
		boolean alive,
		boolean visible,
		long lastSeenMillis,
		long firstSeenMillis,
		String flags,
		/** Compact visible-equipment summary, e.g. {@code 剑/盾/甲2}. */
		String equipment
) {
}
