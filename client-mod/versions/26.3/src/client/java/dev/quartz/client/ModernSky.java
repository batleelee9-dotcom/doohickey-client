package dev.quartz.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.quartz.client.adapter.PipelineBackend;
import dev.quartz.core.fx.Atmosphere;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix4f;

/**
 * Draws the chosen Atmosphere sky in the overworld sky pass, the way vanilla
 * draws the End's sky box: one textured cube (the painted atlas) through the
 * End sky pipeline, turned slowly if the sky drifts.
 */
public final class ModernSky {
	private static GpuBuffer cube;
	/** Whether this frame's sky was ours (so vanilla's sun, moon and stars stay off). */
	private static boolean drawn;

	private ModernSky() {
	}

	public static boolean drawn() {
		return drawn;
	}

	/** Draws into {@code pass}; false (vanilla draws) while no sky is chosen or it's still being painted. */
	public static boolean draw(RenderPass pass) {
		drawn = false;
		int[] atlas = Atmosphere.atlas();
		PipelineBackend backend = PipelineBackend.current();
		if (atlas == null || backend == null) {
			return false;
		}
		int handle = backend.image(Atmosphere.key(), Atmosphere.FACE * 3, Atmosphere.FACE * 2, () -> atlas);
		AbstractTexture texture = backend.texture(handle);
		if (cube == null) {
			cube = buildCube();
		}
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(36);
		Matrix4f view = RenderSystem.getModelViewMatrixCopy().rotateY(Atmosphere.rotation());
		pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.END_SKY));
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(view));
		pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
		pass.setVertexBuffer(0, cube.slice());
		pass.setIndexBuffer(indexBuffer, indices.type());
		pass.drawIndexed(36, 1, 0, 0, 0);
		drawn = true;
		return true;
	}

	/** Six faces, 100 blocks out, mapped onto the 3×2 atlas exactly as the painter laid it out. */
	private static GpuBuffer buildCube() {
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			float inset = 0.5f / Atmosphere.FACE;
			for (int face = 0; face < 6; face++) {
				float[] a = Atmosphere.FACES[face];
				float u0 = (face % 3 + inset) / 3f;
				float u1 = (face % 3 + 1 - inset) / 3f;
				float v0 = (face / 3 + inset) / 2f;
				float v1 = (face / 3 + 1 - inset) / 2f;
				corner(builder, a, -1, 1, u0, v0);
				corner(builder, a, 1, 1, u1, v0);
				corner(builder, a, 1, -1, u1, v1);
				corner(builder, a, -1, -1, u0, v1);
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Doohickey sky", 40, mesh.vertexBuffer());
			}
		}
	}

	private static void corner(BufferBuilder builder, float[] a, float s, float t, float u, float v) {
		float x = (a[0] + s * a[3] + t * a[6]) * 100;
		float y = (a[1] + s * a[4] + t * a[7]) * 100;
		float z = (a[2] + s * a[5] + t * a[8]) * 100;
		builder.addVertex(x, y, z).setUv(u, v).setColor(-1);
	}
}
