package com.chaoshelper.module.modules;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;
import com.chaoshelper.world.EndShipFinder;
import net.minecraft.client.MinecraftClient;

/**
 * Runs the End Ship finder and its looting loop (fly → walk in → take → next).
 * Passive detection, plus Baritone calls the player could make by hand.
 */
public final class EndShipModule extends Module {
	private final EndShipFinder finder;

	public EndShipModule(EndShipFinder finder) {
		super("endship", "末地船自动搜刮", true, true, true);
		this.finder = finder;
	}

	@Override
	protected void onEnable() {
		finder.resetForWorldChange();
	}

	@Override
	protected void onDisable() {
		finder.cancelOwnLeg();
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
