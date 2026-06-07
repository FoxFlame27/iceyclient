package com.iceymod.cape;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Loads the user-uploaded cape PNG from
 * {@code <gameDir>/config/iceyclient/cape.png} and exposes it as a
 * registered Minecraft texture identifier the
 * {@link com.iceymod.mixin.AbstractClientPlayerEntityMixin} can swap
 * into the local player's PlayerSkin.
 *
 * <h2>Why this exists</h2>
 * The launcher's "drop a PNG" UI writes the cape to a known path on
 * every installation's game dir; this class is the in-game half that
 * actually turns that file into a renderable cape texture. The
 * Mojang asset cache at {@code .minecraft/assets/skins} is keyed by
 * SHA-1 hashes — files there with arbitrary names are ignored. To
 * actually show a custom cape on the local player we have to load
 * the PNG ourselves, register it with the TextureManager, and inject
 * its Identifier into {@code AbstractClientPlayerEntity
 * .getSkinTextures()} for the local player only.
 *
 * <h2>Lifecycle</h2>
 * Lazily initialised on first call to {@link #getCapeIdentifier()}.
 * If the file is missing or fails to decode, returns null and the
 * mixin falls through to the original cape (Mojang's, or nothing).
 * mtimestamp is re-checked on each call so dropping a new PNG into
 * the config folder while MC is running picks up after a few seconds
 * of cache TTL without needing a restart.
 */
public final class CapeLoader {

    /** Registered texture identifier — namespaced under iceymod. */
    private static final Identifier CAPE_ID = Identifier.of("iceymod", "local_cape");
    /** Re-check the file every 3 seconds so the user can hot-swap. */
    private static final long FILE_CHECK_INTERVAL_MS = 3000L;

    private static final AtomicReference<Identifier> CACHED = new AtomicReference<>();
    private static long lastFileCheck = 0L;
    private static long lastFileMtime = 0L;
    private static boolean noCapeReported = false;
    private static boolean debugLogged = false;

    private CapeLoader() {}

    /**
     * Returns the registered identifier for the user's custom cape
     * texture, or {@code null} if no cape PNG is present / loadable.
     * Caller is responsible for limiting this to the local player.
     */
    public static Identifier getCapeIdentifier() {
        long now = System.currentTimeMillis();
        if (now - lastFileCheck < FILE_CHECK_INTERVAL_MS) {
            return CACHED.get();
        }
        lastFileCheck = now;

        Path capePath = capeFilePath();
        boolean exists = capePath != null && Files.isRegularFile(capePath);
        if (!debugLogged) {
            System.out.println("[IceyMod] CapeLoader.getCapeIdentifier: path=" + capePath + " exists=" + exists);
            debugLogged = true;
        }
        if (!exists) {
            if (CACHED.get() != null) {
                // File deleted while running — clear cache.
                CACHED.set(null);
            }
            return null;
        }

        long mtime;
        try {
            mtime = Files.getLastModifiedTime(capePath).toMillis();
        } catch (Exception e) {
            return CACHED.get();
        }

        if (mtime == lastFileMtime && CACHED.get() != null) {
            return CACHED.get();
        }
        lastFileMtime = mtime;

        Identifier loaded = loadAndRegister(capePath);
        CACHED.set(loaded);
        return loaded;
    }

    /** Path to {@code <gameDir>/config/iceyclient/cape.png}, or null
     *  if Fabric loader isn't available. */
    private static Path capeFilePath() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            return gameDir.resolve("config").resolve("iceyclient").resolve("cape.png");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Read the PNG bytes off disk, decode via NativeImage, register
     * with the TextureManager under {@link #CAPE_ID}, and return the
     * identifier. Returns null on any failure.
     *
     * <p>Must run on the render thread — {@link
     * net.minecraft.client.MinecraftClient#getTextureManager()} +
     * {@code registerTexture} aren't thread-safe. Callers come from
     * the mixin path which fires during render, so this is fine.
     */
    private static Identifier loadAndRegister(Path capePath) {
        try {
            byte[] bytes = Files.readAllBytes(capePath);
            NativeImage img;
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                img = NativeImage.read(in);
            }
            // 1.21.10+ removed the single-arg (NativeImage) ctor —
            // it now requires a name supplier first (used by the
            // texture manager for debug labels / leak diagnostics).
            // The Supplier<String> form is stable on both 1.21.8 and
            // 1.21.11 so the same source compiles for both matrix
            // jars.
            Supplier<String> labelSupplier = () -> "iceymod_local_cape";
            NativeImageBackedTexture tex = new NativeImageBackedTexture(labelSupplier, img);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getTextureManager() == null) return null;
            mc.getTextureManager().registerTexture(CAPE_ID, tex);
            if (!noCapeReported) {
                System.out.println("[IceyMod] CapeLoader: registered custom cape from " + capePath);
            }
            noCapeReported = true;
            return CAPE_ID;
        } catch (Throwable t) {
            System.out.println("[IceyMod] CapeLoader: failed to load " + capePath + ": " + t);
            return null;
        }
    }
}
