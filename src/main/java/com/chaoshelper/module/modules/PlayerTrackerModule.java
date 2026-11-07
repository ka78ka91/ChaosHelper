package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.api.PlayerData;
import com.chaoshelper.module.Module;
import com.chaoshelper.player.HealthEstimator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks client-visible players. Data refresh is separated from rendering: this
 * class only rebuilds an immutable list every N ticks, the HUD reads that list.
 *
 * <p>Only client-visible information is used. When a tracked player leaves the
 * entity view their last visible position is recorded locally (unless the local
 * player just changed dimension, which would produce a false record).
 */
public final class PlayerTrackerModule extends Module {
	private final Map<UUID, Track> tracks = new HashMap<>();
	private final HealthEstimator health = new HealthEstimator();
	private List<PlayerData> players = List.of();
	private int tickCounter;
	private int lastRefreshCount;
	private String worldId = "";

	public PlayerTrackerModule() {
		super("player-tracker", "玩家追踪", false, false, true);
	}

	@Override
	protected void onEnable() {
		tracks.clear();
		health.clear();
		players = List.of();
		worldId = "";
		refresh();
	}

	@Override
	protected void onDisable() {
		tracks.clear();
		health.clear();
		players = List.of();
	}

	@Override
	protected void tick() {
		tickCounter++;
		if (tickCounter >= Math.max(2, ChaosHelperClient.get().config().playerTracker.refreshTicks)) {
			tickCounter = 0;
			refresh();
		}
	}

	public List<PlayerData> players() {
		return players;
	}

	public int lastRefreshCount() {
		return lastRefreshCount;
	}

	public void resetForWorldChange() {
		tracks.clear();
		health.clear();
		players = List.of();
		worldId = "";
	}

	private void refresh() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = mod.client();
		ClientPlayerEntity self = client.player;
		if (self == null || client.world == null) {
			tracks.clear();
			players = List.of();
			worldId = "";
			return;
		}
		long now = System.currentTimeMillis();
		String currentWorld = client.world.getRegistryKey().getValue().toString();
		if (!currentWorld.equals(worldId)) {
			// Local dimension change: drop everything without writing log points,
			// otherwise every tracked player would look like they logged off.
			tracks.clear();
			health.clear();
			worldId = currentWorld;
			lastRefreshCount++;
			players = List.of();
			return;
		}

		Set<UUID> visible = new HashSet<>();
		for (AbstractClientPlayerEntity entity : client.world.getPlayers()) {
			if (entity == self || entity.isRemoved()) {
				continue;
			}
			UUID uuid = entity.getUuid();
			Track track = tracks.computeIfAbsent(uuid, ignored -> new Track(uuid, entity.getName().getString()));
			EntityData snapshot = mod.entityCache().byUuid(uuid);
			track.name = entity.getName().getString();
			track.x = entity.getX();
			track.y = entity.getY();
			track.z = entity.getZ();
			track.armor = entity.getArmor();
			track.flags = flags(entity);
			track.lastSeen = now;
			track.hasSnapshot = true;
			track.wasVisible = true;
			track.logged = false;
			track.health = snapshot != null ? snapshot.health() : entity.getHealth();
			track.maxHealth = snapshot != null ? snapshot.maxHealth() : entity.getMaxHealth();
			track.absorption = snapshot != null ? snapshot.absorption() : entity.getAbsorptionAmount();
			track.equipment = equipment(entity);
			health.observe(uuid, track.health, track.maxHealth, now);
			visible.add(uuid);
		}

