package com.iceymod.structure;

import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Predicts likely structure locations from a world seed using vanilla's
 * region-grid placement algorithm.
 *
 * Vanilla every-region structure placement, simplified:
 *   region = floor(chunk / spacing)
 *   offset = (random.nextInt(spacing - separation), nextInt(spacing - separation))
 *   chunk  = region * spacing + offset
 *
 * The random source is a fresh java.util.Random seeded with
 *   worldSeed
 *     + regionX * 341873128712L
 *     + regionZ * 132897987541L
 *     + salt
 * which mirrors {@code WorldgenRandom.setLargeFeatureWithSalt(...)}.
 *
 * The chunk this returns is a CANDIDATE — vanilla also runs biome and
 * terrain checks before actually placing the structure. We emit every
 * candidate inside the radius and let the player visit / verify; the
 * candidates we miss are the ones whose biome failed (rare for End
 * Cities since outer-end is uniform).
 */
public final class SeedPredictor {

    public static final long REGION_X_MULT = 341873128712L;
    public static final long REGION_Z_MULT = 132897987541L;

    public static final int END_CITY_SPACING    = 20;
    public static final int END_CITY_SEPARATION = 11;
    public static final int END_CITY_SALT       = 10387313;

    // The outer-end ring starts ~chunk 64 from origin (block 1024).
    public static final int OUTER_END_MIN_CHUNK = 64;

    private SeedPredictor() {}

    /**
     * Returns candidate End-City start-chunks within the given block
     * radius from origin. The list is sorted by Chebyshev distance so
     * the closest candidates come first.
     */
    public static List<ChunkPos> predictEndCities(long worldSeed, int blockRadius) {
        int regionRadius = (blockRadius / 16 / END_CITY_SPACING) + 2;
        List<ChunkPos> out = new ArrayList<>();
        for (int rx = -regionRadius; rx <= regionRadius; rx++) {
            for (int rz = -regionRadius; rz <= regionRadius; rz++) {
                ChunkPos cp = getRegionStartChunk(worldSeed, rx, rz,
                        END_CITY_SPACING, END_CITY_SEPARATION, END_CITY_SALT);
                int chunkDist = Math.max(Math.abs(cp.x), Math.abs(cp.z));
                if (chunkDist < OUTER_END_MIN_CHUNK) continue;
                int blockDist = chunkDist * 16;
                if (blockDist > blockRadius) continue;
                out.add(cp);
            }
        }
        out.sort((a, b) -> {
            int da = Math.max(Math.abs(a.x), Math.abs(a.z));
            int db = Math.max(Math.abs(b.x), Math.abs(b.z));
            return Integer.compare(da, db);
        });
        return out;
    }

    /**
     * Region-stride placement core. Reusable for other structure types
     * (strongholds, monuments, mansions all use this same shape with
     * different spacing/separation/salt — exposed for future predictors).
     */
    public static ChunkPos getRegionStartChunk(long worldSeed, int regionX, int regionZ,
                                               int spacing, int separation, int salt) {
        Random random = new Random();
        random.setSeed(worldSeed
                + (long) regionX * REGION_X_MULT
                + (long) regionZ * REGION_Z_MULT
                + (long) salt);
        int spread = Math.max(1, spacing - separation);
        int offX = random.nextInt(spread);
        int offZ = random.nextInt(spread);
        return new ChunkPos(regionX * spacing + offX, regionZ * spacing + offZ);
    }
}
