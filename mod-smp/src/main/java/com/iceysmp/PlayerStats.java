package com.iceysmp;

/**
 * Per-player counters. Mutable, accessed from the server thread only — all
 * StatTracker writes happen inside Fabric event callbacks that fire on the
 * server tick thread, so no lock is needed.
 *
 * <p>{@code firstJoinTimestamp} is the millis-epoch the player first joined.
 * Used for noob protection ({@link IceySmp#config}.noobProtectionMinutes).
 */
public final class PlayerStats {
    public String name;
    public long firstJoinTimestamp;

    // Stealable counters (transferred to killer on PvP death):
    public long mining;
    public long pvpKills;
    public long playtimeTicks;
    public long mobKills;        // hostile mobs
    public long animalKills;     // peaceful mobs (cows, sheep, etc.)
    public long crops;           // wheat/carrots/potatoes/beetroot/melon/pumpkin
    public long diamonds;        // diamond ore (both variants)
    public long woodChopped;     // logs of any kind
    public long damageDealt;     // total HP dealt (× 10, since damage is float)
    public long damageTaken;     // total HP taken
    public long deaths;          // own deaths
    // New in v1.82 — read from MC's StatHandler via per-tick deltas:
    public long fishCaught;
    public long distanceWalkedCm;
    public long jumps;
    public long xpLevelsGained;
    public long sneakTimeTicks;
    /** Sum of SWIM + WALK_UNDER_WATER + WALK_ON_WATER from MC stats, cm. */
    public long distanceInWaterCm;

    /** Semicolon-separated set of category IDs the player has already
     *  received a max-level reward (Frostfang) for. So if they hit Lv max
     *  in Mining twice (e.g. through a reset), they don't get two swords. */
    public String frostfangAwardedFor = "";

    /** Sum of XP-level bounties placed on this player by others. Paid out
     *  (and zeroed) to whoever kills them in legitimate PvP. */
    public int bountyXp = 0;

    /** Epoch-millis of the last /icey daily roll. Cooldown is 14h. */
    public long lastDailyMs = 0L;

    /** True after a successful /admin &lt;password&gt; — unlocks /reward,
     *  /crate, /setspawn, /noobprotect without needing real op perms.
     *  Works on singleplayer worlds where /op doesn't grant anything. */
    public boolean adminAccess = false;

    public PlayerStats(String name) {
        this.name = name;
    }

    public boolean wasAwardedFrostfangFor(String categoryId) {
        if (frostfangAwardedFor == null || frostfangAwardedFor.isEmpty()) return false;
        for (String s : frostfangAwardedFor.split(";")) if (s.equals(categoryId)) return true;
        return false;
    }

    public void markFrostfangAwardedFor(String categoryId) {
        if (wasAwardedFrostfangFor(categoryId)) return;
        if (frostfangAwardedFor == null || frostfangAwardedFor.isEmpty()) frostfangAwardedFor = categoryId;
        else frostfangAwardedFor = frostfangAwardedFor + ";" + categoryId;
    }

    /** Stealable categories — fields the killer absorbs on PvP kill. */
    public static final String[] STEALABLE_FIELDS = {
            "mining", "pvpKills", "playtimeTicks", "mobKills", "animalKills",
            "crops", "diamonds", "woodChopped", "damageDealt", "damageTaken", "deaths"
    };

    /** Transfer 10% of each stealable counter from {@code victim} to this
     *  stats block. Per user request — full transfer was too punishing,
     *  the victim keeps the other 90% and stays useful after a death.
     *  Called only on PvP kill, never on environmental death. */
    public void absorbFrom(PlayerStats victim) {
        if (victim == this) return;
        this.mining            += takeTenth(victim, "mining");
        this.pvpKills          += takeTenth(victim, "pvpKills");
        this.playtimeTicks     += takeTenth(victim, "playtimeTicks");
        this.mobKills          += takeTenth(victim, "mobKills");
        this.animalKills       += takeTenth(victim, "animalKills");
        this.crops             += takeTenth(victim, "crops");
        this.diamonds          += takeTenth(victim, "diamonds");
        this.woodChopped       += takeTenth(victim, "woodChopped");
        this.damageDealt       += takeTenth(victim, "damageDealt");
        this.damageTaken       += takeTenth(victim, "damageTaken");
        this.deaths            += takeTenth(victim, "deaths");
        this.fishCaught        += takeTenth(victim, "fishCaught");
        this.distanceWalkedCm  += takeTenth(victim, "distanceWalkedCm");
        this.jumps             += takeTenth(victim, "jumps");
        this.xpLevelsGained    += takeTenth(victim, "xpLevelsGained");
        this.sneakTimeTicks    += takeTenth(victim, "sneakTimeTicks");
        this.distanceInWaterCm += takeTenth(victim, "distanceInWaterCm");
    }

    /** Take floor(field/10) from victim, leave the rest. */
    private static long takeTenth(PlayerStats victim, String fieldName) {
        try {
            java.lang.reflect.Field f = PlayerStats.class.getField(fieldName);
            long val = f.getLong(victim);
            long take = val / 10;
            f.setLong(victim, val - take);
            return take;
        } catch (Throwable t) { return 0; }
    }
}
