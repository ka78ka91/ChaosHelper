package com.chaoshelper;

import com.chaoshelper.baritone.BaritoneBridge;
import com.chaoshelper.cache.EntityCache;
import com.chaoshelper.command.ChaosCommand;
import com.chaoshelper.config.ChaosConfig;
import com.chaoshelper.config.ConfigManager;
import com.chaoshelper.data.DataStore;
import com.chaoshelper.dynamic.DynamicModuleLoader;
import com.chaoshelper.hud.HudRenderer;
import com.chaoshelper.keybind.KeybindManager;
import com.chaoshelper.module.ModuleManager;
import com.chaoshelper.module.modules.AutoEatModule;
import com.chaoshelper.module.modules.AutoLogModule;
import com.chaoshelper.module.modules.AutoTakeModule;
import com.chaoshelper.module.modules.ChamsModule;
import com.chaoshelper.module.modules.DropTrackerModule;
import com.chaoshelper.module.modules.EndShipModule;
import com.chaoshelper.module.modules.EspModule;
import com.chaoshelper.module.modules.HudModule;
import com.chaoshelper.module.modules.PlayerTrackerModule;
import com.chaoshelper.module.modules.RecordModule;
import com.chaoshelper.module.modules.TravelModule;
import com.chaoshelper.record.RecordManager;
import com.chaoshelper.render.EspWorldRenderer;
import com.chaoshelper.target.TargetLink;
import com.chaoshelper.team.TeamManager;
import com.chaoshelper.util.ErrorLog;
import com.chaoshelper.util.NotificationManager;
import com.chaoshelper.world.EndMarkerService;
import com.chaoshelper.world.EndShipFinder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Client bootstrap and service locator. Everything is created once, in a fixed
 * order, so modules can rely on the shared cache and stores being non-null.
 */
public final class ChaosHelperClient implements ClientModInitializer {
	public static final String MOD_ID = "chaoshelper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ChaosHelperClient instance;

	private final ConfigManager configManager = new ConfigManager();
	private final DataStore dataStore = new DataStore();
	private final EntityCache entityCache = new EntityCache();
	private final ErrorLog errorLog = new ErrorLog();
	private final NotificationManager notifications = new NotificationManager();

	private ChaosConfig config;
	private ModuleManager moduleManager;
	private PlayerTrackerModule playerTracker;
	private DropTrackerModule dropTracker;
	private TravelModule travelModule;
	private EndMarkerService endMarkerService;
	private EndShipFinder endShipFinder;
	private RecordManager recordManager;
	private TeamManager teamManager;
	private TargetLink targetLink;
	private BaritoneBridge baritoneBridge;
	private DynamicModuleLoader dynamicModuleLoader;
	private HudRenderer hudRenderer;
	private EspWorldRenderer espWorldRenderer;
	private KeybindManager keybindManager;
	private long tickCount;

	public static ChaosHelperClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		long startedAt = System.currentTimeMillis();

		config = configManager.load();
		dataStore.load();
		for (String error : configManager.loadErrors()) {
			errorLog.add(ErrorLog.Level.WARN, "config", error);
		}
		for (String error : dataStore.loadErrors()) {
			errorLog.add(ErrorLog.Level.WARN, "data", error);
		}

		teamManager = new TeamManager(dataStore);
		targetLink = new TargetLink(dataStore);
		recordManager = new RecordManager(dataStore);
		endMarkerService = new EndMarkerService();
		endShipFinder = new EndShipFinder();
		baritoneBridge = new BaritoneBridge();

		moduleManager = new ModuleManager(this);
		playerTracker = new PlayerTrackerModule();
		dropTracker = new DropTrackerModule();
		travelModule = new TravelModule();
		moduleManager.registerBuiltin(new HudModule());
		moduleManager.registerBuiltin(new EspModule());
		moduleManager.registerBuiltin(new ChamsModule());
		moduleManager.registerBuiltin(playerTracker);
		moduleManager.registerBuiltin(dropTracker);
		moduleManager.registerBuiltin(new RecordModule());
		moduleManager.registerBuiltin(travelModule);
		moduleManager.registerBuiltin(new AutoEatModule());
		moduleManager.registerBuiltin(new AutoTakeModule());
		moduleManager.registerBuiltin(new EndShipModule(endShipFinder));
		moduleManager.registerBuiltin(new AutoLogModule());
		moduleManager.initializeStates();

