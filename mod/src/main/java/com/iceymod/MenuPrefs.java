package com.iceymod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Look of the Y menu: which layout (the button grid, which is the default
 * again since 1.87.21, the glass panels, or the tiles) and which accent colour.
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
    public static final String STYLE_CARDS = "cards";
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

    /** Which main menu the game shows: Minecraft's own, the Icey one, or the scene with the player and friends. */
    public static final String TITLE_VANILLA = "vanilla";
    public static final String TITLE_ICEY = "icey";
    public static final String TITLE_SCENE = "scene";

    private static boolean loaded;
    private static String titleMenu = "";
    private static String style = STYLE_GRID;
    private static int accent = DEFAULT_ACCENT;

    private MenuPrefs() {}

    public static boolean isPanels() { load(); return STYLE_PANELS.equals(style); }
    public static boolean isCards() { load(); return STYLE_CARDS.equals(style); }

    private static String clean(String s) {
        return STYLE_CARDS.equals(s) ? STYLE_CARDS : STYLE_PANELS.equals(s) ? STYLE_PANELS : STYLE_GRID;
    }
    public static int accent() { load(); return accent; }

    /** "" until the player has chosen (the game then asks once), else one of the TITLE_ values. */
    public static String titleMenu() { load(); return titleMenu; }
    public static boolean isVanillaTitle() { load(); return TITLE_VANILLA.equals(titleMenu); }
    public static boolean isSceneTitle() { load(); return TITLE_SCENE.equals(titleMenu); }

    private static String cleanTitle(String s) {
        return TITLE_VANILLA.equals(s) ? TITLE_VANILLA : TITLE_ICEY.equals(s) ? TITLE_ICEY : TITLE_SCENE.equals(s) ? TITLE_SCENE : "";
    }

    public static void setTitleMenu(String s) {
        load();
        titleMenu = cleanTitle(s);
        updateRequest("titleMenu", titleMenu);
    }

    public static void setStyle(String s) {
        load();
        style = clean(s);
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
                style = clean(o.get("hudMenuStyle").getAsString());
            }
            if (o.has("titleMenu")) titleMenu = cleanTitle(o.get("titleMenu").getAsString());
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
