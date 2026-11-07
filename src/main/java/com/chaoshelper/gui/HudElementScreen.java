package com.chaoshelper.gui;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.HudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Property editor for a single HUD widget, split into three pages so everything
 * fits at the minimum supported GUI height (240 px).
 */
public final class HudElementScreen extends Screen {
	private static final int[] SCALES = { 75, 100, 125, 150, 200 };
	private static final int[] LINE_HEIGHTS = { 8, 9, 10, 11, 12, 14, 16 };
	private static final int[] PADDINGS = { 0, 2, 4, 6, 8, 10 };
	private static final int[] GAPS = { 0, 2, 4, 6, 8, 10 };
	private static final int[] MAX_ENTRIES = { 3, 5, 8, 10, 15, 20, 30 };
	private static final String[] TYPES = { "text", "coords", "self", "target", "players", "drops", "records",
			"modules", "notices", "baritone", "perf", "ships" };
	private static final String[] ANCHORS = { "tl", "tr", "bl", "br" };
	private static final String[] ALIGNS = { "left", "center", "right" };
	private static final String[] SORTS = { "distance", "name", "health", "age" };
	private static final int[] COLORS = {
			0xFFFFFFFF, 0xFFE0E0E0, 0xFFAAAAAA, 0xFF888888,
			0xFF55FFFF, 0xFF55FF55, 0xFFFFFF55, 0xFFFFAA55,
			0xFFFF5555, 0xFFFF55FF, 0xFF55AAFF, 0xFFFFD700
	};

	private final Screen parent;
	private final HudElement element;
	private final List<Runnable> refreshers = new ArrayList<>();
	private int page;

	public HudElementScreen(Screen parent, HudElement element) {
		super(Text.literal("元素属性"));
		this.parent = parent;
		this.element = element;
	}

	@Override
	protected void init() {
		contextLabels.clear();
		int tabY = 22;
		int start = width / 2 - 153;
		for (int index = 0; index < 3; index++) {
			int tab = index;
			String[] names = { "基础", "样式", "数据" };
			addDrawableChild(ButtonWidget.builder(
					Text.literal((page == tab ? "§b" : "§7") + names[index]),
					button -> switchPage(tab)).dimensions(start + index * 104, tabY, 100, 20).build());
		}
		switch (page) {
			case 0 -> initBasic();
			case 1 -> initStyle();
			default -> initData();
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
				.dimensions(width / 2 - 50, height - 26, 100, 20).build());
	}

	private void initBasic() {
		int left = width / 2 - 160;
		int right = width / 2 + 8;
		toggle(left, 48, "显示", () -> element.enabled, value -> element.enabled = value);
		toggle(right, 48, "显示标题", () -> element.showTitle, value -> element.showTitle = value);
		cycle(left, 70, "类型", () -> typeLabel(), () -> element.type = next(TYPES, element.type),
				() -> element.template = "");
		cycle(right, 70, "锚点", () -> element.anchor, () -> element.anchor = next(ANCHORS, element.anchor), null);
		cycle(left, 92, "对齐", () -> alignLabel(), () -> element.align = next(ALIGNS, element.align), null);
		addDrawableChild(ButtonWidget.builder(Text.literal("位置: 重置为自动"), button -> {
			element.x = -1;
			element.y = -1;
			save();
			refreshAll();
		}).dimensions(right, 92, 152, 20).build());
		cycle(left, 114, "排序", () -> sortLabel(), () -> element.sort = next(SORTS, element.sort), null);
		toggle(right, 114, "空列表隐藏", () -> element.hideEmpty, value -> element.hideEmpty = value);
	}

	private void initStyle() {
		int left = width / 2 - 160;
		int right = width / 2 + 8;
		cycle(left, 48, "缩放", () -> percent(element.scale), () -> element.scale = nextScale(element.scale), null);
		cycle(right, 48, "行高", () -> element.lineHeight + " px", () -> element.lineHeight = next(LINE_HEIGHTS, element.lineHeight), null);
		cycle(left, 70, "内边距", () -> element.padding + " px", () -> element.padding = next(PADDINGS, element.padding), null);
		cycle(right, 70, "间距", () -> element.gap + " px", () -> element.gap = next(GAPS, element.gap), null);
		toggle(left, 92, "背景", () -> element.background != 0, value -> element.background = value ? 0x88000000 : 0);
		toggle(right, 92, "边框", () -> element.border, value -> element.border = value);
		toggle(left, 114, "文字阴影", () -> element.shadow, value -> element.shadow = value);
		cycle(right, 114, "文字颜色", () -> colorLabel(element.color), () -> element.color = nextColor(element.color), null);
		cycle(left, 136, "强调色", () -> colorLabel(element.accent), () -> element.accent = nextColor(element.accent), null);
		addDrawableChild(ButtonWidget.builder(Text.literal("重置样式"), button -> {
			element.color = 0xFFE0E0E0;
			element.accent = 0xFF55FFFF;
			element.background = 0x88000000;
			element.border = true;
			element.shadow = true;
			element.scale = 1.0F;
			element.lineHeight = 10;
			element.padding = 4;
			element.gap = 2;
			save();
			refreshAll();
		}).dimensions(right, 136, 152, 20).build());
	}

