package com.chaoshelper.hud;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.DropData;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.api.PlayerData;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.config.HudElement;
import com.chaoshelper.data.RecordEntry;
import com.chaoshelper.data.ShipPoint;
import com.chaoshelper.team.TeamManager;
import com.chaoshelper.util.DirectionUtil;
import com.chaoshelper.util.ErrorLog;
import com.chaoshelper.util.NotificationManager;
import com.chaoshelper.util.TimeUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Element-driven HUD.
 *
 * <p>Every widget in {@code hud.elements} is rendered independently: the player
 * decides what it shows (type), how each line reads (template), where it sits
 * (drag or anchor stacking) and how it looks (colour, scale, background,
 * border, shadow, padding, alignment). Nothing here walks the world; all values
 * come from cached snapshots.
 */
public final class HudRenderer implements HudRenderCallback {
	private final ChaosHelperClient mod;

	public HudRenderer(ChaosHelperClient mod) {
		this.mod = mod;
	}

	/** Screen box of one rendered widget, used by the editor for hit testing. */
	public record Placed(String id, int x, int y, int width, int height) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
		}
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = mod.client();
		if (client.player == null || client.world == null || client.options.hudHidden) {
			return;
		}
		// A screen already dims the world; drawing widgets on top of menus is noise.
		if (client.currentScreen != null) {
			return;
		}
		drawErrors(context, client);
		if (mod.moduleManager().isEnabled("esp") && mod.config().esp.showLabels) {
			drawEspLabels(context, client);
		}
		if (!mod.moduleManager().isEnabled("hud")) {
			return;
		}
		renderHud(context, client, false);
	}

	/**
	 * Draws every widget and returns its box. {@code previewDisabled} also draws
	 * switched-off widgets (dimmed) so the editor can still grab them.
	 */
	public List<Placed> renderHud(DrawContext context, MinecraftClient client, boolean previewDisabled) {
		ChaosConfig.HudConfig hud = mod.config().hud;
		Map<String, String> global = HudVariables.global(mod, client);
		int margin = hud.margin;
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();
		// Stacking cursors: 0=tl, 1=tr, 2=bl, 3=br. bl/br grow upwards.
		int[] topLeft = {margin, margin};
		int[] topRight = {screenWidth - margin, margin};
		int[] bottomLeft = {margin, screenHeight - margin};
		int[] bottomRight = {screenWidth - margin, screenHeight - margin};

		List<Placed> placed = new ArrayList<>(hud.elements.size());
		for (HudElement element : hud.elements) {
			boolean dimmed = !element.enabled;
			if (dimmed && !previewDisabled) {
				continue;
			}
			List<Line> lines = buildLines(element, client, global);
			if (lines.isEmpty() && element.hideEmpty) {
				continue;
			}
			if (lines.isEmpty() && !element.showTitle) {
				continue;
			}
			int rawWidth = measureWidth(client, element, lines);
			int rawHeight = measureHeight(element, lines);
			int boxWidth = Math.round((rawWidth + element.padding * 2) * element.scale);
			int boxHeight = Math.round((rawHeight + element.padding * 2) * element.scale);
			if (boxWidth <= 0 || boxHeight <= 0) {
				continue;
			}

			int anchor = anchorIndex(element.anchor);
			int x;
			int y;
			if (element.x >= 0 && element.y >= 0) {
				x = element.x;
				y = element.y;
			} else {
				switch (anchor) {
					case 0 -> {
						x = topLeft[0];
						y = topLeft[1];
					}
					case 1 -> {
						x = topRight[0] - boxWidth;
						y = topRight[1];
					}
					case 2 -> {
						x = bottomLeft[0];
						y = bottomLeft[1] - boxHeight;
					}
					default -> {
						x = bottomRight[0] - boxWidth;
						y = bottomRight[1] - boxHeight;
					}
				}
			}
			x = clamp(x, 0, Math.max(0, screenWidth - boxWidth));
			y = clamp(y, 0, Math.max(0, screenHeight - boxHeight));
			drawElement(context, client, element, lines, x, y, boxWidth, boxHeight, dimmed);
			placed.add(new Placed(element.id, x, y, boxWidth, boxHeight));

			switch (anchor) {
				case 0 -> topLeft[1] = y + boxHeight + element.gap;
				case 1 -> topRight[1] = y + boxHeight + element.gap;
				case 2 -> bottomLeft[1] = y - element.gap;
				default -> bottomRight[1] = y - element.gap;
			}
		}
		return placed;
	}

	private static int anchorIndex(String anchor) {
		return switch (anchor) {
			case "tr" -> 1;
			case "bl" -> 2;
			case "br" -> 3;
			default -> 0;
		};
	}

	private void drawElement(DrawContext context, MinecraftClient client, HudElement element, List<Line> lines,
			int x, int y, int boxWidth, int boxHeight, boolean dimmed) {
		float scale = element.scale <= 0 ? 1.0F : element.scale;
		float localWidth = boxWidth / scale;
		float localHeight = boxHeight / scale;
		int accent = multiplyAlpha(element.accent, dimmed ? 0.35F : 1.0F);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(scale, scale);

		int left = Math.round(localWidth);
		int bottom = Math.round(localHeight);
		if (element.background != 0) {
			context.fill(0, 0, left, bottom, multiplyAlpha(element.background, dimmed ? 0.35F : 1.0F));
		}
		if (element.border) {
			int borderColor = (accent & 0x00FFFFFF) | 0x66000000;
			context.fill(0, 0, left, 1, borderColor);
			context.fill(0, bottom - 1, left, bottom, borderColor);
			context.fill(0, 0, 1, bottom, borderColor);
			context.fill(left - 1, 0, left, bottom, borderColor);
		}
		// Left accent stripe: the "cheat client" signature.
		context.fill(0, 0, 2, bottom, accent);

		int lineY = element.padding;
		if (element.showTitle && !element.heading().isBlank()) {
			String heading = element.heading();
			int headingX = textX(element, left, client.textRenderer.getWidth(heading));
			drawText(context, client, element, heading, headingX, lineY, accent);
			lineY += element.lineHeight;
			if (element.border) {
				context.fill(element.padding, lineY - 1, left - element.padding, lineY,
						(accent & 0x00FFFFFF) | 0x33000000);
			}
		}
		for (Line line : lines) {
			if (lineY + element.lineHeight > bottom) {
				break;
			}
			int lineX = textX(element, left, client.textRenderer.getWidth(line.text()));
			drawText(context, client, element, line.text(), lineX, lineY, multiplyAlpha(line.color(), dimmed ? 0.45F : 1.0F));
			lineY += element.lineHeight;
		}
		context.getMatrices().popMatrix();
	}

	private int textX(HudElement element, int localWidth, int textWidth) {
		return switch (element.align) {
			case "center" -> Math.max(element.padding, (localWidth - textWidth) / 2);
			case "right" -> Math.max(element.padding, localWidth - element.padding - textWidth);
			default -> element.padding;
		};
	}

	private void drawText(DrawContext context, MinecraftClient client, HudElement element, String text,
			int x, int y, int color) {
		if (element.shadow) {
			context.drawTextWithShadow(client.textRenderer, text, x, y, color);
		} else {
			context.drawText(client.textRenderer, text, x, y, color, false);
		}
	}

	/** Unscaled text width; the caller multiplies by {@code element.scale}. */
	private int measureWidth(MinecraftClient client, HudElement element, List<Line> lines) {
		int max = 0;
		if (element.showTitle && !element.heading().isBlank()) {
			max = client.textRenderer.getWidth(element.heading());
		}
		for (Line line : lines) {
			max = Math.max(max, client.textRenderer.getWidth(line.text()));
		}
		return max;
	}

	/** Unscaled text height. */
	private int measureHeight(HudElement element, List<Line> lines) {
		int rows = lines.size() + (element.showTitle && !element.heading().isBlank() ? 1 : 0);
		return rows * element.lineHeight;
	}

	// ---------------------------------------------------------------- content

	private List<Line> buildLines(HudElement element, MinecraftClient client, Map<String, String> global) {
		List<Line> lines = switch (element.type) {
			case "players" -> playerLines(element, global);
			case "drops" -> dropLines(element, global);
			case "ships" -> shipLines(element, global);
			case "records" -> recordLines(element, client, global);
			case "modules" -> moduleLines(element, global);
			case "notices" -> noticeLines(element, global);
			case "target" -> targetLines(element, client, global);
			case "baritone" -> List.of(new Line(HudVariables.apply("{baritone}", global), element.color));
			case "self", "coords", "perf", "text" -> templateLines(element, global, global);
			default -> templateLines(element, global, global);
		};
		int limit = Math.max(1, element.maxEntries);
		if (lines.size() > limit) {
			lines = new ArrayList<>(lines.subList(0, limit));
		}
		return lines;
	}

	private List<Line> templateLines(HudElement element, Map<String, String> global, Map<String, String> row) {
		String[] parts = HudVariables.splitLines(element.effectiveTemplate());
		List<Line> lines = new ArrayList<>(parts.length);
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			lines.add(new Line(HudVariables.apply(part, row), element.color));
		}
		return lines;
	}

	private List<Line> playerLines(HudElement element, Map<String, String> global) {
		List<PlayerData> players = new ArrayList<>(mod.playerTracker().players());
		players.removeIf(player -> !matches(element.filter, player.name()));
		switch (element.sort) {
			case "name" -> players.sort(Comparator.comparing(PlayerData::name, String.CASE_INSENSITIVE_ORDER));
			case "health" -> players.sort(Comparator.comparingDouble(PlayerData::health).reversed());
			default -> players.sort(Comparator.comparingDouble(PlayerData::distance));
		}
		List<Line> lines = new ArrayList<>(players.size());
		int index = 0;
		for (PlayerData player : players) {
			Map<String, String> row = new HashMap<>(global);
			row.put("index", Integer.toString(++index));
			row.put("name", player.name());
			row.put("dist", player.distance() < 0 ? "-" : Math.round(player.distance()) + "m");
			row.put("dist_raw", Double.toString(player.distance()));
			row.put("hp", player.health() < 0 ? "?" : String.format(Locale.ROOT, "%.1f", player.health()));
			row.put("maxhp", String.format(Locale.ROOT, "%.1f", player.maxHealth()));
			row.put("absorption", String.format(Locale.ROOT, "%.0f", player.absorption()));
			row.put("armor", Integer.toString(player.armor()));
			row.put("flags", player.flags());
			row.put("equip", player.equipment());
			row.put("source", player.healthSource());
			row.put("conf", player.healthConfidence());
			row.put("visible", player.visible() ? "是" : "否");
			row.put("team", mod.teamManager().relation(player) == TeamManager.Relation.TEAMMATE ? "队友" : "其他");
			row.put("x", String.format(Locale.ROOT, "%.0f", player.x()));
			row.put("y", String.format(Locale.ROOT, "%.0f", player.y()));
			row.put("z", String.format(Locale.ROOT, "%.0f", player.z()));
			row.put("first", player.firstSeenMillis() > 0
					? TimeUtil.duration(System.currentTimeMillis() - player.firstSeenMillis()) : "-");
			row.put("lastseen", player.lastSeenMillis() > 0
					? TimeUtil.duration(System.currentTimeMillis() - player.lastSeenMillis()) + "前" : "-");
			int color = mod.targetLink().matches(player) ? mod.config().team.colorTarget : element.color;
			lines.add(new Line(HudVariables.apply(element.effectiveTemplate(), row), color));
		}
		return lines;
	}

	private List<Line> dropLines(HudElement element, Map<String, String> global) {
		List<DropData> drops = new ArrayList<>(mod.dropTracker().drops());
		drops.removeIf(drop -> !matches(element.filter, drop.itemName()));
		switch (element.sort) {
			case "name" -> drops.sort(Comparator.comparing(DropData::itemName, String.CASE_INSENSITIVE_ORDER));
			case "age" -> drops.sort(Comparator.comparingLong(DropData::ageSeconds));
			default -> drops.sort(Comparator.comparingDouble(DropData::distance));
		}
		List<Line> lines = new ArrayList<>(drops.size());
		int index = 0;
		for (DropData drop : drops) {
			Map<String, String> row = new HashMap<>(global);
			row.put("index", Integer.toString(++index));
			row.put("item", drop.itemName());
			row.put("count", Integer.toString(drop.count()));
			row.put("dist", Math.round(drop.distance()) + "m");
			row.put("dist_raw", Double.toString(drop.distance()));
			row.put("age", drop.ageSeconds() + "s");
			row.put("visible", drop.visible() ? "是" : "否");
			lines.add(new Line(HudVariables.apply(element.effectiveTemplate(), row), element.color));
		}
		return lines;
	}

	/** Discovered End Ships in the current dimension, nearest first. */
	private List<Line> shipLines(HudElement element, Map<String, String> global) {
		MinecraftClient client = mod.client();
		List<Line> lines = new ArrayList<>();
		if (client.player == null || client.world == null) {
			return lines;
		}
		String dimension = client.world.getRegistryKey().getValue().toString();
		List<ShipPoint> ships = mod.endShipFinder().shipsIn(dimension);
		ships.removeIf(ship -> !matches(element.filter, "末地船"));
		ships.sort(Comparator.comparingDouble(ship ->
				ship.distanceSquaredTo(client.player.getX(), client.player.getY(), client.player.getZ())));
		int index = 0;
		for (ShipPoint ship : ships) {
			if (index >= element.maxEntries) {
				break;
			}
			double dx = ship.x + 0.5 - client.player.getX();
			double dz = ship.z + 0.5 - client.player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			Map<String, String> row = new HashMap<>(global);
			row.put("index", Integer.toString(++index));
			row.put("name", "末地船");
			row.put("dist", Math.round(distance) + "m");
			row.put("dist_raw", Double.toString(distance));
			row.put("dir", bearing(dx, dz));
			row.put("looted", ship.looted ? "已搜" : "未搜");
			row.put("status", ship.looted ? "已搜" : "未搜");
			row.put("x", Integer.toString(ship.x));
			row.put("y", Integer.toString(ship.y));
			row.put("z", Integer.toString(ship.z));
			lines.add(new Line(HudVariables.apply(element.effectiveTemplate(), row), element.color));
		}
		return lines;
	}

	/** Compass bearing from a delta, so the HUD line tells you where to look. */
	private static String bearing(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dz, dx));
		String[] names = { "东", "东南", "南", "西南", "西", "西北", "北", "东北" };
		int index = (int) Math.round(((degrees + 360.0) % 360.0) / 45.0) % 8;
		return names[index];
	}

	private List<Line> recordLines(HudElement element, MinecraftClient client, Map<String, String> global) {
		List<RecordEntry> records = new ArrayList<>(mod.recordManager().records());
		records.removeIf(record -> !matches(element.filter, record.message, record.type.name()));
		List<Line> lines = new ArrayList<>();
		for (int index = records.size() - 1; index >= 0; index--) {
			RecordEntry record = records.get(index);
			Map<String, String> row = new HashMap<>(global);
			row.put("type", record.type.name());
			row.put("message", record.message == null ? "" : record.message);
			row.put("x", String.format(Locale.ROOT, "%.0f", record.x));
			row.put("y", String.format(Locale.ROOT, "%.0f", record.y));
			row.put("z", String.format(Locale.ROOT, "%.0f", record.z));
			row.put("time", TimeUtil.clock(record.timeMillis));
			double distance = -1.0;
			if (client.player != null && client.world != null
					&& record.world != null
					&& record.world.equals(client.world.getRegistryKey().getValue().toString())) {
				distance = Math.sqrt(client.player.squaredDistanceTo(record.x, record.y, record.z));
			}
			row.put("dist", distance < 0 ? "其他维度" : Math.round(distance) + "m");
			lines.add(new Line(HudVariables.apply(element.effectiveTemplate(), row), element.color));
		}
		return lines;
	}

	private List<Line> moduleLines(HudElement element, Map<String, String> global) {
		List<Line> lines = new ArrayList<>();
		for (var module : mod.moduleManager().sortedModules()) {
			if (!module.isEnabled()) {
				continue;
			}
			if (!matches(element.filter, module.id(), module.displayName())) {
				continue;
			}
			Map<String, String> row = new HashMap<>(global);
			row.put("id", module.id());
			row.put("name", module.displayName());
			row.put("status", module.status() == null ? "" : module.status());
			lines.add(new Line(HudVariables.apply(element.effectiveTemplate(), row), element.color));
		}
		return lines;
	}

	private List<Line> noticeLines(HudElement element, Map<String, String> global) {
		List<NotificationManager.Notice> notices = mod.notifications().recent(mod.config().notificationSeconds);
		List<Line> lines = new ArrayList<>();
		int from = Math.max(0, notices.size() - Math.max(1, element.maxEntries));
		for (int index = notices.size() - 1; index >= from; index--) {
			NotificationManager.Notice notice = notices.get(index);
			if (!matches(element.filter, notice.message())) {
				continue;
			}
			Map<String, String> row = new HashMap<>(global);
			row.put("message", notice.message());
			row.put("error", notice.error() ? "是" : "否");
			lines.add(new Line(HudVariables.apply(element.effectiveTemplate(), row),
					notice.error() ? 0xFFFF7777 : element.color));
		}
		return lines;
	}

	private List<Line> targetLines(HudElement element, MinecraftClient client, Map<String, String> global) {
		PlayerData target = mod.targetLink().resolve(mod.playerTracker().players());
		Map<String, String> row = new HashMap<>(global);
		if (target != null) {
			Vec3d delta = new Vec3d(target.x() - client.player.getX(), target.y() - client.player.getY(),
					target.z() - client.player.getZ());
			String arrow = DirectionUtil.relativeArrow(delta, client.player.getYaw());
			row.put("target", target.name());
			row.put("dist", Math.round(target.distance()) + "m");
			row.put("arrow", arrow);
			row.put("hp", target.health() < 0 ? "?" : String.format(Locale.ROOT, "%.1f", target.health()));
			row.put("x", String.format(Locale.ROOT, "%.0f", target.x()));
			row.put("y", String.format(Locale.ROOT, "%.0f", target.y()));
			row.put("z", String.format(Locale.ROOT, "%.0f", target.z()));
		} else {
			row.put("target", mod.targetLink().isSet() ? mod.targetLink().name() : "-");
			row.put("dist", "未加载");
			row.put("arrow", "-");
			row.put("hp", "?");
		}
		row.put("visible", target != null && target.visible() ? "是" : "否");
		List<Line> lines = new ArrayList<>();
		for (String part : HudVariables.splitLines(element.effectiveTemplate())) {
			if (!part.isEmpty()) {
				lines.add(new Line(HudVariables.apply(part, row), element.color));
			}
		}
		return lines;
	}

	private static boolean matches(String filter, String... fields) {
		if (filter == null || filter.isBlank()) {
			return true;
		}
		String needle = filter.toLowerCase(Locale.ROOT);
		for (String field : fields) {
			if (field != null && field.toLowerCase(Locale.ROOT).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------- decoration

	public static int multiplyAlpha(int color, float factor) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * factor);
		alpha = Math.max(0, Math.min(255, alpha));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------ error + ESP

	private void drawEspLabels(DrawContext context, MinecraftClient client) {
		Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getCamera();
		if (camera == null || !camera.isReady()) {
			return;
		}
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float fov = client.options.getFov().getValue();
		Vec3d cameraPos = camera.getCameraPos();
		double rangeSquared = mod.config().esp.range * mod.config().esp.range;
		int drawn = 0;
		int max = mod.config().esp.maxLabels;
		for (EntityData entity : mod.entityCache().entitiesView()) {
			if (drawn >= max) {
				break;
			}
			if (entity.category() == com.chaoshelper.api.EntityCategory.ITEM && !mod.config().esp.showItems) {
				continue;
			}
			double distanceSquared = cameraPos.squaredDistanceTo(entity.x(), entity.y(), entity.z());
			if (distanceSquared > rangeSquared) {
				continue;
			}
			ScreenProjector.Point point = ScreenProjector.project(
					new Vec3d(entity.x(), entity.maxY() + 0.3, entity.z()), camera, fov, width, height);
			if (point == null) {
				continue;
			}
			String label = entity.displayName();
			if (entity.living() && entity.health() >= 0.0F) {
				label += String.format(Locale.ROOT, " %.1f", entity.health());
			}
			label += String.format(Locale.ROOT, " %.0fm", Math.sqrt(distanceSquared));
			int x = (int) Math.max(1, Math.min(point.x(), width - client.textRenderer.getWidth(label) - 1));
			int y = (int) Math.max(1, point.y());
			context.fill(x - 1, y - 1, x + client.textRenderer.getWidth(label) + 1, y + 9, 0x66000000);
			context.drawTextWithShadow(client.textRenderer, label, x, y,
					com.chaoshelper.api.EntityCategory.ITEM == entity.category()
							? mod.config().esp.colorItem
							: 0xFFFFFFFF);
			drawn++;
		}
	}

	private void drawErrors(DrawContext context, MinecraftClient client) {
		List<ErrorLog.Entry> entries = mod.errorLog().recent(mod.config().errorDisplaySeconds);
		if (entries.isEmpty()) {
			return;
		}
		int max = Math.min(4, entries.size());
		int width = client.textRenderer.getWidth("错误 (" + entries.size() + ")");
		for (int index = 0; index < max; index++) {
			String message = entries.get(entries.size() - max + index).message();
			if (message.length() > 90) {
				message = message.substring(0, 90) + "...";
			}
			width = Math.max(width, client.textRenderer.getWidth(message));
		}
		width += 8;
		int x = Math.max(0, context.getScaledWindowWidth() - width - 8);
		int y = 8;
		int height = 14 + max * 10;
		context.fill(x, y, x + width, y + height, 0xCC330000);
		context.fill(x, y, x + 2, y + height, 0xFFFF5555);
		context.drawTextWithShadow(client.textRenderer, "错误 (" + entries.size() + ")", x + 6, y + 4, 0xFFFF7777);
		for (int index = 0; index < max; index++) {
			String message = entries.get(entries.size() - max + index).message();
			if (message.length() > 90) {
				message = message.substring(0, 90) + "...";
			}
			context.drawTextWithShadow(client.textRenderer, message, x + 6, y + 15 + index * 10, 0xFFFFDDDD);
		}
	}

	private record Line(String text, int color) {
	}
}
