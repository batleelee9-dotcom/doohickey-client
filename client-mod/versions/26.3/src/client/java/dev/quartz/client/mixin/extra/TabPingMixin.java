package dev.quartz.client.mixin.extra;

import dev.quartz.core.hud.TabPing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab ping: the number, coloured, in place of the signal bars. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabPingMixin {
	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void quartz$ping(GuiGraphicsExtractor g, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		if (!TabPing.enabled()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int ping = info.getLatency();
		String label = TabPing.label(ping);
		float scale = 0.75F;
		g.pose().pushMatrix();
		g.pose().translate(x + width - 1 - font.width(label) * scale, y + 1.5F);
		g.pose().scale(scale, scale);
		g.text(font, label, 0, 0, TabPing.colour(ping), true);
		g.pose().popMatrix();
		ci.cancel();
	}
}
