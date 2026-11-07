package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;

public final class ChamsModule extends Module {
	public ChamsModule() {
		super("chams", "Chams 着色", false, false, false);
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
		return String.format(java.util.Locale.ROOT, "%.0fm | %s", mod.config().chams.range,
				mod.config().chams.throughWalls ? "描边" : "仅着色");
	}
}
