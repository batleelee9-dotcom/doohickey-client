package dev.quartz.legacy.mixin.extra;

import com.mojang.authlib.GameProfile;
import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mouse delay fix: in 1.8 your look direction comes from the head's yaw,
 * which trails your actual turn by a tick, so a flick can land just off the
 * crosshair. Use the camera's own yaw, like later versions do.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class MouseDelayFixMixin extends AbstractClientPlayerEntity {
	private MouseDelayFixMixin(World world, GameProfile profile) {
		super(world, profile);
	}

	@Override
	public Vec3d getRotationVector(float tickDelta) {
		if (!ClientConfig.get().mouseDelayFix || !Quartz.available(Feature.MOUSE_DELAY_FIX)) {
			return super.getRotationVector(tickDelta);
		}
		float pitch = this.prevPitch + (this.pitch - this.prevPitch) * tickDelta;
		float yaw = this.prevYaw + (this.yaw - this.prevYaw) * tickDelta;
		return this.getRotationVector(pitch, yaw);
	}
}
