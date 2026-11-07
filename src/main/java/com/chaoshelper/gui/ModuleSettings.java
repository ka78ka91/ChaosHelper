package com.chaoshelper.gui;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps every built-in module to the settings the ClickGUI shows. Keeping this
 * in one place means the GUI needs no reflection and each module stays free of
 * UI code.
 */
public final class ModuleSettings {
	private static final int[] PALETTE = {
			0xFFFFFFFF, 0xFF55FFFF, 0xFF55FF55, 0xFFFFFF55,
			0xFFFFAA55, 0xFFFF5555, 0xFFFF55FF, 0xFF55AAFF,
			0xFFAAAAAA, 0xFF888888, 0xFFFFD700
	};
	private static final String[] BEARINGS = {
			"北 (-Z)", "东北", "东 (+X)", "东南", "南 (+Z)", "西南", "西 (-X)", "西北"
	};

	private ModuleSettings() {
	}

	public static List<Setting> forModule(Module module, ChaosHelperClient mod) {
		List<Setting> list = new ArrayList<>();
		if (module == null) {
			return list;
		}
		ChaosConfig config = mod.config();
		switch (module.id()) {
			case "hud" -> hud(list, mod, config);
			case "esp" -> esp(list, config);
			case "chams" -> chams(list, config);
			case "player-tracker" -> playerTracker(list, config);
			case "drop-tracker" -> dropTracker(list, config);
			case "record" -> record(list, mod, config);
			case "travel" -> travel(list, mod, config);
			case "autoeat" -> autoEat(list, config);
			case "autotake" -> autoTake(list, config);
			case "endship" -> endShip(list, mod, config);
			case "autolog" -> autoLog(list, config);
			default -> list.add(Setting.button("来源", () -> "动态模块", () -> {
			}));
		}
		return list;
	}

	private static void hud(List<Setting> list, ChaosHelperClient mod, ChaosConfig config) {
		list.add(Setting.button("HUD 布局", () -> config.hud.elements.size() + " 个元素",
				() -> mod.client().setScreen(new HudEditorScreen(null))));
		list.add(Setting.integer("屏幕边距", 0, 32, 2, () -> config.hud.margin, value -> config.hud.margin = value));
		list.add(Setting.button("恢复默认布局", () -> "点击", config.hud::resetElements));
	}

	private static void esp(List<Setting> list, ChaosConfig config) {
		ChaosConfig.EspConfig esp = config.esp;
		list.add(Setting.decimal("距离", 4, 128, 4, () -> esp.range, value -> esp.range = value));
		list.add(Setting.bool("穿墙", () -> esp.throughWalls, value -> esp.throughWalls = value));
		list.add(Setting.bool("名称标签", () -> esp.showLabels, value -> esp.showLabels = value));
		list.add(Setting.bool("显示掉落物", () -> esp.showItems, value -> esp.showItems = value));
		list.add(Setting.integer("最大实体", 16, 512, 16, () -> esp.maxEntities, value -> esp.maxEntities = value));
		list.add(Setting.integer("最大标签", 0, 128, 8, () -> esp.maxLabels, value -> esp.maxLabels = value));
		list.add(Setting.decimal("线宽", 0.5, 6.0, 0.5, () -> esp.lineWidth, value -> esp.lineWidth = (float) value));
		list.add(Setting.color("玩家颜色", () -> esp.colorPlayer, value -> esp.colorPlayer = value, PALETTE));
		list.add(Setting.color("敌对颜色", () -> esp.colorHostile, value -> esp.colorHostile = value, PALETTE));
		list.add(Setting.color("被动颜色", () -> esp.colorPassive, value -> esp.colorPassive = value, PALETTE));
		list.add(Setting.color("掉落颜色", () -> esp.colorItem, value -> esp.colorItem = value, PALETTE));
		list.add(Setting.color("队友颜色", () -> esp.colorTeammate, value -> esp.colorTeammate = value, PALETTE));
		list.add(Setting.color("目标颜色", () -> esp.colorTarget, value -> esp.colorTarget = value, PALETTE));
	}

	private static void chams(List<Setting> list, ChaosConfig config) {
		ChaosConfig.ChamsConfig chams = config.chams;
		list.add(Setting.decimal("距离", 4, 128, 4, () -> chams.range, value -> chams.range = value));
		list.add(Setting.bool("穿墙", () -> chams.throughWalls, value -> chams.throughWalls = value));
		list.add(Setting.integer("不透明度", 0, 255, 5, () -> chams.opacity, value -> chams.opacity = value));
		list.add(Setting.bool("玩家", () -> chams.applyPlayers, value -> chams.applyPlayers = value));
		list.add(Setting.bool("敌对", () -> chams.applyHostile, value -> chams.applyHostile = value));
		list.add(Setting.bool("被动", () -> chams.applyPassive, value -> chams.applyPassive = value));
		list.add(Setting.color("默认颜色", () -> chams.color, value -> chams.color = value, PALETTE));
		list.add(Setting.color("队友颜色", () -> chams.colorTeammate, value -> chams.colorTeammate = value, PALETTE));
		list.add(Setting.color("目标颜色", () -> chams.colorTarget, value -> chams.colorTarget = value, PALETTE));
	}

