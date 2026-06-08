package com.iceymod.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cape registry. When the cape mixin asks for another
 * player's cape Identifier:
 *   - cache hit → return it.
 *   - negative-cache hit → return null (don't re-fetch known failures).
 *   - otherwise → schedule a background fetch from the Icey network,
 *     decode the PNG, register it as a texture on the render thread,
 *     and populate the cache.
 *
 * <p>The first render after a player joins is a no-op while the fetch
 * is in flight; the next frame after the texture lands picks it up.
 */
public final class RemoteCapeManager {

    private static final ConcurrentHashMap<UUID, Identifier> capes = new ConcurrentHashMap<>();
    private static final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> failed = ConcurrentHashMap.newKeySet();

    private RemoteCapeManager() {}

    public static Identifier getCapeIdentifier(UUID uuid) {
        if (uuid == null) return null;
        Identifier cached = capes.get(uuid);
        if (cached != null) return cached;
        if (failed.contains(uuid)) return null;
        scheduleFetch(uuid);
        return null;
    }

    private static void scheduleFetch(UUID uuid) {
        if (!pending.add(uuid)) return;
        Thread.ofVirtual().name("icey-cape-fetch-" + uuid).start(() -> {
            try {
                byte[] png = IceyNetwork.fetchCape(uuid);
                if (png == null || png.length == 0) {
                    failed.add(uuid);
                    return;
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null) {
                    failed.add(uuid);
                    return;
                }
                mc.execute(() -> {
                    try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
                        NativeImage img = NativeImage.read(in);
                        String slug = uuid.toString().replace("-", "_").toLowerCase();
                        Identifier texId = Identifier.of("iceymod", "remote_cape_" + slug);
                        NativeImageBackedTexture tex = new NativeImageBackedTexture(
                            () -> "iceymod_remote_cape_" + uuid, img);
                        mc.getTextureManager().registerTexture(texId, tex);
                        capes.put(uuid, texId);
                        System.out.println("[IceyMod] RemoteCapeManager: registered cape for " + uuid);
                    } catch (Throwable t) {
                        System.out.println("[IceyMod] RemoteCapeManager: register failed for " + uuid + ": " + t);
                        failed.add(uuid);
                    }
                });
            } catch (Throwable t) {
                failed.add(uuid);
            } finally {
                pending.remove(uuid);
            }
        });
    }
}
