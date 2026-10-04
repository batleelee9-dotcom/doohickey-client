package dev.quartz.legacy.mixin.extra;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.hud.TabPing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab ping: the number, coloured, in place of the signal bars. */
@Mixin(PlayerListHud.class)
public abstract class TabPingMixin {
	@Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
	private void quartz$ping(int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
		if (!TabPing.enabled()) {
			return;
		}
		TextRenderer text = MinecraftClient.getInstance().textRenderer;
		int ping = entry.getLatency();
		String label = TabPing.label(ping);
		float scale = 0.75F;
		GlStateManager.pushMatrix();
		GlStateManager.translate(x + width - 1 - text.getStringWidth(label) * scale, y + 1.5F, 0.0F);
		GlStateManager.scale(scale, scale, 1.0F);
		text.drawWithShadow(label, 0, 0, TabPing.colour(ping));
		GlStateManager.popMatrix();
		ci.cancel();
	}
}
