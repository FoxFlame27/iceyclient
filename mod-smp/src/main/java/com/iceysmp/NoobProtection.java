package com.iceysmp;

/**
 * "Noob protection" — within N minutes of a player's first-ever join, they
 * can't deal or receive PvP damage. Stored as a millis-epoch field on
 * {@link PlayerStats#firstJoinTimestamp}, set the first time we ever
 * {@code computeIfAbsent} their stats entry (i.e. their first event on a
 * fresh database).
 */
public final class NoobProtection {

    private NoobProtection() {}

    public static boolean isProtected(PlayerStats ps, SmpConfig config) {
        if (ps == null) return false;
        if (config.noobProtectionMinutes() <= 0) return false;
        long elapsedMs = System.currentTimeMillis() - ps.firstJoinTimestamp;
        return elapsedMs < config.noobProtectionMinutes() * 60_000L;
    }

    /** Minutes of protection remaining (rounded up). 0 if not protected. */
    public static int remainingMinutes(PlayerStats ps, SmpConfig config) {
        if (!isProtected(ps, config)) return 0;
        long elapsedMs = System.currentTimeMillis() - ps.firstJoinTimestamp;
        long remainingMs = config.noobProtectionMinutes() * 60_000L - elapsedMs;
        return (int) Math.max(1, (remainingMs + 59_999) / 60_000);
    }
}
