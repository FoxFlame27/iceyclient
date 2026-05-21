package com.iceymod;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.FreecamModule;
import com.iceymod.hud.modules.FreelookModule;
import com.iceymod.hud.modules.PerspectiveModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import com.iceymod.hud.modules.ZoomModule;
import com.iceymod.screen.IceyModScreen;
import com.iceymod.screen.LeaderboardScreen;
import com.iceymod.screen.StructureMenuScreen;
import com.iceymod.screen.WaypointMenuScreen;
import com.iceymod.structure.StructureTracker;
import com.iceymod.structure.BiomeTracker;
import com.iceymod.screen.BiomeMenuScreen;
import com.iceymod.chat.ChatCoordParser;
import com.iceymod.render.WaypointBeamRenderer;
import com.iceymod.render.HitboxRenderer;
import com.iceymod.render.HealthHudRenderer;
import com.iceymod.compat.KeyBindingCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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
    private static KeyBinding structureKey;
    private static KeyBinding freecamKey;
    private static KeyBinding biomeKey;
    private static KeyBinding leaderboardKey;

    @Override
    public void onInitializeClient() {
        // Each setup call is independently caught so a single failure (e.g.
        // a new MC version having renamed a class one of our modules
        // references) doesn't take the whole mod down — partial Icey >
        // entirely-broken Icey.
        try { HudManager.init(); }            catch (Throwable t) { System.out.println("[IceyMod] HudManager.init failed: " + t); }
        try { WaypointManager.init(); }        catch (Throwable t) { System.out.println("[IceyMod] WaypointManager.init failed: " + t); }
        try { WaypointBeamRenderer.register(); } catch (Throwable t) { System.out.println("[IceyMod] WaypointBeamRenderer failed: " + t); }
        try { HitboxRenderer.register(); }     catch (Throwable t) { System.out.println("[IceyMod] HitboxRenderer failed: " + t); }
        try { HealthHudRenderer.register(); } catch (Throwable t) { System.out.println("[IceyMod] HealthHudRenderer failed: " + t); }
        try { StructureTracker.register(); }   catch (Throwable t) { System.out.println("[IceyMod] StructureTracker failed: " + t); }
        try { BiomeTracker.register(); }       catch (Throwable t) { System.out.println("[IceyMod] BiomeTracker failed: " + t); }
        try { ChatCoordParser.register(); }    catch (Throwable t) { System.out.println("[IceyMod] ChatCoordParser failed: " + t); }

        menuKey         = registerKey("key.iceymod.menu",         GLFW.GLFW_KEY_Y);
        zoomKey         = registerKey("key.iceymod.zoom",         GLFW.GLFW_KEY_M);
        perspectiveKey  = registerKey("key.iceymod.perspective",  GLFW.GLFW_KEY_R);
        waypointKey     = registerKey("key.iceymod.waypoint",     GLFW.GLFW_KEY_B);
        hideHudKey      = registerKey("key.iceymod.hidehud",      GLFW.GLFW_KEY_H);
        // N moved to leaderboard; user can rebind autosprint via Controls if they want.
        toggleSprintKey = registerKey("key.iceymod.togglesprint", InputUtil.UNKNOWN_KEY.getCode());
        toggleBrightKey = registerKey("key.iceymod.togglebright", GLFW.GLFW_KEY_G);
        toggleTotemKey  = registerKey("key.iceymod.toggletotem",  GLFW.GLFW_KEY_T);
        freelookKey     = registerKey("key.iceymod.freelook",     GLFW.GLFW_KEY_LEFT_ALT);
        copyCoordsKey   = registerKey("key.iceymod.copycoords",   GLFW.GLFW_KEY_J);
        structureKey    = registerKey("key.iceymod.structure",    GLFW.GLFW_KEY_V);
        freecamKey      = registerKey("key.iceymod.freecam",      GLFW.GLFW_KEY_F4);
        biomeKey        = registerKey("key.iceymod.biome",        GLFW.GLFW_KEY_K);
        // Fresh keybind id ("openboard" instead of "leaderboard") so MC
        // doesn't fall back to a stale `;` binding saved in older options.txt.
        leaderboardKey  = registerKey("key.iceymod.openboard",    GLFW.GLFW_KEY_N);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (wasPressed(menuKey)) {
                if (client.currentScreen == null) {
                    client.setScreen(new IceyModScreen());
                }
            }
            while (wasPressed(perspectiveKey)) {
                HudModule pm = findModule("perspective");
                if (pm instanceof PerspectiveModule) {
                    ((PerspectiveModule) pm).cyclePerspective();
                }
            }
            while (wasPressed(waypointKey)) {
                if (client.currentScreen == null) {
                    client.setScreen(new WaypointMenuScreen());
                }
            }
            while (wasPressed(structureKey)) {
                if (client.currentScreen == null) {
                    client.setScreen(new StructureMenuScreen());
                }
            }
            while (wasPressed(biomeKey)) {
                if (client.currentScreen == null) {
                    client.setScreen(new BiomeMenuScreen());
                }
            }
            while (wasPressed(leaderboardKey)) {
                // Send /leaderboard to the server so it opens the
                // server-side chest GUI (which has live data + clickable
                // category drill-down). Falls back to the client-side
                // LeaderboardScreen on vanilla servers without iceymod+
                // — the client gets "unknown command" but no harm done.
                if (client.player != null && client.currentScreen == null) {
                    try {
                        client.player.networkHandler.sendChatCommand("leaderboard");
                    } catch (Throwable ignored) {}
                }
            }
            while (wasPressed(freecamKey)) {
                HudModule fc = findModule("freecam");
                if (fc instanceof FreecamModule fcm) {
                    fcm.toggle(client);
                }
            }
            while (wasPressed(hideHudKey)) {
                HudManager.toggleHudVisibility();
            }
            while (wasPressed(toggleSprintKey)) {
                HudModule m = findModule("autosprint");
                if (m != null) m.toggle();
            }
            while (wasPressed(toggleBrightKey)) {
                HudModule m = findModule("fullbright");
                if (m != null) m.toggle();
            }
            while (wasPressed(toggleTotemKey)) {
                HudModule m = findModule("autototem");
                if (m != null) m.toggle();
            }

            // Zoom: hold key
            HudModule zm = findModule("zoom");
            if (zm instanceof ZoomModule) {
                ((ZoomModule) zm).setZooming(isPressed(zoomKey));
            }

            // Freelook: hold key — camera rotates while character keeps facing forward
            HudModule fl = findModule("freelook");
            if (fl instanceof FreelookModule flm) {
                boolean shouldBeActive = fl.isEnabled() && isPressed(freelookKey);
                if (shouldBeActive && !FreelookModule.isActive()) {
                    flm.start(client);
                    if (client.player != null) {
                        client.player.sendMessage(net.minecraft.text.Text.literal("§b[Icey] §aFreelook ON"), true);
                    }
                } else if (!shouldBeActive && FreelookModule.isActive()) {
                    flm.stop(client);
                    if (client.player != null) {
                        client.player.sendMessage(net.minecraft.text.Text.literal("§b[Icey] §7Freelook off"), true);
                    }
                }
            }

            // Copy coords to clipboard on key press
            while (wasPressed(copyCoordsKey)) {
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

        // Client-side commands: /iceyhuds resets the HUD module state
        // (force visible, reset positions, re-enable modules that the
        // auto-disable-on-error logic killed in older builds).
        // (/lb removed in v1.84.7 per user — only /leaderboard now.
        // Keybind N sends /leaderboard to the server to open the
        // server-side chest GUI.)
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                dispatcher.register(ClientCommandManager.literal("iceyhuds")
                    .executes(ctx -> {
                        if (!HudManager.isHudVisible()) HudManager.toggleHudVisibility();
                        HudManager.resetAllToDefaults();
                        ctx.getSource().sendFeedback(net.minecraft.text.Text.literal(
                                "§b[IceyMod] §aHUDs reset — visible + defaults restored. Open Y menu to reposition."));
                        return 1;
                    }));
            });
        } catch (Throwable t) {
            System.out.println("[IceyMod] Client command registration failed: " + t);
        }

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

    /**
     * Register a key via the compat helper so versions that removed the
     * (String, Type, int, String) KeyBinding constructor don't crash the mod.
     * Returns null if registration fails — all call sites null-check.
     */
    private static KeyBinding registerKey(String translationKey, int code) {
        try {
            KeyBinding kb = KeyBindingCompat.create(translationKey, InputUtil.Type.KEYSYM, code, KEY_CATEGORY);
            if (kb == null) return null;
            return KeyBindingHelper.registerKeyBinding(kb);
        } catch (Throwable t) {
            System.out.println("[IceyMod] Failed to register key " + translationKey + ": " + t.getMessage());
            return null;
        }
    }

    private static boolean wasPressed(KeyBinding kb) {
        if (kb == null) return false;
        try { return kb.wasPressed(); } catch (Throwable t) { return false; }
    }

    private static boolean isPressed(KeyBinding kb) {
        if (kb == null) return false;
        try { return kb.isPressed(); } catch (Throwable t) { return false; }
    }
}
