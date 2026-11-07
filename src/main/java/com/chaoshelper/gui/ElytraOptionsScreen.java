package com.chaoshelper.gui;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Opt-in screen for the two elytra assists. Both switches default to off and
 * the screen states the trade-offs plainly, so the player - not the mod -
 * decides whether to use them.
 */
public final class ElytraOptionsScreen extends Screen {
	private static final int[] CLIMB_CLEARANCE = { 40, 60, 80, 100, 120 };
	private static final int[] CLIMB_DIVE = { 40, 55, 70, 85, 100 };
	private static final int[] SWAP_LEAD = { 1, 2, 3, 4 };
	private static final int[] SWAP_LIMIT = { 0, 50, 100, 200 };
	private static final int[] SWAP_MAX = { 0, 10, 30, 60 };
	private static final int[] SWAP_CLEARANCE = { 16, 24, 32, 48 };

	private final Screen parent;

	public ElytraOptionsScreen(Screen parent) {
		super(Text.literal("鞘翅辅助（默认全部关闭）"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		ChaosConfig.TravelConfig config = mod.config().travel;
		int left = width / 2 - 160;
		int right = left + 160;

		addDrawableChild(ButtonWidget.builder(toggleLabel("无动力爬升", config.glideClimb), button ->
				toggleClimb(mod)).dimensions(left, 36, 152, 20).build());
		addDrawableChild(ButtonWidget.builder(toggleLabel("甲飞(省耐久)", config.elytraSwap), button ->
				toggleSwap(mod)).dimensions(right, 36, 152, 20).build());

		int y = 96;
		addDrawableChild(ButtonWidget.builder(Text.literal("爬升净空: " + config.glideClimbClearance + " 格"), button -> {
			config.glideClimbClearance = cycle(config.glideClimbClearance, CLIMB_CLEARANCE);
			refresh(mod);
		}).dimensions(left, y, 152, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("爬升下潜: " + config.glideClimbDiveBlocks + " 格"), button -> {
			config.glideClimbDiveBlocks = cycle(config.glideClimbDiveBlocks, CLIMB_DIVE);
			refresh(mod);
		}).dimensions(right, y, 152, 20).build());

		y += 22;
		addDrawableChild(ButtonWidget.builder(Text.literal("甲飞提前: " + config.elytraSwapLeadTicks + " tick"), button -> {
			config.elytraSwapLeadTicks = cycle(config.elytraSwapLeadTicks, SWAP_LEAD);
			refresh(mod);
		}).dimensions(left, y, 152, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("甲飞耐久: " + unlimited(config.elytraSwapDurabilityLimit)), button -> {
			config.elytraSwapDurabilityLimit = cycle(config.elytraSwapDurabilityLimit, SWAP_LIMIT);
			refresh(mod);
		}).dimensions(right, y, 152, 20).build());

		y += 22;
		addDrawableChild(ButtonWidget.builder(Text.literal("切换上限: " + unlimited(config.elytraSwapMaxSwaps)), button -> {
			config.elytraSwapMaxSwaps = cycle(config.elytraSwapMaxSwaps, SWAP_MAX);
			refresh(mod);
		}).dimensions(left, y, 152, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("甲飞净空: " + config.elytraSwapClearance + " 格"), button -> {
			config.elytraSwapClearance = cycle(config.elytraSwapClearance, SWAP_CLEARANCE);
			refresh(mod);
		}).dimensions(right, y, 152, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
				.dimensions(width / 2 - 50, height - 24, 100, 20).build());
	}

	private void refresh(ChaosHelperClient mod) {
		mod.saveConfigQuietly();
		client.setScreen(new ElytraOptionsScreen(parent));
	}

	private void toggleClimb(ChaosHelperClient mod) {
		ChaosConfig.TravelConfig config = mod.config().travel;
		config.glideClimb = !config.glideClimb;
		if (config.glideClimb) {
			ensureTravelEnabled(mod);
		}
		warnIfBoth(mod);
		mod.travel().resetAssists();
		mod.saveConfigQuietly();
		client.setScreen(new ElytraOptionsScreen(parent));
	}

	private void toggleSwap(ChaosHelperClient mod) {
		ChaosConfig.TravelConfig config = mod.config().travel;
		config.elytraSwap = !config.elytraSwap;
		if (config.elytraSwap) {
			ensureTravelEnabled(mod);
		}
		warnIfBoth(mod);
		mod.travel().resetAssists();
		mod.saveConfigQuietly();
		client.setScreen(new ElytraOptionsScreen(parent));
	}

	private static void warnIfBoth(ChaosHelperClient mod) {
		ChaosConfig.TravelConfig config = mod.config().travel;
		if (config.glideClimb && config.elytraSwap) {
			mod.notifications().error("爬升与甲飞同时开启会互相干扰（甲飞每秒会中断一次滑翔）");
		}
	}

	private static void ensureTravelEnabled(ChaosHelperClient mod) {
		if (!mod.moduleManager().isEnabled("travel")) {
			mod.moduleManager().setEnabled("travel", true);
			mod.notifications().info("已自动开启 travel 模块（鞘翅辅助需要它）");
		}
	}

	private static Text toggleLabel(String name, boolean enabled) {
		return Text.literal(name + ": " + (enabled ? "§a[开]" : "§7[关]"));
	}

	private static String unlimited(int value) {
		return value == 0 ? "不限" : Integer.toString(value);
	}

	private static int cycle(int value, int[] options) {
		for (int i = 0; i < options.length; i++) {
			if (options[i] == value) {
				return options[(i + 1) % options.length];
			}
		}
		return options[0];
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFF55FFFF);
		int left = width / 2 - 160;
		context.drawTextWithShadow(textRenderer,
				"两项均默认关闭，是否启用由你自己决定", left, 60, 0xFFAAAAAA);
		context.drawTextWithShadow(textRenderer,
				"爬升只改视角；甲飞用手动换装包，服务端全程权威计算", left, 72, 0xFFAAAAAA);
		context.drawTextWithShadow(textRenderer,
				"甲飞可能被部分服务器判违规，风险自负；优先耐久III+经验修补", left, 84, 0xFFFFAA55);
		ChaosConfig.TravelConfig travel = ChaosHelperClient.get().config().travel;
		String line = ChaosHelperClient.get().travel().assistStatus();
		if (travel.glideClimb && travel.elytraSwap) {
			line = line + " (注意: 两者会互相干扰)";
		}
		context.drawTextWithShadow(textRenderer, line, left, 166, 0xFF88FF88);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
