package com.chaoshelper.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One independently configurable HUD widget.
 *
 * <p>Every element has a type (what data it shows), a template (how each line is
 * formatted), a position/anchor and its own style. The renderer walks the list
 * in order, so "极其高度自定义" means: add, remove, reorder, drag and restyle
 * any element without touching code.
 *
 * <p>Plain mutable fields on purpose: Gson reads and writes them directly.
 */
public final class HudElement {
	/** Unique, stable id used by the editor and the drag map. */
	public String id = "";
	/** text | coords | self | target | players | drops | records | modules | notices | baritone | perf */
	public String type = "text";
	public boolean enabled = true;
	/** Panel heading; blank hides it (or falls back to the type label). */
	public String title = "";
	/** Per-line template. Supports {@code {variables}} and {@code \n}. Blank = type default. */
	public String template = "";
	/** Absolute top-left, or negative to use the anchor stacking. */
	public int x = -1;
	public int y = -1;
	/** tl | tr | bl | br — where auto-placed elements stack from. */
	public String anchor = "tl";
	/** left | center | right — text alignment inside the panel. */
	public String align = "left";
	public int color = 0xFFE0E0E0;
	public int accent = 0xFF55FFFF;
	/** Panel fill; 0 disables the background. */
	public int background = 0x88000000;
	public boolean border = true;
	public boolean shadow = true;
	public float scale = 1.0F;
	public int lineHeight = 10;
	public int maxEntries = 10;
	/** distance | name | health | age */
	public String sort = "distance";
	/** Case-insensitive substring filter applied to list rows. */
	public String filter = "";
	public boolean showTitle = true;
	/** Hide the whole widget while its list/template has no rows. */
	public boolean hideEmpty = true;
	public int padding = 4;
	/** Gap to the next element sharing this anchor. */
	public int gap = 2;

	public HudElement() {
	}

	public HudElement(String id, String type) {
		this.id = id;
		this.type = type;
	}

	public void normalize() {
		if (id == null || id.isBlank()) {
			id = "el-" + UUID.randomUUID().toString().substring(0, 8);
		}
		if (type == null || !type.matches("text|coords|self|target|players|drops|records|modules|notices|baritone|perf|ships")) {
			type = "text";
		}
		if (anchor == null || !anchor.matches("tl|tr|bl|br")) {
			anchor = "tl";
		}
		if (align == null || !align.matches("left|center|right")) {
			align = "left";
		}
		if (sort == null || !sort.matches("distance|name|health|age")) {
			sort = "distance";
		}
		if (title == null) {
			title = "";
		}
		if (template == null) {
			template = "";
		}
		if (filter == null) {
			filter = "";
		}
		scale = clamp(scale, 0.5F, 2.0F);
		lineHeight = clamp(lineHeight, 6, 24);
		maxEntries = clamp(maxEntries, 1, 50);
		padding = clamp(padding, 0, 16);
		gap = clamp(gap, 0, 20);
		if (background != 0 && (background >>> 24) == 0) {
			background |= 0xFF000000;
		}
	}

	public HudElement copy(String newId) {
		HudElement copy = new HudElement(newId, type);
		copy.enabled = enabled;
		copy.title = title;
		copy.template = template;
		copy.x = x;
		copy.y = y;
		copy.anchor = anchor;
		copy.align = align;
		copy.color = color;
		copy.accent = accent;
		copy.background = background;
		copy.border = border;
		copy.shadow = shadow;
		copy.scale = scale;
		copy.lineHeight = lineHeight;
		copy.maxEntries = maxEntries;
		copy.sort = sort;
		copy.filter = filter;
		copy.showTitle = showTitle;
		copy.hideEmpty = hideEmpty;
		copy.padding = padding;
		copy.gap = gap;
		return copy;
	}

	public String typeLabel() {
		return switch (type) {
			case "text" -> "自定义文本";
			case "coords" -> "坐标";
			case "self" -> "自身状态";
			case "target" -> "目标";
			case "players" -> "玩家列表";
			case "drops" -> "掉落物";
			case "records" -> "记录点";
			case "modules" -> "模块状态";
			case "notices" -> "通知";
			case "baritone" -> "Baritone";
			case "perf" -> "性能";
			case "ships" -> "末地船";
			default -> type;
		};
	}

	/** Effective heading: explicit title, otherwise the type label. */
	public String heading() {
		return title == null || title.isBlank() ? typeLabel() : title;
	}

	public String defaultTemplate() {
		return switch (type) {
			case "coords" -> "XYZ {x} {y} {z}\n{dim}";
			case "self" -> "HP {hp}/{maxhp}  护甲 {armor}  饥饿 {food}\n主手 {mainhand}  副手 {offhand}\n速度 {speed} m/s";
			case "target" -> "{target} {dist}";
			case "players" -> "{name} {dist} {hp} {flags}";
			case "drops" -> "{item} x{count} {dist} {age}";
			case "records" -> "{type} {x} {y} {z} {time}";
			case "modules" -> "{name} {status}";
			case "notices" -> "{message}";
			case "baritone" -> "{baritone}";
			case "perf" -> "FPS {fps}  内存 {mem_used}/{mem_max} MB\n游戏时间 {time}  现实 {real_time}";
			case "ships" -> "{name} {dist} {dir} {looted}";
			default -> "XYZ {x} {y} {z}";
		};
	}

	/** Template with an explicit value, otherwise the type default. */
	public String effectiveTemplate() {
		return template == null || template.isBlank() ? defaultTemplate() : template;
	}

	/** Fresh default element set; migrates the old fixed panels once. */
	public static List<HudElement> defaults(ChaosConfig.HudConfig hud) {
		List<HudElement> list = new ArrayList<>();
		list.add(create("players", "players", "玩家", hud.showPlayers, "tl", hud.players,
				"玩家", 0xFF55FFFF, 10));
		list.add(create("coords", "coords", "坐标", hud.showCoordinates, "tr", hud.status,
				"坐标", 0xFF55FFFF, 3));
		list.add(create("modules", "modules", "模块", hud.showStatus, "tr", null,
				"模块", 0xFF9FD8FF, 8));
		list.add(create("drops", "drops", "掉落物", hud.showDrops, "bl", hud.drops,
				"掉落物", 0xFFFFD700, 10));
		list.add(create("records", "records", "记录点", hud.showRecords, "tr", hud.records,
				"记录点", 0xFF55FFFF, 3));
		list.add(create("target", "target", "目标", hud.showTarget, "br", hud.target,
				"目标", 0xFFFF5555, 2));
		list.add(create("self", "self", "自身", hud.showSelf, "br", hud.self,
				"自身", 0xFF55FFFF, 6));
		return list;
	}

	private static HudElement create(String id, String type, String title, boolean enabled, String anchor,
			ChaosConfig.PanelPos pos, String heading, int accent, int maxEntries) {
		HudElement element = new HudElement(id, type);
		element.enabled = enabled;
		element.title = heading;
		element.anchor = anchor;
		element.accent = accent;
		element.maxEntries = maxEntries;
		element.template = element.defaultTemplate();
		if (pos != null && !pos.auto()) {
			element.x = pos.x;
			element.y = pos.y;
		}
		return element;
	}

	public static String newId() {
		return "el-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
