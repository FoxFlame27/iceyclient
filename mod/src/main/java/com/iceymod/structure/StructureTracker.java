package com.iceymod.structure;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.StructureLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
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
import java.util.HashSet;
import java.util.List;
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
        TRIAL_CHAMBER("Trial Chamber", 0xFFFF8800),
        STRONGHOLD("Stronghold",       0xFFBB66FF),
        PLAYER_BASE("Player Base",     0xFF55FF55);

        public final String label;
        public final int color;
        StructureType(String label, int color) { this.label = label; this.color = color; }
    }

    public static final class Found {
        public final StructureType type;
        public final BlockPos pos;
        public Found(StructureType type, BlockPos pos) {
            this.type = type;
            this.pos = pos;
        }
    }

    private static final List<Found> found = new ArrayList<>();
    private static final Set<Long> scannedChunks = new HashSet<>();
    private static String currentWorldKey = "";

    // Structures closer than this (in blocks) are considered the same structure.
    private static final double CLUSTER_DISTANCE = 80.0;

    private StructureTracker() {}

    public static void register() {
        try {
            ClientChunkEvents.CHUNK_LOAD.register(StructureTracker::onChunkLoad);
        } catch (Throwable t) {
            System.out.println("[IceyMod] ClientChunkEvents unavailable — structure locator disabled: " + t.getMessage());
        }
    }

    public static List<Found> getFound() {
        synchronized (found) {
            return new ArrayList<>(found);
        }
    }

    public static void clear() {
        synchronized (found) {
            found.clear();
            scannedChunks.clear();
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
            clear();
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

            long key = chunk.getPos().toLong();
            synchronized (found) {
                if (!scannedChunks.add(key)) return;
            }

            boolean trackTrial = mod.trialChambers.get();
            boolean trackStronghold = mod.strongholds.get();
            boolean trackBase = mod.playerBases.get();

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
                }
                if (type == null) continue;

                BlockPos pos = be.getPos();
                addIfNew(type, pos, mod.autoWaypoint.get());
            }
        } catch (Throwable t) {
            // Never crash the chunk pipeline — just drop the scan for this chunk.
        }
    }

    private static void addIfNew(StructureType type, BlockPos pos, boolean autoWaypoint) {
        synchronized (found) {
            for (Found f : found) {
                if (f.type != type) continue;
                double dx = f.pos.getX() - pos.getX();
                double dy = f.pos.getY() - pos.getY();
                double dz = f.pos.getZ() - pos.getZ();
                if (dx * dx + dy * dy + dz * dz < CLUSTER_DISTANCE * CLUSTER_DISTANCE) return;
            }
            found.add(new Found(type, pos));
        }
        if (autoWaypoint) {
            try {
                WaypointManager.addWaypoint(type.label, pos.getX(), pos.getY(), pos.getZ());
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Sorted-by-distance view for the HUD.
     */
    public static List<Found> getSortedByDistance() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null) return Collections.emptyList();
        double px = c.player.getX(), py = c.player.getY(), pz = c.player.getZ();
        List<Found> copy = getFound();
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
}
