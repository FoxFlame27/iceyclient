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

    public PlayerStats(String name) {
        this.name = name;
    }

    /** Stealable categories — fields the killer absorbs on PvP kill. */
    public static final String[] STEALABLE_FIELDS = {
            "mining", "pvpKills", "playtimeTicks", "mobKills", "animalKills",
            "crops", "diamonds", "woodChopped", "damageDealt", "damageTaken", "deaths"
    };

    /** Transfer all stealable counters from {@code victim} to this stats
     *  block, zero them on the victim. Called on PvP death. */
    public void absorbFrom(PlayerStats victim) {
        if (victim == this) return;
        this.mining       += victim.mining;       victim.mining       = 0;
        this.pvpKills     += victim.pvpKills;     victim.pvpKills     = 0;
        this.playtimeTicks+= victim.playtimeTicks;victim.playtimeTicks= 0;
        this.mobKills     += victim.mobKills;     victim.mobKills     = 0;
        this.animalKills  += victim.animalKills;  victim.animalKills  = 0;
        this.crops        += victim.crops;        victim.crops        = 0;
        this.diamonds     += victim.diamonds;     victim.diamonds     = 0;
        this.woodChopped  += victim.woodChopped;  victim.woodChopped  = 0;
        this.damageDealt  += victim.damageDealt;  victim.damageDealt  = 0;
        this.damageTaken  += victim.damageTaken;  victim.damageTaken  = 0;
        this.deaths       += victim.deaths;       victim.deaths       = 0;
    }
}
