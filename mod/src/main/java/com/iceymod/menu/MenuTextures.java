package com.iceymod.menu;

import com.iceymod.IceyMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pictures the main menu loads itself: skins the launcher left in
 * config/iceymod-skins, server icons, world icons.
 */
public final class MenuTextures {
    private MenuTextures() {}

    public static final class Tex {
        public final Identifier id;
        public final int width, height;
        Tex(Identifier id, int width, int height) { this.id = id; this.width = width; this.height = height; }
    }

    public static final Tex STEVE = new Tex(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"), 64, 64);

    private static final Map<String, Tex> CACHE = new HashMap<>();
    private static final Map<String, Long> STAMP = new HashMap<>();
    private static final Map<String, Long> CHECKED = new HashMap<>();
    private static int counter;

    private static String clean(String uuid) { return uuid == null ? "" : uuid.replace("-", "").toLowerCase(); }

    /** The skin of this player if the launcher has left one, else Steve. Looks again every few seconds. */
    public static Tex skin(String uuid) {
        String key = clean(uuid);
        if (!key.matches("[0-9a-f]{32}")) return STEVE;
        long now = System.currentTimeMillis();
        Long last = CHECKED.get(key);
        if (last == null || now - last > 3000) {
            CHECKED.put(key, now);
            try {
                Path p = FabricLoader.getInstance().getConfigDir().resolve("iceymod-skins").resolve(key + ".png");
                if (Files.exists(p)) {
                    long m = Files.getLastModifiedTime(p).toMillis();
                    Long had = STAMP.get(key);
                    if (had == null || had != m) {
                        STAMP.put(key, m);
                        try (InputStream in = Files.newInputStream(p)) {
                            Tex t = register("skin/" + key, NativeImage.read(in));
                            if (t != null && t.width == 64 && (t.height == 64 || t.height == 32)) CACHE.put(key, t);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        Tex t = CACHE.get(key);
        return t != null ? t : STEVE;
    }

    /** A PNG held in memory (a server's icon). Null when it cannot be read. */
    public static Tex fromBytes(String key, byte[] png) {
        if (png == null || png.length == 0) return null;
        String k = "b:" + key + ":" + java.util.Arrays.hashCode(png);
        if (CACHE.containsKey(k)) return CACHE.get(k);
        Tex t = null;
        try { t = register("icon/" + (counter++), NativeImage.read(png)); } catch (Throwable ignored) {}
        CACHE.put(k, t);
        return t;
    }

    /** A PNG on disk (a world's icon). Null when there is none. */
    public static Tex fromFile(Path p) {
        if (p == null) return null;
        try {
            if (!Files.isRegularFile(p)) return null;
            String k = "f:" + p + ":" + Files.getLastModifiedTime(p).toMillis();
            if (CACHE.containsKey(k)) return CACHE.get(k);
            Tex t = null;
            try (InputStream in = Files.newInputStream(p)) { t = register("icon/" + (counter++), NativeImage.read(in)); } catch (Throwable ignored) {}
            CACHE.put(k, t);
            return t;
        } catch (Throwable t) { return null; }
    }

    private static Tex register(String name, NativeImage img) {
        if (img == null) return null;
        Identifier id = Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "menu/" + name);
        int w = img.getWidth(), h = img.getHeight();
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "icey menu " + name, img));
        return new Tex(id, w, h);
    }
}
