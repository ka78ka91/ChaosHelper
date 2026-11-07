package com.chaoshelper.module;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.baritone.BaritoneBridge;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.util.ErrorLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModuleManager {
	private final ChaosHelperClient mod;
	private final Map<String, Module> builtins = new LinkedHashMap<>();
	private final Map<String, Module> active = new LinkedHashMap<>();
	private final List<Module> dynamicModules = new ArrayList<>();
	private final Set<String> shadowedBuiltins = new LinkedHashSet<>();

	public ModuleManager(ChaosHelperClient mod) {
		this.mod = mod;
	}

	public void registerBuiltin(Module module) {
		Module previous = builtins.put(module.id(), module);
		if (previous != null) {
			ChaosHelperClient.LOGGER.warn("重复注册模块: {}", module.id());
		}
	}

	public void initializeStates() {
		ChaosConfig config = mod.config();
		for (Module module : builtins.values()) {
			config.modules.putIfAbsent(module.id(), module.defaultEnabled());
		}
		config.normalize();
		rebuildActive();
	}

	public void tick() {
		for (Module module : active.values()) {
			if (!module.isEnabled()) {
				continue;
			}
			try {
				module.tickRuntime();
			} catch (Throwable throwable) {
				failModule(module, "tick", throwable);
			}
		}
	}

	public void replaceDynamicModules(List<Module> modules) {
		for (Module old : dynamicModules) {
			if (old.isEnabled()) {
				try {
					old.setRuntimeEnabled(false);
				} catch (Throwable throwable) {
					ChaosHelperClient.LOGGER.warn("关闭动态模块失败: {}", old.id(), throwable);
				}
			}
			if (old instanceof AutoCloseable closeable) {
				try {
					closeable.close();
				} catch (Exception exception) {
					ChaosHelperClient.LOGGER.warn("关闭动态模块 ClassLoader 失败: {}", old.id(), exception);
				}
			}
		}
		dynamicModules.clear();
		for (Module module : modules) {
			Module builtin = builtins.get(module.id());
			if (builtin != null && !builtin.overridable()) {
				String message = "动态模块 '" + module.id() + "' 不能覆盖受保护的内置模块";
				mod.errorLog().add(ErrorLog.Level.ERROR, "dynamic", message);
				ChaosHelperClient.LOGGER.error(message);
				continue;
			}
			if (findDynamicId(module.id()) != null) {
				String message = "动态模块 ID 重复，已跳过后一个: " + module.id();
				mod.errorLog().add(ErrorLog.Level.WARN, "dynamic", message);
				ChaosHelperClient.LOGGER.warn(message);
				continue;
			}
			dynamicModules.add(module);
			mod.config().modules.putIfAbsent(module.id(), module.defaultEnabled());
		}
		rebuildActive();
		mod.saveConfigQuietly();
	}

	private Module findDynamicId(String id) {
		for (Module module : dynamicModules) {
			if (module.id().equals(id)) {
				return module;
			}
		}
		return null;
	}

	private void rebuildActive() {
		active.clear();
		shadowedBuiltins.clear();
		Set<String> dynamicIds = new LinkedHashSet<>();
		for (Module module : dynamicModules) {
			dynamicIds.add(module.id());
			if (builtins.containsKey(module.id())) {
				shadowedBuiltins.add(module.id());
			}
		}
		for (Module module : builtins.values()) {
			if (!dynamicIds.contains(module.id())) {
				active.put(module.id(), module);
			}
		}
		for (Module module : dynamicModules) {
			active.put(module.id(), module);
		}
		for (Module module : active.values()) {
			boolean desired = mod.config().moduleEnabled(module.id(), module.defaultEnabled());
			applyState(module, desired, "rebuild");
		}
		for (Module module : builtins.values()) {
			if (!active.containsValue(module) && module.isEnabled()) {
				applyState(module, false, "shadowed");
			}
		}
	}

	private void applyState(Module module, boolean desired, String source) {
		if (module.isEnabled() == desired) {
			return;
		}
		try {
			module.setRuntimeEnabled(desired);
			if (desired) {
				ChaosHelperClient.LOGGER.info("启用模块 {} ({})", module.id(), source);
			}
		} catch (Throwable throwable) {
			mod.config().modules.put(module.id(), false);
			mod.saveConfigQuietly();
			try {
				module.setRuntimeEnabled(false);
			} catch (Throwable ignored) {
				// The module is already in a failed state.
			}
			failModule(module, "enable", throwable);
		}
	}

	private void failModule(Module module, String phase, Throwable throwable) {
		try {
			if (module.isEnabled()) {
				module.setRuntimeEnabled(false);
			}
		} catch (Throwable ignored) {
			// Keep the original failure as the reported one.
		}
		mod.config().modules.put(module.id(), false);
		mod.saveConfigQuietly();
		String message = "模块 '" + module.id() + "' 在 " + phase + " 阶段失败，已自动禁用: " + throwable.getMessage();
		mod.errorLog().add(ErrorLog.Level.ERROR, module.id(), message);
		mod.notifications().error(message);
		ChaosHelperClient.LOGGER.error(message, throwable);
	}

	public void setEnabled(String id, boolean enabled) {
		Module module = find(id);
		if (module == null) {
			return;
		}
		mod.config().modules.put(module.id(), enabled);
		mod.saveConfigQuietly();
		applyState(module, enabled, "command");
	}

	public boolean toggle(String id) {
		Module module = find(id);
		if (module == null) {
			return false;
		}
		boolean newState = !module.isEnabled();
		setEnabled(module.id(), newState);
		return module.isEnabled();
	}

	public Module find(String id) {
		if (id == null) {
			return null;
		}
		String normalized = id.toLowerCase(Locale.ROOT);
		Module module = active.get(normalized);
		if (module == null) {
			module = builtins.get(normalized);
		}
		return module;
	}

	public boolean isEnabled(String id) {
		Module module = find(id);
		return module != null && module.isEnabled();
	}

	public boolean isProtected(String id) {
		Module builtin = builtins.get(id);
		return builtin != null && !builtin.overridable();
	}

	public Collection<Module> activeModules() {
		return List.copyOf(active.values());
	}

	public List<Module> sortedModules() {
		List<Module> modules = new ArrayList<>(active.values());
		modules.sort(Comparator.comparing(Module::id));
		return modules;
	}

	public void panic() {
		boolean changed = false;
		for (Module module : active.values()) {
			if (module.behavior() && module.isEnabled()) {
				mod.config().modules.put(module.id(), false);
				applyState(module, false, "panic");
				changed = true;
			}
		}
		if (changed) {
			mod.saveConfigQuietly();
			mod.baritoneBridge().cancel();
			mod.notifications().info("Panic: 所有自动行为已停止");
			ChaosHelperClient.LOGGER.warn("Panic key pressed: automatic behaviours disabled");
		} else {
			mod.notifications().info("Panic: 没有正在运行的自动行为");
		}
	}

	public String stateLabel(Module module) {
		if (module.isEnabled()) {
			return "开启";
		}
		return "关闭";
	}

	public String statusLine(Module module) {
		String status = module.status();
		return status == null || status.isBlank() ? stateLabel(module) : stateLabel(module) + " | " + status;
	}

	public int enabledCount() {
		int count = 0;
		for (Module module : active.values()) {
			if (module.isEnabled()) {
				count++;
			}
		}
		return count;
	}

	public void shutdown() {
		for (Module module : active.values()) {
			if (module.isEnabled()) {
				try {
					module.setRuntimeEnabled(false);
				} catch (Throwable throwable) {
					ChaosHelperClient.LOGGER.warn("关闭模块失败: {}", module.id(), throwable);
				}
			}
		}
	}
}
