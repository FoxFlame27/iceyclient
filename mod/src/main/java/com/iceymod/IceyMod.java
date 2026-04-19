package com.iceymod;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.FreelookModule;
import com.iceymod.hud.modules.PerspectiveModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import com.iceymod.hud.modules.ZoomModule;
import com.iceymod.screen.IceyModScreen;
import com.iceymod.screen.WaypointMenuScreen;
import com.iceymod.render.WaypointBeamRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class IceyMod implements ClientModInitializer {
    public static final String MOD_ID = "iceymod";
    public static final Identifier LOGO_TEXTURE = Identifier.of(MOD_ID, "textures/gui/logo.png");
    public static final String KEY_CATEGORY = "key.categories.iceymod";

    private static KeyBinding menuKey;
    private static KeyBinding zoomKey;
    private static KeyBinding perspectiveKey;
    private static KeyBinding waypointKey;
    private static KeyBinding hideHudKey;
    private static KeyBinding toggleSprintKey;
    private static KeyBinding toggleBrightKey;
    private static KeyBinding toggleTotemKey;
    private static KeyBinding freelookKey;
    private static KeyBinding copyCoordsKey;

    @Override
    public void onInitializeClient() {
        HudManager.init();
        WaypointManager.init();
        WaypointBeamRenderer.register();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, KEY_CATEGORY));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, KEY_CATEGORY));
        perspectiveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.perspective", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY));
        waypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.waypoint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, KEY_CATEGORY));
        hideHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.hidehud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, KEY_CATEGORY));
        toggleSprintKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.togglesprint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, KEY_CATEGORY));
        toggleBrightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.togglebright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY));
        toggleTotemKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.toggletotem", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_T, KEY_CATEGORY));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, KEY_CATEGORY));
        copyCoordsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.copycoords", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new IceyModScreen());
                }
            }
            while (perspectiveKey.wasPressed()) {
                HudModule pm = findModule("perspective");
                if (pm instanceof PerspectiveModule) {
                    ((PerspectiveModule) pm).cyclePerspective();
                }
            }
            while (waypointKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new WaypointMenuScreen());
                }
            }
            while (hideHudKey.wasPressed()) {
                HudManager.toggleHudVisibility();
            }
            while (toggleSprintKey.wasPressed()) {
                HudModule m = findModule("autosprint");
                if (m != null) m.toggle();
            }
            while (toggleBrightKey.wasPressed()) {
                HudModule m = findModule("fullbright");
                if (m != null) m.toggle();
            }
            while (toggleTotemKey.wasPressed()) {
                HudModule m = findModule("autototem");
                if (m != null) m.toggle();
            }

            // Zoom: hold key
            HudModule zm = findModule("zoom");
            if (zm instanceof ZoomModule) {
                ((ZoomModule) zm).setZooming(zoomKey.isPressed());
            }

            // Freelook: hold key — camera rotates while character keeps facing forward
            HudModule fl = findModule("freelook");
            if (fl instanceof FreelookModule flm) {
                boolean shouldBeActive = fl.isEnabled() && freelookKey.isPressed();
                if (shouldBeActive && !FreelookModule.isActive()) flm.start(client);
                else if (!shouldBeActive && FreelookModule.isActive()) flm.stop(client);
            }

            // Copy coords to clipboard on key press
            while (copyCoordsKey.wasPressed()) {
                if (client.player != null) {
                    int x = (int) Math.floor(client.player.getX());
                    int y = (int) Math.floor(client.player.getY());
                    int z = (int) Math.floor(client.player.getZ());
                    String coords = x + ", " + y + ", " + z;
                    client.keyboard.setClipboard(coords);
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("\u00A7b[Icey] \u00A7rCopied \u00A7a" + coords),
                            true); // actionBar overlay — doesn't spam chat
                }
            }

            HudManager.tick();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && (client.currentScreen == null
                    || client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
                HudManager.render(drawContext);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Title screen: no extra corner logo — the Icey Client logo is already drawn
            // in place of the vanilla MINECRAFT logo by LogoDrawerMixin.

            if (screen instanceof HandledScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    // Skip brewing stands — tight layout, text overlaps the window
                    if (scr instanceof net.minecraft.client.gui.screen.ingame.BrewingStandScreen) return;
                    // Skip double chests — taller window pushes the text too far down
                    if (scr instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen gcs
                            && gcs.getScreenHandler().getRows() == 6) return;

                    int sw = client.getWindow().getScaledWidth();
                    int windowTop = (scr.height - 166) / 2;
                    int y = Math.max(2, windowTop - 14);
                    ctx.drawCenteredTextWithShadow(
                            client.textRenderer,
                            net.minecraft.text.Text.literal("\u00A7b\u00A7lIcey Client"),
                            sw / 2, y,
                            0xFFFFFFFF
                    );
                });
            }
        });
    }

    private static HudModule findModule(String id) {
        for (HudModule m : HudManager.getModules()) {
            if (m.getId().equals(id)) return m;
        }
        return null;
    }
}
