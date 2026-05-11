package com.iceysmp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-tag state machine. Three jobs:
 *   1. Reject kill credits when attacker / victim weren't actively fighting
 *      each other (anti-farm).
 *   2. Reject repeat kills of the same victim. Default is "never count
 *      twice" (sameVictimCooldownSeconds=0) — once you've killed someone,
 *      that pairing is dead forever.
 *   3. Expose combat-tag state for /spawn gating and logout-kill handling.
 *
 * State is in-memory: combat tags reset on server restart (conservative —
 * you can't combat-log via restart). The pairwise kill record is also
 * in-memory; small enough not to matter on a server with thousands of
 * kills/day.
 */
public final class CombatTracker {

    private final long tagDurationMs;
    private final Map<String, Long> tags = new ConcurrentHashMap<>();
    private final Map<String, Long> lastKill = new ConcurrentHashMap<>();
    // Single-player combat tag (used for /spawn gating + logout death):
    // any time someone was hit by ANY damage source within the tag window.
    private final Map<UUID, Long> playerInCombat = new ConcurrentHashMap<>();

    public CombatTracker(int tagSeconds) {
        this.tagDurationMs = tagSeconds * 1000L;
    }

    /** Tag both directions when player A hits player B. Also marks each
     *  individually as "in combat" for /spawn / logout-death purposes. */
    public void tag(UUID a, UUID b) {
        long now = System.currentTimeMillis();
        tags.put(pairKey(a, b), now);
        tags.put(pairKey(b, a), now);
        playerInCombat.put(a, now);
        playerInCombat.put(b, now);
    }

    public void tagOne(UUID who) {
        playerInCombat.put(who, System.currentTimeMillis());
    }

    public boolean isInCombat(UUID who) {
        Long t = playerInCombat.get(who);
        return t != null && (System.currentTimeMillis() - t) <= tagDurationMs;
    }

    public boolean bothTagged(UUID attacker, UUID victim) {
        long now = System.currentTimeMillis();
        Long t1 = tags.get(pairKey(attacker, victim));
        Long t2 = tags.get(pairKey(victim, attacker));
        return t1 != null && t2 != null
                && (now - t1) <= tagDurationMs
                && (now - t2) <= tagDurationMs;
    }

    /** Can this pair count a kill?
     *  cooldownSeconds = 0 means same victim NEVER counts a 2nd time. */
    public boolean canCountKill(UUID attacker, UUID victim, int cooldownSeconds) {
        Long last = lastKill.get(pairKey(attacker, victim));
        if (last == null) return true;
        if (cooldownSeconds <= 0) return false; // never again
        return (System.currentTimeMillis() - last) >= cooldownSeconds * 1000L;
    }

    public void recordKill(UUID attacker, UUID victim) {
        lastKill.put(pairKey(attacker, victim), System.currentTimeMillis());
        tags.remove(pairKey(attacker, victim));
        tags.remove(pairKey(victim, attacker));
        playerInCombat.remove(attacker);
        playerInCombat.remove(victim);
    }

    public void clearCombat(UUID who) { playerInCombat.remove(who); }

    public void prune() {
        long cutoffTag = System.currentTimeMillis() - tagDurationMs * 4;
        tags.entrySet().removeIf(e -> e.getValue() < cutoffTag);
        playerInCombat.entrySet().removeIf(e -> e.getValue() < cutoffTag);
        // Kill records stay forever by default (cooldown=0 semantics).
    }

    private static String pairKey(UUID a, UUID b) { return a + ":" + b; }
}
