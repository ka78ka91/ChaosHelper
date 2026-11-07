package com.chaoshelper.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Single source of truth for every persisted key: dotted path, type, default and
 * a short Chinese description. Commands and the README both read this list, so a
 * new option cannot silently drift out of the documentation.
 */
public final class ConfigKeys {
	public record Entry(String path, String type, String defaultValue, String description,
			Supplier<Object> getter) {
	}

	private ConfigKeys() {
	}

	public static List<Entry> entries(ChaosConfig config) {
		List<Entry> list = new ArrayList<>();
		list.add(new Entry("configVersion", "int", "3", "配置结构版本，用于将来迁移", () -> config.configVersion));
		list.add(new Entry("cacheIntervalTicks", "int", "5", "实体缓存刷新间隔（tick）", () -> config.cacheIntervalTicks));
		list.add(new Entry("errorDisplaySeconds", "int", "120", "HUD 错误提示保留秒数", () -> config.errorDisplaySeconds));
		list.add(new Entry("notificationSeconds", "int", "45", "普通提示保留秒数", () -> config.notificationSeconds));

		list.add(new Entry("hud.maxEntries", "int", "10", "每个列表最多显示行数，超出折叠为 +N 更多", () -> config.hud.maxEntries));
		list.add(new Entry("hud.margin", "int", "4", "HUD 距屏幕边缘像素", () -> config.hud.margin));
		list.add(new Entry("hud.background", "boolean", "true", "HUD 面板半透明背景", () -> config.hud.background));
		list.add(new Entry("hud.showStatus", "boolean", "true", "右上模块状态面板", () -> config.hud.showStatus));
		list.add(new Entry("hud.showRecords", "boolean", "true", "右上记录点面板", () -> config.hud.showRecords));
		list.add(new Entry("hud.showSelf", "boolean", "true", "右下自身血量/装备面板", () -> config.hud.showSelf));

		list.add(new Entry("esp.range", "double", "32", "ESP 距离过滤（格）", () -> config.esp.range));
		list.add(new Entry("esp.throughWalls", "boolean", "false", "ESP 是否穿墙", () -> config.esp.throughWalls));
		list.add(new Entry("esp.showLabels", "boolean", "true", "ESP 名称/距离标签", () -> config.esp.showLabels));
		list.add(new Entry("esp.maxEntities", "int", "128", "ESP 单帧最多绘制实体", () -> config.esp.maxEntities));
		list.add(new Entry("esp.lineWidth", "float", "1.5", "ESP 线宽", () -> config.esp.lineWidth));

		list.add(new Entry("chams.range", "double", "32", "Chams 距离过滤（格）", () -> config.chams.range));
		list.add(new Entry("chams.throughWalls", "boolean", "false", "Chams 描边是否穿墙", () -> config.chams.throughWalls));
		list.add(new Entry("chams.opacity", "int", "190", "Chams 透明度 0-255", () -> config.chams.opacity));

		list.add(new Entry("playerTracker.range", "double", "64", "玩家追踪距离上限（格）", () -> config.playerTracker.range));
		list.add(new Entry("playerTracker.refreshTicks", "int", "10", "玩家追踪数据刷新间隔（tick）", () -> config.playerTracker.refreshTicks));
		list.add(new Entry("playerTracker.staleSeconds", "int", "15", "离开视野多久后从列表移除", () -> config.playerTracker.staleSeconds));
		list.add(new Entry("playerTracker.estimateResetSeconds", "int", "30", "离开超过该秒数后血量重置为满血估算", () -> config.playerTracker.estimateResetSeconds));
		list.add(new Entry("playerTracker.includeTabListOnly", "boolean", "true", "是否显示仅在 TAB 列表中的玩家", () -> config.playerTracker.includeTabListOnly));
		list.add(new Entry("playerTracker.maxEntries", "int", "100", "追踪列表硬上限（HUD 折叠由 hud.maxEntries 控制）", () -> config.playerTracker.maxEntries));

		list.add(new Entry("dropTracker.range", "double", "32", "掉落物记录距离（格）", () -> config.dropTracker.range));
		list.add(new Entry("dropTracker.recordSeconds", "int", "60", "掉落物消失后保留秒数", () -> config.dropTracker.recordSeconds));
		list.add(new Entry("dropTracker.maxEntries", "int", "100", "掉落物列表硬上限（HUD 折叠由 hud.maxEntries 控制）", () -> config.dropTracker.maxEntries));

		list.add(new Entry("record.maxEntries", "int", "50", "本地记录点最大条数", () -> config.record.maxEntries));
		list.add(new Entry("record.autoDeath", "boolean", "true", "自动记录死亡点", () -> config.record.autoDeath));
		list.add(new Entry("record.autoLog", "boolean", "true", "把聊天/系统消息记为本地记录", () -> config.record.autoLog));
		list.add(new Entry("record.autoLogoff", "boolean", "true", "玩家离开视野时记录最后可见坐标", () -> config.record.autoLogoff));

		list.add(new Entry("team.colorTeammate", "int(ARGB)", "0xFF55FF55", "队友在 HUD/ESP 中的配色", () -> config.team.colorTeammate));

		list.add(new Entry("autoEat.threshold", "int", "18", "饥饿值低于该值开始进食", () -> config.autoEat.threshold));
		list.add(new Entry("autoEat.cooldownTicks", "int", "40", "进食失败后的冷却（tick）", () -> config.autoEat.cooldownTicks));
		list.add(new Entry("autoEat.restoreSlot", "boolean", "true", "进食后恢复原快捷栏槽位", () -> config.autoEat.restoreSlot));

		list.add(new Entry("travel.mode", "string", "auto", "旅行模式 auto|walk|elytra", () -> config.travel.mode));
		list.add(new Entry("travel.glide", "boolean", "false", "滑翔模式：只调视角，不用烟花", () -> config.travel.glide));
		list.add(new Entry("travel.cancelOnInput", "boolean", "false", "旧行为：玩家移动/攻击输入时取消旅行（默认关闭）", () -> config.travel.cancelOnInput));
		list.add(new Entry("travel.cancelOnScreen", "boolean", "false", "旧行为：打开界面时取消旅行（默认关闭）", () -> config.travel.cancelOnScreen));
		list.add(new Entry("travel.maxDistance", "double", "0", "旅行目标最大距离（格），0 = 不限", () -> config.travel.maxDistance));
		list.add(new Entry("travel.gotoTeammatesOnly", "boolean", "false", "goto player 只接受本地队友", () -> config.travel.gotoTeammatesOnly));
		list.add(new Entry("travel.minY", "double", "64", "鞘翅起飞安全高度下限", () -> config.travel.minY));
		list.add(new Entry("travel.arriveRadius", "int", "3", "判定到达的距离", () -> config.travel.arriveRadius));

		list.add(new Entry("autotake.range", "double", "3", "自动取鞘翅的展示框搜索范围（格）", () -> config.autotake.range));
		list.add(new Entry("autotake.onlyWhenMissing", "boolean", "false", "已穿鞘翅时跳过取鞘翅（默认否，方便收集备用）", () -> config.autotake.onlyWhenMissing));
		list.add(new Entry("autotake.cooldownTicks", "int", "20", "取鞘翅后的冷却（tick）", () -> config.autotake.cooldownTicks));

		list.add(new Entry("endship.notify", "boolean", "true", "发现末地船时提示", () -> config.endship.notify));
		list.add(new Entry("endship.markXaero", "boolean", "true", "发现末地船时写入 Xaero 路点", () -> config.endship.markXaero));
		list.add(new Entry("endship.onlyEnd", "boolean", "true", "只在末地扫描（避免误判玩家放置的龙首）", () -> config.endship.onlyEnd));
		list.add(new Entry("endship.scanRadiusChunks", "int", "8", "末地船扫描半径（区块）", () -> config.endship.scanRadiusChunks));
		list.add(new Entry("endship.chunksPerScan", "int", "3", "每次扫描的区块数", () -> config.endship.chunksPerScan));
		list.add(new Entry("endship.intervalTicks", "int", "2", "扫描间隔（tick）", () -> config.endship.intervalTicks));
		list.add(new Entry("endship.dedupeRadius", "int", "48", "同一艘船的信号合并半径（格）", () -> config.endship.dedupeRadius));
		list.add(new Entry("endship.chainNext", "boolean", "true", "取走鞘翅后自动飞往下一艘未搜的末地船", () -> config.endship.chainNext));
		list.add(new Entry("endship.chainDelayTicks", "int", "60", "转场前等待（tick），留给拾取鞘翅", () -> config.endship.chainDelayTicks));
		list.add(new Entry("endship.chainRange", "int", "0", "转场距离上限（格），0 = 不限", () -> config.endship.chainRange));

		list.add(new Entry("autolog.healthThreshold", "double", "6", "血量（含伤害吸收）低于该值且无图腾时退服", () -> config.autolog.healthThreshold));
		list.add(new Entry("autolog.requireNoTotem", "boolean", "true", "只在背包没有不死图腾时退服", () -> config.autolog.requireNoTotem));
		list.add(new Entry("autolog.onlyMultiplayer", "boolean", "true", "只在多人服务器生效", () -> config.autolog.onlyMultiplayer));
		list.add(new Entry("autolog.checkIntervalTicks", "int", "5", "血量检查间隔（tick）", () -> config.autolog.checkIntervalTicks));

		// Elytra assists: both default to false, i.e. the player opts in explicitly.
		list.add(new Entry("travel.glideClimb", "boolean", "false", "无动力爬升：周期性俯仰循环，不用烟花也能保持高度（默认关闭）", () -> config.travel.glideClimb));
		list.add(new Entry("travel.glideClimbClearance", "int", "80", "爬升循环要求的最低离地净空（格），不足则退回普通滑翔", () -> config.travel.glideClimbClearance));
		list.add(new Entry("travel.glideClimbDiveBlocks", "int", "70", "每个爬升循环允许下潜的格数", () -> config.travel.glideClimbDiveBlocks));
		list.add(new Entry("travel.elytraSwap", "boolean", "false", "甲飞：耐久结算前切走胸甲再切回以重置计时（默认关闭）", () -> config.travel.elytraSwap));
		list.add(new Entry("travel.elytraSwapLeadTicks", "int", "2", "甲飞提前触发的 tick 数，用于抵消计时错位", () -> config.travel.elytraSwapLeadTicks));
		list.add(new Entry("travel.elytraSwapDurabilityLimit", "int", "0", "甲飞：0=总是启用，>0 表示仅当剩余耐久低于该值时启用", () -> config.travel.elytraSwapDurabilityLimit));
		list.add(new Entry("travel.elytraSwapMaxSwaps", "int", "0", "甲飞：单次运行最多切换次数，0=不限，超限自动放弃", () -> config.travel.elytraSwapMaxSwaps));
		list.add(new Entry("travel.elytraSwapClearance", "int", "24", "甲飞要求的最低离地净空（格）", () -> config.travel.elytraSwapClearance));
		list.add(new Entry("travel.elytraSwapDuringTravel", "boolean", "false", "自动飞行（旅行/Baritone）期间也执行甲飞；会让自动飞行中断", () -> config.travel.elytraSwapDuringTravel));

		list.add(new Entry("xaero.enabled", "boolean", "true", "启用 Xaero 路点集成", () -> config.xaero.enabled));
		list.add(new Entry("xaero.autoMarkEnd", "boolean", "true", "末地自动标记末地城/鞘翅", () -> config.xaero.autoMarkEnd));
		list.add(new Entry("xaero.setName", "string", "chaoshelper", "写入 Xaero 的路点集合名", () -> config.xaero.setName));
		list.add(new Entry("xaero.allowWrite", "boolean", "true", "允许写入 Xaero 路点文件", () -> config.xaero.allowWrite));

		list.add(new Entry("baritone.enabled", "boolean", "true", "是否允许调用 Baritone 桥接", () -> config.baritone.enabled));
		list.add(new Entry("dynamic.enabled", "boolean", "true", "是否扫描并加载动态模块", () -> config.dynamic.enabled));
		list.add(new Entry("dynamic.maxModules", "int", "16", "动态模块数量上限", () -> config.dynamic.maxModules));
		return list;
	}

	public static List<String> describe(ChaosConfig config) {
		List<String> lines = new ArrayList<>();
		for (Entry entry : entries(config)) {
			lines.add(String.format("%s = %s  // %s (默认 %s)", entry.path(),
					String.valueOf(entry.getter().get()), entry.description(), entry.defaultValue()));
		}
		return lines;
	}
}
