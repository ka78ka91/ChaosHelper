package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;

public final class HudModule extends Module {
	public HudModule() {
		super("hud", "HUD", true, false, false);
	}

	@Override
	protected void onEnable() {
	}

	@Override
	protected void onDisable() {
	}

	@Override
	protected void tick() {
	}

	@Override
	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return "";
		}
		int enabled = 0;
		for (var element : mod.config().hud.elements) {
			if (element.enabled) {
				enabled++;
			}
		}
		return enabled + "/" + mod.config().hud.elements.size() + " 个元素";
	}
}
