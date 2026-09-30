package dev.quartz.client.cosmetics;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Cosmetic geometry, in model pixels. Head pieces are relative to the head
 * pivot (the neck; the head spans y -8..0, its outer skin layer reaches
 * ±4.5), wings to their attachment point on the upper back.
 *
 * Texture offsets and box sizes must match tools/make-textures.mjs.
 */
final class CosmeticModels {
	static final ModelPart TOP_HAT = bake(root -> {
		root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -9.5f, -6, 12, 1, 12), PartPose.ZERO);
		root.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 13).addBox(-4, -16.5f, -4, 8, 7, 8), PartPose.ZERO);
	});

	static final ModelPart CROWN = bake(root -> {
		CubeListBuilder gold = CubeListBuilder.create().texOffs(0, 0)
			// The band, just outside the skin's outer layer.
			.addBox(-4.75f, -10.5f, -4.75f, 9.5f, 2.5f, 0.75f)
			.addBox(-4.75f, -10.5f, 4.0f, 9.5f, 2.5f, 0.75f)
			.addBox(-4.75f, -10.5f, -4.0f, 0.75f, 2.5f, 8.0f)
			.addBox(4.0f, -10.5f, -4.0f, 0.75f, 2.5f, 8.0f);
		// Points at the corners and the middle of each side.
		for (float x : new float[] {-4.75f, -0.5f, 3.75f}) {
			gold.addBox(x, -12, -4.75f, 1, 1.5f, 0.75f);
			gold.addBox(x, -12, 4.0f, 1, 1.5f, 0.75f);
		}
		gold.addBox(-4.75f, -12, -0.5f, 0.75f, 1.5f, 1);
		gold.addBox(4.0f, -12, -0.5f, 0.75f, 1.5f, 1);
		root.addOrReplaceChild("gold", gold, PartPose.ZERO);
		root.addOrReplaceChild("ruby", CubeListBuilder.create().texOffs(56, 24).addBox(-0.75f, -9.75f, -5.0f, 1.5f, 1.25f, 0.25f), PartPose.ZERO);
		root.addOrReplaceChild("sapphires", CubeListBuilder.create().texOffs(48, 24)
			.addBox(-5.0f, -9.75f, -0.75f, 0.25f, 1.25f, 1.5f)
			.addBox(4.75f, -9.75f, -0.75f, 0.25f, 1.25f, 1.5f), PartPose.ZERO);
	});

	static final ModelPart HALO = bake(root -> root.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 0)
		.addBox(-3.5f, -13, -3.5f, 7, 1, 1)
		.addBox(-3.5f, -13, 2.5f, 7, 1, 1)
		.addBox(-3.5f, -13, -2.5f, 1, 1, 5)
		.addBox(2.5f, -13, -2.5f, 1, 1, 5), PartPose.ZERO));

	static final ModelPart BANDANA = bake(root -> {
		root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -7, -4, 8, 2, 8, new CubeDeformation(0.75f)), PartPose.ZERO);
		root.addOrReplaceChild("knot", CubeListBuilder.create().texOffs(0, 10).addBox(-1, -7, 4.75f, 2, 2, 1), PartPose.ZERO);
		CubeListBuilder tail = CubeListBuilder.create().texOffs(8, 10).addBox(-0.5f, 0, 0, 1, 3, 0.5f);
		root.addOrReplaceChild("tail_left", tail, PartPose.offsetAndRotation(-0.6f, -5.5f, 5.3f, 0.3f, 0, 0.2f));
		root.addOrReplaceChild("tail_right", tail, PartPose.offsetAndRotation(0.6f, -5.5f, 5.3f, 0.3f, 0, -0.2f));
	});

	// Flat planes: only the front and back faces exist. The left wing mirrors
	// the texture so both wings share one design, root at column 0.
	private static final Set<Direction> FLAT = Set.of(Direction.NORTH, Direction.SOUTH);
	static final ModelPart RIGHT_WING = bake(root ->
		root.addOrReplaceChild("wing", CubeListBuilder.create().texOffs(0, 0).addBox(0, -6, 0, 14, 20, 0, FLAT), PartPose.ZERO));
	static final ModelPart LEFT_WING = bake(root ->
		root.addOrReplaceChild("wing", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-14, -6, 0, 14, 20, 0, FLAT), PartPose.ZERO));

	private CosmeticModels() {
	}

	private static ModelPart bake(Consumer<PartDefinition> parts) {
		MeshDefinition mesh = new MeshDefinition();
		parts.accept(mesh.getRoot());
		return LayerDefinition.create(mesh, 64, 32).bakeRoot();
	}
}
