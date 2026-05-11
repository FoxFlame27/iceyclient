package com.iceysmp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-tag state machine. Two purposes:
 *   1. Reject kill credits when the attacker / victim weren't actively
 *      fighting each other (anti-farm: prevent giving someone XP for
 *      smashing an AFK target).
 *   2. Reject repeat kills of the same victim within a cooldown window
 *      (anti-farm: prevent a duo farming each other for top of the
 *      leaderboard).
 *
 * State is in-memory only — combat tags reset on server restart, which is
 * the conservative thing. The kill-cooldown map is also in-memory; small
 * enough that a server with a thousand kills/day is fine.
 */
public final class CombatTracker {

    private final long tagDurationMs;
    // Key: "attackerUuid:victimUuid"  Value: tag timestamp (ms)
    private final Map<String, Long> tags = new ConcurrentHashMap<>();
    // Key: "attackerUuid:victimUuid"  Value: last counted-kill timestamp (ms)
    private final Map<String, Long> lastKill = new ConcurrentHashMap<>();

    public CombatTracker(int tagSeconds) {
        this.tagDurationMs = tagSeconds * 1000L;
    }

    /** Tag both directions when player A hits player B. */
    public void tag(UUID a, UUID b) {
        long now = System.currentTimeMillis();
        tags.put(pairKey(a, b), now);
        tags.put(pairKey(b, a), now);
    }

    public boolean bothTagged(UUID attacker, UUID victim) {
        long now = System.currentTimeMillis();
        Long t1 = tags.get(pairKey(attacker, victim));
        Long t2 = tags.get(pairKey(victim, attacker));
        return t1 != null && t2 != null
                && (now - t1) <= tagDurationMs
                && (now - t2) <= tagDurationMs;
    }

    public boolean canCountKill(UUID attacker, UUID victim, int cooldownSeconds) {
        Long last = lastKill.get(pairKey(attacker, victim));
        if (last == null) return true;
        return (System.currentTimeMillis() - last) >= cooldownSeconds * 1000L;
    }

    public void recordKill(UUID attacker, UUID victim) {
        lastKill.put(pairKey(attacker, victim), System.currentTimeMillis());
        // Clear combat tags so the survivor isn't still "in combat"
        tags.remove(pairKey(attacker, victim));
        tags.remove(pairKey(victim, attacker));
    }

    /** Optional housekeeping if the in-memory tag map ever grew unbounded.
     *  Currently called once a minute by {@link LeaderboardManager}. */
    public void prune() {
        long cutoffTag = System.currentTimeMillis() - tagDurationMs * 4;
        tags.entrySet().removeIf(e -> e.getValue() < cutoffTag);
        // Keep kill records longer since cooldown is measured against them
        long cutoffKill = System.currentTimeMillis() - 24L * 3600 * 1000;
        lastKill.entrySet().removeIf(e -> e.getValue() < cutoffKill);
    }

    private static String pairKey(UUID a, UUID b) {
        return a + ":" + b;
    }
}
