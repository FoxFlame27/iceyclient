package com.iceymod.structure;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.StructureLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.world.biome.BiomeKeys;
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

    // Structures closer than this (in blocks) are considered the same
    // structure (50 m as the default; End uses 40 m since cities/ships
    // are smaller and more numerous).
    private static final double CLUSTER_DISTANCE = 50.0;

    private StructureTracker() {}

    private static int tickCounter = 0;
    private static double lastPlayerX = Double.NaN, lastPlayerY = Double.NaN, lastPlayerZ = Double.NaN;

    public static void register() {
        // Primary detection path: react to chunks as they load.
        try {
            ClientChunkEvents.CHUNK_LOAD.register(StructureTracker::onChunkLoad);
        } catch (Throwable t) {
            System.out.println("[IceyMod] ClientChunkEvents.CHUNK_LOAD unavailable — structure locator will rely on periodic tick rescan: " + t.getMessage());
        }
        // Fallback path: periodic tick rescan. Chunks already scanned are
        // deduped via scannedChunksByDim, so this is a near-no-op for
        // already-seen chunks. The End scans 4× as often as overworld so
        // newly-loaded outer-end chunks register the moment they arrive.
        try {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                tickCounter++;
                int threshold = currentWorldKey.contains("the_end") ? 5 : 20;
                if (tickCounter < threshold) return;
                tickCounter = 0;
                StructureLocatorModule mod = getModule();
                if (mod == null || !mod.isEnabled()) return;
                rescanNearby();
                detectEndTeleport(client);
                detectShulkers(client);
            });
        } catch (Throwable t) {
            System.out.println("[IceyMod] ClientTickEvents unavailable — periodic structure rescan disabled: " + t.getMessage());
        }
    }

    /**
     * Shulkers only spawn naturally inside End Cities. If we see one in
     * the loaded entity list, that's a 100% reliable End-City marker —
     * even on chunks where the block-sample scan didn't catch any
     * purpur. Cheap iteration: typical chunk has &lt;50 entities, and
     * we only do this once per second.
     */
    private static void detectShulkers(net.minecraft.client.MinecraftClient client) {
        try {
            if (client == null || client.world == null) return;
            String dim = client.world.getRegistryKey().getValue().toString();
            if (!dim.contains("the_end")) return;
            StructureLocatorModule mod = getModule();
            if (mod == null || !mod.endCities.get()) return;
            for (Entity e : client.world.getEntities()) {
                if (e instanceof ShulkerEntity) {
                    addIfNew(StructureType.END_CITY, e.getBlockPos(), dim, mod.autoWaypoint.get());
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Drop an "End Anchor" waypoint when the player teleports a long way
     * inside the End — i.e. through an end gateway. Lets the user return
     * to outer-end islands they've visited without re-rolling RNG.
     */
    private static void detectEndTeleport(net.minecraft.client.MinecraftClient client) {
        try {
            if (client == null || client.player == null || client.world == null) return;
            String dim = client.world.getRegistryKey().getValue().toString();
            double x = client.player.getX(), y = client.player.getY(), z = client.player.getZ();
            if (dim.contains("the_end") && !Double.isNaN(lastPlayerX)) {
                double dx = x - lastPlayerX, dy = y - lastPlayerY, dz = z - lastPlayerZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > 300 * 300) {
                    WaypointManager.addWaypoint("End Anchor", (int) x, (int) y, (int) z);
                    if (client.player != null) {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§b[IceyClient] §aEnd Anchor waypointed §8(" +
                                        (int) x + ", " + (int) y + ", " + (int) z + ")"),
                                false);
                    }
                }
            }
            lastPlayerX = x; lastPlayerY = y; lastPlayerZ = z;
        } catch (Throwable ignored) {}
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
            // Go beyond the client's view distance: servers sometimes
            // lazy-send extra chunks (simulation distance + neighbor
            // pre-load) that wouldn't be in our render set. Loop a bit
            // wider and let getWorldChunk null-skip the unloaded ones —
            // null check is O(1) so the extra iterations are free.
            int viewRadius = c.options.getViewDistance().getValue();
            int radius = viewRadius + 4;

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
            // minHits gates how many sample matches we require before
            // calling it a structure — a single match is usually a
            // player-placed block, multiple in one chunk = real struct.
            if (isNether && mod.netherFortresses.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.NETHER_BRICK_FENCE), 20, 100, 4, 3);
                if (hit != null) addIfNew(StructureType.NETHER_FORTRESS, hit, dim, autoWp);
            }
            if (isNether && mod.bastions.get()) {
                BlockPos hit = scanChunkForBlock(chunk,
                        s -> s.isOf(Blocks.LODESTONE) || s.isOf(Blocks.GILDED_BLACKSTONE),
                        20, 120, 4, 2);
                if (hit != null) addIfNew(StructureType.BASTION, hit, dim, autoWp);
            }
            if (isEnd && mod.endCities.get()) {
                // Biome-gated max sensitivity:
                //   - END_HIGHLANDS / END_MIDLANDS = the only biomes
                //     where end cities can spawn. Scan EVERY block
                //     (step=1) over full Y (0-128), trigger on a single
                //     hit. Catches even one purpur block sticking out
                //     of an island fragment. About 8× the scan work of
                //     before but still well under 100us per chunk.
                //   - Anywhere else = skip entirely. Cities can't spawn
                //     so no need to waste cycles or risk false hits.
                int chunkCenterX = chunk.getPos().getStartX() + 8;
                int chunkCenterZ = chunk.getPos().getStartZ() + 8;
                boolean rightBiome = false;
                try {
                    var biome = world.getBiome(new BlockPos(chunkCenterX, 64, chunkCenterZ));
                    rightBiome = biome.matchesKey(BiomeKeys.END_HIGHLANDS)
                              || biome.matchesKey(BiomeKeys.END_MIDLANDS);
                } catch (Throwable ignored) {}
                if (rightBiome) {
                    BlockPos hit = scanChunkForBlock(chunk,
                            s -> s.isOf(Blocks.PURPUR_PILLAR)
                              || s.isOf(Blocks.PURPUR_BLOCK)
                              || s.isOf(Blocks.PURPUR_STAIRS)
                              || s.isOf(Blocks.PURPUR_SLAB)
                              || s.isOf(Blocks.END_STONE_BRICKS),
                            0, 128, 1, 1);
                    if (hit != null) addIfNew(StructureType.END_CITY, hit, dim, autoWp);
                }
            }
            if (isOver && mod.oceanMonuments.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.PRISMARINE_BRICKS), 39, 60, 4, 3);
                if (hit != null) addIfNew(StructureType.OCEAN_MONUMENT, hit, dim, autoWp);
            }
            if (isOver && mod.ancientCities.get()) {
                // reinforced_deepslate is genuinely unique — a single hit is enough
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.REINFORCED_DEEPSLATE), -55, -25, 4, 1);
                if (hit != null) addIfNew(StructureType.ANCIENT_CITY, hit, dim, autoWp);
            }
            if (mod.ruinedPortals.get()) {
                int yMin = isNether ? 10 : 30;
                int yMax = isNether ? 100 : 120;
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.CRYING_OBSIDIAN), yMin, yMax, 4, 2);
                if (hit != null) addIfNew(StructureType.RUINED_PORTAL, hit, dim, autoWp);
            }
            if (isOver && mod.desertPyramids.get()) {
                BlockPos hit = scanChunkForBlock(chunk, s -> s.isOf(Blocks.CHISELED_SANDSTONE), 60, 85, 4, 2);
                if (hit != null) addIfNew(StructureType.DESERT_PYRAMID, hit, dim, autoWp);
            }
        } catch (Throwable t) {
            // Never crash the chunk pipeline — just drop the scan for this chunk.
        }
    }

    private static void addIfNew(StructureType type, BlockPos pos, String dimension, boolean autoWaypoint) {
        // Tighter clustering in the End so a city + its end ship don't
        // collapse into a single entry — they're typically 50-80 blocks
        // apart, and we want both as separate waypoints.
        double clusterDist = dimension.contains("the_end") ? 40.0 : CLUSTER_DISTANCE;
        double clusterSq = clusterDist * clusterDist;

        boolean wasNew = false;
        synchronized (found) {
            for (Found f : found) {
                if (f.type != type) continue;
                if (!f.dimension.equals(dimension)) continue;
                double dx = f.pos.getX() - pos.getX();
                double dy = f.pos.getY() - pos.getY();
                double dz = f.pos.getZ() - pos.getZ();
                if (dx * dx + dy * dy + dz * dz < clusterSq) return;
            }
            found.add(new Found(type, pos, dimension));
            wasNew = true;
        }
        if (wasNew) {
            // Action-bar ping so the user actually NOTICES a new find,
            // even if their HUD widget is off-screen or the entry sorts
            // far down the list.
            try {
                net.minecraft.client.MinecraftClient c = net.minecraft.client.MinecraftClient.getInstance();
                if (c != null && c.player != null) {
                    // Real chat message (overlay=false) so it's persistent
                    // in the chat log, not just a fading action-bar line.
                    c.player.sendMessage(net.minecraft.text.Text.literal(
                            "§b[IceyClient] §a" + type.label + " found! §8(" +
                                    pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"), false);
                }
            } catch (Throwable ignored) {}
            if (autoWaypoint) {
                try {
                    // Dedup at 100 m — a Trial Chamber detected from
                    // multiple BEs in the same area should produce one
                    // waypoint, not many.
                    WaypointManager.addWaypointIfNew(type.label,
                            pos.getX(), pos.getY(), pos.getZ(), type.color, 100.0);
                } catch (Throwable ignored) {}
            }
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
     * the first matching BlockPos, or null. Equivalent to
     * scanChunkForBlock(chunk, match, yMin, yMax, step, 1).
     */
    private static BlockPos scanChunkForBlock(WorldChunk chunk,
                                              java.util.function.Predicate<BlockState> match,
                                              int yMin, int yMax, int step) {
        return scanChunkForBlock(chunk, match, yMin, yMax, step, 1);
    }

    /**
     * Returns first matching BlockPos only if at least {@code minHits}
     * sample positions in the chunk match the predicate. Used for
     * structures whose signature block can also be player-placed
     * (purpur, crying_obsidian, etc.) — a single hit is probably a
     * player build, but several hits in one chunk reliably mean a
     * naturally-generated structure.
     */
    private static BlockPos scanChunkForBlock(WorldChunk chunk,
                                              java.util.function.Predicate<BlockState> match,
                                              int yMin, int yMax, int step,
                                              int minHits) {
        try {
            int baseX = chunk.getPos().getStartX();
            int baseZ = chunk.getPos().getStartZ();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            int hits = 0;
            BlockPos firstHit = null;
            for (int y = yMin; y <= yMax; y += step) {
                for (int dx = 0; dx < 16; dx += step) {
                    for (int dz = 0; dz < 16; dz += step) {
                        pos.set(baseX + dx, y, baseZ + dz);
                        BlockState state = chunk.getBlockState(pos);
                        if (match.test(state)) {
                            hits++;
                            if (firstHit == null) firstHit = new BlockPos(baseX + dx, y, baseZ + dz);
                            if (hits >= minHits) return firstHit;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
