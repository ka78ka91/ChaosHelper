package com.chaoshelper.command;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.config.ConfigKeys;
import com.chaoshelper.data.RecordEntry;
import com.chaoshelper.data.ShipPoint;
import com.chaoshelper.data.TeamEntry;
import com.chaoshelper.dynamic.DynamicModuleLoader;
import com.chaoshelper.gui.ClickGuiScreen;
import com.chaoshelper.minimap.XaeroWaypoints;
import com.chaoshelper.module.Module;
import com.chaoshelper.module.modules.TravelModule;
import com.chaoshelper.target.TargetResolver;
import com.chaoshelper.util.TimeUtil;
import com.chaoshelper.world.SeedEvidence;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-side command tree. Everything here is local: no packet is sent to the
 * server, so the commands work on any server and cannot act remotely.
 */
public final class ChaosCommand {
	private ChaosCommand() {
	}

	public static void register(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = literal("chaoshelper")
				.executes(context -> help(context.getSource()));

		root.then(literal("list").executes(context -> list(context.getSource())));
		root.then(literal("status").executes(context -> status(context.getSource())));
		root.then(literal("panic").executes(context -> panic(context.getSource())));
		root.then(literal("reloadmodules").executes(context -> reload(context.getSource())));
		root.then(literal("config").executes(context -> config(context.getSource())));
		root.then(literal("menu").executes(context -> {
			context.getSource().getClient().setScreen(new ClickGuiScreen(null));
			return 1;
		}));

		root.then(literal("on")
				.then(argument("module", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(moduleIds(), builder))
						.executes(context -> setModule(context, true))));
		root.then(literal("off")
				.then(argument("module", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(moduleIds(), builder))
						.executes(context -> setModule(context, false))));
		root.then(literal("toggle")
				.then(argument("module", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(moduleIds(), builder))
						.executes(context -> toggleModule(context))));

		for (Module module : ChaosHelperClient.get().moduleManager().sortedModules()) {
			root.then(literal(module.id())
					.then(literal("on").executes(context -> setModuleById(module.id(), true, context.getSource())))
					.then(literal("off").executes(context -> setModuleById(module.id(), false, context.getSource())))
					.then(literal("toggle").executes(context -> toggleModuleById(module.id(), context.getSource()))));
		}

		root.then(literal("team")
				.then(literal("add")
						.then(argument("player", StringArgumentType.word())
								.executes(context -> teamAdd(context, ""))
								.then(argument("note", StringArgumentType.greedyString())
										.executes(context -> teamAdd(context,
												StringArgumentType.getString(context, "note"))))))
				.then(literal("remove")
						.then(argument("player", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(teamNames(), builder))
								.executes(context -> teamRemove(context))))
				.then(literal("list").executes(context -> teamList(context.getSource())))
				.then(literal("clear").executes(context -> teamClear(context.getSource()))));

		root.then(literal("record")
				.then(literal("list").executes(context -> recordList(context.getSource())))
				.then(literal("clear").executes(context -> recordClear(context.getSource()))));

		root.then(literal("target")
				.then(literal("set")
						.then(argument("player", StringArgumentType.word())
								.executes(context -> targetSet(context))))
				.then(literal("clear").executes(context -> {
					ChaosHelperClient.get().targetLink().clear();
					feedback(context.getSource(), "已清除目标");
					return 1;
				})));

		root.then(literal("travel")
				.then(literal("stop").executes(context -> {
					ChaosHelperClient.get().travel().stop(true);
					feedback(context.getSource(), "旅行已停止");
					return 1;
				}))
				.then(literal("mode")
						.then(argument("mode", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										List.of("auto", "walk", "elytra", "glide"), builder))
								.executes(context -> setTravelMode(context))))
				.then(gotoTree(null)));

		root.then(literal("ship")
				.executes(context -> shipList(context.getSource()))
				.then(literal("list").executes(context -> shipList(context.getSource())))
				.then(literal("clear").executes(context -> shipClear(context.getSource())))
				.then(literal("goto").executes(context -> gotoNearestShip(context, null))));

