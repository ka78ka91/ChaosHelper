package com.chaoshelper.target;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.api.PlayerData;
import com.chaoshelper.data.DataStore;
import net.minecraft.client.network.PlayerListEntry;

import java.util.List;
import java.util.Locale;

/**
 * A local marker for one player. It only affects outlines and the HUD; it never
 * attacks, follows, or sends messages automatically.
 */
public final class TargetLink {
	private final DataStore dataStore;

	public TargetLink(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	public void set(String name) {
		if (name == null || name.isBlank()) {
			clear();
			return;
		}
		String resolvedName = name.trim();
		String uuid = "";
		if (ChaosHelperClient.get() != null && ChaosHelperClient.get().client().getNetworkHandler() != null) {
			PlayerListEntry entry = ChaosHelperClient.get().client().getNetworkHandler().getPlayerListEntry(resolvedName);
			if (entry != null) {
				resolvedName = entry.getProfile().name();
				uuid = entry.getProfile().id().toString();
			}
		}
		dataStore.setTarget(resolvedName, uuid);
	}

	public void clear() {
		dataStore.clearTarget();
	}

	public String name() {
		return dataStore.targetName();
	}

	public String uuid() {
		return dataStore.targetUuid();
	}

	public boolean isSet() {
		return !name().isBlank();
	}

	public boolean matches(EntityData entity) {
		if (entity == null || !isSet()) {
			return false;
		}
		if (!uuid().isBlank() && entity.uuid().equalsIgnoreCase(uuid())) {
			return true;
		}
		return entity.displayName().equalsIgnoreCase(name());
	}

	public boolean matches(PlayerData player) {
		if (player == null || !isSet()) {
			return false;
		}
		if (!uuid().isBlank() && player.uuid().equalsIgnoreCase(uuid())) {
			return true;
		}
		return player.name().equalsIgnoreCase(name());
	}

	public PlayerData resolve(List<PlayerData> players) {
		if (players == null || !isSet()) {
			return null;
		}
		for (PlayerData player : players) {
			if (matches(player)) {
				return player;
			}
		}
		return null;
	}

	public String displayName() {
		return isSet() ? name() : "无";
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
