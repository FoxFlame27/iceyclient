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
        /** How likely it rains or snows there at a start of the game (0..1), and whether it does this time. */
        public float rain, snowfall;
        public boolean raining, snowing;
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

        /** This place under today's weather: the same place, lit for rain or snowfall when the dice say so. */
        Scene withWeather(Random dice) {
            Scene s = new Scene();
            s.id = id; s.name = name; s.rain = rain; s.snowfall = snowfall;
            s.light = light; s.day = day; s.glow = glow; s.ambient = ambient; s.sun = sun; s.shade = shade;
            s.fog = fog; s.fogStart = fogStart; s.fogEnd = fogEnd; s.fogMax = fogMax; s.skyTop = skyTop; s.skyBottom = skyBottom;
            s.spots.addAll(spots);
            if (dice.nextFloat() < snowfall) {
                s.snowing = true;
                s.fog = blend(fog, 0xE4ECF6, 0.35f); s.fogStart = fogStart - 2f;
            } else if (dice.nextFloat() < rain) {
                s.raining = true;
                s.day = day * 0.45f; s.ambient = ambient * 0.92f; s.glow = glow * 1.15f;
                s.sun = blend(sun, 0xB8C4D4, 0.7f); s.shade = blend(shade, 0x8C9AB0, 0.6f);
                s.fog = blend(fog, 0x8795A8, 0.75f); s.fogStart = fogStart - 3f; s.fogMax = Math.min(0.92f, fogMax + 0.1f);
                s.skyTop = blend(skyTop, 0x4A5566, 0.8f); s.skyBottom = blend(skyBottom, 0x8A96A6, 0.8f);
                s.light = blend(light, 0xB4BECC, 0.6f);
            }
            return s;
        }
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static final java.util.Map<String, Scene> TODAY = new java.util.HashMap<>();
    private static final Random DICE = new Random();
    /** The place as it is in this game session: the weather is rolled once per place and start. */
    public static synchronized Scene today(Scene base) {
        return TODAY.computeIfAbsent(base.id, k -> base.withWeather(DICE));
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
                        s.rain = num(o, "rain", 0f); s.snowfall = num(o, "snowfall", 0f);
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
