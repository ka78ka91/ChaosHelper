package com.chaoshelper.api;

import java.util.List;

/**
 * Read-only view of client-visible data plus a small set of safe actions.
 *
 * <p>All methods are called on the Minecraft client thread by the host.
 * A dynamic module must not retain the context after a callback returns.
 */
public interface ChaosContext {
	String apiVersion();

	String moduleId();

	long tick();

	PlayerData self();

	List<EntityData> entities();

	List<PlayerData> players();

	List<DropData> drops();

	String setting(String key, String defaultValue);

	default boolean settingBoolean(String key, boolean defaultValue) {
		String value = setting(key, Boolean.toString(defaultValue));
		return "true".equalsIgnoreCase(value) || "1".equals(value);
	}

	default int settingInt(String key, int defaultValue) {
		try {
			return Integer.parseInt(setting(key, Integer.toString(defaultValue)));
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	default double settingDouble(String key, double defaultValue) {
		try {
			return Double.parseDouble(setting(key, Double.toString(defaultValue)));
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	/**
	 * Submits a world-space box to the host's 2D ESP overlay.
	 * The box is cleared after the current tick.
	 */
	void addRenderBox(RenderBox box);

	void notify(String message);

	void error(String message);

	boolean isModuleEnabled(String moduleId);

	void disableModule(String moduleId);
}
