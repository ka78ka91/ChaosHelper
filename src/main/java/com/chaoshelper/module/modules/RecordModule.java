package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;

public final class RecordModule extends Module {
	public RecordModule() {
		super("record", "本地记录", true, false, true);
	}

	@Override
	protected void onEnable() {
	}

	@Override
	protected void onDisable() {
	}

	@Override
	protected void tick() {
		ChaosHelperClient.get().recordManager().tick(ChaosHelperClient.get().client());
	}

	@Override
	public String status() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		return mod == null ? "" : "记录 " + mod.dataStore().records().size() + " 条";
	}
}
