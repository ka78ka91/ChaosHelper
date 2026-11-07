package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;

public final class EspModule extends Module {
	public EspModule() {
		super("esp", "ESP 实体描边", false, false, false);
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
		return String.format(java.util.Locale.ROOT, "%.0fm | %s", mod.config().esp.range,
				mod.config().esp.throughWalls ? "穿墙" : "遮挡");
	}
}
