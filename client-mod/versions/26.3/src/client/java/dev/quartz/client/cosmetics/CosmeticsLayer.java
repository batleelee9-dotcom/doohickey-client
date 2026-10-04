package dev.quartz.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.fx.RiceHat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;

/** Draws the rice hat on players, and the selected hat, bandana and wings on your own. */
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final int FULL_BRIGHT = 15728880;

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (state.isInvisible || mc.player == null) {
			return;
		}
		boolean you = state.id == mc.player.getId();
		PlayerModel model = this.getParentModel();
		boolean headFree = state.headEquipment.isEmpty() && state.headItem.isEmpty() && state.wornHeadType == null;

		// The rice hat goes on whoever the setting says; over a helmet it sits higher.
		boolean riceHat = RiceHat.enabled() && RiceHat.shows(you);
		if (riceHat) {
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.head.translateAndRotate(poseStack);
			collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), headFree ? CosmeticsLayer::riceHat : CosmeticsLayer::riceHatLifted);
			poseStack.popPose();
		}

		// Everything else is yours only.
		if (!you) {
			return;
		}
		ClientConfig c = ClientConfig.get();
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);

		// Helmets, skulls and pumpkins win over hats — no clipping through them.
		// The rice hat takes the place of any other hat.
		String hat = riceHat ? null : Cosmetics.selected(Cosmetics.HATS, c.hat);
		String bandana = Cosmetics.selected(Cosmetics.BANDANAS, c.bandana);
		if (headFree && (hat != null || bandana != null)) {
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.head.translateAndRotate(poseStack);
			if (bandana != null) {
				submit(collector, CosmeticModels.BANDANA, poseStack, RenderTypes.entityCutoutCull(Cosmetics.texture("bandana", bandana)), light, overlay, state);
			}
			if (hat != null) {
				switch (hat) {
					case "tophat" -> submit(collector, CosmeticModels.TOP_HAT, poseStack, RenderTypes.entityCutoutCull(Cosmetics.texture("hat", hat)), light, overlay, state);
					case "crown" -> submit(collector, CosmeticModels.CROWN, poseStack, RenderTypes.entityCutoutCull(Cosmetics.texture("hat", hat)), light, overlay, state);
					case "halo" -> {
						// Floats and glows: gently bobbing, lit regardless of darkness.
						poseStack.translate(0, Mth.sin(state.ageInTicks * 0.1f) * 0.5f / 16f, 0);
						submit(collector, CosmeticModels.HALO, poseStack, RenderTypes.entityTranslucentEmissive(Cosmetics.texture("hat", hat)), FULL_BRIGHT, overlay, state);
					}
					default -> {
					}
				}
			}
			poseStack.popPose();
		}

		String wings = Cosmetics.selected(Cosmetics.WINGS, c.wings);
		// An elytra already puts wings on your back.
		if (wings != null && !state.chestEquipment.has(DataComponents.GLIDER)) {
			RenderType type = RenderTypes.entityCutoutCull(Cosmetics.texture("wings", wings));
			// Folded back at rest, beating faster while moving and wide open in flight.
			float speed = state.isFallFlying ? 0.6f : 0.1f + state.walkAnimationSpeed * 0.25f;
			float beat = Mth.sin(state.ageInTicks * speed) * (state.isFallFlying ? 0.3f : 0.07f + state.walkAnimationSpeed * 0.12f);
			float fold = (state.isFallFlying ? 0.25f : state.isCrouching ? 1.1f : 0.85f) + beat;
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.body.translateAndRotate(poseStack);
			for (int side : new int[] {1, -1}) {
				poseStack.pushPose();
				poseStack.translate(side * 1.5f / 16f, 2f / 16f, 2.5f / 16f);
				poseStack.rotate(Axis.YP, -side * fold);
				poseStack.rotate(Axis.ZP, -side * 0.18f);
				submit(collector, side == 1 ? CosmeticModels.RIGHT_WING : CosmeticModels.LEFT_WING, poseStack, type, light, overlay, state);
				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}

	private static void riceHat(PoseStack.Pose pose, VertexConsumer consumer) {
		riceHat(pose, consumer, 0f);
	}

	private static void riceHatLifted(PoseStack.Pose pose, VertexConsumer consumer) {
		riceHat(pose, consumer, RiceHat.HELMET_LIFT);
	}

	/** The hat, as quads (each triangle's last corner doubled), in blocks from the head pivot. */
	private static void riceHat(PoseStack.Pose pose, VertexConsumer consumer, float lift) {
		int[] corner = {0};
		RiceHat.build((x, y, z, argb) -> {
			consumer.addVertex(pose, x / 16f, y / 16f, z / 16f).setColor(argb);
			if (++corner[0] % 3 == 0) {
				consumer.addVertex(pose, x / 16f, y / 16f, z / 16f).setColor(argb);
			}
		}, (System.currentTimeMillis() % 1_000_000L) / 1000f, lift);
	}

	private static void submit(SubmitNodeCollector collector, ModelPart part, PoseStack poseStack, RenderType type, int light, int overlay, AvatarRenderState state) {
		collector.submitModelPart(part, poseStack, type, light, overlay, null, -1, state.outlineColor);
	}
}