	private void initData() {
		int left = width / 2 - 160;
		int right = width / 2 + 8;
		contextLabel("模板（每行一条，支持 {x} {y} {z} {fps} {hp} 等）", left, 44);
		TextFieldWidget template = addDrawableChild(new TextFieldWidget(textRenderer, left, 54, 312, 18,
				Text.literal("模板")));
		template.setMaxLength(512);
		template.setText(element.template == null ? "" : element.template);
		template.setChangedListener(value -> element.template = value);

		contextLabel("过滤（列表类型按关键词筛选，空=全部）", left, 78);
		TextFieldWidget filter = addDrawableChild(new TextFieldWidget(textRenderer, left, 88, 312, 18,
				Text.literal("过滤")));
		filter.setMaxLength(64);
		filter.setText(element.filter == null ? "" : element.filter);
		filter.setChangedListener(value -> element.filter = value);

		cycle(left, 112, "行数上限", () -> element.maxEntries + " 行", () -> element.maxEntries = next(MAX_ENTRIES, element.maxEntries), null);
		addDrawableChild(ButtonWidget.builder(Text.literal("使用默认模板"), button -> {
			element.template = element.defaultTemplate();
			save();
			client.setScreen(new HudElementScreen(parent, element));
		}).dimensions(right, 112, 152, 20).build());
		contextLabel("可用变量因类型而异；玩家行: {name} {dist} {hp} {armor} {flags} {team}", left, 138);
		contextLabel("掉落行: {item} {count} {dist} {age}｜模块行: {name} {status}", left, 150);
		contextLabel("记录行: {type} {message} {x} {y} {z} {time} {dist}", left, 162);
	}

	private void contextLabel(String text, int x, int y) {
		contextLabels.add(new Label(text, x, y));
	}

	private record Label(String text, int x, int y) {
	}

	private final List<Label> contextLabels = new ArrayList<>();

	private void toggle(int x, int y, String name, BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
		ButtonWidget[] holder = new ButtonWidget[1];
		holder[0] = addDrawableChild(ButtonWidget.builder(toggleText(name, getter.getAsBoolean()), button -> {
			setter.accept(!getter.getAsBoolean());
			save();
			refreshAll();
		}).dimensions(x, y, 152, 20).build());
		refreshers.add(() -> holder[0].setMessage(toggleText(name, getter.getAsBoolean())));
	}

	private void cycle(int x, int y, String name, Supplier<String> valueText, Runnable action, Runnable after) {
		ButtonWidget[] holder = new ButtonWidget[1];
		holder[0] = addDrawableChild(ButtonWidget.builder(cycleText(name, valueText.get()), button -> {
			action.run();
			if (after != null) {
				after.run();
			}
			save();
			refreshAll();
		}).dimensions(x, y, 152, 20).build());
		refreshers.add(() -> holder[0].setMessage(cycleText(name, valueText.get())));
	}

	private void refreshAll() {
		for (Runnable refresher : refreshers) {
			refresher.run();
		}
	}

	private static Text toggleText(String name, boolean value) {
		return Text.literal(name + ": " + (value ? "§a开" : "§7关"));
	}

	private static Text cycleText(String name, String value) {
		return Text.literal(name + ": §b" + value);
	}

	private void switchPage(int next) {
		save();
		page = next;
		client.setScreen(new HudElementScreen(parent, element));
	}

	private void save() {
		element.normalize();
		ChaosHelperClient.get().saveConfigQuietly();
	}

	private String typeLabel() {
		for (String type : TYPES) {
			if (type.equals(element.type)) {
				return element.typeLabel();
			}
		}
		return element.type;
	}

	private String alignLabel() {
		return switch (element.align) {
			case "center" -> "居中";
			case "right" -> "右对齐";
			default -> "左对齐";
		};
	}

	private String sortLabel() {
		return switch (element.sort) {
			case "name" -> "名称";
			case "health" -> "血量";
			case "age" -> "存在时间";
			default -> "距离";
		};
	}

	private static String percent(float scale) {
		return Math.round(scale * 100) + "%";
	}

	private static String colorLabel(int color) {
		return String.format("#%08X", color);
	}

	private static String next(String[] values, String current) {
		for (int index = 0; index < values.length; index++) {
			if (values[index].equals(current)) {
				return values[(index + 1) % values.length];
			}
		}
		return values[0];
	}

	private static int next(int[] values, int current) {
		for (int index = 0; index < values.length; index++) {
			if (values[index] == current) {
				return values[(index + 1) % values.length];
			}
		}
		return values[0];
	}

	private static float nextScale(float current) {
		int percent = Math.round(current * 100.0F);
		int nextPercent = next(SCALES, percent);
		return nextPercent / 100.0F;
	}

	private static int nextColor(int current) {
		for (int index = 0; index < COLORS.length; index++) {
			if (COLORS[index] == current) {
				return COLORS[(index + 1) % COLORS.length];
			}
		}
		return COLORS[0];
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Background is drawn by Screen.renderWithTooltip; see ClickGuiScreen.
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFF55FFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				element.heading() + "  §8(" + element.typeLabel() + ")", width / 2, 36, 0xFFAAAAAA);
		for (Label label : contextLabels) {
			context.drawTextWithShadow(textRenderer, label.text(), label.x(), label.y(), 0xFF888888);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		save();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
