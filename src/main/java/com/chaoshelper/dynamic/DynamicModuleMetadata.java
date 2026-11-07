package com.chaoshelper.dynamic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contents of {@code META-INF/chaoshelper-module.json} inside a dynamic jar.
 *
 * <pre>
 * {
 *   "schema": 1,
 *   "id": "my-autoeat",
 *   "name": "My AutoEat",
 *   "version": "1.0.0",
 *   "apiVersion": 1,
 *   "entrypoint": "com.example.MyAutoEat",
 *   "description": "...",
 *   "defaultEnabled": false,
 *   "behavior": true,
 *   "overrides": "autoeat",
 *   "settings": { "threshold": "18" }
 * }
 * </pre>
 */
public final class DynamicModuleMetadata {
	/** Metadata schema, independent from the API version. */
	public int schema = 1;
	public String id = "";
	public String name = "";
	public String version = "1.0.0";
	public int apiVersion;
	public String entrypoint = "";
	public String description = "";
	public boolean defaultEnabled;
	/** True when the module performs automatic actions, so panic must stop it. */
	public boolean behavior = true;
	/** Optional built-in module id this jar replaces (only overridable ones). */
	public String overrides = "";
	/** Default values for context.setting(key, default). */
	public Map<String, String> settings = new LinkedHashMap<>();

	/** The id the module is registered under: the override target when present. */
	public String effectiveId() {
		return overrides == null || overrides.isBlank() ? id : overrides;
	}
}
