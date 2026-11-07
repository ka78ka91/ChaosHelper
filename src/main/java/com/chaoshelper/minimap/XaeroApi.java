package com.chaoshelper.minimap;

import com.chaoshelper.ChaosHelperClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection bridge to Xaero's in-memory waypoint API.
 *
 * <p>Xaero does not publish a stable, versioned API, so every handle is
 * resolved lazily and the bridge degrades to "unavailable" if a class or method
 * disappears. Callers fall back to the file writer in that case.
 *
 * <p>Verified against Xaero's Minimap 26.6.0:
 *
 * <pre>
 * XaeroMinimapSession.getCurrentSession()
 *   .getMinimapProcessor()
 *   .getSession()                  // MinimapSession
 *   .getWorldManager()             // MinimapWorldManager
 *   .getCurrentWorld()             // MinimapWorld
 *   .getCurrentWaypointSet()       // WaypointSet
 *   .add(new Waypoint(x, y, z, name, initials, color))
 * </pre>
 *
 * <p>Using the API means Xaero owns persistence and the marker appears
 * immediately, instead of writing a file that Xaero may later overwrite.
 */
public final class XaeroApi {
	private static boolean probed;
	private static boolean available;
	private static Method currentSession;
	private static Method getMinimapProcessor;
	private static Method getMinimapSession;
	private static Method getWorldManager;
	private static Method getCurrentWorld;
	private static Method getCurrentWaypointSet;
	private static Method addWaypoint;
	private static Constructor<?> waypointConstructor;

	private XaeroApi() {
	}

	public static boolean available() {
		probe();
		return available;
	}

	public static boolean add(String name, int x, int y, int z) {
		probe();
		if (!available) {
			return false;
		}
		try {
			Object session = currentSession.invoke(null);
			if (session == null) {
				return false;
			}
			Object processor = getMinimapProcessor.invoke(session);
			Object minimap = processor == null ? null : getMinimapSession.invoke(processor);
			Object manager = minimap == null ? null : getWorldManager.invoke(minimap);
			Object world = manager == null ? null : getCurrentWorld.invoke(manager);
			Object set = world == null ? null : getCurrentWaypointSet.invoke(world);
			if (set == null) {
				return false;
			}
			Object waypoint = waypointConstructor.newInstance(x, y, z, name, initials(name), 0);
			addWaypoint.invoke(set, waypoint);
			return true;
		} catch (Throwable throwable) {
			// One failure is enough: stop retrying and let the file fallback handle it.
			available = false;
			ChaosHelperClient.LOGGER.warn("Xaero API 添加路点失败，改用文件写入: {}", throwable.toString());
			return false;
		}
	}

	private static synchronized void probe() {
		if (probed) {
			return;
		}
		probed = true;
		try {
			ClassLoader loader = XaeroApi.class.getClassLoader();
			Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession", false, loader);
			Class<?> processorClass = Class.forName("xaero.common.minimap.MinimapProcessor", false, loader);
			Class<?> minimapSessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession", false, loader);
			Class<?> worldManagerClass = Class.forName("xaero.hud.minimap.world.MinimapWorldManager", false, loader);
			Class<?> worldClass = Class.forName("xaero.hud.minimap.world.MinimapWorld", false, loader);
			Class<?> setClass = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet", false, loader);
			Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint", false, loader);

			currentSession = sessionClass.getMethod("getCurrentSession");
			getMinimapProcessor = sessionClass.getMethod("getMinimapProcessor");
			getMinimapSession = processorClass.getMethod("getSession");
			getWorldManager = minimapSessionClass.getMethod("getWorldManager");
			getCurrentWorld = worldManagerClass.getMethod("getCurrentWorld");
			getCurrentWaypointSet = worldClass.getMethod("getCurrentWaypointSet");
			waypointConstructor = waypointClass.getConstructor(int.class, int.class, int.class,
					String.class, String.class, int.class);
			addWaypoint = setClass.getMethod("add", waypointClass);
			available = true;
			ChaosHelperClient.LOGGER.info("Xaero 路点 API 已就绪");
		} catch (Throwable throwable) {
			available = false;
			ChaosHelperClient.LOGGER.info("Xaero 路点 API 不可用，回退到文件写入: {}", throwable.toString());
		}
	}

	private static String initials(String name) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < name.length() && builder.length() < 2; index++) {
			char c = name.charAt(index);
			if (Character.isLetterOrDigit(c)) {
				builder.append(Character.toUpperCase(c));
			}
		}
		return builder.length() == 0 ? "C" : builder.toString();
	}
}
