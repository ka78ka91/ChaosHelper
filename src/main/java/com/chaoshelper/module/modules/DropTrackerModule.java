package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.DropData;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DropTrackerModule extends Module {
	private final Map<UUID, Track> tracks = new HashMap<>();
	private List<DropData> drops = List.of();
	private int tickCounter;

	public DropTrackerModule() {
		super("drop-tracker", "掉落物追踪", false, false, true);
	}

	@Override
	protected void onEnable() {
		tracks.clear();
		drops = List.of();
		refresh();
	}

	@Override
	protected void onDisable() {
		tracks.clear();
		drops = List.of();
	}

	@Override
	protected void tick() {
		tickCounter++;
		if (tickCounter >= Math.max(1, ChaosHelperClient.get().config().dropTracker.refreshTicks)) {
			tickCounter = 0;
			refresh();
		}
	}

	public List<DropData> drops() {
		return drops;
	}

	public void resetForWorldChange() {
		tracks.clear();
		drops = List.of();
		tickCounter = 0;
	}

	private void refresh() {
		MinecraftClient client = ChaosHelperClient.get().client();
		ClientPlayerEntity self = client.player;
		if (self == null || client.world == null) {
			tracks.clear();
			drops = List.of();
			return;
		}
		long now = System.currentTimeMillis();
		double range = ChaosHelperClient.get().config().dropTracker.range;
		double rangeSquared = range * range;
		for (EntityData entity : ChaosHelperClient.get().entityCache().items()) {
			double dx = entity.x() - self.getX();
			double dy = entity.y() - self.getY();
			double dz = entity.z() - self.getZ();
			if (dx * dx + dy * dy + dz * dz > rangeSquared) {
				continue;
			}
			UUID uuid;
			try {
				uuid = UUID.fromString(entity.uuid());
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			Track track = tracks.computeIfAbsent(uuid, ignored -> new Track(now));
			track.itemName = entity.displayName();
			track.count = entity.count();
			track.x = entity.x();
			track.y = entity.y();
			track.z = entity.z();
			track.lastSeen = now;
			track.visible = true;
			track.lastDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		int recordMillis = ChaosHelperClient.get().config().dropTracker.recordSeconds * 1000;
		tracks.entrySet().removeIf(entry -> !entry.getValue().visible
				&& now - entry.getValue().lastSeen > recordMillis);
		// An item that vanishes while the player is within pickup range was picked
		// up, not despawned: drop it from the list instead of showing "(已消失)".
		List<UUID> pickedUp = new ArrayList<>();
		for (java.util.Map.Entry<UUID, Track> entry : tracks.entrySet()) {
			Track track = entry.getValue();
			if (track.visible && now - track.lastSeen > 1000L) {
				track.visible = false;
				if (track.lastDistance >= 0.0 && track.lastDistance <= 3.0) {
					pickedUp.add(entry.getKey());
				}
			}
		}
		for (UUID uuid : pickedUp) {
			tracks.remove(uuid);
		}

		List<DropData> built = new ArrayList<>();
		for (Map.Entry<UUID, Track> entry : tracks.entrySet()) {
			Track track = entry.getValue();
			double dx = track.x - self.getX();
			double dy = track.y - self.getY();
			double dz = track.z - self.getZ();
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			built.add(new DropData(entry.getKey().toString(), track.itemName, track.count,
					track.x, track.y, track.z, distance,
					track.firstSeen, track.lastSeen,
					Math.max(0, (now - track.firstSeen) / 1000L), track.visible));
		}
		built.sort(Comparator
				.comparing(DropData::visible).reversed()
				.thenComparingDouble(DropData::distance));
		if (built.size() > ChaosHelperClient.get().config().dropTracker.maxEntries) {
			built = new ArrayList<>(built.subList(0, ChaosHelperClient.get().config().dropTracker.maxEntries));
		}
		drops = List.copyOf(built);
	}

	private static final class Track {
		final long firstSeen;
		String itemName = "";
		int count;
		double x;
		double y;
		double z;
		long lastSeen;
		double lastDistance = -1.0;
		boolean visible;

		Track(long firstSeen) {
			this.firstSeen = firstSeen;
			this.lastSeen = firstSeen;
		}
	}
}