		// Backwards-compatible aliases for the pre-merge command names.
		root.then(literal("autopath")
				.then(literal("stop").executes(context -> {
					ChaosHelperClient.get().travel().stop(true);
					feedback(context.getSource(), "旅行已停止");
					return 1;
				}))
				.then(gotoTree(TravelModule.Mode.WALK)));
		root.then(literal("elytrafly")
				.then(literal("stop").executes(context -> {
					ChaosHelperClient.get().travel().stop(true);
					feedback(context.getSource(), "旅行已停止");
					return 1;
				}))
				.then(gotoTree(TravelModule.Mode.ELYTRA)));

		root.then(literal("elytra")
				.executes(context -> elytraStatus(context.getSource()))
				.then(literal("status").executes(context -> elytraStatus(context.getSource())))
				.then(elytraSwitch("climb", true))
				.then(elytraSwitch("swap", false)));

		root.then(literal("xaero")
				.then(literal("status").executes(context -> {
					feedback(context.getSource(), XaeroWaypoints.statusLine());
					return 1;
				}))
				.then(literal("list").executes(context -> waypointList(context.getSource())))
				.then(literal("mark")
						.then(argument("name", StringArgumentType.greedyString())
								.executes(context -> xaeroMark(context)))));

		root.then(literal("waypoint")
				.then(literal("list").executes(context -> waypointList(context.getSource())))
				.then(literal("add")
						.then(argument("name", StringArgumentType.greedyString())
								.executes(context -> xaeroMark(context)))));

		root.then(literal("seed")
				.executes(context -> {
					feedback(context.getSource(), SeedEvidence.describe());
					return 1;
				})
				.then(literal("export").executes(context -> {
					feedback(context.getSource(), SeedEvidence.export());
					return 1;
				})));

		dispatcher.register(root);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> elytraSwitch(String name, boolean climb) {
		return literal(name)
				.executes(context -> elytraStatus(context.getSource()))
				.then(literal("on").executes(context -> setElytraOption(context.getSource(), climb, true)))
				.then(literal("off").executes(context -> setElytraOption(context.getSource(), climb, false)))
				.then(literal("toggle").executes(context -> setElytraOption(context.getSource(), climb, null)));
	}

