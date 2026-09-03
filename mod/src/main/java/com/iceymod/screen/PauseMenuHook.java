package com.iceymod.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iceymod.screen.widget.IceyButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adds a "Java & Stuff" on/off button to the pause (ESC) menu.
 *
 * Mods can't be unloaded while the game runs, so the button doesn't touch
 * files itself. The launcher writes <config>/iceymod-launcher.json at every
 * launch ({"javaStuffEnabled": bool}); we show that state, and a click
 * writes <config>/iceymod-request.json ({"javaStuffEnabled": bool}) which
 * the launcher applies to its settings on the next launch, then deletes.
 * The label makes the "next launch" part explicit.
 */
public final class PauseMenuHook {

    private static final String STATUS_FILE = "iceymod-launcher.json";
    private static final String REQUEST_FILE = "iceymod-request.json";

    private PauseMenuHook() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GameMenuScreen)) return;
            try { addButton(screen, scaledWidth, scaledHeight); } catch (Throwable t) {
                System.out.println("[IceyMod] pause-menu button failed: " + t);
            }
        });
    }

    private static void addButton(Screen screen, int w, int h) {
        Boolean launcherState = readBool(configPath(STATUS_FILE));
        if (launcherState == null) return; // not launched through Icey Client → nothing to toggle
        Boolean pending = readBool(configPath(REQUEST_FILE));

        int bw = 118, bh = 20;
        int x = 8;
        int y = h / 4 + 48; // roughly level with the pause menu's middle buttons
        IceyButton btn = IceyButton.of(x, y, bw, bh, "", b -> {
            boolean current = readBool(configPath(REQUEST_FILE)) != null
                    ? readBool(configPath(REQUEST_FILE)) : launcherState;
            boolean next = !current;
            if (next == launcherState) {
                // Back to what the launcher already has → no request needed.
                try { Files.deleteIfExists(configPath(REQUEST_FILE)); } catch (Throwable ignored) {}
            } else {
                writeBool(configPath(REQUEST_FILE), next);
            }
            style((IceyButton) b, launcherState, readBool(configPath(REQUEST_FILE)));
        });
        style(btn, launcherState, pending);
        Screens.getButtons(screen).add(btn);
    }

    private static void style(IceyButton btn, boolean launcherState, Boolean pending) {
        boolean shown = pending != null ? pending : launcherState;
        btn.setLeftText("Java & Stuff");
        btn.clearRight();
        if (pending != null && pending != launcherState) {
            // Change queued for next launch.
            btn.setPill(shown ? "ON ↻" : "OFF ↻", 0xFF061018, 0xFFFBBF24);
            btn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                    "Will be " + (shown ? "ON" : "OFF") + " after you quit and relaunch from Icey Client")));
        } else {
            btn.setToggleState(shown);
            btn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                    "Click to turn Java & Stuff " + (shown ? "off" : "on") + " on next launch")));
        }
    }

    private static Path configPath(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    private static Boolean readBool(Path p) {
        try {
            if (!Files.exists(p)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("javaStuffEnabled")) return null;
            return o.get("javaStuffEnabled").getAsBoolean();
        } catch (Throwable t) { return null; }
    }

    private static void writeBool(Path p, boolean value) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, "{\n  \"javaStuffEnabled\": " + value + "\n}\n", StandardCharsets.UTF_8);
        } catch (Throwable t) {
            System.out.println("[IceyMod] could not write " + p + ": " + t);
        }
    }
}
