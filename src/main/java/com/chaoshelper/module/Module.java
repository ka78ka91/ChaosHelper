package com.chaoshelper.module;

/**
 * Base class for built-in and dynamic modules. Runtime state is managed centrally
 * so one failing module can be disabled without touching the others.
 */
public abstract class Module {
	private final String id;
	private final String displayName;
	private final boolean defaultEnabled;
	private final boolean behavior;
	private final boolean overridable;
	private boolean enabled;

	protected Module(String id, String displayName, boolean defaultEnabled, boolean behavior, boolean overridable) {
		this.id = id;
		this.displayName = displayName;
		this.defaultEnabled = defaultEnabled;
		this.behavior = behavior;
		this.overridable = overridable;
	}

	public final String id() {
		return id;
	}

	public final String displayName() {
		return displayName;
	}

	public final boolean defaultEnabled() {
		return defaultEnabled;
	}

	public final boolean behavior() {
		return behavior;
	}

	public final boolean overridable() {
		return overridable;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	/**
	 * Called by ModuleManager only. If onEnable throws, enabled stays false.
	 */
	public final void setRuntimeEnabled(boolean value) {
		if (enabled == value) {
			return;
		}
		if (value) {
			onEnable();
			enabled = true;
		} else {
			try {
				onDisable();
			} finally {
				enabled = false;
			}
		}
	}

	/**
	 * Called by ModuleManager only. A thrown exception disables only this module.
	 */
	public final void tickRuntime() {
		if (enabled) {
			tick();
		}
	}

	public String status() {
		return "";
	}

	protected abstract void onEnable();

	protected abstract void onDisable();

	protected abstract void tick();
}