		dynamicModuleLoader = new DynamicModuleLoader(this);
		if (config.dynamic.enabled) {
			dynamicModuleLoader.reload();
		} else {
			LOGGER.info("动态模块加载已禁用 (dynamic.enabled=false)");
		}

		hudRenderer = new HudRenderer(this);
		HudRenderCallback.EVENT.register(hudRenderer);
		espWorldRenderer = new EspWorldRenderer(this);
		WorldRenderEvents.AFTER_ENTITIES.register(espWorldRenderer);

		keybindManager = new KeybindManager(this);
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> recordManager.onGameMessage(message, overlay));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onWorldLeave());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> ChaosCommand.register(dispatcher));

		LOGGER.info("ChaosHelper 初始化完成，用时 {} ms，模块 {} 个，动态模块 {} 个",
				System.currentTimeMillis() - startedAt, moduleManager.sortedModules().size(),
				dynamicModuleLoader.loadedCount());
	}

	private void onEndTick(MinecraftClient client) {
		tickCount++;
		try {
			keybindManager.tick();
		} catch (Throwable throwable) {
			reportInternal("keybind", throwable);
		}
		try {
			entityCache.tick(client);
		} catch (Throwable throwable) {
			reportInternal("cache", throwable);
		}
		try {
			endMarkerService.tick(client);
		} catch (Throwable throwable) {
			reportInternal("end-marker", throwable);
		}
		moduleManager.tick();
	}

	private void onWorldLeave() {
		playerTracker.resetForWorldChange();
		dropTracker.resetForWorldChange();
		entityCache.clear();
		endMarkerService.clear();
		endShipFinder.resetForWorldChange();
		// cancel() checks availability itself; calling it unconditionally also stops
		// pathing when the bridge was already probed before the disconnect.
		baritoneBridge.cancel();
		notifications.clear();
	}

	private void reportInternal(String source, Throwable throwable) {
		String message = "内部错误 (" + source + "): " + throwable.getMessage();
		errorLog.add(ErrorLog.Level.ERROR, source, message);
		LOGGER.error(message, throwable);
	}

	public MinecraftClient client() {
		return MinecraftClient.getInstance();
	}

	public ChaosConfig config() {
		return config;
	}

	public ConfigManager configManager() {
		return configManager;
	}

	public Path configDirectory() {
		return configManager.directory();
	}

	public DataStore dataStore() {
		return dataStore;
	}

	public EntityCache entityCache() {
		return entityCache;
	}

	public ErrorLog errorLog() {
		return errorLog;
	}

	public NotificationManager notifications() {
		return notifications;
	}

	public ModuleManager moduleManager() {
		return moduleManager;
	}

	public PlayerTrackerModule playerTracker() {
		return playerTracker;
	}

	public DropTrackerModule dropTracker() {
		return dropTracker;
	}

	public TravelModule travel() {
		return travelModule;
	}

	public EndMarkerService endMarkerService() {
		return endMarkerService;
	}

	public EndShipFinder endShipFinder() {
		return endShipFinder;
	}

	public RecordManager recordManager() {
		return recordManager;
	}

	public TeamManager teamManager() {
		return teamManager;
	}

	public TargetLink targetLink() {
		return targetLink;
	}

	public BaritoneBridge baritoneBridge() {
		return baritoneBridge;
	}

	public DynamicModuleLoader dynamicModuleLoader() {
		return dynamicModuleLoader;
	}

	public HudRenderer hudRenderer() {
		return hudRenderer;
	}

	public EspWorldRenderer espWorldRenderer() {
		return espWorldRenderer;
	}

	public KeybindManager keybindManager() {
		return keybindManager;
	}

	public long tickCount() {
		return tickCount;
	}

	public void saveConfigQuietly() {
		if (config == null) {
			return;
		}
		try {
			configManager.save(config);
		} catch (Exception exception) {
			LOGGER.error("保存配置失败", exception);
			errorLog.add(ErrorLog.Level.ERROR, "config", "保存配置失败: " + exception.getMessage());
		}
	}
}
