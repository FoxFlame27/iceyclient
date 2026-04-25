package com.iceymod.structure;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.StructureLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BellBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects structures in loaded chunks by scanning block entities — trial
 * spawners and vaults only exist inside trial chambers, end portals only
 * inside strongholds, so each hit is a reliable sighting. Results are
 * deduped by distance (80-block clustering) so one chamber maps to one
 * entry, not fifty.
 *
 * Hooked into {@link ClientChunkEvents#CHUNK_LOAD}, so every new chunk is
 * scanned exactly once. When the world changes, the found-list resets.
 */
public final class StructureTracker {

    public enum StructureType {
        TRIAL_CHAMBER   ("Trial Chamber",   0xFFFF8800),
        STRONGHOLD      ("Stronghold",      0xFFBB66FF),
        PLAYER_BASE     ("Player Base",     0xFF55FF55),
        NETHER_FORTRESS ("Nether Fortress", 0xFF8B1A1A),
        BASTION         ("Bastion Remnant", 0xFFC78A3E),
        END_CITY        ("End City",        0xFFE2CEF2),
        END_GATEWAY     ("End Gateway",     0xFF9966FF),
        OCEAN_MONUMENT  ("Ocean Monument",  0xFF50C7E8),
        ANCIENT_CITY    ("Ancient City",    0xFF33FFAA),
        RUINED_PORTAL   ("Ruined Portal",   0xFFAA00FF),
        DESERT_PYRAMID  ("Desert Pyramid",  0xFFE8D17A),
        VILLAGE         ("Village",         0xFFCCAA77);

        public final String label;
        public final int color;
        StructureType(String label, int color) { this.label = label; this.color = color; }
    }

    public static final class Found {
        public final StructureType type;
        public final BlockPos pos;
        public final String dimension;
        public Found(StructureType type, BlockPos pos, String dimension) {
            this.type = type;
            this.pos = pos;
            this.dimension = dimension;
        }
    }

    // Everything is per-dimension so a Nether trip doesn't wipe your
    // Overworld findings and vice versa.
    private static final List<Found> found = new ArrayList<>();
    private static final Map<String, Set<Long>> scannedChunksByDim = new HashMap<>();
    private static String currentWorldKey = "";

    // Structures closer than this (in blocks) are considered the same structure.
    private static final double CLUSTER_DISTANCE = 80.0;

    private StructureTracker() {}

    private static int tickCounter = 0;

    public static void register() {
        // Primary detection path: react to chunks as they load.
        try {
            ClientChunkEvents.CHUNK_LOAD.register(StructureTracker::onChunkLoad);
        } catch (Throwable t) {
            System.out.println("[IceyMod] ClientChunkEvents.CHUNK_LOAD unavailable — structure locator will rely on periodic tick rescan: " + t.getMessage());
        }
        // Fallback path: every second, re-scan chunks in render range.
        // Chunks already scanned are deduped via scannedChunksByDim, so this
        // is effectively a no-op for already-seen chunks. It covers the
        // case where CHUNK_LOAD never fires (Fabric API package changes on
        // newer MC versions, for example).
        try {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                tickCounter++;
                if (tickCounter < 20) return;
                tickCounter = 0;
                StructureLocatorModule mod = getModule();
                if (mod == null || !mod.isEnabled()) return;
                rescanNearby();
            });
        } catch (Throwable t) {
            System.out.println("[IceyMod] ClientTickEvents unavailable — periodic structure rescan disabled: " + t.getMessage());
        }
    }

    public static List<Found> getFound() {
        synchronized (found) {
            return new ArrayList<>(found);
        }
    }

    /** Clears findings + scan state for the current dimension only. */
    public static void clear() {
        synchronized (found) {
            found.removeIf(f -> f.dimension.equals(currentWorldKey));
            Set<Long> dimSet = scannedChunksByDim.get(currentWorldKey);
            if (dimSet != null) dimSet.clear();
        }
    }

    /** Clears findings + scan state across every dimension. */
    public static void clearAll() {
        synchronized (found) {
            found.clear();
            scannedChunksByDim.clear();
        }
    }

    public static void remove(Found f) {
        if (f == null) return;
        synchronized (found) {
            found.remove(f);
        }
    }

    /**
     * Scan every chunk currently loaded around the player. Called when the
     * module is toggled on, so a player already standing in a trial chamber
     * doesn't have to walk out and back in to see the chamber show up.
     */
    public static void rescanNearby() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null || c.player == null) return;
            resetIfWorldChanged();

            int cx = c.player.getBlockX() >> 4;
            int cz = c.player.getBlockZ() >> 4;
            int radius = c.options.getViewDistance().getValue();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    WorldChunk chunk = c.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                    if (chunk == null) continue;
                    onChunkLoad(c.world, chunk);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void resetIfWorldChanged() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.world == null) return;
        String key = c.world.getRegistryKey().getValue().toString();
        if (!key.equals(currentWorldKey)) {
            currentWorldKey = key;
            // Don't clear — findings + scanned-chunks persist per dimension.
        }
    }

    private static StructureLocatorModule getModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof StructureLocatorModule sm) return sm;
        }
        return null;
    }

    private static void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        try {
            StructureLocatorModule mod = getModule();
            if (mod == null || !mod.isEnabled()) return;

            resetIfWorldChanged();
            String dim = world.getRegistryKey().getValue().toString();

            long key = chunk.getPos().toLong();
            synchronized (found) {
                Set<Long> dimSet = scannedChunksByDim.computeIfAbsent(dim, k -> new HashSet<>());
                if (!dimSet.add(key)) return;
            }

            boolean autoWp = mod.autoWaypoint.get();
            boolean isNether = dim.contains("nether");
            boolean isEnd    = dim.contains("the_end");
            boolean isOver   = !isNether && !isEnd;

            // --- Block-entity based detections (cheap — no block iteration) ---
            boolean trackTrial = mod.trialChambers.get();
            boolean trackStronghold = mod.strongholds.get();
            boolean trackBase = mod.playerBases.get();
            boolean trackVillage = mod.villages.get();
            boolean trackGateway = mod.endGateways.get();

            for (BlockEntity be : chunk.getBlockEntities().values()) {
                StructureType type = null;
                if (trackTrial && (be instanceof TrialSpawnerBlockEntity || be instanceof VaultBlockEntity)) {
                    type = StructureType.TRIAL_CHAMBER;
                } else if (trackStronghold && be instanceof EndPortalBlockEntity) {
                    type = StructureType.STRONGHOLD;
                } else if (trackBase && (be instanceof EnderChestBlockEntity
                        || be instanceof ShulkerBoxBlockEntity
                        || be instanceof BeaconBlockEntity)) {
                    type = StructureType.PLAYER_BASE;
                } else if (trackVillage && be instanceof BellBlockEntity) {
                    type = StructureType.VILLAGE;
                } else if (trackGateway && be instanceof EndGatewayBlockEntity) {
                    type = StructureType.END_GATEWAY;
                }
                if (type != null) addIfNew(type, be.getPos(), dim, autoWp);
            }

            // --- Block-sample detections (unique signature blocks) ---
            if (isNether && mod.netherFortresses.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.NETHER_BRICK_FENCE), 20, 100, 4);
                if (hit != null) addIfNew(StructureType.NETHER_FORTRESS, hit, dim, autoWp);
            }
            if (isNether && mod.bastions.get()) {
                BlockPos hit = scanChunkForBlock(chunk,
                        s -> s.isOf(Blocks.LODESTONE) || s.isOf(Blocks.GILDED_BLACKSTONE),
                        20, 120, 4);
                if (hit != null) addIfNew(StructureType.BASTION, hit, dim, autoWp);
            }
            if (isEnd && mod.endCities.get()) {
                // Maximize End-city detection: every chunk fragment of an
                // outer-end island that gets sent to the client gets
                // sampled with a finer step (2 instead of 4) over a wider
                // Y range (30-110 covers low islands + tall city spires),
                // and we accept ANY of the 5 unique end-city blocks —
                // pillars are sparse, but purpur_block + end_stone_bricks
                // make up most of the city walls/floors.
                BlockPos hit = scanChunkForBlock(chunk,
                        s -> s.isOf(Blocks.PURPUR_PILLAR)
                          || s.isOf(Blocks.PURPUR_BLOCK)
                          || s.isOf(Blocks.PURPUR_STAIRS)
                          || s.isOf(Blocks.PURPUR_SLAB)
                          || s.isOf(Blocks.END_STONE_BRICKS),
                        30, 110, 2);
                if (hit != null) addIfNew(StructureType.END_CITY, hit, dim, autoWp);
            }
            if (isOver && mod.oceanMonuments.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.PRISMARINE_BRICKS), 39, 60, 4);
                if (hit != null) addIfNew(StructureType.OCEAN_MONUMENT, hit, dim, autoWp);
            }
            if (isOver && mod.ancientCities.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.REINFORCED_DEEPSLATE), -55, -25, 4);
                if (hit != null) addIfNew(StructureType.ANCIENT_CITY, hit, dim, autoWp);
            }
            if (mod.ruinedPortals.get()) {
                int yMin = isNether ? 10 : 30;
                int yMax = isNether ? 100 : 120;
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.CRYING_OBSIDIAN), yMin, yMax, 4);
                if (hit != null) addIfNew(StructureType.RUINED_PORTAL, hit, dim, autoWp);
            }
            if (isOver && mod.desertPyramids.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.CHISELED_SANDSTONE), 60, 85, 4);
                if (hit != null) addIfNew(StructureType.DESERT_PYRAMID, hit, dim, autoWp);
            }
        } catch (Throwable t) {
            // Never crash the chunk pipeline — just drop the scan for this chunk.
        }
    }

    private static void addIfNew(StructureType type, BlockPos pos, String dimension, boolean autoWaypoint) {
        synchronized (found) {
            for (Found f : found) {
                if (f.type != type) continue;
                if (!f.dimension.equals(dimension)) continue;
                double dx = f.pos.getX() - pos.getX();
                double dy = f.pos.getY() - pos.getY();
                double dz = f.pos.getZ() - pos.getZ();
                if (dx * dx + dy * dy + dz * dz < CLUSTER_DISTANCE * CLUSTER_DISTANCE) return;
            }
            found.add(new Found(type, pos, dimension));
        }
        if (autoWaypoint) {
            try {
                WaypointManager.addWaypoint(type.label, pos.getX(), pos.getY(), pos.getZ());
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Sorted-by-distance view for the HUD, filtered to the current
     * dimension so Nether entries don't pollute an Overworld list.
     */
    public static List<Found> getSortedByDistance() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null || c.world == null) return Collections.emptyList();
        String dim = c.world.getRegistryKey().getValue().toString();
        double px = c.player.getX(), py = c.player.getY(), pz = c.player.getZ();
        List<Found> all = getFound();
        List<Found> copy = new ArrayList<>(all.size());
        for (Found f : all) {
            if (f.dimension.equals(dim)) copy.add(f);
        }
        copy.sort((a, b) -> {
            double da = distSq(a.pos, px, py, pz);
            double db = distSq(b.pos, px, py, pz);
            return Double.compare(da, db);
        });
        return copy;
    }

    private static double distSq(BlockPos p, double x, double y, double z) {
        double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Coarse-grid sample of a chunk for a specific signature block. Returns
     * the first matching BlockPos, or null. Grid step trades precision for
     * speed — step=4 = 16 horizontal samples per chunk (covers 16x16 block
     * chunk reliably for any structure bigger than a 4x4 tile).
     */
    private static BlockPos scanChunkForBlock(WorldChunk chunk,
                                              java.util.function.Predicate<BlockState> match,
                                              int yMin, int yMax, int step) {
        try {
            int baseX = chunk.getPos().getStartX();
            int baseZ = chunk.getPos().getStartZ();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int y = yMin; y <= yMax; y += step) {
                for (int dx = 0; dx < 16; dx += step) {
                    for (int dz = 0; dz < 16; dz += step) {
                        pos.set(baseX + dx, y, baseZ + dz);
                        BlockState state = chunk.getBlockState(pos);
                        if (match.test(state)) return new BlockPos(baseX + dx, y, baseZ + dz);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
