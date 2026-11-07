package com.chaoshelper.target;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.api.DropData;
import com.chaoshelper.api.EntityData;
import com.chaoshelper.api.PlayerData;
import com.chaoshelper.data.RecordEntry;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Turns a user-supplied goto argument into a concrete block position.
 *
 * <p>Every source is client-visible data only: the shared entity cache, the
 * player tracker, or a locally stored record. Cross-dimension targets are
 * rejected with a friendly message instead of pathing into nothing.
 */
public final class TargetResolver {
	public record Target(String label, int x, int y, int z, boolean approximate) {
	}

	public record Result(Target target, String error) {
		public boolean ok() {
			return target != null;
		}

		static Result fail(String message) {
			return new Result(null, message);
		}

		static Result of(Target target) {
			return new Result(target, null);
		}
	}

	private TargetResolver() {
	}

	public static Result item(String uuid) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		EntityData entity = mod.entityCache().byUuid(uuid);
		if (entity == null) {
			DropData drop = findDrop(uuid);
			if (drop == null) {
				return Result.fail("该掉落物不在客户端缓存中（可能已消失或超出范围）");
			}
			return Result.of(new Target(drop.itemName(),
					floor(drop.x()), floor(drop.y()), floor(drop.z()), !drop.visible()));
		}
		return Result.of(new Target(entity.displayName(),
				floor(entity.x()), floor(entity.y()), floor(entity.z()), false));
	}

	public static Result player(String name) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (name == null || name.isBlank()) {
			return Result.fail("缺少玩家名");
		}
		PlayerData found = null;
		for (PlayerData player : mod.playerTracker().players()) {
			if (player.name().equalsIgnoreCase(name)) {
				found = player;
				break;
			}
		}
		if (found == null) {
			return Result.fail("玩家 " + name + " 不在追踪列表中");
		}
		if (mod.config().travel.gotoTeammatesOnly
				&& !mod.teamManager().isTeammate(found.name(), found.uuid())) {
			return Result.fail("当前配置只允许 goto 队友 (autoPath.gotoTeammatesOnly=true)");
		}
		if (found.distance() > mod.config().travel.maxDistance) {
			return Result.fail(String.format(Locale.ROOT, "目标距离 %.0f 超过上限 %.0f",
					found.distance(), mod.config().travel.maxDistance));
		}
		return Result.of(new Target(found.name(), floor(found.x()), floor(found.y()), floor(found.z()),
				!found.visible()));
	}

	public static Result death() {
		RecordEntry death = ChaosHelperClient.get().recordManager().lastDeath();
		if (death == null) {
			return Result.fail("没有死亡记录");
		}
		String error = dimensionError(death);
		if (error != null) {
			return Result.fail(error);
		}
		return Result.of(new Target("死亡点", floor(death.x), floor(death.y), floor(death.z), false));
	}

	public static Result log(String name) {
		RecordEntry entry = ChaosHelperClient.get().recordManager().lastLogFor(name);
		if (entry == null) {
			return Result.fail("没有 " + name + " 的离开记录");
		}
		String error = dimensionError(entry);
		if (error != null) {
			return Result.fail(error);
		}
		return Result.of(new Target(name + " 最后位置", floor(entry.x), floor(entry.y), floor(entry.z), false));
	}

	/** Navigates to a waypoint stored in the current Xaero dimension folder. */
	public static Result waypoint(String name) {
		var waypoint = com.chaoshelper.minimap.XaeroWaypoints.find(name);
		if (waypoint == null) {
			return Result.fail("当前维度没有名为 " + name + " 的小地图路点");
		}
		return Result.of(new Target(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), false));
	}

	private static String dimensionError(RecordEntry entry) {
		MinecraftClient client = ChaosHelperClient.get().client();
		if (client.world == null) {
			return "当前不在世界中";
		}
		String current = client.world.getRegistryKey().getValue().toString();
		if (!entry.world.isBlank() && !entry.world.equals(current)) {
			return "目标在 " + entry.world + "，当前维度 " + current + "，请先前往同一维度";
		}
		return null;
	}

	private static DropData findDrop(String uuid) {
		for (DropData drop : ChaosHelperClient.get().dropTracker().drops()) {
			if (drop.uuid().equalsIgnoreCase(uuid)) {
				return drop;
			}
		}
		return null;
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
