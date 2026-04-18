package com.iceymod.hud.modules;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads named waypoints to config/iceymod_waypoints.json.
 */
public class WaypointManager {
    private static final List<Waypoint> waypoints = new ArrayList<>();
    private static Path configPath;

    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("iceymod_waypoints.json");
        load();
    }

    public static List<Waypoint> getWaypoints() { return waypoints; }

    public static void addWaypoint(String name, int x, int y, int z) {
        waypoints.add(new Waypoint(name, x, y, z, randomColor()));
        save();
    }

    public static void removeWaypoint(int index) {
        if (index >= 0 && index < waypoints.size()) {
            waypoints.remove(index);
            save();
        }
    }

    public static void renameWaypoint(int index, String newName) {
        if (index < 0 || index >= waypoints.size()) return;
        if (newName == null) return;
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) return;
        Waypoint old = waypoints.get(index);
        waypoints.set(index, new Waypoint(trimmed, old.x, old.y, old.z, old.color));
        save();
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        for (Waypoint wp : waypoints) {
            JsonObject o = new JsonObject();
            o.addProperty("name", wp.name);
            o.addProperty("x", wp.x);
            o.addProperty("y", wp.y);
            o.addProperty("z", wp.z);
            o.addProperty("color", wp.color);
            arr.add(o);
        }
        try {
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        } catch (IOException e) { /* fail silently — waypoints will be re-saved on next add */ }
    }

    public static void load() {
        waypoints.clear();
        if (!Files.exists(configPath)) return;
        try {
            String json = Files.readString(configPath);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                waypoints.add(new Waypoint(
                    o.get("name").getAsString(),
                    o.get("x").getAsInt(),
                    o.get("y").getAsInt(),
                    o.get("z").getAsInt(),
                    o.has("color") ? o.get("color").getAsInt() : 0xFF5BC8F5
                ));
            }
        } catch (Exception e) { /* corrupt waypoints file — start fresh */ }
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
