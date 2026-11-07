package com.chaoshelper.baritone;

import com.chaoshelper.ChaosHelperClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Optional Baritone integration through reflection only.
 *
 * <p>No Baritone class is referenced at compile time and no Baritone code is
 * copied or bundled. The user installs the standalone Fabric build; if it is
 * missing or incompatible this bridge reports a friendly reason and every caller
 * degrades to "unavailable" instead of failing at startup.
 *
 * <p>Verified against the official release
 * {@code baritone-api-fabric-1.17.0.jar} (sha1 553bf1f5…, mod id {@code baritone},
 * minecraft {@code 1.21.11}):
 * <ul>
 *   <li>{@code BaritoneAPI.getProvider()}</li>
 *   <li>{@code IBaritoneProvider.getPrimaryBaritone()}</li>
 *   <li>{@code IBaritone.getPathingBehavior() / getCustomGoalProcess() / getElytraProcess()}</li>
 *   <li>{@code ICustomGoalProcess.setGoalAndPath(Goal)}</li>
 *   <li>{@code IElytraProcess.pathTo(Goal) / resetState()}</li>
 *   <li>{@code IPathingBehavior.isPathing() / cancelEverything()}</li>
 *   <li>{@code IBaritoneProcess.isActive()}</li>
 *   <li>{@code GoalBlock(int,int,int)}, {@code GoalXZ(int,int)}</li>
 * </ul>
 */
public final class BaritoneBridge {
	private static final String[] MOD_IDS = {"baritone", "baritone-api-fabric"};
	private static final String API_CLASS = "baritone.api.BaritoneAPI";
	private static final String SUPPORTED_PREFIX = "1.17.";
	private static final long RETRY_DELAY_MILLIS = 5_000L;

	/** True once the state is definitive (available, not installed, or wrong version). */
	private boolean initialized;
	private boolean available;
	private boolean elytraAvailable;
	private String reason = "尚未探测";
	private String elytraReason = "尚未探测";
	private String version = "";
	private long retryAfterMillis;
	private boolean failureLogged;

	private Object pathingBehavior;
	private Object customGoalProcess;
	private Object elytraProcess;
	private Method setGoalAndPath;
	private Method pathToGoal;
	private Method resetElytra;
	private Method isElytraActive;
	private Method cancelEverything;
	private Method isPathing;
	private Constructor<?> goalBlockConstructor;
	private Constructor<?> goalXZConstructor;

	public synchronized boolean isAvailable() {
		ensureProbed();
		return available && ChaosHelperClient.get().config().baritone.enabled;
	}

	public synchronized boolean isElytraAvailable() {
		ensureProbed();
		return available && elytraAvailable && ChaosHelperClient.get().config().baritone.enabled;
	}

	public synchronized String unavailableReason() {
		ensureProbed();
		if (!ChaosHelperClient.get().config().baritone.enabled) {
			return "配置中已禁用 baritone.enabled";
		}
		return reason;
	}

	public synchronized String elytraUnavailableReason() {
		ensureProbed();
		if (!ChaosHelperClient.get().config().baritone.enabled) {
			return "配置中已禁用 baritone.enabled";
		}
		return available ? elytraReason : reason;
	}

	public synchronized String statusLine() {
		ensureProbed();
		if (!ChaosHelperClient.get().config().baritone.enabled) {
			return "Baritone: 已禁用";
		}
		if (!available) {
			return "Baritone: " + reason;
		}
		if (!elytraAvailable) {
			return "Baritone: 可用 " + version + " (鞘翅不可用)";
		}
		return "Baritone: 可用 " + version;
	}

	/**
	 * @return null on success, or a user-facing error message
	 */
	public synchronized String startPath(int x, int y, int z) {
		if (!isAvailable()) {
			return "Baritone 不可用: " + unavailableReason();
		}
		try {
			Object goal = goalBlockConstructor.newInstance(x, y, z);
			setGoalAndPath.invoke(customGoalProcess, goal);
			return null;
		} catch (Throwable throwable) {
			ChaosHelperClient.LOGGER.error("Baritone 设置路径失败", throwable);
			return "Baritone 设置路径失败: " + throwable.getMessage();
		}
	}

	/**
	 * @return null on success, or a user-facing error message
	 */
	public synchronized String startElytra(int x, int z) {
		if (!isElytraAvailable()) {
			return "ElytraFly 不可用: " + elytraUnavailableReason();
		}
		try {
			Object goal = goalXZConstructor.newInstance(x, z);
			pathToGoal.invoke(elytraProcess, goal);
			return null;
		} catch (Throwable throwable) {
			ChaosHelperClient.LOGGER.error("Baritone 设置鞘翅路径失败", throwable);
			return "Baritone 设置鞘翅路径失败: " + throwable.getMessage();
		}
	}

