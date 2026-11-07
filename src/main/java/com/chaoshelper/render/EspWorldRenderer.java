package com.chaoshelper.render;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.EntityCategory;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.api.RenderBox;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * World-space 3D boxes. Registered on {@code WorldRenderEvents.AFTER_ENTITIES},
 * which in this Minecraft version receives the world {@link MatrixStack} and the
 * shared {@link VertexConsumerProvider} (extract/render two-stage pipeline).
 *
 * <p>The renderer never scans the world: it reads the shared entity cache and the
 * boxes submitted by dynamic modules during their tick.
 */
public final class EspWorldRenderer implements WorldRenderEvents.AfterEntities {
	private final ChaosHelperClient mod;

	public EspWorldRenderer(ChaosHelperClient mod) {
		this.mod = mod;
	}

	@Override
	public void afterEntities(WorldRenderContext context) {
		MinecraftClient client = mod.client();
		if (client.player == null || client.world == null || client.gameRenderer == null) {
			return;
		}
		boolean espEnabled = mod.moduleManager().isEnabled("esp");
		List<RenderBox> dynamicBoxes = mod.dynamicModuleLoader() == null
				? List.of()
				: mod.dynamicModuleLoader().renderBoxes();
		if (!espEnabled && dynamicBoxes.isEmpty()) {
			return;
		}
		Camera camera = client.gameRenderer.getCamera();
		if (camera == null || !camera.isReady()) {
			return;
		}
		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			return;
		}
		VertexConsumer buffer = consumers.getBuffer(RenderLayers.lines());
		if (buffer == null) {
			return;
		}
		Vec3d cameraPos = camera.getCameraPos();
		if (espEnabled) {
			drawEntities(matrices, buffer, cameraPos);
		}
		drawDynamicBoxes(matrices, buffer, cameraPos, dynamicBoxes);
	}

	private void drawEntities(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos) {
		double range = mod.config().esp.range;
		double rangeSquared = range * range;
		int maxEntities = mod.config().esp.maxEntities;
		boolean throughWalls = mod.config().esp.throughWalls;
		float width = mod.config().esp.lineWidth;
		int drawn = 0;
		for (EntityData entity : mod.entityCache().entitiesView()) {
			if (drawn >= maxEntities) {
				break;
			}
			if (entity.category() == EntityCategory.ITEM && !mod.config().esp.showItems) {
				continue;
			}
			double distanceSquared = cameraPos.squaredDistanceTo(entity.x(), entity.y(), entity.z());
			if (distanceSquared > rangeSquared) {
				continue;
			}
			if (!throughWalls && mod.entityCache().isOccluded(entity.entityId())) {
				continue;
			}
			drawBox(matrices, buffer, cameraPos,
					entity.minX(), entity.minY(), entity.minZ(),
					entity.maxX(), entity.maxY(), entity.maxZ(),
					color(entity), width);
			drawn++;
		}
	}

	private void drawDynamicBoxes(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos, List<RenderBox> boxes) {
		float width = mod.config().esp.lineWidth;
		for (RenderBox box : boxes) {
			drawBox(matrices, buffer, cameraPos,
					box.minX(), box.minY(), box.minZ(),
					box.maxX(), box.maxY(), box.maxZ(),
					box.argb(), width);
		}
	}

	/**
	 * Emits the 12 edges of a box. Coordinates are world-space; the matrix stack is
	 * translated to camera space because vanilla keeps the world stack at origin.
	 */
	private void drawBox(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
			int argb, float width) {
		matrices.push();
		matrices.translate(minX - cameraPos.x, minY - cameraPos.y, minZ - cameraPos.z);
		MatrixStack.Entry entry = matrices.peek();
		float x1 = 0.0F;
		float y1 = 0.0F;
		float z1 = 0.0F;
		float x2 = (float) (maxX - minX);
		float y2 = (float) (maxY - minY);
		float z2 = (float) (maxZ - minZ);

		// bottom
		line(buffer, entry, x1, y1, z1, x2, y1, z1, argb, width);
		line(buffer, entry, x2, y1, z1, x2, y1, z2, argb, width);
		line(buffer, entry, x2, y1, z2, x1, y1, z2, argb, width);
		line(buffer, entry, x1, y1, z2, x1, y1, z1, argb, width);
		// top
		line(buffer, entry, x1, y2, z1, x2, y2, z1, argb, width);
		line(buffer, entry, x2, y2, z1, x2, y2, z2, argb, width);
		line(buffer, entry, x2, y2, z2, x1, y2, z2, argb, width);
		line(buffer, entry, x1, y2, z2, x1, y2, z1, argb, width);
		// verticals
		line(buffer, entry, x1, y1, z1, x1, y2, z1, argb, width);
		line(buffer, entry, x2, y1, z1, x2, y2, z1, argb, width);
		line(buffer, entry, x2, y1, z2, x2, y2, z2, argb, width);
		line(buffer, entry, x1, y1, z2, x1, y2, z2, argb, width);
		matrices.pop();
	}

	private void line(VertexConsumer buffer, MatrixStack.Entry entry,
			float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
		float nx = x2 - x1;
		float ny = y2 - y1;
		float nz = z2 - z1;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length > 1.0E-5F) {
			nx /= length;
			ny /= length;
			nz /= length;
		}
		buffer.vertex(entry, x1, y1, z1).color(argb).normal(entry, nx, ny, nz).lineWidth(width);
		buffer.vertex(entry, x2, y2, z2).color(argb).normal(entry, nx, ny, nz).lineWidth(width);
	}

	private int color(EntityData entity) {
		if (entity.category() == EntityCategory.PLAYER && mod.targetLink().matches(entity)) {
			return mod.config().esp.colorTarget;
		}
		if (entity.category() == EntityCategory.PLAYER
				&& mod.teamManager().isTeammate(entity.displayName(), entity.uuid())) {
			return mod.config().esp.colorTeammate;
		}
		return switch (entity.category()) {
			case PLAYER -> mod.config().esp.colorPlayer;
			case HOSTILE -> mod.config().esp.colorHostile;
			case PASSIVE -> mod.config().esp.colorPassive;
			case ITEM -> mod.config().esp.colorItem;
			case OTHER -> mod.config().esp.colorOther;
		};
	}
}