		Map<UUID, PlayerListEntry> online = new HashMap<>();
		if (client.getNetworkHandler() != null) {
			for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
				UUID uuid = entry.getProfile().id();
				if (!uuid.equals(self.getUuid())) {
					online.put(uuid, entry);
				}
			}
		}
		for (Map.Entry<UUID, PlayerListEntry> entry : online.entrySet()) {
			UUID uuid = entry.getKey();
			Track track = tracks.computeIfAbsent(uuid, ignored -> new Track(uuid, entry.getValue().getProfile().name()));
			PlayerListEntry listEntry = entry.getValue();
			track.name = listEntry.getProfile().name();
			track.online = true;
			track.ping = listEntry.getLatency();
			track.gameMode = listEntry.getGameMode() == null ? "未知" : listEntry.getGameMode().getId();
		}

		// Players that were visible but are no longer in the entity view: record the
		// last visible position once. Local only, never sent anywhere.
		for (Track track : tracks.values()) {
			if (track.wasVisible && !visible.contains(track.uuid) && !track.logged) {
				track.logged = true;
				track.wasVisible = false;
				health.markInvisible(track.uuid, now);
				if (mod.config().record.autoLogoff) {
					mod.recordManager().logoff(track.name, track.x, track.y, track.z);
					mod.notifications().info(track.name + " 已离开视野，记录最后位置");
				}
			}
		}

		int staleMillis = Math.max(3, mod.config().playerTracker.staleSeconds) * 3000;
		tracks.entrySet().removeIf(entry -> !visible.contains(entry.getKey())
				&& now - Math.max(entry.getValue().lastSeen, 1L) > staleMillis);

		List<PlayerData> built = new ArrayList<>();
		double range = mod.config().playerTracker.range;
		int resetSeconds = mod.config().playerTracker.estimateResetSeconds;
		double errorBase = mod.config().playerTracker.healthErrorBase;
		boolean includeTabListOnly = mod.config().playerTracker.includeTabListOnly;
		for (Track track : tracks.values()) {
			if (track.uuid.equals(self.getUuid())) {
				continue;
			}
			boolean isVisible = visible.contains(track.uuid);
			double distance = isVisible ? Math.sqrt(track.distanceSquared(self)) : -1.0;
			if (isVisible && distance > range) {
				// Out of the configured display range: keep tracking, hide from the list.
				continue;
			}
			if (!isVisible && !includeTabListOnly && !track.online) {
				continue;
			}
			HealthEstimator.Estimate estimate = isVisible
					? health.observe(track.uuid, track.health, track.maxHealth, now)
					: health.absent(track.uuid, now, resetSeconds, errorBase);
			built.add(new PlayerData(track.uuid.toString(), track.name,
					track.x, track.y, track.z, distance,
					estimate.health(), estimate.maxHealth(), isVisible ? track.absorption : 0.0F,
					track.armor, track.ping, track.gameMode,
					estimate.source(), estimate.confidence(), estimate.error(),
					isVisible || track.online, isVisible, track.lastSeen,
					Math.max(estimate.firstSeenMillis(), track.firstSeen),
					track.flags.isBlank() ? "未知" : track.flags, track.equipment));
		}
		built.sort(Comparator
				.comparing(PlayerData::visible).reversed()
				.thenComparingDouble(player -> player.distance() < 0 ? Double.MAX_VALUE : player.distance())
				.thenComparing(PlayerData::name, String.CASE_INSENSITIVE_ORDER));
		int maxEntries = mod.config().playerTracker.maxEntries;
		if (built.size() > maxEntries) {
			built = new ArrayList<>(built.subList(0, maxEntries));
		}
		players = List.copyOf(built);
		lastRefreshCount++;
	}

	public PlayerData self() {
		MinecraftClient client = ChaosHelperClient.get().client();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return null;
		}
		String gameMode = client.interactionManager == null || client.interactionManager.getCurrentGameMode() == null
				? "未知"
				: client.interactionManager.getCurrentGameMode().getId();
		return new PlayerData(player.getUuidAsString(), player.getGameProfile().name(),
				player.getX(), player.getY(), player.getZ(), 0.0,
				player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(),
				player.getArmor(), 0, gameMode,
				"自身", "高", 0.0,
				player.isAlive(), true, System.currentTimeMillis(), System.currentTimeMillis(),
				flags(player), equipment(player));
	}

	private static String equipment(AbstractClientPlayerEntity player) {
		StringBuilder builder = new StringBuilder(shortName(player.getMainHandStack()));
		ItemStack offhand = player.getOffHandStack();
		if (!offhand.isEmpty()) {
			builder.append('/').append(shortName(offhand));
		}
		int pieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!player.getEquippedStack(slot).isEmpty()) {
				pieces++;
			}
		}
		if (pieces > 0) {
			builder.append("/甲").append(pieces);
		}
		return builder.toString();
	}

	private static String shortName(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "-";
		}
		String name = stack.getName().getString();
		return name.length() > 6 ? name.substring(0, 6) : name;
	}

	private static String flags(AbstractClientPlayerEntity player) {
		StringBuilder builder = new StringBuilder();
		if (player.isSneaking()) {
			builder.append('潜');
		}
		if (player.isSprinting()) {
			builder.append('跑');
		}
		if (player.isSwimming()) {
			builder.append('泳');
		}
		if (player.isGliding()) {
			builder.append('滑');
		}
		if (player.hasVehicle()) {
			builder.append('骑');
		}
		if (player.isUsingItem()) {
			builder.append('用');
		}
		return builder.isEmpty() ? "站" : builder.toString();
	}

	private static final class Track {
		final UUID uuid;
		String name;
		double x;
		double y;
		double z;
		float health;
		float maxHealth = 20.0F;
		float absorption;
		int armor;
		int ping;
		String gameMode = "未知";
		String flags = "未知";
		String equipment = "";
		long lastSeen;
		long firstSeen = System.currentTimeMillis();
		boolean hasSnapshot;
		boolean online;
		boolean wasVisible;
		boolean logged;

		Track(UUID uuid, String name) {
			this.uuid = uuid;
			this.name = name == null ? uuid.toString() : name;
		}

		double distanceSquared(ClientPlayerEntity self) {
			double dx = x - self.getX();
			double dy = y - self.getY();
			double dz = z - self.getZ();
			return dx * dx + dy * dy + dz * dz;
		}
	}
}