	/**
	 * Elytra flight to an exact block position.
	 *
	 * <p>Baritone's {@code pathTo(GoalXZ)} hard-codes the cruise altitude to y=64
	 * (see {@code ElytraProcess.pathTo(Goal)}), so an XZ goal can never target a
	 * floating structure: the process finishes "at 64" and then looks for a safe
	 * landing spot on whatever is below. A {@code GoalBlock} is the only way to
	 * hand it a real Y, which is what makes landing on an End Ship possible.
	 *
	 * @return null on success, or a user-facing error message
	 */
	public synchronized String startElytraTo(int x, int y, int z) {
		if (!isElytraAvailable()) {
			return "ElytraFly 不可用: " + elytraUnavailableReason();
		}
		try {
			Object goal = goalBlockConstructor.newInstance(x, y, z);
			pathToGoal.invoke(elytraProcess, goal);
			return null;
		} catch (Throwable throwable) {
			ChaosHelperClient.LOGGER.error("Baritone 设置鞘翅路径失败", throwable);
			return "Baritone 设置鞘翅路径失败: " + throwable.getMessage();
		}
	}

	/**
	 * Re-issues an elytra goal after the process dropped out (e.g. a brief
	 * non-gliding tick during an armour swap). Resets the internal state first so
	 * a half-finished landing does not block the new goal.
	 *
	 * @return null on success, or a user-facing error message
	 */
	public synchronized String restartElytra(int x, int z) {
		if (!isElytraAvailable()) {
			return "ElytraFly 不可用: " + elytraUnavailableReason();
		}
		try {
			resetElytra.invoke(elytraProcess);
			Object goal = goalXZConstructor.newInstance(x, z);
			pathToGoal.invoke(elytraProcess, goal);
			return null;
		} catch (Throwable throwable) {
			ChaosHelperClient.LOGGER.error("Baritone 恢复鞘翅路径失败", throwable);
			return "Baritone 恢复鞘翅路径失败: " + throwable.getMessage();
		}
	}

	/** Same as {@link #restartElytra(int, int)} but keeps the exact altitude. */
	public synchronized String restartElytraTo(int x, int y, int z) {
		if (!isElytraAvailable()) {
			return "ElytraFly 不可用: " + elytraUnavailableReason();
		}
		try {
			resetElytra.invoke(elytraProcess);
			Object goal = goalBlockConstructor.newInstance(x, y, z);
			pathToGoal.invoke(elytraProcess, goal);
			return null;
		} catch (Throwable throwable) {
			ChaosHelperClient.LOGGER.error("Baritone 恢复鞘翅路径失败", throwable);
			return "Baritone 恢复鞘翅路径失败: " + throwable.getMessage();
		}
	}

	public synchronized void cancel() {
		if (!initialized || !available) {
			return;
		}
		try {
			cancelEverything.invoke(pathingBehavior);
		} catch (Throwable ignored) {
			// Baritone may already be stopping; cancellation is best-effort.
		}
		if (elytraAvailable) {
			try {
				resetElytra.invoke(elytraProcess);
			} catch (Throwable ignored) {
				// Same as above.
			}
		}
	}

