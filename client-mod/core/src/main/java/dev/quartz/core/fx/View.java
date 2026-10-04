package dev.quartz.core.fx;

/**
 * This frame's camera, as the game drew the world: a view-projection matrix
 * (column-major, applied to positions relative to {@link #camX}…) and the
 * camera position. Each version sets it once a frame; the client uses it to
 * place its own particles over the world and to skip off-screen work.
 */
public final class View {
	private static final float[] M = new float[16];
	private static double camX;
	private static double camY;
	private static double camZ;
	/** Screen pixels per block at clip depth 1, per axis (differs when the view is stretched). */
	private static float scaleX;
	private static float scaleY;
	private static boolean valid;

	private View() {
	}

	public static void set(float[] viewProjection, double x, double y, double z) {
		System.arraycopy(viewProjection, 0, M, 0, 16);
		camX = x;
		camY = y;
		camZ = z;
		// The x and y rows of a projection × rotation have the projection's scale as their length.
		scaleX = (float) Math.sqrt(M[0] * M[0] + M[4] * M[4] + M[8] * M[8]);
		scaleY = (float) Math.sqrt(M[1] * M[1] + M[5] * M[5] + M[9] * M[9]);
		valid = true;
	}

	public static void clear() {
		valid = false;
	}

	public static boolean valid() {
		return valid;
	}

	/**
	 * Projects a world position onto a {@code width}×{@code height} screen.
	 * Fills {@code out} with x, y and the screen size of one block (x and y)
	 * at that depth; false when it's behind the camera or far off-screen.
	 */
	public static boolean project(double x, double y, double z, float width, float height, float[] out) {
		if (!valid) {
			return false;
		}
		float rx = (float) (x - camX);
		float ry = (float) (y - camY);
		float rz = (float) (z - camZ);
		float w = M[3] * rx + M[7] * ry + M[11] * rz + M[15];
		if (w < 0.05f) {
			return false;
		}
		float nx = (M[0] * rx + M[4] * ry + M[8] * rz + M[12]) / w;
		float ny = (M[1] * rx + M[5] * ry + M[9] * rz + M[13]) / w;
		if (nx < -1.3f || nx > 1.3f || ny < -1.3f || ny > 1.3f) {
			return false;
		}
		out[0] = (nx * 0.5f + 0.5f) * width;
		out[1] = (0.5f - ny * 0.5f) * height;
		out[2] = scaleX * width * 0.5f / w;
		out[3] = scaleY * height * 0.5f / w;
		return true;
	}

	/** Whether a sphere of {@code radius} blocks is at least partly in view (true when unknown). */
	public static boolean visible(double x, double y, double z, float radius) {
		if (!valid) {
			return true;
		}
		float rx = (float) (x - camX);
		float ry = (float) (y - camY);
		float rz = (float) (z - camZ);
		float w = M[3] * rx + M[7] * ry + M[11] * rz + M[15];
		// In clip space a sphere spans about radius × scale around its centre.
		float cx = M[0] * rx + M[4] * ry + M[8] * rz + M[12];
		float cy = M[1] * rx + M[5] * ry + M[9] * rz + M[13];
		float mx = radius * scaleX;
		float my = radius * scaleY;
		return w > -radius && cx >= -w - mx && cx <= w + mx && cy >= -w - my && cy <= w + my;
	}
}
