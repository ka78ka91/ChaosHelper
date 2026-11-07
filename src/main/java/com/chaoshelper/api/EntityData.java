package com.chaoshelper.api;

/**
 * Immutable snapshot of one client-visible entity. Health is -1 for non-living entities.
 */
public record EntityData(
		int entityId,
		String uuid,
		String typeId,
		String displayName,
		int count,
		EntityCategory category,
		double x,
		double y,
		double z,
		double minX,
		double minY,
		double minZ,
		double maxX,
		double maxY,
		double maxZ,
		boolean living,
		float health,
		float maxHealth,
		float absorption
) {
}
