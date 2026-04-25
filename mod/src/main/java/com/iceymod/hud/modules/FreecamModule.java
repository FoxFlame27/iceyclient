package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.DoubleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Spectator-style freecam: detached camera that flies around with WASD
 * while the player stays in place. The camera position + rotation are
 * fully independent of the player.
 *
 * - Activation: toggle via the freecam keybind (default F4).
 * - Movement:   WASD (relative to camera yaw), Space = up, Shift = down,
 *               Sprint key (Ctrl) = ~3x speed.
 * - Look:       mouse rotates the freecam (rerouted in EntityLookMixin).
 * - Player:     KeyboardInputMixin zeroes movementForward/Sideways +
 *               jumping/sneaking while freecam is active so vanilla input
 *               doesn't make the player walk away under you.
 *
 * The actual position + rotation overrides happen in CameraMixin, which
 * already wraps everything in try/catch so a 1.21.11 API drift won't
 * crash render.
 */
public class FreecamModule extends HudModule {

    private static boolean active = false;
    private static double posX, posY, posZ;
    private static float yaw, pitch;
    private static Perspective savedPerspective = null;

    // Block-per-tick base; multiplied by 20 internally to derive blocks/sec.
    public final DoubleSetting moveSpeed = addSetting(
            new DoubleSetting("moveSpeed", "Move Speed", 0.5, 0.05, 5.0, 0.05));
    public final DoubleSetting sprintMul = addSetting(
            new DoubleSetting("sprintMul", "Sprint Multiplier", 3.0, 1.0, 10.0, 0.5));

    // Anchor module instance so the static frame loop can read settings.
    private static FreecamModule INSTANCE;
    private static long lastFrameNanos = 0L;

    public FreecamModule() {
        super("freecam", "Freecam", 0, 0);
        setEnabled(true);
        INSTANCE = this;
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    public static boolean isActive() { return active; }
    // Renamed to avoid clashing with HudModule.getX()/getY() (instance methods).
    public static double camX() { return posX; }
    public static double camY() { return posY; }
    public static double camZ() { return posZ; }
    public static float camYaw() { return yaw; }
    public static float camPitch() { return pitch; }

    /** Toggle on/off. Called from the keybind. */
    public void toggle(MinecraftClient client) {
        if (active) stop(client);
        else        start(client);
    }

    public void start(MinecraftClient client) {
        if (active || client == null || client.player == null) return;
        if (!isEnabled()) return;
        active = true;
        lastFrameNanos = 0L; // Reset delta-time so the first frame doesn't jump.
        var eye = client.player.getEyePos();
        posX = eye.x;
        posY = eye.y;
        posZ = eye.z;
        yaw = client.player.getYaw();
        pitch = client.player.getPitch();

        if (client.options != null) {
            savedPerspective = client.options.getPerspective();
            if (savedPerspective == Perspective.FIRST_PERSON) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            } else {
                savedPerspective = null;
            }
        }
        if (client.player != null) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§b[Icey] §aFreecam ON §7— WASD to fly, key again to exit"),
                    true);
        }
    }

    public void stop(MinecraftClient client) {
        if (!active) return;
        active = false;
        if (savedPerspective != null && client != null && client.options != null) {
            client.options.setPerspective(savedPerspective);
        }
        savedPerspective = null;
        if (client != null && client.player != null) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§b[Icey] §7Freecam off"), true);
        }
    }

    /** Disabling the module turns freecam off immediately. */
    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && active) stop(MinecraftClient.getInstance());
        super.setEnabled(enabled);
    }

    /** Mouse-look while in freecam. Routed from EntityLookMixin. */
    public static void applyDelta(double cursorDeltaX, double cursorDeltaY) {
        yaw   = (yaw + (float) cursorDeltaX * 0.15f) % 360f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) cursorDeltaY * 0.15f));
    }

    /**
     * Per-FRAME movement. Called from CameraMixin before each frame's
     * setPos so motion stays smooth at render rate (60+ fps) instead of
     * stepping at the 20 Hz tick rate. Also clamps the camera within
     * the player's chunk-load radius — the server only sends chunks
     * around the player, so flying outside that range gives you a
     * blank/stale view.
     */
    public static void updatePerFrame() {
        if (!active || INSTANCE == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.options == null || c.player == null) return;

        long now = System.nanoTime();
        double dt;
        if (lastFrameNanos == 0L) {
            dt = 0.0;
        } else {
            dt = (now - lastFrameNanos) / 1_000_000_000.0;
            // Cap so a hitch / pause doesn't teleport the camera.
            if (dt > 0.1) dt = 0.1;
        }
        lastFrameNanos = now;

        // Don't read movement keys when a non-chat screen is open —
        // typing in inventory shouldn't fly the camera.
        boolean inputActive = c.currentScreen == null
                || c.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;

        if (inputActive && dt > 0.0) {
            boolean fwd    = c.options.forwardKey.isPressed();
            boolean back   = c.options.backKey.isPressed();
            boolean left   = c.options.leftKey.isPressed();
            boolean right  = c.options.rightKey.isPressed();
            boolean up     = c.options.jumpKey.isPressed();
            boolean down   = c.options.sneakKey.isPressed();
            boolean sprint = c.options.sprintKey.isPressed();

            // moveSpeed setting is "blocks per tick"; 20 ticks/sec → blocks/sec.
            double speed = INSTANCE.moveSpeed.get() * 20.0;
            if (sprint) speed *= INSTANCE.sprintMul.get();

            double dx = 0, dy = 0, dz = 0;
            float yawRad = (float) Math.toRadians(yaw);
            float sinY = (float) Math.sin(yawRad);
            float cosY = (float) Math.cos(yawRad);

            if (fwd)   { dx -= sinY; dz += cosY; }
            if (back)  { dx += sinY; dz -= cosY; }
            if (left)  { dx -= cosY; dz -= sinY; }
            if (right) { dx += cosY; dz += sinY; }
            if (up)    { dy += 1.0; }
            if (down)  { dy -= 1.0; }

            double mag = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (mag > 0.001) {
                double step = speed * dt;
                posX += (dx / mag) * step;
                posY += (dy / mag) * step;
                posZ += (dz / mag) * step;
            }
        }

        // Clamp distance to the player so the camera stays inside chunks
        // the server has actually sent. View distance × 16 = block radius;
        // shave 32 so we don't sit at the very edge where chunks pop in/out.
        try {
            int viewChunks = c.options.getViewDistance().getValue();
            double maxDist = Math.max(64.0, viewChunks * 16.0 - 32.0);
            double px = c.player.getX(), py = c.player.getEyeY(), pz = c.player.getZ();
            double rx = posX - px, ry = posY - py, rz = posZ - pz;
            double distSq = rx * rx + ry * ry + rz * rz;
            if (distSq > maxDist * maxDist) {
                double dist = Math.sqrt(distSq);
                double scale = maxDist / dist;
                posX = px + rx * scale;
                posY = py + ry * scale;
                posZ = pz + rz * scale;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void tick() {
        // Movement now runs per frame in updatePerFrame() — see CameraMixin.
        // Keep this empty so 20 Hz tick doesn't double-apply movement.
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
