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

    public final DoubleSetting moveSpeed = addSetting(
            new DoubleSetting("moveSpeed", "Move Speed", 0.5, 0.05, 5.0, 0.05));
    public final DoubleSetting sprintMul = addSetting(
            new DoubleSetting("sprintMul", "Sprint Multiplier", 3.0, 1.0, 10.0, 0.5));

    public FreecamModule() {
        super("freecam", "Freecam", 0, 0);
        setEnabled(true);
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
     * Per-tick movement update. Reads vanilla movement keys (forward,
     * back, strafe, jump, sneak, sprint) and walks the freecam position
     * by their composite vector.
     */
    @Override
    public void tick() {
        if (!active) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.options == null) return;
        // While a screen is open (chat / inventory / pause) input keys
        // aren't being polled by the player anyway — leave camera still.
        if (c.currentScreen != null
                && !(c.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;

        boolean fwd   = c.options.forwardKey.isPressed();
        boolean back  = c.options.backKey.isPressed();
        boolean left  = c.options.leftKey.isPressed();
        boolean right = c.options.rightKey.isPressed();
        boolean up    = c.options.jumpKey.isPressed();
        boolean down  = c.options.sneakKey.isPressed();
        boolean sprint = c.options.sprintKey.isPressed();

        double speed = moveSpeed.get();
        if (sprint) speed *= sprintMul.get();

        double dx = 0, dy = 0, dz = 0;
        float yawRad = (float) Math.toRadians(yaw);
        float sinY = (float) Math.sin(yawRad);
        float cosY = (float) Math.cos(yawRad);

        // Forward in MC: -sin(yaw) X, +cos(yaw) Z (yaw 0 = facing +Z south)
        if (fwd)   { dx -= sinY; dz += cosY; }
        if (back)  { dx += sinY; dz -= cosY; }
        // Strafe is perpendicular to forward
        if (left)  { dx -= cosY; dz -= sinY; }
        if (right) { dx += cosY; dz += sinY; }
        if (up)    { dy += 1.0; }
        if (down)  { dy -= 1.0; }

        double mag = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (mag > 0.001) {
            posX += (dx / mag) * speed;
            posY += (dy / mag) * speed;
            posZ += (dz / mag) * speed;
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
