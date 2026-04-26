package com.iceymod.structure;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.BiomeLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Same shape as {@link StructureTracker} but for biomes. Each chunk
 * load samples the biome at chunk center; if it matches one of the
 * user-enabled biome types, it's recorded as a Found entry. Big
 * 256-block clustering since biomes cover huge contiguous regions —
 * one entry per blob, not per chunk.
 */
public final class BiomeTracker {

    public enum BiomeType {
        CHERRY_GROVE     ("Cherry Grove",      0xFFFF9DC4, BiomeKeys.CHERRY_GROVE),
        MUSHROOM_FIELDS  ("Mushroom Fields",   0xFFC07DCC, BiomeKeys.MUSHROOM_FIELDS),
        ICE_SPIKES       ("Ice Spikes",        0xFFB4DCFF, BiomeKeys.ICE_SPIKES),
        SUNFLOWER_PLAINS ("Sunflower Plains",  0xFFFFE45A, BiomeKeys.SUNFLOWER_PLAINS),
        BAMBOO_JUNGLE    ("Bamboo Jungle",     0xFF77BC3D, BiomeKeys.BAMBOO_JUNGLE),
        ERODED_BADLANDS  ("Eroded Badlands",   0xFFBF6A29, BiomeKeys.ERODED_BADLANDS),
        DEEP_DARK        ("Deep Dark",         0xFF22BBAA, BiomeKeys.DEEP_DARK),
        PALE_GARDEN      ("Pale Garden",       0xFFD2D2C5, BiomeKeys.PALE_GARDEN),
        DEEP_FROZEN_OCEAN("Deep Frozen Ocean", 0xFF7BBDF5, BiomeKeys.DEEP_FROZEN_OCEAN),
        BADLANDS         ("Badlands",          0xFFD9905C, BiomeKeys.BADLANDS),
        JUNGLE           ("Jungle",            0xFF49B349, BiomeKeys.JUNGLE),
        SAVANNA          ("Savanna",           0xFFBDB25F, BiomeKeys.SAVANNA);

        public final String label;
        public final int color;
        public final RegistryKey<Biome> key;
        BiomeType(String label, int color, RegistryKey<Biome> key) {
            this.label = label; this.color = color; this.key = key;
        }
    }

    public static final class Found {
        public final BiomeType type;
        public final BlockPos pos;
        public final String dimension;
        public Found(BiomeType type, BlockPos pos, String dimension) {
            this.type = type; this.pos = pos; this.dimension = dimension;
        }
    }

    private static final List<Found> found = new ArrayList<>();
    private static final Map<String, Set<Long>> scannedChunksByDim = new HashMap<>();
    private static String currentWorldKey = "";

    // Biomes are big — one cherry-grove blob can span dozens of chunks.
    // Cluster aggressively so each blob gets one entry, not 50.
    private static final double CLUSTER_DISTANCE = 256.0;
    private static int tickCounter = 0;

    private BiomeTracker() {}

