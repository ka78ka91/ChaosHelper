package com.chaoshelper.mixin;

import com.chaoshelper.chams.ChamsController;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only visual hooks. No movement, packet, or server state is modified.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
			method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN")
	)
	private void chaoshelper$applyChamsOutline(LivingEntity entity, LivingEntityRenderState state, float tickDelta,
			CallbackInfo callbackInfo) {
		ChamsController.applyOutline(entity, state);
	}

	@Inject(method = "getMixColor", at = @At("RETURN"), cancellable = true)
	private void chaoshelper$applyChamsTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> callbackInfo) {
		int color = ChamsController.colorFor(state);
		if (color != 0) {
			callbackInfo.setReturnValue(color);
		}
	}
}