	private static void playerTracker(List<Setting> list, ChaosConfig config) {
		ChaosConfig.PlayerTrackerConfig tracker = config.playerTracker;
		list.add(Setting.decimal("距离", 8, 256, 8, () -> tracker.range, value -> tracker.range = value));
		list.add(Setting.integer("刷新间隔", 2, 200, 2, () -> tracker.refreshTicks, value -> tracker.refreshTicks = value));
		list.add(Setting.integer("过期秒数", 3, 600, 5, () -> tracker.staleSeconds, value -> tracker.staleSeconds = value));
		list.add(Setting.integer("估算重置", 5, 600, 5, () -> tracker.estimateResetSeconds,
				value -> tracker.estimateResetSeconds = value));
		list.add(Setting.decimal("血量误差 ±", 0, 20, 0.5, () -> tracker.healthErrorBase,
				value -> tracker.healthErrorBase = value));
		list.add(Setting.bool("仅 TAB 玩家", () -> tracker.includeTabListOnly,
				value -> tracker.includeTabListOnly = value));
		list.add(Setting.integer("硬上限", 1, 100, 1, () -> tracker.maxEntries, value -> tracker.maxEntries = value));
	}

	private static void dropTracker(List<Setting> list, ChaosConfig config) {
		ChaosConfig.DropTrackerConfig drops = config.dropTracker;
		list.add(Setting.decimal("距离", 4, 128, 4, () -> drops.range, value -> drops.range = value));
		list.add(Setting.integer("保留秒数", 5, 3600, 5, () -> drops.recordSeconds, value -> drops.recordSeconds = value));
		list.add(Setting.integer("硬上限", 1, 100, 1, () -> drops.maxEntries, value -> drops.maxEntries = value));
		list.add(Setting.integer("刷新间隔", 1, 100, 1, () -> drops.refreshTicks, value -> drops.refreshTicks = value));
	}

	private static void record(List<Setting> list, ChaosHelperClient mod, ChaosConfig config) {
		ChaosConfig.RecordConfig record = config.record;
		list.add(Setting.integer("上限", 5, 500, 5, () -> record.maxEntries, value -> record.maxEntries = value));
		list.add(Setting.bool("记录死亡点", () -> record.autoDeath, value -> record.autoDeath = value));
		list.add(Setting.bool("记录聊天", () -> record.autoLog, value -> record.autoLog = value));
		list.add(Setting.bool("记录离开点", () -> record.autoLogoff, value -> record.autoLogoff = value));
		list.add(Setting.button("清空记录", () -> mod.recordManager().records().size() + " 条",
				() -> mod.recordManager().clear()));
	}

	private static void travel(List<Setting> list, ChaosHelperClient mod, ChaosConfig config) {
		ChaosConfig.TravelConfig travel = config.travel;
		String[] modes = {"自动", "步行", "鞘翅直飞"};
		list.add(Setting.option("旅行模式", modes,
				() -> switch (travel.mode) {
					case "walk" -> 1;
					case "elytra" -> 2;
					default -> 0;
				},
				index -> travel.mode = switch (index) {
					case 1 -> "walk";
					case 2 -> "elytra";
					default -> "auto";
				}));
		list.add(Setting.bool("滑翔模式", () -> travel.glide, value -> travel.glide = value));
		list.add(Setting.bool("输入取消", () -> travel.cancelOnInput, value -> travel.cancelOnInput = value));
		list.add(Setting.bool("界面取消", () -> travel.cancelOnScreen, value -> travel.cancelOnScreen = value));
		list.add(Setting.decimal("最大距离(0=不限)", 0, 10000, 100, () -> travel.maxDistance,
				value -> travel.maxDistance = value));
		list.add(Setting.integer("起飞高度", -64, 320, 8, () -> (int) travel.minY,
				value -> travel.minY = value));
		list.add(Setting.integer("到达半径", 1, 16, 1, () -> travel.arriveRadius, value -> travel.arriveRadius = value));
		list.add(Setting.button("鞘翅辅助设置", () -> travel.glideClimb || travel.elytraSwap
				? (travel.glideClimb ? "爬升+" : "") + (travel.elytraSwap ? "甲飞" : "")
				: "全部关闭", () -> mod.client().setScreen(new ElytraOptionsScreen(null))));
		list.add(Setting.bool("无动力爬升", () -> travel.glideClimb, value -> travel.glideClimb = value));
		list.add(Setting.bool("甲飞(切甲省耐久)", () -> travel.elytraSwap, value -> travel.elytraSwap = value));
		list.add(Setting.bool("自动飞行时也切甲", () -> travel.elytraSwapDuringTravel,
				value -> travel.elytraSwapDuringTravel = value));
	}

