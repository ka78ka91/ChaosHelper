package com.chaoshelper.chams;

import com.chaoshelper.ChaosHelperClient;
import com.chaoshelper.config.ChaosConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;

/**
 * Shared, allocation-light hooks for the Chams mixin. Only client render state
 * is modified; gameplay, packets and server state are untouched.
 *
 * <p>The entity type and (for players) the display name are the only inputs, so
 * the hook can run for every living entity without a world lookup.
 */
public final class ChamsController {
	private ChamsController() {
	}

	public static int colorFor(LivingEntityRenderState state) {
		if (state == null || state.entityType == null) {
			return 0;
		}
		return colorFor(state.entityType, state.squaredDistanceToCamera, displayNameOf(state));
	}

	public static int colorFor(EntityType<?> type, double squaredDistance) {
		return colorFor(type, squaredDistance, "");
	}

	public static int colorFor(EntityType<?> type, double squaredDistance, String playerName) {
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || !mod.moduleManager().isEnabled("chams")) {
			return 0;
		}
		ChaosConfig.ChamsConfig config = mod.config().chams;
		if (squaredDistance > config.range * config.range) {
			return 0;
		}
		if (type == EntityType.PLAYER && !config.applyPlayers) {
			return 0;
		}
		SpawnGroup group = type.getSpawnGroup();
		if (group == SpawnGroup.MONSTER && !config.applyHostile) {
			return 0;
		}
		if (group != SpawnGroup.MONSTER && type != EntityType.PLAYER && !config.applyPassive) {
			return 0;
		}
		int base = config.color;
		if (type == EntityType.PLAYER && playerName != null && !playerName.isBlank()) {
			if (mod.targetLink().isSet() && playerName.equalsIgnoreCase(mod.targetLink().name())) {
				base = config.colorTarget;
			} else if (mod.teamManager().isTeammate(playerName, "")) {
				base = config.colorTeammate;
			}
		}
		return withOpacity(base, config.opacity);
	}

	public static void applyOutline(LivingEntity entity, LivingEntityRenderState state) {
		if (entity == null || state == null) {
			return;
		}
		ChaosHelperClient mod = ChaosHelperClient.get();
		if (mod == null || !mod.moduleManager().isEnabled("chams") || !mod.config().chams.throughWalls) {
			return;
		}
		MinecraftClient client = mod.client();
		if (client.player == null || entity == client.player) {
			return;
		}
		int color = colorFor(entity.getType(), entity.squaredDistanceTo(client.player), entity.getName().getString());
		if (color != 0) {
			state.outlineColor = color;
		}
	}

	private static String displayNameOf(LivingEntityRenderState state) {
		if (state.entityType != EntityType.PLAYER || state.displayName == null) {
			return "";
		}
		return state.displayName.getString();
	}

	private static int withOpacity(int argb, int opacity) {
		int alpha = Math.max(0, Math.min(255, opacity));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
