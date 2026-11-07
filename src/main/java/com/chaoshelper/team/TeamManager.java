package com.chaoshelper.team;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.PlayerData;
import com.chaoshelper.data.DataStore;
import com.chaoshelper.data.TeamEntry;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Local-only team list. Membership is never inferred from server scoreboard data.
 */
public final class TeamManager {
	public enum Relation {
		SELF,
		TEAMMATE,
		NEUTRAL
	}

	private final DataStore dataStore;

	public TeamManager(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	public boolean add(String name, String note) {
		if (name == null || name.isBlank()) {
			return false;
		}
		String resolvedName = name.trim();
		String uuid = "";
		PlayerListEntry entry = resolveEntry(resolvedName);
		if (entry != null) {
			resolvedName = entry.getProfile().name();
			uuid = entry.getProfile().id().toString();
		}
		if (isSelf(resolvedName, uuid)) {
			return false;
		}
		String key = key(resolvedName);
		TeamEntry old = dataStore.team().get(key);
		String mergedNote = note == null ? "" : note.trim();
		if (old != null && !old.note.isBlank() && mergedNote.isBlank()) {
			mergedNote = old.note;
		}
		dataStore.team().put(key, new TeamEntry(resolvedName, uuid, mergedNote, System.currentTimeMillis()));
		dataStore.saveQuietly();
		return true;
	}

	public boolean remove(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		TeamEntry removed = dataStore.team().remove(key(name));
		if (removed != null) {
			dataStore.saveQuietly();
			return true;
		}
		for (String candidate : new ArrayList<>(dataStore.team().keySet())) {
			TeamEntry entry = dataStore.team().get(candidate);
			if (entry != null && entry.uuid.equalsIgnoreCase(name.trim())) {
				dataStore.team().remove(candidate);
				dataStore.saveQuietly();
				return true;
			}
		}
		return false;
	}

	public void clear() {
		dataStore.team().clear();
		dataStore.saveQuietly();
	}

	public List<TeamEntry> entries() {
		List<TeamEntry> result = new ArrayList<>(dataStore.team().values());
		result.sort(Comparator.comparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
		return result;
	}

	public Relation relation(PlayerData player) {
		if (player == null) {
			return Relation.NEUTRAL;
		}
		if (isSelf(player.name(), player.uuid())) {
			return Relation.SELF;
		}
		if (isTeammate(player.name(), player.uuid())) {
			return Relation.TEAMMATE;
		}
		return Relation.NEUTRAL;
	}

	public boolean isTeammate(String name, String uuid) {
		TeamEntry byKey = dataStore.team().get(key(name));
		if (byKey != null) {
			return true;
		}
		if (uuid != null && !uuid.isBlank()) {
			for (TeamEntry entry : dataStore.team().values()) {
				if (entry.uuid.equalsIgnoreCase(uuid)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isSelf(String name, String uuid) {
		if (ChaosHelperClient.get() == null || ChaosHelperClient.get().client().player == null) {
			return false;
		}
		var self = ChaosHelperClient.get().client().player;
		if (uuid != null && !uuid.isBlank() && uuid.equalsIgnoreCase(self.getUuidAsString())) {
			return true;
		}
		return name != null && name.equalsIgnoreCase(self.getGameProfile().name());
	}

	private static PlayerListEntry resolveEntry(String name) {
		if (ChaosHelperClient.get() == null || ChaosHelperClient.get().client().getNetworkHandler() == null) {
			return null;
		}
		return ChaosHelperClient.get().client().getNetworkHandler().getPlayerListEntry(name);
	}

	private static String key(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	public static String uuidOf(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return "";
		}
		try {
			return UUID.fromString(uuid).toString();
		} catch (IllegalArgumentException ignored) {
			return "";
		}
	}
}
