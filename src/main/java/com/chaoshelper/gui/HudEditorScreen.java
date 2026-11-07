package com.chaoshelper.gui;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.config.HudElement;
import com.chaoshelper.hud.HudRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Live HUD editor: pick any widget on the left, drag it on the canvas, add or
 * delete widgets, and open a per-widget property page. The real renderer draws
 * the preview, so what is on screen is exactly what the game will show.
 */
public final class HudEditorScreen extends Screen {
	private static final int LIST_WIDTH = 150;
	private static final int ROW_HEIGHT = 20;
	private final Screen parent;
	private final List<HudRenderer.Placed> placed = new ArrayList<>();
	private String selectedId = "";
	private String draggingId = "";
	private int grabX;
	private int grabY;
	private int listScroll;
	private String status = "";

	public HudEditorScreen(Screen parent) {
		super(Text.literal("HUD 编辑器"));
		this.parent = parent;
	}

	private ChaosConfig.HudConfig hud() {
		return ChaosHelperClient.get().config().hud;
	}

	@Override
	protected void init() {
		int rowOne = height - 50;
		int rowTwo = height - 26;
		int start = width / 2 - 154;
		addDrawableChild(ButtonWidget.builder(Text.literal("新增"), button -> addElement())
				.dimensions(start, rowOne, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("复制"), button -> duplicateElement())
				.dimensions(start + 104, rowOne, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("删除"), button -> deleteElement())
				.dimensions(start + 208, rowOne, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("属性"), button -> openProperties())
				.dimensions(start, rowTwo, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("恢复默认"), button -> {
			hud().resetElements();
			selectedId = "";
			status = "已恢复默认 HUD";
			ChaosHelperClient.get().saveConfigQuietly();
		}).dimensions(start + 104, rowTwo, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("完成"), button -> close())
				.dimensions(start + 208, rowTwo, 100, 20).build());
	}

	private HudElement selected() {
		for (HudElement element : hud().elements) {
			if (element.id.equals(selectedId)) {
				return element;
			}
		}
		return null;
	}

	private void select(String id) {
		selectedId = id;
	}

	private void addElement() {
		HudElement element = new HudElement(HudElement.newId(), "text");
		element.template = element.defaultTemplate();
		element.x = width / 2 - 60;
		element.y = height / 2 - 20;
		hud().elements.add(element);
		select(element.id);
		status = "已新增自定义文本，可在「属性」里改类型/模板";
		ChaosHelperClient.get().saveConfigQuietly();
	}

	private void duplicateElement() {
		HudElement element = selected();
		if (element == null) {
			status = "先选中一个元素";
			return;
		}
		HudElement copy = element.copy(HudElement.newId());
		if (copy.x >= 0 && copy.y >= 0) {
			copy.x += 8;
			copy.y += 8;
		}
		hud().elements.add(copy);
		select(copy.id);
		status = "已复制";
		ChaosHelperClient.get().saveConfigQuietly();
	}

	private void deleteElement() {
		HudElement element = selected();
		if (element == null) {
			status = "先选中一个元素";
			return;
		}
		hud().elements.remove(element);
		selectedId = "";
		status = "已删除";
		ChaosHelperClient.get().saveConfigQuietly();
	}

