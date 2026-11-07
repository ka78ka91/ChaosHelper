package com.chaoshelper.api;

public record DropData(
		String uuid,
		String itemName,
		int count,
		double x,
		double y,
		double z,
		double distance,
		long firstSeenMillis,
		long lastSeenMillis,
		long ageSeconds,
		boolean visible
) {
}
