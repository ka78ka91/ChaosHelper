package com.chaoshelper.data;

import com.chaoshelper.ChaosHelperClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local-only storage: team list, waypoints, records, target link.
 * No data is sent to a server.
 */
public final class DataStore {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private final Path file;
	private final Data data = new Data();
	private final List<String> loadErrors = new ArrayList<>();

	public DataStore() {
		this.file = FabricLoader.getInstance().getConfigDir().resolve(ChaosHelperClient.MOD_ID).resolve("data.json");
	}

	public void load() {
		loadErrors.clear();
		try {
			Files.createDirectories(file.getParent());
			if (Files.notExists(file)) {
				return;
			}
			String json = Files.readString(file, StandardCharsets.UTF_8);
			Data loaded = GSON.fromJson(json, Data.class);
			if (loaded == null) {
				return;
			}
			data.team.clear();
			if (loaded.team != null) {
				data.team.putAll(loaded.team);
			}
			data.waypoints.clear();
			if (loaded.waypoints != null) {
				data.waypoints.addAll(loaded.waypoints);
			}
			data.records.clear();
			if (loaded.records != null) {
				data.records.addAll(loaded.records);
			}
			data.ships.clear();
			if (loaded.ships != null) {
				data.ships.addAll(loaded.ships);
			}
			data.targetName = loaded.targetName == null ? "" : loaded.targetName;
			data.targetUuid = loaded.targetUuid == null ? "" : loaded.targetUuid;
			normalize();
		} catch (Exception exception) {
			String message = "读取本地数据失败，已使用空数据: " + exception.getMessage();
			ChaosHelperClient.LOGGER.error(message, exception);
			loadErrors.add(message);
		}
	}

	public void saveQuietly() {
		try {
			save();
		} catch (IOException exception) {
			ChaosHelperClient.LOGGER.error("保存本地数据失败", exception);
		}
	}

	public void save() throws IOException {
		Files.createDirectories(file.getParent());
		Path temporary = file.resolveSibling("data.json.tmp");
		Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public Map<String, TeamEntry> team() {
		return data.team;
	}

	public List<Waypoint> waypoints() {
		return data.waypoints;
	}

	public List<RecordEntry> records() {
		return data.records;
	}

	/** Discovered End Ships, newest last. Persisted with everything else. */
	public List<ShipPoint> ships() {
		return data.ships;
	}

	public String targetName() {
		return data.targetName;
	}

	public String targetUuid() {
		return data.targetUuid;
	}

	public void setTarget(String name, String uuid) {
		data.targetName = name == null ? "" : name;
		data.targetUuid = uuid == null ? "" : uuid;
		saveQuietly();
	}

	public void clearTarget() {
		setTarget("", "");
	}

	public void addRecord(RecordType type, String world, double x, double y, double z, String message, int maxEntries) {
		addRecord(type, world, x, y, z, message, "", maxEntries);
	}

	public void addRecord(RecordType type, String world, double x, double y, double z, String message, String subject,
			int maxEntries) {
		data.records.add(new RecordEntry(type, System.currentTimeMillis(), world, x, y, z, message, subject));
		pruneRecords(maxEntries);
		saveQuietly();
	}

	public RecordEntry lastRecordBySubject(String subject) {
		if (subject == null || subject.isBlank()) {
			return null;
		}
		for (int index = data.records.size() - 1; index >= 0; index--) {
			RecordEntry entry = data.records.get(index);
			if (entry.type == RecordType.LOG && subject.equalsIgnoreCase(entry.subject)) {
				return entry;
			}
		}
		return null;
	}

	public void pruneRecords(int maxEntries) {
		while (data.records.size() > Math.max(5, maxEntries)) {
			data.records.removeFirst();
		}
	}

	public RecordEntry lastDeath() {
		for (int index = data.records.size() - 1; index >= 0; index--) {
			RecordEntry entry = data.records.get(index);
			if (entry.type == RecordType.DEATH) {
				return entry;
			}
		}
		return null;
	}

	public List<String> loadErrors() {
		return List.copyOf(loadErrors);
	}

	public Path file() {
		return file;
	}

	private void normalize() {
		data.team.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
		data.waypoints.removeIf(waypoint -> waypoint == null || waypoint.name == null || waypoint.name.isBlank());
		data.records.removeIf(record -> record == null);
		while (data.records.size() > 500) {
			data.records.removeFirst();
		}
		data.ships.removeIf(ship -> ship == null);
		while (data.ships.size() > 2000) {
			data.ships.removeFirst();
		}
	}

	private static final class Data {
		Map<String, TeamEntry> team = new LinkedHashMap<>();
		List<Waypoint> waypoints = new ArrayList<>();
		List<RecordEntry> records = new ArrayList<>();
		List<ShipPoint> ships = new ArrayList<>();
		String targetName = "";
		String targetUuid = "";
	}
}