	private void openProperties() {
		HudElement element = selected();
		if (element == null) {
			status = "先选中一个元素";
			return;
		}
		client.setScreen(new HudElementScreen(this, element));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		ChaosHelperClient mod = ChaosHelperClient.get();
		placed.clear();
		if (client != null && client.player != null) {
			placed.addAll(mod.hudRenderer().renderHud(context, client, true));
		}

		// Selection outline + drag handle.
		for (HudRenderer.Placed box : placed) {
			if (!box.id().equals(selectedId)) {
				continue;
			}
			int color = 0xFF55FFFF;
			context.fill(box.x() - 1, box.y() - 1, box.x() + box.width() + 1, box.y(), color);
			context.fill(box.x() - 1, box.y() + box.height(), box.x() + box.width() + 1, box.y() + box.height() + 1, color);
			context.fill(box.x() - 1, box.y(), box.x(), box.y() + box.height(), color);
			context.fill(box.x() + box.width(), box.y(), box.x() + box.width() + 1, box.y() + box.height(), color);
		}

		drawList(context, mouseX, mouseY);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFF55FFFF);
		context.drawTextWithShadow(textRenderer, "左键拖动画面里的元素；点左侧列表选中", width / 2, 20, 0xFF888888);
		if (!status.isBlank()) {
			context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 62, 0xFFFFFF77);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawList(DrawContext context, int mouseX, int mouseY) {
		int top = 32;
		int bottom = height - 56;
		context.fill(8, top, 8 + LIST_WIDTH, bottom, 0xCC0E1116);
		context.fill(8, top, 10, bottom, 0xFF55FFFF);
		List<HudElement> elements = hud().elements;
		int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
		int maxScroll = Math.max(0, elements.size() - visible);
		listScroll = Math.max(0, Math.min(listScroll, maxScroll));

		context.enableScissor(10, top, 8 + LIST_WIDTH, bottom);
		for (int index = 0; index < elements.size(); index++) {
			int rowY = top + index * ROW_HEIGHT - listScroll;
			if (rowY + ROW_HEIGHT < top || rowY > bottom) {
				continue;
			}
			HudElement element = elements.get(index);
			boolean hovered = mouseX >= 8 && mouseX <= 8 + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			boolean selected = element.id.equals(selectedId);
			if (selected) {
				context.fill(10, rowY, 8 + LIST_WIDTH, rowY + ROW_HEIGHT, 0x5533AAFF);
			} else if (hovered) {
				context.fill(10, rowY, 8 + LIST_WIDTH, rowY + ROW_HEIGHT, 0x22FFFFFF);
			}
			context.fill(12, rowY + 6, 18, rowY + 12, element.enabled ? 0xFF55FF55 : 0xFF666666);
			context.drawTextWithShadow(textRenderer, element.heading(), 24, rowY + 6,
					element.enabled ? 0xFFFFFFFF : 0xFF888888);
			String type = element.typeLabel();
			context.drawTextWithShadow(textRenderer, type,
					8 + LIST_WIDTH - 6 - textRenderer.getWidth(type), rowY + 6, 0xFF777777);
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int barHeight = Math.max(12, (bottom - top) * visible / elements.size());
			int barY = top + (bottom - top - barHeight) * listScroll / maxScroll;
			context.fill(8 + LIST_WIDTH - 3, barY, 8 + LIST_WIDTH - 1, barY + barHeight, 0xFF55FFFF);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		double mouseX = click.x();
		double mouseY = click.y();
		int top = 32;
		int bottom = height - 56;
		if (click.button() == 0 && mouseX >= 8 && mouseX <= 8 + LIST_WIDTH && mouseY >= top && mouseY < bottom) {
			int index = (int) ((mouseY - top + listScroll) / ROW_HEIGHT);
			if (index >= 0 && index < hud().elements.size()) {
				HudElement element = hud().elements.get(index);
				if (mouseX >= 12 && mouseX <= 18) {
					element.enabled = !element.enabled;
					ChaosHelperClient.get().saveConfigQuietly();
				} else {
					select(element.id);
				}
				return true;
			}
			return true;
		}
		if (click.button() != 0) {
			return false;
		}
		// Topmost widget wins, matching the render order.
		for (int index = placed.size() - 1; index >= 0; index--) {
			HudRenderer.Placed box = placed.get(index);
			if (box.contains(mouseX, mouseY)) {
				select(box.id());
				HudElement element = selected();
				if (element != null) {
					draggingId = element.id;
					grabX = (int) mouseX - box.x();
					grabY = (int) mouseY - box.y();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (draggingId != null && !draggingId.isEmpty()) {
			HudElement element = selected();
			if (element != null) {
				HudRenderer.Placed box = boxOf(element.id);
				int boxWidth = box == null ? 40 : box.width();
				int boxHeight = box == null ? 14 : box.height();
				element.x = (int) Math.max(0, Math.min(click.x() - grabX, width - boxWidth));
				element.y = (int) Math.max(0, Math.min(click.y() - grabY, height - boxHeight));
			}
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (draggingId != null && !draggingId.isEmpty()) {
			draggingId = "";
			ChaosHelperClient.get().saveConfigQuietly();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int top = 32;
		int bottom = height - 56;
		if (mouseX >= 8 && mouseX <= 8 + LIST_WIDTH && mouseY >= top && mouseY < bottom) {
			listScroll = Math.max(0, listScroll - (int) Math.signum(verticalAmount) * 2);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private HudRenderer.Placed boxOf(String id) {
		for (HudRenderer.Placed box : placed) {
			if (box.id().equals(id)) {
				return box;
			}
		}
		return null;
	}

	@Override
	public void close() {
		ChaosHelperClient.get().saveConfigQuietly();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
