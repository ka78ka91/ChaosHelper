package com.chaoshelper.record;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.data.DataStore;
import com.chaoshelper.data.RecordEntry;
import com.chaoshelper.data.RecordType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Local death points and optional chat/system log entries. Deaths are inferred
 * only from the client player's visible state and last known position.
 */
public final class RecordManager {
	private final DataStore dataStore;
	private boolean deathRecorded;
	private String lastWorld = "";
	private float lastHealth = -1.0F;

	public RecordManager(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	public void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			deathRecorded = false;
			lastWorld = "";
			lastHealth = -1.0F;
			return;
		}
		if (!ChaosHelperClient.get().moduleManager().isEnabled("record")) {
			return;
		}
		String world = client.world.getRegistryKey().getValue().toString();
		if (!world.equals(lastWorld)) {
			lastWorld = world;
			deathRecorded = false;
			lastHealth = player.getHealth();
			return;
		}
		boolean dead = player.isDead() || player.getHealth() <= 0.0F || !player.isAlive();
		if (dead && !deathRecorded) {
			deathRecorded = true;
			if (!ChaosHelperClient.get().config().record.autoDeath) {
				return;
			}
			String message = "死亡点 " + format(player.getX()) + ", " + format(player.getY()) + ", " + format(player.getZ());
			dataStore.addRecord(RecordType.DEATH, world, player.getX(), player.getY(), player.getZ(), message,
					ChaosHelperClient.get().config().record.maxEntries);
			ChaosHelperClient.get().notifications().error("已记录死亡点: " + message);
		} else if (!dead && deathRecorded && player.getHealth() > 0.0F) {
			deathRecorded = false;
		}
		lastHealth = player.getHealth();
	}

	public void onGameMessage(Text message, boolean overlay) {
		if (overlay || !ChaosHelperClient.get().config().record.autoLog) {
			return;
		}
		if (!ChaosHelperClient.get().moduleManager().isEnabled("record")) {
			return;
		}
		MinecraftClient client = ChaosHelperClient.get().client();
		if (client.player == null || client.world == null || message == null) {
			return;
		}
		String text = message.getString();
		if (text == null || text.isBlank()) {
			return;
		}
		if (text.length() > 500) {
			text = text.substring(0, 500) + "...";
		}
		dataStore.addRecord(RecordType.LOG, client.world.getRegistryKey().getValue().toString(),
				client.player.getX(), client.player.getY(), client.player.getZ(), text,
				ChaosHelperClient.get().config().record.maxEntries);
	}

	public List<RecordEntry> records() {
		return List.copyOf(dataStore.records());
	}

	public RecordEntry lastDeath() {
		return dataStore.lastDeath();
	}

	public RecordEntry lastLogFor(String name) {
		return dataStore.lastRecordBySubject(name);
	}

	/**
	 * Stores the last position where a tracked player was visible. Only called
	 * when the entity actually left the client view; a local dimension change is
	 * filtered out because it is not a log-off.
	 */
	public void logoff(String name, double x, double y, double z) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (!mod.config().record.autoLogoff || name == null || name.isBlank()) {
			return;
		}
		MinecraftClient client = mod.client();
		if (client.world == null) {
			return;
		}
		String world = client.world.getRegistryKey().getValue().toString();
		if (!lastWorld.isBlank() && !lastWorld.equals(world)) {
			return;
		}
		String message = name + " 已离开视野/下线";
		dataStore.addRecord(RecordType.LOG, world, x, y, z, message, name,
				mod.config().record.maxEntries);
	}

	public void clear() {
		dataStore.records().clear();
		dataStore.saveQuietly();
	}

	private static String format(double value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
