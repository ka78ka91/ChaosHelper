package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;
import com.chaoshelper.world.EndShipFinder;
import net.minecraft.client.MinecraftClient;

/**
 * Runs the End Ship finder. Passive: it only reads loaded chunks and writes
 * local markers, so it is safe to leave on while flying.
 */
public final class EndShipModule extends Module {
	private final EndShipFinder finder;

	public EndShipModule(EndShipFinder finder) {
		super("endship", "末地船搜寻", true, false, true);
		this.finder = finder;
	}

	@Override
	protected void onEnable() {
		finder.resetForWorldChange();
	}

	@Override
	protected void onDisable() {
		finder.resetForWorldChange();
	}

	@Override
	protected void tick() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null) {
			return;
		}
		MinecraftClient client = mod.client();
		finder.tick(client);
	}

	@Override
	public String status() {
		return finder.status();
	}
}