	private static void autoEat(List<Setting> list, ChaosConfig config) {
		ChaosConfig.AutoEatConfig eat = config.autoEat;
		list.add(Setting.integer("饥饿阈值", 1, 20, 1, () -> eat.threshold, value -> eat.threshold = value));
		list.add(Setting.integer("失败冷却", 10, 200, 5, () -> eat.cooldownTicks, value -> eat.cooldownTicks = value));
		list.add(Setting.bool("恢复槽位", () -> eat.restoreSlot, value -> eat.restoreSlot = value));
		list.add(Setting.bool("优先高营养", () -> eat.preferHighestNutrition,
				value -> eat.preferHighestNutrition = value));
	}

	private static void autoTake(List<Setting> list, ChaosConfig config) {
		ChaosConfig.AutoTakeConfig take = config.autotake;
		list.add(Setting.decimal("搜索范围", 1, 16, 1, () -> take.range, value -> take.range = value));
		list.add(Setting.bool("已有鞘翅时跳过", () -> take.onlyWhenMissing, value -> take.onlyWhenMissing = value));
		list.add(Setting.integer("取下后冷却", 5, 200, 5, () -> take.cooldownTicks, value -> take.cooldownTicks = value));
	}

	private static void endShip(List<Setting> list, ChaosHelperClient mod, ChaosConfig config) {
		ChaosConfig.EndShipConfig ship = config.endship;
		list.add(Setting.bool("自动搜船(飞→取→下一艘)", () -> ship.autoLoot, value -> ship.autoLoot = value));
		list.add(Setting.bool("发现时提示", () -> ship.notify, value -> ship.notify = value));
		list.add(Setting.bool("写入 Xaero 路点", () -> ship.markXaero, value -> ship.markXaero = value));
		list.add(Setting.bool("只在末地", () -> ship.onlyEnd, value -> ship.onlyEnd = value));
		list.add(Setting.integer("扫描半径(区块)", 1, 16, 1, () -> ship.scanRadiusChunks,
				value -> ship.scanRadiusChunks = value));
		list.add(Setting.integer("每刻扫描区块", 1, 16, 1, () -> ship.chunksPerScan, value -> ship.chunksPerScan = value));
		list.add(Setting.integer("信号合并半径", 16, 256, 8, () -> ship.dedupeRadius, value -> ship.dedupeRadius = value));
		list.add(Setting.integer("停飞距离", 8, 128, 8, () -> ship.approachRadius, value -> ship.approachRadius = value));
		list.add(Setting.decimal("取鞘翅距离", 1, 6, 0.5, () -> ship.takeRange, value -> ship.takeRange = value));
		list.add(Setting.integer("接近超时(tick)", 40, 2000, 20, () -> ship.approachTimeoutTicks,
				value -> ship.approachTimeoutTicks = value));
		list.add(Setting.bool("取完鞘翅飞下一艘", () -> ship.chainNext, value -> ship.chainNext = value));
		list.add(Setting.integer("转场等待(tick)", 0, 400, 10, () -> ship.chainDelayTicks,
				value -> ship.chainDelayTicks = value));
		list.add(Setting.integer("转场距离上限", 0, 30000, 1000, () -> ship.chainRange, value -> ship.chainRange = value));
		list.add(Setting.bool("巡航(朝一个方向飞)", () -> ship.sweep, value -> ship.sweep = value));
		list.add(Setting.option("巡航方位", BEARINGS,
				() -> (int) Math.round(ship.sweepBearing / 45.0) % 8,
				value -> ship.sweepBearing = value * 45.0));
		list.add(Setting.integer("每段距离", 64, 1024, 64, () -> ship.sweepStep, value -> ship.sweepStep = value));
		list.add(Setting.integer("走廊半宽", 16, 512, 16, () -> ship.sweepCorridor,
				value -> ship.sweepCorridor = value));
		list.add(Setting.integer("允许回头", 0, 256, 8, () -> ship.sweepBacktrack,
				value -> ship.sweepBacktrack = value));
		list.add(Setting.button("已发现", () -> mod.endShipFinder().status(), () -> {
			// Read-only summary; clearing lives in /chaoshelper ship clear.
		}));
	}

	private static void autoLog(List<Setting> list, ChaosConfig config) {
		ChaosConfig.AutoLogConfig log = config.autolog;
		list.add(Setting.decimal("血量阈值", 1, 20, 1, () -> log.healthThreshold, value -> log.healthThreshold = value));
		list.add(Setting.bool("要求无图腾", () -> log.requireNoTotem, value -> log.requireNoTotem = value));
		list.add(Setting.bool("仅多人模式", () -> log.onlyMultiplayer, value -> log.onlyMultiplayer = value));
		list.add(Setting.integer("检查间隔", 1, 40, 1, () -> log.checkIntervalTicks,
				value -> log.checkIntervalTicks = value));
	}

	/** ClickGUI category for a module id. */
	public static String categoryOf(String id) {
		return switch (id) {
			case "hud", "esp", "chams" -> "render";
			case "player-tracker", "drop-tracker", "record", "endship" -> "info";
			case "travel" -> "movement";
			case "autoeat", "autotake", "autolog" -> "player";
			default -> "misc";
		};
	}
}
