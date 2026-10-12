package net.minecraft.client.mods;

import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

/**
 * Global state for the built-in client mods (toggled from the ClickGUI, Right Shift).
 */
public final class Mods {

	public static boolean fullbright = false;
	public static boolean noParticles = false;
	public static boolean noHurtCam = false;
	public static boolean freecam = false;

	/** TuffX+ ViaBlocks: show blocks newer than 1.14 (takes effect on the next server join). */
	public static boolean viaBlocks = true;
	/** TuffX+ Below Y0: world below y=0 (takes effect on the next server join). */
	public static boolean belowY0 = true;

	// freecam state
	public static boolean fcInit = false;
	public static double fcX, fcY, fcZ;
	public static float fcYaw, fcPitch;
	private static long fcLastNanos = 0L;

	public static void setFreecam(boolean on) {
		freecam = on;
		fcInit = false;
		fcLastNanos = 0L;
	}

	/** Called by ActiveRenderInfo the first frame after freecam is enabled. */
	public static void initFreecam(double x, double y, double z, float yaw, float pitch) {
		fcX = x;
		fcY = y;
		fcZ = z;
		fcYaw = yaw;
		fcPitch = pitch;
		fcInit = true;
		fcLastNanos = System.nanoTime();
	}

	/** Mouse look while freecam is active (mirrors Entity.rotateTowards). */
	public static void rotateFreecam(double yawDelta, double pitchDelta) {
		fcYaw = (float) ((double) fcYaw + yawDelta * 0.15D);
		fcPitch = MathHelper.clamp((float) ((double) fcPitch + pitchDelta * 0.15D), -90.0F, 90.0F);
	}

	/** Moves the free camera using the normal movement keybinds. Called once per rendered frame. */
	public static void stepFreecam() {
		long now = System.nanoTime();
		float dt = (now - fcLastNanos) / 1.0E9F;
		fcLastNanos = now;
		if (dt > 0.1F) {
			dt = 0.1F;
		}
		if (dt <= 0.0F) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.currentScreen != null) {
			return;
		}
		GameSettings gs = mc.gameSettings;
		float fwd = (gs.keyBindForward.isKeyDown() ? 1.0F : 0.0F) - (gs.keyBindBack.isKeyDown() ? 1.0F : 0.0F);
		float side = (gs.keyBindRight.isKeyDown() ? 1.0F : 0.0F) - (gs.keyBindLeft.isKeyDown() ? 1.0F : 0.0F);
		float vert = (gs.keyBindJump.isKeyDown() ? 1.0F : 0.0F) - (gs.keyBindSneak.isKeyDown() ? 1.0F : 0.0F);
		if (fwd == 0.0F && side == 0.0F && vert == 0.0F) {
			return;
		}
		float speed = (gs.keyBindSprint.isKeyDown() ? 40.0F : 15.0F) * dt;
		double yawRad = (double) fcYaw * (Math.PI / 180.0D);
		double pitchRad = (double) fcPitch * (Math.PI / 180.0D);
		double cp = Math.cos(pitchRad);
		// look vector (Minecraft yaw 0 = +Z, positive pitch = looking down)
		double lx = -Math.sin(yawRad) * cp;
		double ly = -Math.sin(pitchRad);
		double lz = Math.cos(yawRad) * cp;
		// right vector
		double rx = -Math.cos(yawRad);
		double rz = -Math.sin(yawRad);
		fcX += (lx * fwd + rx * side) * speed;
		fcY += (ly * fwd + vert) * speed;
		fcZ += (lz * fwd + rz * side) * speed;
	}

	private Mods() {
	}
}
