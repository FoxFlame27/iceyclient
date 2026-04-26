package com.iceymod.hud.modules;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves and loads named waypoints to config/iceymod_getWaypoints().json,
 * keyed by world identity. Multiplayer = the server address; single-
 * player = the save folder's level name. Pre-v1.69 single-list files
 * migrate into a "default" key on first load so existing waypoints
 * stay visible (just no longer cross-world).
 */
public class WaypointManager {
    /** worldKey → list of waypoints saved in that world. */
    private static final Map<String, List<Waypoint>> worldWaypoints = new HashMap<>();
    private static Path configPath;

    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("iceymod_getWaypoints().json");
        load();
    }

    /**
     * Stable identity for "the world the player is currently in". Used
     * to scope waypoint storage so a waypoint on lifesteal.net doesn't
     * leak into a singleplayer world or a different server.
     */
    private static String currentWorldKey() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null) return "default";
            var serverInfo = c.getCurrentServerEntry();
            if (serverInfo != null && serverInfo.address != null) {
                return "server:" + serverInfo.address;
            }
            if (c.isInSingleplayer() && c.getServer() != null
                    && c.getServer().getSaveProperties() != null) {
                String level = c.getServer().getSaveProperties().getLevelName();
                if (level != null) return "sp:" + level;
            }
        } catch (Throwable ignored) {}
        return "default";
    }

    /** Returns (and lazily creates) the live list for the current world. */
    public static List<Waypoint> getWaypoints() {
        return worldWaypoints.computeIfAbsent(currentWorldKey(), k -> new ArrayList<>());
    }

    public static void addWaypoint(String name, int x, int y, int z) {
        getWaypoints().add(new Waypoint(name, x, y, z, randomColor()));
        save();
    }

    /** Add with explicit color (used by death-waypoint, structure pings, etc.). */
    public static void addWaypoint(String name, int x, int y, int z, int color) {
        getWaypoints().add(new Waypoint(name, x, y, z, color));
        save();
    }

    /**
     * Auto-paths use this — refuses the add if a same-named waypoint
     * already exists within {@code dedupRadius} blocks. Stops the
     * waypoint list from filling up with "Trial Chamber" duplicates
     * every time you re-enter a chunk you've already detected.
     */
    public static boolean addWaypointIfNew(String name, int x, int y, int z, int color, double dedupRadius) {
        if (name == null) return false;
        double rsq = dedupRadius * dedupRadius;
        List<Waypoint> here = getWaypoints();
        for (Waypoint wp : here) {
            if (!name.equals(wp.name)) continue;
            double dx = wp.x - x, dy = wp.y - y, dz = wp.z - z;
            if (dx * dx + dy * dy + dz * dz < rsq) return false;
        }
        here.add(new Waypoint(name, x, y, z, color));
        save();
        return true;
    }

    /** Repaint an existing waypoint. */
    public static void updateWaypointColor(int index, int color) {
        if (index < 0 || index >= getWaypoints().size()) return;
        Waypoint old = getWaypoints().get(index);
        getWaypoints().set(index, new Waypoint(old.name, old.x, old.y, old.z, color));
        save();
    }

    public static void removeWaypoint(int index) {
        if (index >= 0 && index < getWaypoints().size()) {
            getWaypoints().remove(index);
            save();
        }
    }

    public static void renameWaypoint(int index, String newName) {
        if (index < 0 || index >= getWaypoints().size()) return;
        if (newName == null) return;
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) return;
        Waypoint old = getWaypoints().get(index);
        getWaypoints().set(index, new Waypoint(trimmed, old.x, old.y, old.z, old.color));
        save();
    }

    public static void updateWaypointCoords(int index, int x, int y, int z) {
        if (index < 0 || index >= getWaypoints().size()) return;
        Waypoint old = getWaypoints().get(index);
        getWaypoints().set(index, new Waypoint(old.name, x, y, z, old.color));
        save();
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            JsonObject worldsObj = new JsonObject();
            for (Map.Entry<String, List<Waypoint>> e : worldWaypoints.entrySet()) {
                JsonArray arr = new JsonArray();
                for (Waypoint wp : e.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", wp.name);
                    o.addProperty("x", wp.x);
                    o.addProperty("y", wp.y);
                    o.addProperty("z", wp.z);
                    o.addProperty("color", wp.color);
                    arr.add(o);
                }
                worldsObj.add(e.getKey(), arr);
            }
            root.add("worlds", worldsObj);
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            /* fail silently — next add will re-attempt the write */
        }
    }

    public static void load() {
        worldWaypoints.clear();
        if (!Files.exists(configPath)) return;
        try {
            String json = Files.readString(configPath);
            JsonElement parsed = JsonParser.parseString(json);

            // Pre-v1.69 format: flat JsonArray of waypoints. Migrate
            // them under a "default" key so existing entries remain
            // visible — they just no longer cross-leak between worlds.
            if (parsed.isJsonArray()) {
                List<Waypoint> migrated = new ArrayList<>();
                for (JsonElement el : parsed.getAsJsonArray()) migrated.add(parseWaypoint(el.getAsJsonObject()));
                worldWaypoints.put("default", migrated);
                save();
                return;
            }

            // Current format: { version, worlds: { "<key>": [...] } }
            JsonObject root = parsed.getAsJsonObject();
            JsonObject worldsObj = root.has("worlds") ? root.getAsJsonObject("worlds") : new JsonObject();
            for (var entry : worldsObj.entrySet()) {
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement el : entry.getValue().getAsJsonArray()) list.add(parseWaypoint(el.getAsJsonObject()));
                worldWaypoints.put(entry.getKey(), list);
            }
        } catch (Exception e) {
            /* corrupt waypoints file — start fresh */
        }
    }

    private static Waypoint parseWaypoint(JsonObject o) {
        return new Waypoint(
                o.get("name").getAsString(),
                o.get("x").getAsInt(),
                o.get("y").getAsInt(),
                o.get("z").getAsInt(),
                o.has("color") ? o.get("color").getAsInt() : 0xFF5BC8F5
        );
    }

    private static int randomColor() {
        int[] colors = { 0xFF5BC8F5, 0xFF4ADE80, 0xFFFBBF24, 0xFFF87171, 0xFFA78BFA, 0xFFFB923C };
        return colors[(int)(Math.random() * colors.length)];
    }

    public static class Waypoint {
        public final String name;
        public final int x, y, z;
        public final int color;

        public Waypoint(String name, int x, int y, int z, int color) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.color = color;
        }
    }
}
