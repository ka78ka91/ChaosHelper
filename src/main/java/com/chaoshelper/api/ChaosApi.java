package com.chaoshelper.api;

/**
 * Stable, Minecraft-independent API used by dynamic modules.
 *
 * <p>Dynamic modules are compiled against the separate chaoshelper-api jar.
 * They must not reference Minecraft classes, Fabric API, Mixin, or resources.
 */
public final class ChaosApi {
	public static final int API_VERSION = 1;
	public static final String MOD_ID = "chaoshelper";

	private ChaosApi() {
	}
}