	private static int setElytraOption(FabricClientCommandSource source, boolean climb, Boolean value) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		ChaosConfig.TravelConfig travel = mod.config().travel;
		boolean current = climb ? travel.glideClimb : travel.elytraSwap;
		boolean next = value == null ? !current : value;
		if (climb) {
			travel.glideClimb = next;
		} else {
			travel.elytraSwap = next;
		}
		if (next && !mod.moduleManager().isEnabled("travel")) {
			mod.moduleManager().setEnabled("travel", true);
			feedback(source, "已自动开启 travel 模块（鞘翅辅助需要它）");
		}
		mod.travel().resetAssists();
		mod.saveConfigQuietly();
		feedback(source, (climb ? "无动力爬升" : "甲飞(切甲省耐久)") + " -> " + (next ? "开启" : "关闭"));
		feedback(source, "提示：鞘翅辅助在部分服务器可能违规，风险由使用者承担");
		return 1;
	}

	private static int elytraStatus(FabricClientCommandSource source) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		ChaosConfig.TravelConfig travel = mod.config().travel;
		feedback(source, "鞘翅辅助（默认全部关闭，由玩家自行选择）:");
		feedback(source, "  无动力爬升: " + onOff(travel.glideClimb) + " | 净空 "
				+ travel.glideClimbClearance + " 格 / 下潜 " + travel.glideClimbDiveBlocks + " 格");
		feedback(source, "  甲飞(切甲省耐久): " + onOff(travel.elytraSwap) + " | 提前 "
				+ travel.elytraSwapLeadTicks + " tick / 耐久上限 "
				+ (travel.elytraSwapDurabilityLimit == 0 ? "不限" : travel.elytraSwapDurabilityLimit)
				+ " / 次数 " + (travel.elytraSwapMaxSwaps == 0 ? "不限" : travel.elytraSwapMaxSwaps)
				+ " / 净空 " + travel.elytraSwapClearance + " 格");
		feedback(source, "  " + mod.travel().assistStatus());
		feedback(source, "  阈值调整: /chaoshelper menu -> 鞘翅辅助");
		return 1;
	}

	private static String onOff(boolean value) {
		return value ? "开启" : "关闭";
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> gotoTree(TravelModule.Mode forced) {
		return literal("goto")
				.then(literal("item")
						.then(argument("uuid", StringArgumentType.word())
								.executes(context -> gotoItem(context, forced))))
				.then(literal("player")
						.then(argument("player", StringArgumentType.word())
								.executes(context -> gotoPlayer(context, forced))))
				.then(literal("death").executes(context -> startPath(TargetResolver.death(),
						context.getSource(), forced, null)))
				.then(literal("log")
						.then(argument("player", StringArgumentType.word())
								.executes(context -> gotoLog(context, forced))))
				.then(literal("waypoint")
						.then(argument("name", StringArgumentType.greedyString())
								.executes(context -> gotoWaypoint(context, forced))))
				.then(literal("elytra").executes(context -> gotoElytra(context, forced)))
				.then(literal("ship").executes(context -> gotoNearestShip(context, forced)))
				.then(argument("x", IntegerArgumentType.integer())
						.then(argument("y", IntegerArgumentType.integer())
								.then(argument("z", IntegerArgumentType.integer())
										.executes(context -> travelCoords(context, forced, null))
										.then(argument("mode", StringArgumentType.word())
												.suggests((context, builder) -> CommandSource.suggestMatching(
														List.of("auto", "walk", "elytra", "glide"), builder))
												.executes(context -> travelCoords(context, forced,
														StringArgumentType.getString(context, "mode")))))));
	}

	private static List<String> moduleIds() {
		return ChaosHelperClient.get().moduleManager().sortedModules().stream().map(Module::id).toList();
	}

	private static List<String> teamNames() {
		return ChaosHelperClient.get().teamManager().entries().stream().map(entry -> entry.name).toList();
	}

	private static int help(FabricClientCommandSource source) {
		feedback(source, "/chaoshelper menu | list | status | panic | reloadmodules | config");
		feedback(source, "/chaoshelper <module> on|off|toggle");
		feedback(source, "/chaoshelper travel goto <x> <y> <z> [auto|walk|elytra|glide]");
		feedback(source, "/chaoshelper travel goto item|player|death|log|waypoint ...");
		feedback(source, "/chaoshelper travel goto elytra  走向最近的鞘翅展示框");
		feedback(source, "/chaoshelper travel goto ship    飞向最近的未搜末地船");
		feedback(source, "/chaoshelper ship list|goto|clear  末地船列表 / 前往 / 清空");
		feedback(source, "/chaoshelper travel stop | travel mode <mode>");
		feedback(source, "/chaoshelper elytra [status | climb|swap on|off|toggle]  鞘翅辅助开关");
		feedback(source, "/chaoshelper team add|remove|list|clear");
		feedback(source, "/chaoshelper record list|clear | target set|clear");
		feedback(source, "/chaoshelper xaero status|list|mark <名称>");
		return 1;
	}

	private static int list(FabricClientCommandSource source) {
		List<Module> modules = ChaosHelperClient.get().moduleManager().sortedModules();
		feedback(source, "模块 (" + modules.size() + "):");
		for (Module module : modules) {
			feedback(source, String.format(Locale.ROOT, "  %-14s %-4s %s",
					module.id(), module.isEnabled() ? "开启" : "关闭", module.displayName()));
		}
		return 1;
	}

	private static int status(FabricClientCommandSource source) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		feedback(source, mod.baritoneBridge().statusLine());
		feedback(source, XaeroWaypoints.statusLine());
		feedback(source, "动态模块: " + mod.dynamicModuleLoader().loadedCount()
				+ " | 缓存实体: " + mod.entityCache().entities().size());
		for (Module module : mod.moduleManager().sortedModules()) {
			if (module.isEnabled()) {
				feedback(source, "  " + module.id() + " -> " + module.status());
			}
		}
		return 1;
	}

	private static int panic(FabricClientCommandSource source) {
		ChaosHelperClient.get().moduleManager().panic();
		feedback(source, "已执行 panic");
		return 1;
	}

	private static int reload(FabricClientCommandSource source) {
		DynamicModuleLoader.LoadResult result = ChaosHelperClient.get().dynamicModuleLoader().reload();
		feedback(source, "动态模块: " + result.loaded() + " 个已加载");
		for (String error : result.errors()) {
			error(source, error);
		}
		return 1;
	}

	private static int config(FabricClientCommandSource source) {
		for (String line : ConfigKeys.describe(ChaosHelperClient.get().config())) {
			feedback(source, line);
		}
		return 1;
	}

	private static int setTravelMode(CommandContext<FabricClientCommandSource> context) {
		String mode = StringArgumentType.getString(context, "mode").toLowerCase(Locale.ROOT);
		ChaosHelperClient mod = ChaosHelperClient.get();
		switch (mode) {
			case "auto" -> {
				mod.config().travel.mode = "auto";
				mod.config().travel.glide = false;
			}
			case "walk" -> {
				mod.config().travel.mode = "walk";
				mod.config().travel.glide = false;
			}
			case "elytra" -> {
				mod.config().travel.mode = "elytra";
				mod.config().travel.glide = false;
			}
			case "glide" -> mod.config().travel.glide = true;
			default -> {
				error(context.getSource(), "未知模式: " + mode);
				return 0;
			}
		}
		mod.saveConfigQuietly();
		feedback(context.getSource(), "旅行模式: " + mode);
		return 1;
	}

	private static int travelCoords(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced,
			String explicit) {
		int x = IntegerArgumentType.getInteger(context, "x");
		int y = IntegerArgumentType.getInteger(context, "y");
		int z = IntegerArgumentType.getInteger(context, "z");
		TravelModule.Mode mode = forced != null ? forced : parseMode(explicit);
		return startPath(new TargetResolver.Result(new TargetResolver.Target("坐标", x, y, z, false), null),
				context.getSource(), mode, explicit);
	}

	private static int gotoItem(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced) {
		String uuid = StringArgumentType.getString(context, "uuid");
		return startPath(TargetResolver.item(uuid), context.getSource(), forced, null);
	}

	private static int gotoPlayer(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced) {
		String name = StringArgumentType.getString(context, "player");
		return startPath(TargetResolver.player(name), context.getSource(), forced, null);
	}

	private static int gotoLog(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced) {
		String name = StringArgumentType.getString(context, "player");
		return startPath(TargetResolver.log(name), context.getSource(), forced, null);
	}

	private static int gotoWaypoint(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced) {
		String name = StringArgumentType.getString(context, "name");
		return startPath(TargetResolver.waypoint(name), context.getSource(), forced, null);
	}

	/** Walks to the nearest item frame that holds an elytra (pair with autotake). */
	private static int gotoElytra(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced) {
		MinecraftClient client = context.getSource().getClient();
		if (client.player == null || client.world == null) {
			error(context.getSource(), "当前不在世界中");
			return 0;
		}
		ItemFrameEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof ItemFrameEntity frame)) {
				continue;
			}
			if (!frame.getHeldItemStack().isOf(Items.ELYTRA)) {
				continue;
			}
			double distance = frame.squaredDistanceTo(client.player);
			if (distance < best) {
				best = distance;
				nearest = frame;
			}
		}
		if (nearest == null) {
			error(context.getSource(), "附近没有装鞘翅的展示框");
			return 0;
		}
		BlockPos pos = nearest.getBlockPos();
		feedback(context.getSource(), String.format(Locale.ROOT, "最近的鞘翅展示框: %d, %d, %d (%.0f 格)",
				pos.getX(), pos.getY(), pos.getZ(), Math.sqrt(best)));
		return startPath(new TargetResolver.Result(
				new TargetResolver.Target("鞘翅展示框", pos.getX(), pos.getY(), pos.getZ(), false), null),
				context.getSource(), forced, null);
	}

	/** Flies to the nearest End Ship that still has its elytra. */
	private static int gotoNearestShip(CommandContext<FabricClientCommandSource> context, TravelModule.Mode forced) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = context.getSource().getClient();
		if (client.player == null || client.world == null) {
			error(context.getSource(), "当前不在世界中");
			return 0;
		}
		ShipPoint target = mod.endShipFinder().nextUnlooted(mod, null);
		if (target == null) {
			error(context.getSource(), "没有已发现的未搜末地船（开启 endship 模块并在末地飞行扫描）");
			return 0;
		}
		mod.endShipFinder().resumeAutomation();
		double distance = Math.sqrt(target.distanceSquaredTo(client.player.getX(), client.player.getY(),
				client.player.getZ()));
		feedback(context.getSource(), String.format(Locale.ROOT, "末地船: %d, %d, %d (%.0f 格)",
				target.x, target.y, target.z, distance));
		return startPath(new TargetResolver.Result(
				new TargetResolver.Target("末地船", target.x, target.y, target.z, false), null),
				context.getSource(), forced == null ? TravelModule.Mode.ELYTRA : forced, null);
	}

	private static int shipList(FabricClientCommandSource source) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		MinecraftClient client = source.getClient();
		if (client.player == null || client.world == null) {
			error(source, "当前不在世界中");
			return 0;
		}
		String dimension = client.world.getRegistryKey().getValue().toString();
		List<ShipPoint> ships = mod.endShipFinder().shipsIn(dimension);
		if (ships.isEmpty()) {
			feedback(source, "当前维度没有已发现的末地船");
			return 1;
		}
		ships.sort(java.util.Comparator.comparingDouble(ship ->
				ship.distanceSquaredTo(client.player.getX(), client.player.getY(), client.player.getZ())));
		feedback(source, "末地船 (" + ships.size() + "):");
		for (ShipPoint ship : ships) {
			double distance = Math.sqrt(ship.distanceSquaredTo(client.player.getX(), client.player.getY(),
					client.player.getZ()));
			feedback(source, String.format(Locale.ROOT, "  %s %d, %d, %d  %.0f 格",
					ship.looted ? "[已搜]" : "[未搜]", ship.x, ship.y, ship.z, distance));
		}
		return 1;
	}

	private static int shipClear(FabricClientCommandSource source) {
		ChaosHelperClient.get().endShipFinder().clearFound();
		feedback(source, "已清空末地船列表");
		return 1;
	}

	private static TravelModule.Mode parseMode(String explicit) {		if (explicit == null) {
			return TravelModule.Mode.AUTO;
		}
		return switch (explicit.toLowerCase(Locale.ROOT)) {
			case "walk" -> TravelModule.Mode.WALK;
			case "elytra" -> TravelModule.Mode.ELYTRA;
			case "glide" -> TravelModule.Mode.GLIDE;
			default -> TravelModule.Mode.AUTO;
		};
	}

	private static int startPath(TargetResolver.Result result, FabricClientCommandSource source,
			TravelModule.Mode mode, String explicit) {
		if (!result.ok()) {
			error(source, result.error());
			return 0;
		}
		ChaosHelperClient mod = ChaosHelperClient.get();
		TravelModule travel = mod.travel();
		if (travel == null) {
			error(source, "旅行模块未初始化");
			return 0;
		}
		if (!mod.moduleManager().isEnabled("travel")) {
			mod.moduleManager().setEnabled("travel", true);
			feedback(source, "已自动开启 travel");
		}
		TargetResolver.Target target = result.target();
		TravelModule.Mode requested = mode == null ? parseMode(explicit) : mode;
		String error = travel.start(target.x(), target.y(), target.z(), requested, target.label());
		if (error != null) {
			error(source, error);
			return 0;
		}
		feedback(source, String.format(Locale.ROOT, "前往 %s (%d, %d, %d)%s", target.label(),
				target.x(), target.y(), target.z(), target.approximate() ? " [最后可见位置]" : ""));
		return 1;
	}

	private static int xaeroMark(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "name");
		if (context.getSource().getPlayer() == null) {
			error(context.getSource(), "当前不在世界中");
			return 0;
		}
		var player = context.getSource().getPlayer();
		BlockPos pos = new BlockPos((int) Math.floor(player.getX()), (int) Math.floor(player.getY()),
				(int) Math.floor(player.getZ()));
		if (XaeroWaypoints.mark(name, pos)) {
			feedback(context.getSource(), "已标记 " + name + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
			return 1;
		}
		error(context.getSource(), "写入失败: " + XaeroWaypoints.statusLine());
		return 0;
	}

	private static int waypointList(FabricClientCommandSource source) {
		List<XaeroWaypoints.Waypoint> waypoints = XaeroWaypoints.readAll();
		feedback(source, "当前维度路点 (" + waypoints.size() + "):");
		int shown = 0;
		for (XaeroWaypoints.Waypoint waypoint : waypoints) {
			if (shown++ >= 12) {
				feedback(source, "  ... 还有 " + (waypoints.size() - 12) + " 个");
				break;
			}
			feedback(source, String.format(Locale.ROOT, "  %s  %d, %d, %d  [%s]",
					waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.set()));
		}
		return 1;
	}

	private static int setModule(CommandContext<FabricClientCommandSource> context, boolean enabled) {
		return setModuleById(StringArgumentType.getString(context, "module"), enabled, context.getSource());
	}

	private static int setModuleById(String id, boolean enabled, FabricClientCommandSource source) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		Module module = mod.moduleManager().find(id);
		if (module == null) {
			error(source, "未知模块: " + id);
			return 0;
		}
		mod.moduleManager().setEnabled(module.id(), enabled);
		feedback(source, module.id() + " -> " + (module.isEnabled() ? "开启" : "关闭"));
		return 1;
	}

	private static int toggleModule(CommandContext<FabricClientCommandSource> context) {
		return toggleModuleById(StringArgumentType.getString(context, "module"), context.getSource());
	}

	private static int toggleModuleById(String id, FabricClientCommandSource source) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		Module module = mod.moduleManager().find(id);
		if (module == null) {
			error(source, "未知模块: " + id);
			return 0;
		}
		boolean state = mod.moduleManager().toggle(module.id());
		feedback(source, module.id() + " -> " + (state ? "开启" : "关闭"));
		return 1;
	}

	private static int teamAdd(CommandContext<FabricClientCommandSource> context, String note) {
		String name = StringArgumentType.getString(context, "player");
		boolean added = ChaosHelperClient.get().teamManager().add(name, note);
		if (!added) {
			error(context.getSource(), "无法添加 " + name + "（名字为空或就是你自己）");
			return 0;
		}
		feedback(context.getSource(), "已加入队友: " + name);
		return 1;
	}

	private static int teamRemove(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "player");
		if (ChaosHelperClient.get().teamManager().remove(name)) {
			feedback(context.getSource(), "已移除队友: " + name);
			return 1;
		}
		error(context.getSource(), "队友名单中没有 " + name);
		return 0;
	}

	private static int teamList(FabricClientCommandSource source) {
		List<TeamEntry> entries = ChaosHelperClient.get().teamManager().entries();
		feedback(source, "队友 (" + entries.size() + "):");
		for (TeamEntry entry : entries) {
			feedback(source, "  " + entry.name + (entry.note.isBlank() ? "" : "  // " + entry.note));
		}
		return 1;
	}

	private static int teamClear(FabricClientCommandSource source) {
		ChaosHelperClient.get().teamManager().clear();
		feedback(source, "已清空队友名单");
		return 1;
	}

	private static int recordList(FabricClientCommandSource source) {
		List<RecordEntry> records = ChaosHelperClient.get().recordManager().records();
		feedback(source, "本地记录 (" + records.size() + "):");
		int shown = 0;
		for (int index = records.size() - 1; index >= 0 && shown < 8; index--, shown++) {
			RecordEntry entry = records.get(index);
			feedback(source, String.format(Locale.ROOT, "  [%s] %s %.0f %.0f %.0f %s",
					entry.type, TimeUtil.clock(entry.timeMillis), entry.x, entry.y, entry.z, entry.message));
		}
		return 1;
	}

	private static int recordClear(FabricClientCommandSource source) {
		ChaosHelperClient.get().recordManager().clear();
		feedback(source, "已清空本地记录");
		return 1;
	}

	private static int targetSet(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, "player");
		ChaosHelperClient.get().targetLink().set(name);
		feedback(context.getSource(), "目标已设为 " + ChaosHelperClient.get().targetLink().name());
		return 1;
	}

	private static void feedback(FabricClientCommandSource source, String message) {
		source.sendFeedback(Text.literal(message));
	}

	private static void error(FabricClientCommandSource source, String message) {
		source.sendError(Text.literal(message));
	}
}
