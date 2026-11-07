package com.chaoshelper.gui;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.minimap.XaeroWaypoints;
import com.chaoshelper.module.Module;
import com.chaoshelper.module.modules.TravelModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Clickable menu: module toggles, travel goal input, HUD editor entry.
 * All labels are Chinese on purpose (this build targets a zh-CN client).
 */
public final class ToolsScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget xField;
	private TextFieldWidget yField;
	private TextFieldWidget zField;
	private String status = "";
	private int waypointIndex;

	public ToolsScreen(Screen parent) {
		super(Text.literal("工具 / 旅行"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ChaosHelperClient mod = ChaosHelperClient.get();
		int left = width / 2 - 162;
		int right = width / 2 + 8;
		int top = 42;

		// Modules: two compact columns so the screen also fits at 240px height.
		int index = 0;
		for (Module module : mod.moduleManager().sortedModules()) {
			int column = index % 2;
			int row = index / 2;
			int x = left + column * 78;
			int y = top + row * 22;
			addDrawableChild(ButtonWidget.builder(moduleLabel(module), button -> {
				mod.moduleManager().toggle(module.id());
				client.setScreen(new ToolsScreen(parent));
			}).dimensions(x, y, 74, 20).build());
			index++;
		}

		int actionY = top;
		addDrawableChild(ButtonWidget.builder(Text.literal("HUD 编辑"), button ->
				client.setScreen(new HudEditorScreen(this))).dimensions(right, actionY, 154, 20).build());
		actionY += 22;
		addDrawableChild(ButtonWidget.builder(Text.literal("重新加载动态模块"), button -> {
			var result = mod.dynamicModuleLoader().reload();
			status = "动态模块: " + result.loaded() + " 个"
					+ (result.errors().isEmpty() ? "" : "，错误 " + result.errors().size() + " 个");
		}).dimensions(right, actionY, 154, 20).build());
		actionY += 22;
		addDrawableChild(ButtonWidget.builder(modeLabel(mod), button -> {
			cycleMode(mod);
			client.setScreen(new ToolsScreen(parent));
		}).dimensions(right, actionY, 154, 20).build());
		actionY += 28;

		xField = addDrawableChild(new TextFieldWidget(textRenderer, right, actionY, 46, 18, Text.literal("X")));
		yField = addDrawableChild(new TextFieldWidget(textRenderer, right + 50, actionY, 46, 18, Text.literal("Y")));
		zField = addDrawableChild(new TextFieldWidget(textRenderer, right + 100, actionY, 46, 18, Text.literal("Z")));
		prefillGoal(mod);

		int buttonY = actionY + 24;
		addDrawableChild(ButtonWidget.builder(Text.literal("出发"), button -> startTravel(mod))
				.dimensions(right, buttonY, 74, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("停止"), button -> {
			mod.travel().stop(true);
			status = "已停止旅行";
		}).dimensions(right + 80, buttonY, 74, 20).build());
		buttonY += 24;
		addDrawableChild(ButtonWidget.builder(Text.literal("填入当前坐标"), button -> fillCurrent(mod))
				.dimensions(right, buttonY, 74, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("下个路点"), button -> nextWaypoint())
				.dimensions(right + 80, buttonY, 74, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("鞘翅辅助"), button ->
				client.setScreen(new ElytraOptionsScreen(this)))
				.dimensions(width / 2 - 155, height - 24, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("完成"), button -> close())
				.dimensions(width / 2 - 50, height - 24, 100, 20).build());
	}

	private Text moduleLabel(Module module) {
		return Text.literal((module.isEnabled() ? "§a[开] " : "§7[关] ") + module.displayName());
	}

	private Text modeLabel(ChaosHelperClient mod) {
		String mode;
		if (mod.config().travel.glide) {
			mode = "滑翔";
		} else {
			mode = switch (mod.config().travel.mode) {
				case "walk" -> "步行";
				case "elytra" -> "鞘翅直飞";
				default -> "自动";
			};
		}
		return Text.literal("旅行模式: " + mode);
	}

	private void cycleMode(ChaosHelperClient mod) {
		if (mod.config().travel.glide) {
			mod.config().travel.glide = false;
			mod.config().travel.mode = "auto";
			return;
		}
		switch (mod.config().travel.mode) {
			case "auto" -> mod.config().travel.mode = "walk";
			case "walk" -> mod.config().travel.mode = "elytra";
			default -> {
				mod.config().travel.mode = "auto";
				mod.config().travel.glide = true;
			}
		}
		mod.saveConfigQuietly();
	}

	private void prefillGoal(ChaosHelperClient mod) {
		if (mod.travel().hasTarget()) {
			String[] parts = mod.travel().targetText().split(", ");
			if (parts.length == 3) {
				xField.setText(parts[0]);
				yField.setText(parts[1]);
				zField.setText(parts[2]);
				return;
			}
		}
		if (client != null && client.player != null) {
			xField.setText(Integer.toString((int) Math.floor(client.player.getX())));
			yField.setText(Integer.toString((int) Math.floor(client.player.getY())));
			zField.setText(Integer.toString((int) Math.floor(client.player.getZ())));
		}
	}

	private void fillCurrent(ChaosHelperClient mod) {
		if (client == null || client.player == null) {
			return;
		}
		xField.setText(Integer.toString((int) Math.floor(client.player.getX())));
		yField.setText(Integer.toString((int) Math.floor(client.player.getY())));
		zField.setText(Integer.toString((int) Math.floor(client.player.getZ())));
	}

	/**
	 * Cycles through the waypoints Xaero stored for the current dimension and
	 * fills their coordinates, so a minimap marker becomes a travel goal in one click.
	 */
	private void nextWaypoint() {
		List<XaeroWaypoints.Waypoint> waypoints = XaeroWaypoints.readAll();
		if (waypoints.isEmpty()) {
			status = "当前维度没有小地图路点";
			return;
		}
		waypointIndex = Math.floorMod(waypointIndex, waypoints.size());
		XaeroWaypoints.Waypoint waypoint = waypoints.get(waypointIndex);
		waypointIndex = (waypointIndex + 1) % waypoints.size();
		xField.setText(Integer.toString(waypoint.x()));
		yField.setText(Integer.toString(waypoint.y()));
		zField.setText(Integer.toString(waypoint.z()));
		status = "路点 " + (waypointIndex == 0 ? waypoints.size() : waypointIndex) + "/"
				+ waypoints.size() + ": " + waypoint.name();
	}

	private void startTravel(ChaosHelperClient mod) {
		try {
			int x = (int) Math.round(Double.parseDouble(xField.getText().trim()));
			int y = (int) Math.round(Double.parseDouble(yField.getText().trim()));
			int z = (int) Math.round(Double.parseDouble(zField.getText().trim()));
			TravelModule.Mode mode;
			if (mod.config().travel.glide) {
				mode = TravelModule.Mode.GLIDE;
			} else {
				mode = switch (mod.config().travel.mode) {
					case "walk" -> TravelModule.Mode.WALK;
					case "elytra" -> TravelModule.Mode.ELYTRA;
					default -> TravelModule.Mode.AUTO;
				};
			}
			if (!mod.moduleManager().isEnabled("travel")) {
				mod.moduleManager().setEnabled("travel", true);
			}
			String error = mod.travel().start(x, y, z, mode, "手动坐标");
			status = error == null ? String.format(Locale.ROOT, "前往 %d, %d, %d", x, y, z) : error;
		} catch (NumberFormatException exception) {
			status = "坐标格式不正确";
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFF55FFFF);
		context.drawTextWithShadow(textRenderer, "模块（点击切换）", width / 2 - 162, 30, 0xFFAAAAAA);
		context.drawTextWithShadow(textRenderer, "旅行 / 工具", width / 2 + 8, 30, 0xFFAAAAAA);
		context.drawTextWithShadow(textRenderer, "目标坐标", width / 2 + 8, 106, 0xFFAAAAAA);
		ChaosConfig.TravelConfig travel = ChaosHelperClient.get().config().travel;
		context.drawTextWithShadow(textRenderer,
				"鞘翅辅助: " + (travel.glideClimb ? "§a爬升开" : "§7爬升关") + " / "
						+ (travel.elytraSwap ? "§a甲飞开" : "§7甲飞关"),
				width / 2 - 162, height - 40, 0xFFAAAAAA);
		if (!status.isBlank()) {
			context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 44, 0xFFFFFF77);
		}
		context.drawCenteredTextWithShadow(textRenderer, XaeroWaypoints.statusLine(), width / 2, height - 56,
				0xFF888888);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
