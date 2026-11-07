package com.chaoshelper.api;

/**
 * A color ARGB box submitted by a dynamic module for the client-side overlay.
 */
public record RenderBox(
		double minX,
		double minY,
		double minZ,
		double maxX,
		double maxY,
		double maxZ,
		int argb,
		String label
) {
}