	public synchronized boolean isPathing() {
		if (!initialized || !available || isPathing == null) {
			return false;
		}
		try {
			Object value = isPathing.invoke(pathingBehavior);
			return value instanceof Boolean bool && bool;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public synchronized boolean isElytraActive() {
		if (!initialized || !elytraAvailable || isElytraActive == null) {
			return false;
		}
		try {
			Object value = isElytraActive.invoke(elytraProcess);
			return value instanceof Boolean bool && bool;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public synchronized String version() {
		return version;
	}

	/**
	 * Baritone's provider only exists once a world is loaded, and the HUD can call
	 * us from the main menu. Probing before that would permanently mark the bridge
	 * unavailable, so we wait for a world and retry transient failures.
	 */
	private void ensureProbed() {
		if (initialized) {
			return;
		}
		MinecraftClient client = ChaosHelperClient.get() == null ? null : ChaosHelperClient.get().client();
		if (client == null || client.player == null || client.world == null) {
			reason = "等待进入世界";
			return;
		}
		long now = System.currentTimeMillis();
		if (now < retryAfterMillis) {
			return;
		}
		if (probe()) {
			initialized = true;
		} else {
			retryAfterMillis = now + RETRY_DELAY_MILLIS;
		}
	}

	/**
	 * @return true when the result is definitive (available or permanently unusable)
	 */
	private boolean probe() {
		Optional<ModContainer> container = findContainer();
		if (container.isEmpty()) {
			available = false;
			reason = "未安装 (需要 Baritone 1.17.x Fabric)";
			elytraReason = reason;
			return true;
		}
		version = container.get().getMetadata().getVersion().getFriendlyString();
		if (!version.startsWith(SUPPORTED_PREFIX)) {
			available = false;
			reason = "版本 " + version + " 不兼容 (需要 " + SUPPORTED_PREFIX + "x)";
			elytraReason = reason;
			return true;
		}
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader == null || !isVisible(loader, API_CLASS)) {
				loader = BaritoneBridge.class.getClassLoader();
			}
			Class<?> api = Class.forName(API_CLASS, false, loader);
			Class<?> providerInterface = Class.forName("baritone.api.IBaritoneProvider", false, loader);
			Class<?> baritoneInterface = Class.forName("baritone.api.IBaritone", false, loader);
			Class<?> pathingInterface = Class.forName("baritone.api.behavior.IPathingBehavior", false, loader);
			Class<?> customGoalInterface = Class.forName("baritone.api.process.ICustomGoalProcess", false, loader);
			Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal", false, loader);
			Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock", false, loader);
			Class<?> goalXZClass = Class.forName("baritone.api.pathing.goals.GoalXZ", false, loader);

			Object provider = api.getMethod("getProvider").invoke(null);
			if (provider == null) {
				throw new IllegalStateException("BaritoneAPI.getProvider() returned null");
			}
			Object baritone = providerInterface.getMethod("getPrimaryBaritone").invoke(provider);
			if (baritone == null) {
				throw new IllegalStateException("primary Baritone is null");
			}
			pathingBehavior = baritoneInterface.getMethod("getPathingBehavior").invoke(baritone);
			customGoalProcess = baritoneInterface.getMethod("getCustomGoalProcess").invoke(baritone);
			if (pathingBehavior == null || customGoalProcess == null) {
				throw new IllegalStateException("pathing/customGoal process unavailable");
			}
			setGoalAndPath = customGoalInterface.getMethod("setGoalAndPath", goalClass);
			cancelEverything = pathingInterface.getMethod("cancelEverything");
			isPathing = pathingInterface.getMethod("isPathing");
			goalBlockConstructor = goalBlockClass.getConstructor(int.class, int.class, int.class);
			goalXZConstructor = goalXZClass.getConstructor(int.class, int.class);
			available = true;
			reason = "可用";
		} catch (Throwable throwable) {
			available = false;
			reason = "初始化失败: " + throwable.getClass().getSimpleName();
			elytraReason = reason;
			if (!failureLogged) {
				failureLogged = true;
				ChaosHelperClient.LOGGER.warn("Baritone API 不可用: " + throwable.getMessage(), throwable);
			}
			// Transient (e.g. world still loading): allow another attempt later.
			return false;
		}
		return initializeElytra();
	}

	/**
	 * Elytra support is optional even when Baritone itself works: a build without
	 * IElytraProcess must not disable normal pathing.
	 *
	 * @return true when the result is definitive
	 */
	private boolean initializeElytra() {
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader == null) {
				loader = BaritoneBridge.class.getClassLoader();
			}
			Class<?> baritoneInterface = Class.forName("baritone.api.IBaritone", false, loader);
			Class<?> elytraInterface = Class.forName("baritone.api.process.IElytraProcess", false, loader);
			Class<?> processInterface = Class.forName("baritone.api.process.IBaritoneProcess", false, loader);
			Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal", false, loader);
			Object provider = Class.forName(API_CLASS, false, loader).getMethod("getProvider").invoke(null);
			Object primary = Class.forName("baritone.api.IBaritoneProvider", false, loader)
					.getMethod("getPrimaryBaritone").invoke(provider);
			elytraProcess = baritoneInterface.getMethod("getElytraProcess").invoke(primary);
			if (elytraProcess == null) {
				throw new IllegalStateException("elytra process is null");
			}
			pathToGoal = elytraInterface.getMethod("pathTo", goalClass);
			resetElytra = elytraInterface.getMethod("resetState");
			isElytraActive = processInterface.getMethod("isActive");
			elytraAvailable = true;
			elytraReason = "可用";
		} catch (Throwable throwable) {
			elytraAvailable = false;
			elytraReason = "鞘翅 API 不可用: " + throwable.getClass().getSimpleName();
			ChaosHelperClient.LOGGER.warn("Baritone 鞘翅 API 不可用: {}", throwable.getMessage());
		}
		return true;
	}

	private static Optional<ModContainer> findContainer() {
		for (String id : MOD_IDS) {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(id);
			if (container.isPresent()) {
				return container;
			}
		}
		return Optional.empty();
	}

	private static boolean isVisible(ClassLoader loader, String className) {
		try {
			Class.forName(className, false, loader);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}
}
