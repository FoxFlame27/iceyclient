package com.iceysmp;

/**
 * Per-player counters. Mutable, accessed from the server thread only — all
 * StatTracker writes happen inside Fabric event callbacks that fire on the
 * server tick thread, so no lock needed.
 */
public final class PlayerStats {
    public String name;
    public long mining;
    public long pvpKills;
    public long playtimeTicks;

    public PlayerStats(String name) {
        this.name = name;
    }
}
