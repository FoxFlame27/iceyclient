package com.iceymod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Look of the Y menu: which layout (the glass panels, which is the
 * default, or the older button grid) and which accent colour.
 *
 * The launcher owns these (setup wizard and Settings) and hands them over
 * in config/iceymod-launcher.json. Changes made in-game are written to
 * config/iceymod-request.json, which the launcher folds back into its
 * settings on the next launch. Until it does, the request wins, so a
 * choice made in-game also sticks when the jar runs without the launcher.
 */
public final class MenuPrefs {
    public static final String STYLE_GRID = "grid";
    public static final String STYLE_PANELS = "panels";
    public static final int DEFAULT_ACCENT = 0xFF5BC8F5;

    /** Presets offered in-game; the launcher can hand over any colour. */
    public static final int[] ACCENTS = {
        0xFF5BC8F5, 0xFFA78BFA, 0xFF4ADE80, 0xFFFB923C, 0xFFF472B6, 0xFFFF8A3D
    };
    public static final String[] ACCENT_NAMES = {
        "Ice", "Violet", "Mint", "Sunset", "Rose", "Flame"
    };

    private static final String STATUS_FILE = "iceymod-launcher.json";
    private static final String REQUEST_FILE = "iceymod-request.json";

    private static boolean loaded;
    private static String style = STYLE_PANELS;
    private static int accent = DEFAULT_ACCENT;

    private MenuPrefs() {}

    public static boolean isPanels() { load(); return STYLE_PANELS.equals(style); }
    public static int accent() { load(); return accent; }

    public static void setStyle(String s) {
        load();
        style = STYLE_GRID.equals(s) ? STYLE_GRID : STYLE_PANELS;
        updateRequest("hudMenuStyle", style);
    }

    public static void setAccent(int argb) {
        load();
        accent = 0xFF000000 | argb;
        updateRequest("hudMenuColor", toHex(accent));
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        apply(read(STATUS_FILE));
        apply(read(REQUEST_FILE));
    }

    private static void apply(JsonObject o) {
        if (o == null) return;
        try {
            if (o.has("hudMenuStyle")) {
                style = STYLE_GRID.equals(o.get("hudMenuStyle").getAsString()) ? STYLE_GRID : STYLE_PANELS;
            }
            if (o.has("hudMenuColor")) {
                String hex = o.get("hudMenuColor").getAsString().trim();
                if (hex.startsWith("#")) hex = hex.substring(1);
                if (hex.length() == 6) accent = 0xFF000000 | Integer.parseInt(hex, 16);
            }
        } catch (Throwable ignored) {}
    }

    private static String toHex(int argb) {
        return String.format("#%06x", argb & 0xFFFFFF);
    }

    private static Path path(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    private static JsonObject read(String name) {
        try {
            Path p = path(name);
            if (!Files.exists(p)) return null;
            return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Throwable t) { return null; }
    }

    /**
     * Set one key in the request file, keeping whatever else is queued in
     * it (the U key's Java &amp; Stuff switch shares the file).
     */
    public static void updateRequest(String key, Object value) {
        try {
            JsonObject o = read(REQUEST_FILE);
            if (o == null) o = new JsonObject();
            if (value instanceof Boolean b) o.addProperty(key, b);
            else if (value instanceof Number n) o.addProperty(key, n);
            else o.addProperty(key, String.valueOf(value));
            Path p = path(REQUEST_FILE);
            Files.createDirectories(p.getParent());
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }
}
