package com.chaoshelper.hud;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ScreenProjector {
	private ScreenProjector() {
	}

	public record Point(double x, double y, double depth) {
	}

	public record ScreenBox(int minX, int minY, int maxX, int maxY) {
		public int width() {
			return Math.max(0, maxX - minX);
		}

		public int height() {
			return Math.max(0, maxY - minY);
		}
	}

	public static Point project(Vec3d world, Camera camera, double fovDegrees, int width, int height) {
		Vec3d relative = world.subtract(camera.getCameraPos());
		double yaw = Math.toRadians(camera.getYaw());
		double pitch = Math.toRadians(camera.getPitch());
		double cosYaw = Math.cos(yaw);
		double sinYaw = Math.sin(yaw);
		double cosPitch = Math.cos(pitch);
		double sinPitch = Math.sin(pitch);

		double forwardX = -sinYaw * cosPitch;
		double forwardY = -sinPitch;
		double forwardZ = cosYaw * cosPitch;
		double rightX = -cosYaw;
		double rightZ = -sinYaw;
		double upX = -sinYaw * sinPitch;
		double upY = cosPitch;
		double upZ = cosYaw * sinPitch;

		double depth = relative.x * forwardX + relative.y * forwardY + relative.z * forwardZ;
		if (depth <= 0.05) {
			return null;
		}
		double cameraX = relative.x * rightX + relative.z * rightZ;
		double cameraY = relative.x * upX + relative.y * upY + relative.z * upZ;
		double halfFov = Math.toRadians(Math.max(5.0, fovDegrees)) * 0.5;
		double focal = 1.0 / Math.tan(halfFov);
		double aspect = width / (double) Math.max(1, height);
		double screenX = width * 0.5 + (cameraX * focal / (depth * aspect)) * (width * 0.5);
		double screenY = height * 0.5 - (cameraY * focal / depth) * (height * 0.5);
		if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
			return null;
		}
		return new Point(screenX, screenY, depth);
	}

	public static ScreenBox projectBox(Box box, Camera camera, double fovDegrees, int width, int height) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (int index = 0; index < 8; index++) {
			double x = (index & 1) == 0 ? box.minX : box.maxX;
			double y = (index & 2) == 0 ? box.minY : box.maxY;
			double z = (index & 4) == 0 ? box.minZ : box.maxZ;
			Point point = project(new Vec3d(x, y, z), camera, fovDegrees, width, height);
			if (point == null) {
				return null;
			}
			minX = Math.min(minX, point.x);
			minY = Math.min(minY, point.y);
			maxX = Math.max(maxX, point.x);
			maxY = Math.max(maxY, point.y);
		}
		int x1 = (int) Math.max(0, Math.floor(minX));
		int y1 = (int) Math.max(0, Math.floor(minY));
		int x2 = (int) Math.min(width, Math.ceil(maxX));
		int y2 = (int) Math.min(height, Math.ceil(maxY));
		if (x2 <= x1 || y2 <= y1) {
			return null;
		}
		return new ScreenBox(x1, y1, x2, y2);
	}
}
