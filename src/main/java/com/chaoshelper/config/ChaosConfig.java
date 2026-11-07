package com.chaoshelper.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON-backed settings for the whole client. Unknown fields are ignored by Gson
 * so an older or newer config file stays usable.
 *
 * <p>Every knob is declared here on purpose: modules never own their own file.
 */
public final class ChaosConfig {
	public int configVersion = 3;
	/** How often the shared entity cache is rebuilt, in client ticks. */
	public int cacheIntervalTicks = 5;
	/** How long an error stays on the HUD, in seconds (0 = until cleared). */
	public int errorDisplaySeconds = 120;
	/** How long a notification stays on the HUD, in seconds. */
	public int notificationSeconds = 45;

	public Map<String, Boolean> modules = new LinkedHashMap<>();
	public Map<String, String> keybinds = new LinkedHashMap<>();
	public Map<String, Map<String, String>> dynamicSettings = new LinkedHashMap<>();

	public HudConfig hud = new HudConfig();
	public EspConfig esp = new EspConfig();
	public ChamsConfig chams = new ChamsConfig();
	public PlayerTrackerConfig playerTracker = new PlayerTrackerConfig();
	public DropTrackerConfig dropTracker = new DropTrackerConfig();
	public RecordConfig record = new RecordConfig();
	public TeamConfig team = new TeamConfig();
	public AutoEatConfig autoEat = new AutoEatConfig();
	public AutoTakeConfig autotake = new AutoTakeConfig();
	public EndShipConfig endship = new EndShipConfig();
	public AutoLogConfig autolog = new AutoLogConfig();
	public TravelConfig travel = new TravelConfig();
	public XaeroConfig xaero = new XaeroConfig();
	public BaritoneConfig baritone = new BaritoneConfig();
	public DynamicConfig dynamic = new DynamicConfig();

	public ChaosConfig() {
		modules.put("hud", true);
		modules.put("esp", false);
		modules.put("chams", false);
		modules.put("player-tracker", false);
		modules.put("drop-tracker", false);
		modules.put("record", true);
		modules.put("travel", false);
		modules.put("autoeat", false);
	}

	public void normalize() {
		if (configVersion < 2) {
			configVersion = 2;
		}
		cacheIntervalTicks = clamp(cacheIntervalTicks, 1, 40);
		errorDisplaySeconds = clamp(errorDisplaySeconds, 0, 3600);
		notificationSeconds = clamp(notificationSeconds, 5, 3600);
		if (modules == null) {
			modules = new LinkedHashMap<>();
		}
		if (keybinds == null) {
			keybinds = new LinkedHashMap<>();
		}
		if (dynamicSettings == null) {
			dynamicSettings = new LinkedHashMap<>();
		}
		modules.replaceAll((id, value) -> value != null && value);
		hud = hud == null ? new HudConfig() : hud;
		esp = esp == null ? new EspConfig() : esp;
		chams = chams == null ? new ChamsConfig() : chams;
		playerTracker = playerTracker == null ? new PlayerTrackerConfig() : playerTracker;
		dropTracker = dropTracker == null ? new DropTrackerConfig() : dropTracker;
		record = record == null ? new RecordConfig() : record;
		team = team == null ? new TeamConfig() : team;
		autoEat = autoEat == null ? new AutoEatConfig() : autoEat;
		autotake = autotake == null ? new AutoTakeConfig() : autotake;
		endship = endship == null ? new EndShipConfig() : endship;
		autolog = autolog == null ? new AutoLogConfig() : autolog;
		travel = travel == null ? new TravelConfig() : travel;
		xaero = xaero == null ? new XaeroConfig() : xaero;
		baritone = baritone == null ? new BaritoneConfig() : baritone;
		dynamic = dynamic == null ? new DynamicConfig() : dynamic;
		if (configVersion < 3) {
			configVersion = 3;
			// 1.0.1: old configs stored cancelOnInput/cancelOnScreen=true. Travel
			// now keeps running through normal input; the stop key (V) or panic (P)
			// ends it. Players can still re-enable the old behaviour in the ClickGUI.
			travel.cancelOnInput = false;
			travel.cancelOnScreen = false;
		}
		hud.normalize();
		esp.normalize();
		chams.normalize();
		playerTracker.normalize();
		dropTracker.normalize();
		record.normalize();
		team.normalize();
		autoEat.normalize();
		autotake.normalize();
		endship.normalize();
		autolog.normalize();
		travel.normalize();
		xaero.normalize();
		baritone.normalize();
		dynamic.normalize();
	}

