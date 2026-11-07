package com.chaoshelper.cache;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.EntityCategory;
import com.chaoshelper.api.EntityData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-thread-only snapshot cache. Every feature reads this instead of walking
 * the world on every HUD/render frame.
 */
public final class EntityCache {
	private final List<EntityData> entities = new ArrayList<>();
	private final List<EntityData> view = java.util.Collections.unmodifiableList(entities);
	private final Map<Integer, EntityData> byEntityId = new HashMap<>();
	private final Map<UUID, EntityData> byUuid = new HashMap<>();
	private final Set<Integer> occluded = new HashSet<>();
	private int tickCounter;
	private long lastRefreshMillis;
	private int refreshCount;

	public void tick(MinecraftClient client) {
		tickCounter++;
		int interval = Math.max(1, ChaosHelperClient.get().config().cacheIntervalTicks);
		if (tickCounter < interval) {
			return;
		}
		tickCounter = 0;
		refresh(client);
	}

	public void refresh(MinecraftClient client) {
		entities.clear();
		byEntityId.clear();
		byUuid.clear();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return;
		}
		Vec3d origin = new Vec3d(player.getX(), player.getY(), player.getZ());
		double maxRange = Math.max(64.0, Math.max(ChaosHelperClient.get().config().esp.range,
				Math.max(ChaosHelperClient.get().config().playerTracker.range,
						Math.max(ChaosHelperClient.get().config().dropTracker.range,
								ChaosHelperClient.get().config().chams.range)))) + 16.0;
		double maxRangeSquared = maxRange * maxRange;
		List<EntityData> collected = new ArrayList<>();
		for (Entity entity : world.getEntities()) {
			if (entity == null || entity == player || entity.isRemoved() || !entity.isAlive()) {
				continue;
			}
			if (entity.squaredDistanceTo(origin) > maxRangeSquared) {
				continue;
			}
			EntityData data = snapshot(entity);
			if (data != null) {
				collected.add(data);
			}
		}
		Vec3d sortOrigin = origin;
		collected.sort(Comparator.comparingDouble(data -> {
			double dx = data.x() - sortOrigin.x;
			double dy = data.y() - sortOrigin.y;
			double dz = data.z() - sortOrigin.z;
			return dx * dx + dy * dy + dz * dz;
		}));
		entities.addAll(collected);
		for (EntityData data : collected) {
			byEntityId.put(data.entityId(), data);
			if (!data.uuid().isEmpty()) {
				try {
					byUuid.put(UUID.fromString(data.uuid()), data);
				} catch (IllegalArgumentException ignored) {
					// A malformed UUID is not useful; the rest of the snapshot still is.
				}
			}
		}
		refreshOcclusion(client, player, collected);
		lastRefreshMillis = System.currentTimeMillis();
		refreshCount++;
	}

	/**
	 * Visibility is resolved here, once per cache refresh, so the render callback
	 * never performs ray casts. Only entities inside the ESP range are tested.
	 */
	private void refreshOcclusion(MinecraftClient client, ClientPlayerEntity player, List<EntityData> collected) {
		occluded.clear();
		if (ChaosHelperClient.get().config().esp.throughWalls || client.world == null) {
			return;
		}
		double rangeSquared = ChaosHelperClient.get().config().esp.range * ChaosHelperClient.get().config().esp.range;
		Vec3d eye = player.getEyePos();
		for (EntityData data : collected) {
			if (eye.squaredDistanceTo(data.x(), data.y(), data.z()) > rangeSquared) {
				continue;
			}
			Vec3d target = new Vec3d(data.x(), (data.minY() + data.maxY()) * 0.5, data.z());
			BlockHitResult hit = client.world.raycast(new RaycastContext(eye, target,
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				continue;
			}
			if (hit.getPos().squaredDistanceTo(eye) < target.squaredDistanceTo(eye) - 0.35) {
				occluded.add(data.entityId());
			}
		}
	}

	public boolean isOccluded(int entityId) {
		return occluded.contains(entityId);
	}

	private EntityData snapshot(Entity entity) {
		EntityCategory category;
		if (entity instanceof PlayerEntity) {
			category = EntityCategory.PLAYER;
		} else if (entity instanceof ItemEntity) {
			category = EntityCategory.ITEM;
		} else if (entity instanceof Monster || entity.getType().getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER) {
			category = EntityCategory.HOSTILE;
		} else if (entity instanceof LivingEntity) {
			category = EntityCategory.PASSIVE;
		} else {
			category = EntityCategory.OTHER;
		}
		Box box = entity.getBoundingBox();
		float health = -1.0F;
		float maxHealth = -1.0F;
		float absorption = 0.0F;
		boolean living = entity instanceof LivingEntity;
		if (entity instanceof LivingEntity livingEntity) {
			health = livingEntity.getHealth();
			maxHealth = livingEntity.getMaxHealth();
			absorption = livingEntity.getAbsorptionAmount();
		}
		String displayName = entity.getName().getString();
		int count = 1;
		if (entity instanceof ItemEntity itemEntity) {
			count = itemEntity.getStack().getCount();
			displayName = itemEntity.getStack().getName().getString() + " x" + count;
		}
		String uuid = entity.getUuidAsString();
		String typeId;
		try {
			typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
		} catch (RuntimeException ignored) {
			typeId = entity.getType().toString();
		}
		return new EntityData(entity.getId(), uuid, typeId, displayName, count, category,
				entity.getX(), entity.getY(), entity.getZ(),
				box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
				living, health, maxHealth, absorption);
	}

	public List<EntityData> entities() {
		return List.copyOf(entities);
	}

	/** Live read-only view for per-frame rendering; avoids copying every frame. */
	public List<EntityData> entitiesView() {
		return view;
	}

	/**
	 * Drops all snapshots, e.g. when leaving a world. Prevents stale positions
	 * from being rendered or used by the trackers.
	 */
	public void clear() {
		entities.clear();
		byEntityId.clear();
		byUuid.clear();
		occluded.clear();
		tickCounter = 0;
	}

	public List<EntityData> items() {
		List<EntityData> result = new ArrayList<>();
		for (EntityData data : entities) {
			if (data.category() == EntityCategory.ITEM) {
				result.add(data);
			}
		}
		return result;
	}

	public EntityData byEntityId(int id) {
		return byEntityId.get(id);
	}

	public EntityData byUuid(UUID uuid) {
		return byUuid.get(uuid);
	}

	public EntityData byUuid(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return null;
		}
		try {
			return byUuid(UUID.fromString(uuid));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public long lastRefreshMillis() {
		return lastRefreshMillis;
	}

	public int refreshCount() {
		return refreshCount;
	}
}
