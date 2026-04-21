package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * True freelook. While the key is held:
 *   - mouse movement moves the camera only
 *   - player's yaw/pitch is unchanged (so the character keeps facing /
 *     walking in whatever direction you had before)
 *   - on release, the camera snaps back to the player's facing
 *
 * The actual rerouting happens in EntityLookMixin (intercepts
 * changeLookDirection) and CameraMixin (overrides rotation at render time).
 */
public class FreelookModule extends HudModule {
    private static final long BLEND_OUT_MS = 180L;
    private static boolean active = false;
    private static float cameraYaw = 0f;
    private static float cameraPitch = 0f;
    private static long blendOutEndsAt = 0;
    private static Perspective savedPerspective = null;

    // Default OFF: first-person freelook is what users expect (camera rotates
    // while they keep walking forward). Third-person freelook looks wrong
    // because the camera position is still calculated from the player's
    // real yaw, so the camera sits behind the player but points elsewhere.
    public final BoolSetting autoThirdPerson = addSetting(
            new BoolSetting("autoThirdPerson", "Switch to 3rd Person", false));

    public FreelookModule() {
        super("freelook", "Freelook", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    public static boolean isActive() { return active; }
    /** Active OR still smoothly blending back to the player's orientation. */
    public static boolean isRendering() {
        return active || System.currentTimeMillis() < blendOutEndsAt;
    }
    public static float getCameraYaw() { return cameraYaw; }
    public static float getCameraPitch() { return cameraPitch; }

    /**
     * Rendered yaw: while active, returns the free camera yaw; during the
     * short blend-out, lerps from camera yaw back to the player's yaw so
     * the snap-back feels smooth.
     */
    public static float getRenderYaw(float playerYaw) {
        if (active) return cameraYaw;
        long remaining = blendOutEndsAt - System.currentTimeMillis();
        if (remaining <= 0) return playerYaw;
        float t = remaining / (float) BLEND_OUT_MS; // 1 -> 0
        // shortest-arc interpolation so 359->1 doesn't wrap the wrong way
        float delta = (((cameraYaw - playerYaw) % 360f + 540f) % 360f) - 180f;
        return playerYaw + delta * t;
    }

    public static float getRenderPitch(float playerPitch) {
        if (active) return cameraPitch;
        long remaining = blendOutEndsAt - System.currentTimeMillis();
        if (remaining <= 0) return playerPitch;
        float t = remaining / (float) BLEND_OUT_MS;
        return playerPitch + (cameraPitch - playerPitch) * t;
    }

    /** Called by the keybind handler on key-down. */
    public void start(MinecraftClient client) {
        if (active || client == null || client.player == null) return;
        active = true;
        cameraYaw = client.player.getYaw();
        cameraPitch = client.player.getPitch();
        if (autoThirdPerson.get() && client.options != null) {
            savedPerspective = client.options.getPerspective();
            if (savedPerspective == Perspective.FIRST_PERSON) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            } else {
                savedPerspective = null; // don't restore something we didn't change
            }
        }
    }

    /** Called by the keybind handler on key-up. */
    public void stop(MinecraftClient client) {
        if (!active) return;
        active = false;
        blendOutEndsAt = System.currentTimeMillis() + BLEND_OUT_MS;
        if (savedPerspective != null && client != null && client.options != null) {
            client.options.setPerspective(savedPerspective);
        }
        savedPerspective = null;
    }

    /**
     * Disabling the module from the menu must kill freelook instantly —
     * no blend-out, no active state. Otherwise the user sees freelook still
     * "hanging on" until the next tick.
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && (active || blendOutEndsAt > 0)) {
            active = false;
            blendOutEndsAt = 0;
            MinecraftClient client = MinecraftClient.getInstance();
            if (savedPerspective != null && client != null && client.options != null) {
                client.options.setPerspective(savedPerspective);
            }
            savedPerspective = null;
        }
        super.setEnabled(enabled);
    }

    /**
     * Apply a mouse-cursor delta to the freelook camera. Called from the
     * EntityLookMixin when the freelook key is held.
     */
    public static void applyDelta(double cursorDeltaX, double cursorDeltaY) {
        cameraYaw = (cameraYaw + (float) cursorDeltaX * 0.15f) % 360f;
        cameraPitch = Math.max(-90f, Math.min(90f, cameraPitch + (float) cursorDeltaY * 0.15f));
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
