package com.iceymod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which face the mod shows: the normal Icey Client one, or "Skiflame"
 * when the launcher's secret mode is on. The launcher writes
 * config/iceymod-launcher.json with {"skiflame": true} at every launch;
 * we read it once at startup.
 */
public final class Branding {
    private static Boolean skiflame;

    private Branding() {}

    public static boolean isSkiflame() {
        if (skiflame == null) {
            boolean v = false;
            try {
                Path p = FabricLoader.getInstance().getConfigDir().resolve("iceymod-launcher.json");
                if (Files.exists(p)) {
                    JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                    v = o.has("skiflame") && o.get("skiflame").getAsBoolean();
                }
            } catch (Throwable ignored) {}
            skiflame = v;
            if (v) System.out.println("[IceyMod] Skiflame mode active");
        }
        return skiflame;
    }

    /** Display name used in menus and container headers. */
    public static String name() { return isSkiflame() ? "Skiflame" : "Icey Client"; }

    /** §-code prefix for the name (bold blue-violet vs bold aqua). */
    public static String colorCode() { return isSkiflame() ? "§9§l" : "§b§l"; }

    /** Title-screen logo texture and its pixel size. */
    public static Identifier logo() {
        return isSkiflame()
                ? Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "textures/gui/skiflame_logo.png")
                : Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "textures/gui/title/iceyclient.png");
    }
    public static int logoWidth()  { return isSkiflame() ? 720 : 1536; }
    public static int logoHeight() { return isSkiflame() ? 240 : 1024; }
    /** How wide the logo should be drawn on the title screen (GUI pixels). */
    public static int logoTargetWidth(int screenWidth) {
        return isSkiflame() ? Math.min(300, Math.max(96, screenWidth - 40)) : Math.min(200, Math.max(64, screenWidth - 40));
    }
}
