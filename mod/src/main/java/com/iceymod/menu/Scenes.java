package com.iceymod.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iceymod.IceyMod;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * The places the main menu can be set in. Each is a piece of a real
 * Minecraft world kept as a 3D model (see SceneModel), with how it is lit
 * and the spots in it where friends can stand. A different one is picked
 * at every start of the game.
 */
public final class Scenes {
    private Scenes() {}

/** A place to stand, in blocks from where the player stands. */
    public static final class Spot {
        public final float x, y, z;
        Spot(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    }

    public static final class Scene {
        public String id, name;
        public boolean snow;
        /** The light on the players here (RGB): a dark place dims them so they do not look pasted in. */
        public int light = 0xFFFFFF;
        /** How the place is lit: daylight strength and colour, how strong lamps are, the darkest it gets. */
        public float day = 1f, glow = 1f, ambient = 0.14f;
        public int sun = 0xFFFFFF;
        /** The colour of what the sun does not reach (the sky's own light). */
        public int shade = 0xA9C2EE;
        /** The air: its colour, where the haze starts and ends (blocks from the middle), how thick it gets. */
        public int fog = 0xA9C4E8;
        public float fogStart = 10f, fogEnd = 40f, fogMax = 0.8f;
        /** The sky behind everything, top and bottom. */
        public int skyTop = 0x5B8DD6, skyBottom = 0xBFD8F2;
        public final List<Spot> spots = new ArrayList<>();
    }

    private static List<Scene> all;
    private static Scene current;

    public static synchronized List<Scene> all() {
        if (all != null) return all;
        List<Scene> list = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "scenes/scenes.json"));
            if (res.isPresent()) {
                try (InputStreamReader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
                    for (JsonElement e : arr) {
                        JsonObject o = e.getAsJsonObject();
                        Scene s = new Scene();
                        s.id = o.get("id").getAsString();
                        s.name = o.get("name").getAsString();
                        s.snow = o.has("snow") && o.get("snow").getAsBoolean();
                        s.light = hex(o, "light", s.light);
                        s.sun = hex(o, "sun", s.sun);
                        s.shade = hex(o, "shade", s.shade);
                        s.fog = hex(o, "fog", s.fog);
                        s.skyTop = hex(o, "skyTop", s.skyTop);
                        s.skyBottom = hex(o, "skyBottom", s.skyBottom);
                        s.day = num(o, "day", s.day); s.glow = num(o, "glow", s.glow); s.ambient = num(o, "ambient", s.ambient);
                        s.fogStart = num(o, "fogStart", s.fogStart); s.fogEnd = num(o, "fogEnd", s.fogEnd); s.fogMax = num(o, "fogMax", s.fogMax);
                        for (JsonElement f : o.getAsJsonArray("spots")) {
                            JsonArray a = f.getAsJsonArray();
                            s.spots.add(new Spot(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()));
                        }
                        if (s.id.matches("[a-z0-9_]{1,40}")) list.add(s);
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] scenes not read: " + t);
        }
        all = list;
        return all;
    }

    private static int hex(JsonObject o, String key, int fallback) {
        try { return o.has(key) ? Integer.parseInt(o.get(key).getAsString(), 16) & 0xFFFFFF : fallback; } catch (Throwable t) { return fallback; }
    }
    private static float num(JsonObject o, String key, float fallback) {
        try { return o.has(key) ? o.get(key).getAsFloat() : fallback; } catch (Throwable t) { return fallback; }
    }

    /** The scene of this game session: chosen at the first call, never the one of the last session. */
    public static synchronized Scene current() {
        if (current != null) return current;
        List<Scene> list = all();
        if (list.isEmpty()) return null;
        String last = "";
        Path mark = FabricLoader.getInstance().getConfigDir().resolve("iceymod-scene.txt");
        try { if (Files.exists(mark)) last = Files.readString(mark, StandardCharsets.UTF_8).trim(); } catch (Throwable ignored) {}
        List<Scene> pool = new ArrayList<>();
        for (Scene s : list) if (!s.id.equals(last)) pool.add(s);
        if (pool.isEmpty()) pool = list;
        current = pool.get(new Random().nextInt(pool.size()));
        try { Files.writeString(mark, current.id, StandardCharsets.UTF_8); } catch (Throwable ignored) {}
        return current;
    }

    /** The next scene in the list (the "another place" button). */
    public static synchronized Scene next() {
        List<Scene> list = all();
        if (list.isEmpty()) return null;
        int i = current == null ? -1 : list.indexOf(current);
        current = list.get((i + 1) % list.size());
        try { Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("iceymod-scene.txt"), current.id, StandardCharsets.UTF_8); } catch (Throwable ignored) {}
        return current;
    }
}