    public static void register() {
        try {
            ClientChunkEvents.CHUNK_LOAD.register(BiomeTracker::onChunkLoad);
        } catch (Throwable t) {
            System.out.println("[IceyMod] BiomeTracker chunk-load unavailable: " + t.getMessage());
        }
        try {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                tickCounter++;
                if (tickCounter < 20) return;
                tickCounter = 0;
                BiomeLocatorModule mod = getModule();
                if (mod == null || !mod.isEnabled()) return;
                rescanNearby();
            });
        } catch (Throwable t) {
            System.out.println("[IceyMod] BiomeTracker tick unavailable: " + t.getMessage());
        }
    }

    public static List<Found> getFound() {
        synchronized (found) { return new ArrayList<>(found); }
    }

    public static void clear() {
        synchronized (found) {
            found.removeIf(f -> f.dimension.equals(currentWorldKey));
            Set<Long> dimSet = scannedChunksByDim.get(currentWorldKey);
            if (dimSet != null) dimSet.clear();
        }
    }

    public static void remove(Found f) {
        if (f == null) return;
        synchronized (found) { found.remove(f); }
    }

    public static void rescanNearby() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null || c.player == null) return;
            resetIfWorldChanged();
            int cx = c.player.getBlockX() >> 4;
            int cz = c.player.getBlockZ() >> 4;
            int radius = c.options.getViewDistance().getValue() + 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    WorldChunk chunk = c.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                    if (chunk == null) continue;
                    onChunkLoad(c.world, chunk);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static BiomeLocatorModule getModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof BiomeLocatorModule bm) return bm;
        }
        return null;
    }

    private static void resetIfWorldChanged() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.world == null) return;
        String key = c.world.getRegistryKey().getValue().toString();
        if (!key.equals(currentWorldKey)) {
            currentWorldKey = key;
            // keep findings across dim switches, same as StructureTracker
        }
    }

    private static void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        try {
            BiomeLocatorModule mod = getModule();
            if (mod == null || !mod.isEnabled()) return;
            resetIfWorldChanged();
            String dim = world.getRegistryKey().getValue().toString();
            long key = chunk.getPos().toLong();
            synchronized (found) {
                Set<Long> dimSet = scannedChunksByDim.computeIfAbsent(dim, k -> new HashSet<>());
                if (!dimSet.add(key)) return;
            }

            int bx = chunk.getPos().getStartX() + 8;
            int bz = chunk.getPos().getStartZ() + 8;
            BlockPos sample = new BlockPos(bx, 64, bz);
            var biomeEntry = world.getBiome(sample);

            for (BiomeType type : BiomeType.values()) {
                if (!mod.isTypeEnabled(type)) continue;
                if (biomeEntry.matchesKey(type.key)) {
                    addIfNew(type, sample, dim, mod.autoWaypoint.get());
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void addIfNew(BiomeType type, BlockPos pos, String dimension, boolean autoWaypoint) {
        boolean wasNew = false;
        synchronized (found) {
            for (Found f : found) {
                if (f.type != type) continue;
                if (!f.dimension.equals(dimension)) continue;
                double dx = f.pos.getX() - pos.getX();
                double dz = f.pos.getZ() - pos.getZ();
                if (dx * dx + dz * dz < CLUSTER_DISTANCE * CLUSTER_DISTANCE) return;
            }
            found.add(new Found(type, pos, dimension));
            wasNew = true;
        }
        if (wasNew) {
            try {
                MinecraftClient c = MinecraftClient.getInstance();
                if (c != null && c.player != null) {
                    c.player.sendMessage(net.minecraft.text.Text.literal(
                            "§b[IceyClient] §a" + type.label + " biome found! §8(" +
                                    pos.getX() + ", ~, " + pos.getZ() + ")"), false);
                }
            } catch (Throwable ignored) {}
            if (autoWaypoint) {
                try {
                    // Same dedup logic as structures, larger radius
                    // since biomes can stretch hundreds of blocks.
                    WaypointManager.addWaypointIfNew(type.label,
                            pos.getX(), 64, pos.getZ(), type.color, 256.0);
                } catch (Throwable ignored) {}
            }
        }
    }

    public static List<Found> getSortedByDistance() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null || c.world == null) return Collections.emptyList();
        String dim = c.world.getRegistryKey().getValue().toString();
        double px = c.player.getX(), pz = c.player.getZ();
        List<Found> all = getFound();
        List<Found> copy = new ArrayList<>(all.size());
        for (Found f : all) if (f.dimension.equals(dim)) copy.add(f);
        copy.sort((a, b) -> {
            double da = (a.pos.getX() - px) * (a.pos.getX() - px) + (a.pos.getZ() - pz) * (a.pos.getZ() - pz);
            double db = (b.pos.getX() - px) * (b.pos.getX() - px) + (b.pos.getZ() - pz) * (b.pos.getZ() - pz);
            return Double.compare(da, db);
        });
        return copy;
    }
}
