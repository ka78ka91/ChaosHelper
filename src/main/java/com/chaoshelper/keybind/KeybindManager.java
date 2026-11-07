package com.chaoshelper.keybind;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.gui.ClickGuiScreen;
import com.chaoshelper.module.Module;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers key bindings and persists their user-selected keys in config.
 * Only client input is read; no keys are simulated or sent to a server.
 */
public final class KeybindManager {
	private final ChaosHelperClient mod;
	private final Map<String, KeyBinding> moduleBindings = new LinkedHashMap<>();
	private KeyBinding panicBinding;
	private KeyBinding hudBinding;
	private KeyBinding menuBinding;
	private KeyBinding stopBinding;

	public KeybindManager(ChaosHelperClient mod) {
		this.mod = mod;
		register();
		loadFromConfig();
	}

	private void register() {
		panicBinding = register("key.chaoshelper.panic", GLFW.GLFW_KEY_P);
		hudBinding = register("key.chaoshelper.hud", GLFW.GLFW_KEY_H);
		menuBinding = register("key.chaoshelper.menu", GLFW.GLFW_KEY_K);
		// Dedicated "stop automation" key: travel keeps running through normal
		// movement/attack input, so this (or panic) is the way to end it.
		stopBinding = register("key.chaoshelper.stop", GLFW.GLFW_KEY_V);
		registerModule("esp", GLFW.GLFW_KEY_UNKNOWN);
		registerModule("chams", GLFW.GLFW_KEY_UNKNOWN);
		registerModule("player-tracker", GLFW.GLFW_KEY_UNKNOWN);
		registerModule("drop-tracker", GLFW.GLFW_KEY_UNKNOWN);
		registerModule("record", GLFW.GLFW_KEY_UNKNOWN);
		registerModule("travel", GLFW.GLFW_KEY_UNKNOWN);
		registerModule("autoeat", GLFW.GLFW_KEY_UNKNOWN);
	}

	private KeyBinding register(String id, int defaultKey) {
		KeyBinding binding = new KeyBinding(id, InputUtil.Type.KEYSYM, defaultKey, KeyBinding.Category.MISC);
		return KeyBindingHelper.registerKeyBinding(binding);
	}

	private void registerModule(String moduleId, int defaultKey) {
		KeyBinding binding = register("key.chaoshelper.toggle." + moduleId, defaultKey);
		moduleBindings.put(moduleId, binding);
	}

	private void loadFromConfig() {
		for (KeyBinding binding : allBindings()) {
			String stored = mod.config().keybinds.get(binding.getId());
			if (stored != null && !stored.isBlank()) {
				try {
					binding.setBoundKey(InputUtil.fromTranslationKey(stored));
				} catch (RuntimeException exception) {
					ChaosHelperClient.LOGGER.warn("无法恢复快捷键 {} = {}", binding.getId(), stored);
				}
			}
		}
		saveCurrentKeys();
	}

	public void tick() {
		MinecraftClient client = mod.client();
		if (client == null || client.options == null) {
			return;
		}
		saveIfChanged();
		if (client.currentScreen != null) {
			return;
		}
		if (panicBinding.wasPressed()) {
			mod.moduleManager().panic();
			return;
		}
		if (stopBinding.wasPressed()) {
			mod.travel().stop(false);
			// Also drop anything Baritone may be running on its own.
			mod.baritoneBridge().cancel();
			mod.notifications().info("已停止旅行 / 寻路");
			return;
		}
		if (hudBinding.wasPressed()) {
			boolean enabled = mod.moduleManager().toggle("hud");
			mod.notifications().info("HUD: " + (enabled ? "开启" : "关闭"));
		}
		if (menuBinding.wasPressed()) {
			client.setScreen(new ClickGuiScreen(null));
			return;
		}
		for (Map.Entry<String, KeyBinding> entry : moduleBindings.entrySet()) {
			if (entry.getValue().wasPressed()) {
				Module module = mod.moduleManager().find(entry.getKey());
				if (module != null) {
					boolean enabled = mod.moduleManager().toggle(module.id());
					mod.notifications().info(module.displayName() + ": " + (enabled ? "开启" : "关闭"));
				}
			}
		}
	}

	private void saveIfChanged() {
		boolean changed = false;
		for (KeyBinding binding : allBindings()) {
			String current = binding.getBoundKeyTranslationKey();
			if (!current.equals(mod.config().keybinds.get(binding.getId()))) {
				mod.config().keybinds.put(binding.getId(), current);
				changed = true;
			}
		}
		if (changed) {
			mod.saveConfigQuietly();
		}
	}

	private void saveCurrentKeys() {
		for (KeyBinding binding : allBindings()) {
			mod.config().keybinds.putIfAbsent(binding.getId(), binding.getBoundKeyTranslationKey());
		}
		mod.saveConfigQuietly();
	}

	private java.util.List<KeyBinding> allBindings() {
		java.util.List<KeyBinding> result = new java.util.ArrayList<>();
		result.add(panicBinding);
		result.add(hudBinding);
		result.add(menuBinding);
		result.add(stopBinding);
		result.addAll(moduleBindings.values());
		return result;
	}

	public String panicKeyName() {
		return panicBinding.getBoundKeyLocalizedText().getString();
	}
}
