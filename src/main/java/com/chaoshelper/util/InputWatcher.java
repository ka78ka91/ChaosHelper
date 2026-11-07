package com.chaoshelper.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class InputWatcher {
	private InputWatcher() {
	}

	/**
	 * Any manual movement or action cancels automatic behaviours. This deliberately
	 * checks client key state only; it does not inspect or control server data.
	 */
	public static boolean hasInterruptingInput(MinecraftClient client) {
		if (client.options == null) {
			return false;
		}
		return pressed(client.options.forwardKey)
				|| pressed(client.options.backKey)
				|| pressed(client.options.leftKey)
				|| pressed(client.options.rightKey)
				|| pressed(client.options.jumpKey)
				|| pressed(client.options.sneakKey)
				|| pressed(client.options.sprintKey)
				|| pressed(client.options.attackKey)
				|| pressed(client.options.useKey);
	}

	private static boolean pressed(KeyBinding binding) {
		return binding != null && binding.isPressed();
	}
}
