package com.chaoshelper.util;

import net.minecraft.util.math.Vec3d;

public final class DirectionUtil {
	private static final String[] HORIZONTAL = {"南", "西南", "西", "西北", "北", "东北", "东", "东南"};

	private DirectionUtil() {
	}

	public static String cardinal(Vec3d delta) {
		double angle = Math.toDegrees(Math.atan2(delta.x, delta.z));
		if (angle < 0) {
			angle += 360;
		}
		int index = (int) Math.round(angle / 45.0) & 7;
		return HORIZONTAL[index];
	}

	public static String relativeArrow(Vec3d delta, float playerYaw) {
		double targetYaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
		double relative = normalize(targetYaw - playerYaw);
		if (relative >= -22.5 && relative < 22.5) {
			return "↑";
		}
		if (relative >= 22.5 && relative < 67.5) {
			return "↗";
		}
		if (relative >= 67.5 && relative < 112.5) {
			return "→";
		}
		if (relative >= 112.5 && relative < 157.5) {
			return "↘";
		}
		if (relative >= 157.5 || relative < -157.5) {
			return "↓";
		}
		if (relative >= -157.5 && relative < -112.5) {
			return "↙";
		}
		if (relative >= -112.5 && relative < -67.5) {
			return "←";
		}
		return "↖";
	}

	private static double normalize(double degrees) {
		double value = degrees % 360.0;
		if (value >= 180.0) {
			value -= 360.0;
		}
		if (value < -180.0) {
			value += 360.0;
		}
		return value;
	}
}
