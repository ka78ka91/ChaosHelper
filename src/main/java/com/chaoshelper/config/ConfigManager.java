package com.chaoshelper.config;

import com.chaoshelper.ChaosHelperClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private final Path directory;
	private final Path configFile;
	private final List<String> loadErrors = new ArrayList<>();

	public ConfigManager() {
		this.directory = FabricLoader.getInstance().getConfigDir().resolve(ChaosHelperClient.MOD_ID);
		this.configFile = directory.resolve("config.json");
	}

	public ChaosConfig load() {
		loadErrors.clear();
		try {
			Files.createDirectories(directory);
			if (Files.notExists(configFile)) {
				ChaosConfig fresh = new ChaosConfig();
				fresh.normalize();
				return fresh;
			}
			String json = Files.readString(configFile, StandardCharsets.UTF_8);
			ChaosConfig config = GSON.fromJson(json, ChaosConfig.class);
			if (config == null) {
				throw new JsonParseException("root object is null");
			}
			config.normalize();
			return config;
		} catch (Exception exception) {
			String message = "读取配置失败，已使用默认值: " + exception.getMessage();
			ChaosHelperClient.LOGGER.error(message, exception);
			loadErrors.add(message);
			backupBrokenFile();
			ChaosConfig fresh = new ChaosConfig();
			fresh.normalize();
			return fresh;
		}
	}

	public void save(ChaosConfig config) throws IOException {
		Files.createDirectories(directory);
		Path temporary = directory.resolve("config.json.tmp");
		Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public Path directory() {
		return directory;
	}

	public Path configFile() {
		return configFile;
	}

	public List<String> loadErrors() {
		return List.copyOf(loadErrors);
	}

	private void backupBrokenFile() {
		if (Files.notExists(configFile)) {
			return;
		}
		try {
			Path backup = directory.resolve("config.broken-" + System.currentTimeMillis() + ".json");
			Files.move(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			ChaosHelperClient.LOGGER.warn("无法备份损坏的配置文件", exception);
		}
	}
}
