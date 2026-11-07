package com.chaoshelper.hud;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.util.TimeUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Template expansion for custom HUD widgets.
 *
 * <p>Syntax is intentionally tiny: {@code {name}} is replaced by a value and
 * unknown names are left untouched so a typo is visible instead of silently
 * rendering nothing. List widgets add their own per-row keys on top of these.
 */
public final class HudVariables {
	private HudVariables() {
	}

	/** Values that are the same for every widget; rebuilt once per frame. */
	public static Map<String, String> global(ChaosHelperClient mod, MinecraftClient client) {
		Map<String, String> vars = new HashMap<>();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return vars;
		}
		Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
		Vec3d velocity = player.getVelocity();
		vars.put("x", dec(pos.x, 1));
		vars.put("y", dec(pos.y, 1));
		vars.put("z", dec(pos.z, 1));
		vars.put("xi", Integer.toString(player.getBlockX()));
		vars.put("yi", Integer.toString(player.getBlockY()));
		vars.put("zi", Integer.toString(player.getBlockZ()));
		vars.put("chunk", (player.getBlockX() >> 4) + ", " + (player.getBlockZ() >> 4));
		vars.put("dim", dimensionName(client));
		vars.put("biome", biomeName(client, player.getBlockPos()));
		vars.put("yaw", Integer.toString(Math.round(player.getYaw())));
		vars.put("pitch", Integer.toString(Math.round(player.getPitch())));
		vars.put("facing", facing(player.getYaw()));
		vars.put("hp", dec(player.getHealth(), 1));
		vars.put("maxhp", dec(player.getMaxHealth(), 1));
		vars.put("absorption", dec(player.getAbsorptionAmount(), 1));
		vars.put("armor", Integer.toString(player.getArmor()));
		vars.put("food", Integer.toString(player.getHungerManager().getFoodLevel()));
		vars.put("air", Integer.toString(player.getAir()));
		vars.put("exp", Integer.toString(player.experienceLevel));
		vars.put("speed", dec(Math.hypot(velocity.x, velocity.z) * 20.0, 2));
		vars.put("vspeed", dec(velocity.y * 20.0, 2));
		vars.put("onground", player.isOnGround() ? "是" : "否");
		vars.put("gliding", player.isGliding() ? "是" : "否");
		vars.put("sprinting", player.isSprinting() ? "是" : "否");
		vars.put("sneaking", player.isSneaking() ? "是" : "否");
		vars.put("light", Integer.toString(client.world.getLightLevel(player.getBlockPos())));
		vars.put("fps", Integer.toString(client.getCurrentFps()));
		vars.put("ping", Integer.toString(ping(client, player)));
		Runtime runtime = Runtime.getRuntime();
		vars.put("mem_used", Long.toString((runtime.totalMemory() - runtime.freeMemory()) / 1048576L));
		vars.put("mem_max", Long.toString(runtime.maxMemory() / 1048576L));
		vars.put("time", gameTime(client));
		vars.put("real_time", TimeUtil.clock(System.currentTimeMillis()));
		vars.put("modules", Integer.toString(mod.moduleManager().enabledCount()));
		vars.put("players", Integer.toString(mod.playerTracker().players().size()));
		vars.put("drops", Integer.toString(mod.dropTracker().drops().size()));
		vars.put("target", mod.targetLink().isSet() ? mod.targetLink().name() : "-");
		vars.put("baritone", mod.baritoneBridge().statusLine());
		vars.put("mainhand", itemName(player.getMainHandStack()));
		vars.put("offhand", itemName(player.getOffHandStack()));
		vars.put("helmet", itemName(player.getEquippedStack(EquipmentSlot.HEAD)));
		vars.put("chest", itemName(player.getEquippedStack(EquipmentSlot.CHEST)));
		vars.put("legs", itemName(player.getEquippedStack(EquipmentSlot.LEGS)));
		vars.put("boots", itemName(player.getEquippedStack(EquipmentSlot.FEET)));
		return vars;
	}

	/** Replaces {@code {key}} with values; unknown keys are kept verbatim. */
	public static String apply(String template, Map<String, String> vars) {
		if (template == null || template.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(template.length() + 16);
		int index = 0;
		while (index < template.length()) {
			char current = template.charAt(index);
			if (current == '{') {
				int end = template.indexOf('}', index + 1);
				if (end > index) {
					String key = template.substring(index + 1, end);
					String value = vars.get(key);
					if (value != null) {
						out.append(value);
						index = end + 1;
						continue;
					}
				}
			}
			out.append(current);
			index++;
		}
		return out.toString();
	}

	/** Splits on real newlines and on a literal {@code \n} typed into a text field. */
	public static String[] splitLines(String text) {
		if (text == null || text.isEmpty()) {
			return new String[0];
		}
		return text.replace("\\n", "\n").split("\n", -1);
	}

	public static String itemName(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "-" : stack.getName().getString();
	}

	private static int ping(MinecraftClient client, ClientPlayerEntity player) {
		if (client.getNetworkHandler() == null) {
			return 0;
		}
		var entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
		return entry == null ? 0 : entry.getLatency();
	}

	private static String dimensionName(MinecraftClient client) {
		String path = client.world.getRegistryKey().getValue().getPath();
		return switch (path) {
			case "overworld" -> "主世界";
			case "the_nether" -> "下界";
			case "the_end" -> "末地";
			default -> path;
		};
	}

	private static String biomeName(MinecraftClient client, BlockPos pos) {
		return client.world.getBiome(pos).getKey()
				.map(key -> key.getValue().getPath().replace('_', ' '))
				.orElse("-");
	}

	private static String gameTime(MinecraftClient client) {
		long dayTime = client.world.getTimeOfDay() % 24000L;
		long hours = (dayTime / 1000L + 6L) % 24L;
		long minutes = (dayTime % 1000L) * 60L / 1000L;
		return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
	}

	private static String facing(float yaw) {
		String[] names = {"南", "西南", "西", "西北", "北", "东北", "东", "东南"};
		int index = Math.floorMod(Math.round(yaw / 45.0F), 8);
		return names[index];
	}

	private static String dec(double value, int decimals) {
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}
}
