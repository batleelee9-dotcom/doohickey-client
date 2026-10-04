package dev.quartz.legacy.mixin.extra;

import dev.quartz.legacy.RiceHatFeature;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the rice hat layer to the player renderer. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity> {
	private PlayerRendererMixin(EntityRenderDispatcher dispatcher, EntityModel model, float shadow) {
		super(dispatcher, model, shadow);
	}

	@Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("RETURN"))
	private void quartz$riceHat(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
		this.addFeature(new RiceHatFeature((PlayerEntityRenderer) (Object) this));
	}
}