	public boolean moduleEnabled(String id, boolean fallback) {
		Boolean value = modules.get(id);
		return value == null ? fallback : value;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Layout and visibility of the four HUD corners. */
	public static final class HudConfig {
		public boolean showStatus = true;
		public boolean showPlayers = true;
		public boolean showDrops = true;
		public boolean showTarget = true;
		public boolean showRecords = true;
		public boolean showSelf = true;
		public boolean showCoordinates = true;
		public boolean background = true;
		/** Max rows per list before the "+N 更多" fold. */
		public int maxEntries = 10;
		public int margin = 4;
		public int lineHeight = 10;
		/**
		 * Fully custom widget list. Generated once from the legacy flags above,
		 * then owned entirely by the player (add / delete / drag / restyle).
		 */
		public List<HudElement> elements = new ArrayList<>();
		/** False until the custom list has been created; lets "delete everything" stick. */
		public boolean customLayout = false;
		/** Per-panel position; x/y < 0 means "auto corner". Set by the HUD editor. */
		public PanelPos players = new PanelPos();
		public PanelPos drops = new PanelPos();
		public PanelPos status = new PanelPos();
		public PanelPos records = new PanelPos();
		public PanelPos self = new PanelPos();
		public PanelPos target = new PanelPos();

		void normalize() {
			maxEntries = clamp(maxEntries, 1, 50);
			margin = clamp(margin, 0, 64);
			lineHeight = clamp(lineHeight, 8, 16);
			players = players == null ? new PanelPos() : players;
			drops = drops == null ? new PanelPos() : drops;
			status = status == null ? new PanelPos() : status;
			records = records == null ? new PanelPos() : records;
			self = self == null ? new PanelPos() : self;
			target = target == null ? new PanelPos() : target;
			players.normalize();
			drops.normalize();
			status.normalize();
			records.normalize();
			self.normalize();
			target.normalize();
			if (elements == null) {
				elements = new ArrayList<>();
			}
			elements.removeIf(element -> element == null);
			for (HudElement element : elements) {
				element.normalize();
			}
			Set<String> seen = new HashSet<>();
			elements.removeIf(element -> !seen.add(element.id));
			if (!customLayout) {
				elements.clear();
				elements.addAll(HudElement.defaults(this));
				customLayout = true;
			}
		}

		/** Replaces the whole layout with the built-in default widgets. */
		public void resetElements() {
			elements.clear();
			elements.addAll(HudElement.defaults(this));
			customLayout = true;
		}
	}

	/** Absolute top-left of a HUD panel, or auto placement when negative. */
	public static final class PanelPos {
		public int x = -1;
		public int y = -1;

		public boolean auto() {
			return x < 0 || y < 0;
		}

		public void reset() {
			x = -1;
			y = -1;
		}

		void normalize() {
			if (x < -1) {
				x = -1;
			}
			if (y < -1) {
				y = -1;
			}
		}
	}

	public static final class EspConfig {
		public double range = 32.0;
		public boolean throughWalls = false;
		public boolean showLabels = true;
		public boolean showItems = true;
		public int maxEntities = 128;
		public int maxLabels = 32;
		public float lineWidth = 1.5F;
		public int colorPlayer = 0xFF55FFFF;
		public int colorHostile = 0xFFFF5555;
		public int colorPassive = 0xFF55FF55;
		public int colorItem = 0xFFFFD700;
		public int colorOther = 0xFFAAAAAA;
		public int colorTeammate = 0xFF55FF55;
		public int colorTarget = 0xFFFF0000;

		void normalize() {
			range = clamp(range, 4, 256);
			maxEntities = clamp(maxEntities, 1, 512);
			maxLabels = clamp(maxLabels, 0, 128);
			lineWidth = (float) clamp(lineWidth, 0.5, 6.0);
		}
	}

	public static final class ChamsConfig {
		public double range = 32.0;
		public boolean throughWalls = false;
		public int opacity = 190;
		public int color = 0xFFFF5555;
		public int colorTeammate = 0xFF55FF55;
		public int colorTarget = 0xFFFF0000;
		public boolean applyPlayers = true;
		public boolean applyHostile = true;
		public boolean applyPassive = false;

		void normalize() {
			range = clamp(range, 4, 256);
			opacity = clamp(opacity, 0, 255);
		}
	}

	public static final class PlayerTrackerConfig {
		public double range = 64.0;
		public int refreshTicks = 10;
		/** Hard safety cap for the tracking list; the HUD folds at hud.maxEntries. */
		public int maxEntries = 100;
		/** Seconds before an unseen player is dropped from the tracker. */
		public int staleSeconds = 15;
		/** Seconds after which a re-entering player's stored health resets to full (estimate). */
		public int estimateResetSeconds = 30;
		/** Base +/- error shown for a health value read from the entity. */
		public double healthErrorBase = 0.5;
		/** Keep players that are only in the tab list (never seen in world). */
		public boolean includeTabListOnly = true;

		void normalize() {
			range = clamp(range, 8, 256);
			refreshTicks = clamp(refreshTicks, 2, 200);
			maxEntries = clamp(maxEntries, 1, 100);
			staleSeconds = clamp(staleSeconds, 3, 600);
			estimateResetSeconds = clamp(estimateResetSeconds, 5, 600);
			healthErrorBase = clamp(healthErrorBase, 0.0, 20.0);
		}
	}

	public static final class DropTrackerConfig {
		public double range = 32.0;
		public int recordSeconds = 60;
		/** Hard safety cap; the HUD folds at hud.maxEntries. */
		public int maxEntries = 100;
		public int refreshTicks = 5;

		void normalize() {
			range = clamp(range, 4, 256);
			recordSeconds = clamp(recordSeconds, 5, 3600);
			maxEntries = clamp(maxEntries, 1, 100);
			refreshTicks = clamp(refreshTicks, 1, 100);
		}
	}

	public static final class RecordConfig {
		public int maxEntries = 50;
		public boolean autoDeath = true;
		/** Record chat/system lines as local log points. */
		public boolean autoLog = true;
		/** Record the last visible position when a tracked player leaves view. */
		public boolean autoLogoff = true;

		void normalize() {
			maxEntries = clamp(maxEntries, 5, 500);
		}
	}

	public static final class TeamConfig {
		public int colorTeammate = 0xFF55FF55;
		public int colorNeutral = 0xFFFFFF77;
		public int colorTarget = 0xFFFF5555;
		public boolean showTag = true;

		void normalize() {
		}
	}

	public static final class AutoEatConfig {
		public int threshold = 18;
		public int cooldownTicks = 40;
		public boolean restoreSlot = true;
		public boolean preferHighestNutrition = true;

		void normalize() {
			threshold = clamp(threshold, 1, 20);
			cooldownTicks = clamp(cooldownTicks, 10, 200);
		}
	}

	/** Picks elytra out of nearby item frames; independent from travel goals. */
	public static final class AutoTakeConfig {
		public double range = 3.0;
		/**
		 * 1.0.2: default false, so a spare elytra is still taken while one is
		 * equipped - that is the whole point of looting ships. Set true to keep
		 * the old "only when missing" behaviour.
		 */
		public boolean onlyWhenMissing = false;
		public int cooldownTicks = 20;

		void normalize() {
			range = clamp(range, 1, 16);
			cooldownTicks = clamp(cooldownTicks, 5, 200);
		}
	}

	/**
	 * End Ship finder. While flying, shulkers are useless (they sit in the city
	 * below and do not spawn on ships), so detection works on blocks that only an
	 * End Ship contains: the dragon head at the bow, the brewing stand in the
	 * treasure room, and the item frame holding the elytra.
	 */
	public static final class EndShipConfig {
		/** Chat/HUD notification when a new ship is found. */
		public boolean notify = true;
		/** Write a Xaero waypoint for every new ship. */
		public boolean markXaero = true;
		/** End only; a player-placed dragon head elsewhere would be a false hit. */
		public boolean onlyEnd = true;
		/** Chunk scan radius around the player. */
		public int scanRadiusChunks = 8;
		/** Chunk columns scanned per scan tick. */
		public int chunksPerScan = 3;
		/** Scan cadence. 2 = every other tick. */
		public int intervalTicks = 2;
		/** A chunk is not scanned again for this many ticks. */
		public int rescanTicks = 100;
		/** Signals within this distance belong to the same ship. */
		public int dedupeRadius = 48;
		/** Full loop: Baritone flight, walk to the frame, take, next ship. */
		public boolean autoLoot = true;
		/** Stop the elytra flight and start walking once this close (blocks). */
		public int approachRadius = 32;
		/** Attack range for the elytra frame; the server rejects anything over 3. */
		public double takeRange = 3.0;
		/** How long the walk path to the frame may take before asking the player. */
		public int approachTimeoutTicks = 600;
		/** How long to wait for the player to fly in by hand. */
		public int manualTimeoutTicks = 1200;
		/** Fly to the next unlooted ship after taking an elytra. */
		public boolean chainNext = true;
		/** Wait this long before setting off, so the elytra is picked up first. */
		public int chainDelayTicks = 60;
		/** 0 = any distance; otherwise skip ships farther than this. */
		public int chainRange = 0;

		void normalize() {
			scanRadiusChunks = clamp(scanRadiusChunks, 1, 16);
			chunksPerScan = clamp(chunksPerScan, 1, 16);
			intervalTicks = clamp(intervalTicks, 1, 40);
			rescanTicks = clamp(rescanTicks, 20, 1200);
			dedupeRadius = clamp(dedupeRadius, 16, 256);
			approachRadius = clamp(approachRadius, 8, 128);
			takeRange = clamp(takeRange, 1, 6);
			approachTimeoutTicks = clamp(approachTimeoutTicks, 40, 2000);
			manualTimeoutTicks = clamp(manualTimeoutTicks, 100, 6000);
			chainDelayTicks = clamp(chainDelayTicks, 0, 400);
			chainRange = clamp(chainRange, 0, 30000);
		}
	}

	/** Disconnects before a death when there is no totem left to save the player. */
	public static final class AutoLogConfig {
		/** Health (including absorption) at or below which the client leaves. */
		public double healthThreshold = 6.0;
		/** Only leave when no totem of undying is left in the inventory. */
		public boolean requireNoTotem = true;
		/** Never trigger in singleplayer. */
		public boolean onlyMultiplayer = true;
		public int checkIntervalTicks = 5;

		void normalize() {
			healthThreshold = clamp(healthThreshold, 1, 20);
			checkIntervalTicks = clamp(checkIntervalTicks, 1, 40);
		}
	}

	/** Unified walk / elytra travel. Replaces the old autoPath + elytraFly pair. */
	public static final class TravelConfig {
		/** auto | walk | elytra (elytra uses direct flight). */
		public String mode = "auto";
		/** Glide-only flight: steer toward the goal without using fireworks. */
		public boolean glide = false;
		/**
		 * 1.0.1: travel no longer stops on movement/attack input. The dedicated
		 * stop key (default V) or panic (P) is the intended way out; this flag is
		 * kept for players who prefer the old behaviour.
		 */
		public boolean cancelOnInput = false;
		/** Also false by default: opening a screen no longer cancels travel. */
		public boolean cancelOnScreen = false;
		/** 0 = no limit. */
		public double maxDistance = 0.0;
		/** When true, "goto player" only accepts local teammates. */
		public boolean gotoTeammatesOnly = false;
		/** Minimum Y allowed when starting an elytra flight. */
		public double minY = 64.0;
		/** Distance at which a travel goal counts as reached. */
		public int arriveRadius = 3;

		/*
		 * Elytra assists. Both are OFF by default and are the player's own choice:
		 * they change only look angles or send the very same inventory-click /
		 * start-fall-flying packets a human would send. The server still runs all
		 * the physics and durability bookkeeping. See ElytraOptionsScreen.
		 */

		/** Periodic pitch cycling so a glide does not lose height (no fireworks). */
		public boolean glideClimb = false;
		/** Minimum clearance above the ground required before a climb cycle. */
		public int glideClimbClearance = 80;
		/** How many blocks each cycle is allowed to dive before pulling up. */
		public int glideClimbDiveBlocks = 70;

		/** Swap the chest slot away just before the 1s durability tick, then back. */
		public boolean elytraSwap = false;
		/** Fire the swap this many ticks early to absorb latency/tick misalignment. */
		public int elytraSwapLeadTicks = 2;
		/** 0 = always; otherwise only run when the elytra has less than this much durability left. */
		public int elytraSwapDurabilityLimit = 0;
		/** 0 = unlimited; otherwise stop swapping after this many cycles in one run. */
		public int elytraSwapMaxSwaps = 0;
		/** Minimum clearance above the ground required before swapping. */
		public int elytraSwapClearance = 24;
		/**
		 * 1.0.3: a swap drops the glide flag for a couple of ticks. Default true:
		 * the travel module now re-issues the Baritone goal automatically, so the
		 * route continues instead of stopping. Set false to never swap in flight.
		 */
		public boolean elytraSwapDuringTravel = true;

		void normalize() {
			if (mode == null || !mode.matches("auto|walk|elytra")) {
				mode = "auto";
			}
			maxDistance = clamp(maxDistance, 0, 1000000);
			minY = clamp(minY, -64, 320);
			arriveRadius = clamp(arriveRadius, 1, 32);
			glideClimbClearance = clamp(glideClimbClearance, 16, 256);
			glideClimbDiveBlocks = clamp(glideClimbDiveBlocks, 20, 200);
			elytraSwapLeadTicks = clamp(elytraSwapLeadTicks, 0, 10);
			elytraSwapDurabilityLimit = clamp(elytraSwapDurabilityLimit, 0, 500);
			elytraSwapMaxSwaps = clamp(elytraSwapMaxSwaps, 0, 200);
			elytraSwapClearance = clamp(elytraSwapClearance, 8, 128);
		}
	}

	/** Xaero's Minimap / XaeroPlus waypoint integration (optional, file based). */
	public static final class XaeroConfig {
		public boolean enabled = true;
		/** Auto mark End cities and elytra frames while in the End. */
		public boolean autoMarkEnd = true;
		/** Waypoint set ("目录") created inside the Xaero world folder. */
		public String setName = "chaoshelper";
		/** Allow writing markers into Xaero's files. */
		public boolean allowWrite = true;

		void normalize() {
			if (setName == null || setName.isBlank()) {
				setName = "chaoshelper";
			}
			setName = setName.replaceAll("[^a-zA-Z0-9_-]", "");
			if (setName.isEmpty()) {
				setName = "chaoshelper";
			}
		}
	}

	public static final class BaritoneConfig {
		public boolean enabled = true;
		public int statusRefreshTicks = 40;

		void normalize() {
			statusRefreshTicks = clamp(statusRefreshTicks, 5, 600);
		}
	}

	public static final class DynamicConfig {
		public boolean enabled = true;
		public int maxModules = 16;

		void normalize() {
			maxModules = clamp(maxModules, 1, 64);
		}
	}
}
