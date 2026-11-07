package com.chaoshelper.gui;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.module.Module;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Dark three-column ClickGUI: category sidebar, module list with switches, and
 * a live settings panel. Everything is drawn by hand so the look matches the
 * rest of the client instead of vanilla widgets.
 */
public class ClickGuiScreen extends Screen {
	private static final String[] CATEGORIES = { "render", "info", "movement", "player", "misc" };
	private static final String[] CATEGORY_NAMES = { "渲染", "信息", "移动", "玩家", "杂项" };
	private static final int TITLE_HEIGHT = 26;
	private static final int FOOTER_HEIGHT = 16;
	private static final int SIDEBAR_WIDTH = 88;
	private static final int MODULE_ROW = 24;
	private static final int SETTING_ROW = 24;

	private final Screen parent;
	private String category = "render";
	private String selectedModule = "";
	private int moduleScroll;
	private int settingScroll;
	private Setting draggingSetting;
	private String status = "";

	private int x0;
	private int y0;
	private int windowWidth;
	private int windowHeight;
	private int contentTop;
	private int contentBottom;
	private int listX;
	private int listWidth;
	private int settingsX;

	public ClickGuiScreen(Screen parent) {
		super(Text.literal("ChaosHelper"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		windowWidth = Math.min(660, width - 20);
		windowHeight = Math.min(360, height - 20);
		x0 = (width - windowWidth) / 2;
		y0 = (height - windowHeight) / 2;
		contentTop = y0 + TITLE_HEIGHT;
		contentBottom = y0 + windowHeight - FOOTER_HEIGHT;
		listX = x0 + SIDEBAR_WIDTH + 3;
		listWidth = Math.min(220, windowWidth / 3);
		settingsX = listX + listWidth + 1;
		if (selectedModule.isBlank()) {
			List<Module> modules = modulesInCategory();
			if (!modules.isEmpty()) {
				selectedModule = modules.get(0).id();
			}
		}
	}

	private static ChaosHelperClient mod() {
		return ChaosHelperClient.get();
	}

	private List<Module> modulesInCategory() {
		List<Module> result = new ArrayList<>();
		for (Module module : mod().moduleManager().sortedModules()) {
			if (ModuleSettings.categoryOf(module.id()).equals(category)) {
				result.add(module);
			}
		}
		return result;
	}

	private Module selectedModule() {
		for (Module module : mod().moduleManager().sortedModules()) {
			if (module.id().equals(selectedModule)) {
				return module;
			}
		}
		return null;
	}

	private int countEnabled(String categoryId) {
		int count = 0;
		for (Module module : mod().moduleManager().sortedModules()) {
			if (module.isEnabled() && ModuleSettings.categoryOf(module.id()).equals(categoryId)) {
				count++;
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ render

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// 1.21.11: Screen.renderWithTooltip already renders the background, and the
		// blur controller allows only one blur per frame. Calling renderBackground
		// here as well throws "Can only blur once per frame".
		drawFrame(context, mouseX, mouseY);
		drawSidebar(context, mouseX, mouseY);
		drawModules(context, mouseX, mouseY);
		drawSettings(context, mouseX, mouseY);
		drawFooter(context);
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawFrame(DrawContext context, int mouseX, int mouseY) {
		context.fill(x0, y0, x0 + windowWidth, y0 + windowHeight, 0xF0141720);
		context.fill(x0, y0, x0 + windowWidth, y0 + TITLE_HEIGHT, 0xFF0C0F14);
		context.fill(x0, y0 + TITLE_HEIGHT - 1, x0 + windowWidth, y0 + TITLE_HEIGHT, 0xFF2A3342);
		context.fill(x0, y0, x0 + 3, y0 + windowHeight, 0xFF55FFFF);
		border(context, x0, y0, windowWidth, windowHeight, 0xFF2A3342);
		context.drawTextWithShadow(textRenderer, "ChaosHelper", x0 + 12, y0 + 9, 0xFF55FFFF);
		int nameWidth = textRenderer.getWidth("ChaosHelper");
		context.drawTextWithShadow(textRenderer, "CLICKGUI", x0 + 12 + nameWidth + 8, y0 + 9, 0xFF4C5868);
		int closeX = x0 + windowWidth - 18;
		boolean closeHover = mouseX >= closeX - 4 && mouseX <= closeX + 10 && mouseY >= y0 + 5
				&& mouseY <= y0 + 21;
		int toolsX = closeX - 46;
		boolean toolsHover = mouseX >= toolsX - 6 && mouseX <= toolsX + 28 && mouseY >= y0 + 5
				&& mouseY <= y0 + 21;
		context.drawTextWithShadow(textRenderer, "工具", toolsX, y0 + 9,
				toolsHover ? 0xFFFFFFFF : 0xFF8899AA);
		context.drawTextWithShadow(textRenderer, "x", closeX, y0 + 9,
				closeHover ? 0xFFFF6666 : 0xFF8899AA);
	}

	private void drawSidebar(DrawContext context, int mouseX, int mouseY) {
		context.fill(x0 + 3, contentTop, listX, contentBottom, 0xFF0F1218);
		for (int index = 0; index < CATEGORIES.length; index++) {
			int rowY = contentTop + 6 + index * 26;
			boolean active = CATEGORIES[index].equals(category);
			boolean hover = mouseX >= x0 + 3 && mouseX <= listX && mouseY >= rowY && mouseY < rowY + 24;
			if (active) {
				context.fill(x0 + 3, rowY, listX, rowY + 24, 0x5533AAFF);
				context.fill(x0 + 3, rowY, x0 + 5, rowY + 24, 0xFF55FFFF);
			} else if (hover) {
				context.fill(x0 + 3, rowY, listX, rowY + 24, 0x18FFFFFF);
			}
			context.drawTextWithShadow(textRenderer, CATEGORY_NAMES[index], x0 + 12, rowY + 8,
					active ? 0xFFFFFFFF : 0xFF8899AA);
			String count = Integer.toString(countEnabled(CATEGORIES[index]));
			if (countEnabled(CATEGORIES[index]) > 0) {
				context.drawTextWithShadow(textRenderer, count, listX - 8 - textRenderer.getWidth(count),
						rowY + 8, 0xFF55FF55);
			}
		}
	}

	private void drawModules(DrawContext context, int mouseX, int mouseY) {
		List<Module> modules = modulesInCategory();
		context.fill(listX, contentTop, settingsX, contentBottom, 0xF011151C);
		context.drawTextWithShadow(textRenderer, "模块 " + modules.size(), listX + 8, contentTop + 7, 0xFF8899AA);
		int listTop = contentTop + 22;
		int visible = Math.max(1, (contentBottom - listTop) / MODULE_ROW);
		int maxScroll = Math.max(0, modules.size() - visible);
		moduleScroll = Math.max(0, Math.min(moduleScroll, maxScroll));

		context.enableScissor(listX, listTop, settingsX, contentBottom);
		for (int index = 0; index < modules.size(); index++) {
			Module module = modules.get(index);
			int rowY = listTop + index * MODULE_ROW - moduleScroll;
			if (rowY + MODULE_ROW < listTop || rowY > contentBottom) {
				continue;
			}
			boolean hover = mouseX >= listX && mouseX < settingsX && mouseY >= rowY && mouseY < rowY + MODULE_ROW;
			boolean selected = module.id().equals(selectedModule);
			if (selected) {
				context.fill(listX, rowY, settingsX, rowY + MODULE_ROW, 0x3333AAFF);
			} else if (hover) {
				context.fill(listX, rowY, settingsX, rowY + MODULE_ROW, 0x18FFFFFF);
			}
			if (module.isEnabled()) {
				context.fill(listX + 5, rowY + 7, listX + 7, rowY + 17, 0xFF55FF55);
			}
			context.drawTextWithShadow(textRenderer, module.displayName(), listX + 12, rowY + 8,
					module.isEnabled() ? 0xFFE8F0FF : 0xFF77808F);
			drawToggle(context, settingsX - 28, rowY + 7, module.isEnabled());
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int barHeight = Math.max(14, (contentBottom - listTop) * visible / modules.size());
			int barY = listTop + (contentBottom - listTop - barHeight) * moduleScroll / maxScroll;
			context.fill(settingsX - 3, barY, settingsX - 1, barY + barHeight, 0xFF55FFFF);
		}
	}

	private void drawSettings(DrawContext context, int mouseX, int mouseY) {
		context.fill(settingsX, contentTop, x0 + windowWidth, contentBottom, 0xF00D1015);
		Module module = selectedModule();
		if (module == null) {
			context.drawTextWithShadow(textRenderer, "没有可配置的模块", settingsX + 10, contentTop + 8, 0xFF77808F);
			return;
		}
		context.drawTextWithShadow(textRenderer, module.displayName(), settingsX + 10, contentTop + 7, 0xFF55FFFF);
		String state = module.isEnabled() ? "已启用" : "已关闭";
		int nameWidth = textRenderer.getWidth(module.displayName());
		context.drawTextWithShadow(textRenderer, state, settingsX + 18 + nameWidth, contentTop + 7,
				module.isEnabled() ? 0xFF55FF55 : 0xFF77808F);
		context.fill(settingsX + 8, contentTop + 20, x0 + windowWidth - 8, contentTop + 21, 0xFF2A3342);

		List<Setting> settings = ModuleSettings.forModule(module, mod());
		int top = contentTop + 24;
		int maxScroll = Math.max(0, settings.size() * SETTING_ROW - (contentBottom - top));
		settingScroll = Math.max(0, Math.min(settingScroll, maxScroll));

		context.enableScissor(settingsX, top, x0 + windowWidth, contentBottom);
		for (int index = 0; index < settings.size(); index++) {
			Setting setting = settings.get(index);
			int rowY = top + index * SETTING_ROW - settingScroll;
			if (rowY + SETTING_ROW < top || rowY > contentBottom) {
				continue;
			}
			boolean hover = mouseX >= settingsX && mouseX < x0 + windowWidth && mouseY >= rowY
					&& mouseY < rowY + SETTING_ROW;
			if (hover) {
				context.fill(settingsX + 4, rowY, x0 + windowWidth - 4, rowY + SETTING_ROW, 0x14FFFFFF);
			}
			context.drawTextWithShadow(textRenderer, setting.label(), settingsX + 10, rowY + 4, 0xFFB8C4D4);
			switch (setting.kind()) {
				case BOOL -> drawToggle(context, x0 + windowWidth - 30, rowY + 7, setting.boolValue());
				case SLIDER -> {
					int trackX = settingsX + 10;
					int trackY = rowY + 15;
					int trackWidth = Math.max(20, x0 + windowWidth - 10 - trackX - 60);
					context.fill(trackX, trackY, trackX + trackWidth, trackY + 3, 0xFF2A3342);
					int filled = (int) (trackWidth * setting.ratio());
					context.fill(trackX, trackY, trackX + filled, trackY + 3, 0xFF55FFFF);
					context.fill(trackX + filled - 1, trackY - 2, trackX + filled + 1, trackY + 5, 0xFFE8F0FF);
					String value = setting.valueText();
					context.drawTextWithShadow(textRenderer, value,
							x0 + windowWidth - 10 - textRenderer.getWidth(value), rowY + 4, 0xFF55FFFF);
				}
				case BUTTON -> {
					String value = setting.valueText();
					context.drawTextWithShadow(textRenderer, value,
							x0 + windowWidth - 10 - textRenderer.getWidth(value), rowY + 4, setting.valueColor());
				}
			}
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int barHeight = Math.max(14, (contentBottom - top) * (contentBottom - top) / (settings.size() * SETTING_ROW));
			int barY = top + (contentBottom - top - barHeight) * settingScroll / maxScroll;
			context.fill(x0 + windowWidth - 3, barY, x0 + windowWidth - 1, barY + barHeight, 0xFF55FFFF);
		}
	}

	private void drawFooter(DrawContext context) {
		context.fill(x0, contentBottom, x0 + windowWidth, y0 + windowHeight, 0xFF0C0F14);
		context.fill(x0, contentBottom, x0 + windowWidth, contentBottom + 1, 0xFF2A3342);
		String left = "模块 " + mod().moduleManager().enabledCount() + " 开启";
		context.drawTextWithShadow(textRenderer, left, x0 + 10, contentBottom + 4, 0xFF77808F);
		String hint = status.isBlank() ? "左键开关 / 选中，滑块可拖动" : status;
		if (hint.length() > 42) {
			hint = hint.substring(0, 42) + "...";
		}
		context.drawTextWithShadow(textRenderer, hint,
				x0 + windowWidth - 10 - textRenderer.getWidth(hint), contentBottom + 4, 0xFF556070);
	}

	private static void drawToggle(DrawContext context, int x, int y, boolean on) {
		context.fill(x, y, x + 20, y + 10, on ? 0xFF23503A : 0xFF2A3342);
		context.fill(on ? x + 11 : x + 2, y + 2, on ? x + 18 : x + 9, y + 8,
				on ? 0xFF55FF55 : 0xFF667080);
	}

	private static void border(DrawContext context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}

	// ------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mouseX = click.x();
		double mouseY = click.y();
		if (click.button() != 0) {
			return super.mouseClicked(click, doubled);
		}
		if (mouseX >= x0 + windowWidth - 22 && mouseX <= x0 + windowWidth - 8
				&& mouseY >= y0 + 5 && mouseY <= y0 + 21) {
			close();
			return true;
		}
		if (mouseX >= x0 + windowWidth - 68 && mouseX <= x0 + windowWidth - 28
				&& mouseY >= y0 + 5 && mouseY <= y0 + 21) {
			client.setScreen(new ToolsScreen(this));
			return true;
		}
		if (mouseX >= x0 + 3 && mouseX <= listX && mouseY >= contentTop + 6
				&& mouseY < contentTop + 6 + CATEGORIES.length * 26) {
			int index = (int) ((mouseY - contentTop - 6) / 26);
			if (index >= 0 && index < CATEGORIES.length) {
				category = CATEGORIES[index];
				moduleScroll = 0;
				settingScroll = 0;
				List<Module> modules = modulesInCategory();
				selectedModule = modules.isEmpty() ? "" : modules.get(0).id();
				return true;
			}
		}

		List<Module> modules = modulesInCategory();
		int listTop = contentTop + 22;
		if (mouseX >= listX && mouseX < settingsX && mouseY >= listTop && mouseY < contentBottom) {
			int index = (int) ((mouseY - listTop + moduleScroll) / MODULE_ROW);
			if (index >= 0 && index < modules.size()) {
				Module module = modules.get(index);
				int toggleX = settingsX - 28;
				if (mouseX >= toggleX && mouseX <= toggleX + 20) {
					mod().moduleManager().toggle(module.id());
					status = module.displayName() + (module.isEnabled() ? " 开启" : " 关闭");
					save();
				} else {
					selectedModule = module.id();
					settingScroll = 0;
				}
				return true;
			}
		}

		Module module = selectedModule();
		if (module != null && mouseX >= settingsX && mouseX < x0 + windowWidth
				&& mouseY >= contentTop + 24 && mouseY < contentBottom) {
			List<Setting> settings = ModuleSettings.forModule(module, mod());
			int top = contentTop + 24;
			int index = (int) ((mouseY - top + settingScroll) / SETTING_ROW);
			if (index >= 0 && index < settings.size()) {
				Setting setting = settings.get(index);
				switch (setting.kind()) {
					case BOOL -> {
						setting.toggleBool();
						status = setting.label() + ": " + setting.valueText();
						save();
					}
					case SLIDER -> {
						int rowY = top + index * SETTING_ROW - settingScroll;
						if (mouseY < rowY + 11) {
							// Clicking the label must not jump the value.
							return true;
						}
						draggingSetting = setting;
						applySlider(setting, mouseX);
						status = setting.label() + ": " + setting.valueText();
					}
					case BUTTON -> {
						setting.press();
						status = setting.label();
						save();
					}
				}
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	/** Updates the value only; the drag ends with a single save on release. */
	private void applySlider(Setting setting, double mouseX) {
		int trackX = settingsX + 10;
		int trackWidth = Math.max(20, x0 + windowWidth - 10 - trackX - 60);
		setting.setRatio((mouseX - trackX) / trackWidth);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (draggingSetting != null) {
			applySlider(draggingSetting, click.x());
			status = draggingSetting.label() + ": " + draggingSetting.valueText();
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (draggingSetting != null) {
			draggingSetting = null;
			save();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int step = (int) Math.signum(verticalAmount) * 2;
		if (mouseX >= listX && mouseX < settingsX && mouseY >= contentTop && mouseY < contentBottom) {
			moduleScroll -= step;
			return true;
		}
		if (mouseX >= settingsX && mouseX < x0 + windowWidth && mouseY >= contentTop && mouseY < contentBottom) {
			settingScroll -= step;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void save() {
		mod().saveConfigQuietly();
	}

	@Override
	public void close() {
		save();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
