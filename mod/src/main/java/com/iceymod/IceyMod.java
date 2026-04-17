package com.iceymod;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.PerspectiveModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import com.iceymod.hud.modules.ZoomModule;
import com.iceymod.screen.IceyModScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
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

    @Override
    public void onInitializeClient() {
        HudManager.init();
        WaypointManager.init();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, KEY_CATEGORY));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, KEY_CATEGORY));
        perspectiveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.perspective", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY));
        waypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.iceymod.waypoint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, KEY_CATEGORY));

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
                HudModule wp = findModule("waypoints");
                if (wp instanceof WaypointsModule) {
                    // Auto-enable the module so the user can see what they just added
                    wp.setEnabled(true);
                    ((WaypointsModule) wp).addCurrentPosition();
                }
            }

            // Zoom: hold key
            HudModule zm = findModule("zoom");
            if (zm instanceof ZoomModule) {
                ((ZoomModule) zm).setZooming(zoomKey.isPressed());
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
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    int sw = client.getWindow().getScaledWidth();
                    int sh = client.getWindow().getScaledHeight();
                    int logoW = 200;
                    int logoH = 40;
                    int x = sw - logoW - 8;
                    int y = sh - logoH - 8;
                    ctx.drawTexturedQuad(LOGO_TEXTURE, x, x + logoW, y, y + logoH, 0, 0, 1, 1);
                });
            }

            if (screen instanceof HandledScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> {
                    int sw = client.getWindow().getScaledWidth();
                    int logoW = 160;
                    int logoH = 32;
                    int x = (sw - logoW) / 2;
                    int windowTop = (scr.height - 166) / 2;
                    int y = Math.max(2, windowTop - logoH - 4);
                    ctx.drawTexturedQuad(LOGO_TEXTURE, x, x + logoW, y, y + logoH, 0, 0, 1, 1);
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
