package com.iceysmp;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain-text config in {@code config/iceysmp.properties}. No toml/gson
 * dependency. Reload-able via {@code /icey reload}.
 */
public final class SmpConfig {

    private int recomputeSeconds = 30;
    private int combatTagSeconds = 25;          // bumped from 10 → 25
    private int sameVictimCooldownSeconds = 0;   // 0 = never count same victim twice
    private int effectDurationSeconds = 60;
    private int noobProtectionMinutes = 10;
    private boolean noobProtectionEnabled = true;
    private boolean starterKit = true;
    private boolean killStealsStats = true;
    private boolean killOnCombatLogout = true;

    public int recomputeSeconds() { return recomputeSeconds; }
    public int combatTagSeconds() { return combatTagSeconds; }
    public int sameVictimCooldownSeconds() { return sameVictimCooldownSeconds; }
    public int effectDurationSeconds() { return effectDurationSeconds; }
    public int noobProtectionMinutes() { return noobProtectionMinutes; }
    public boolean noobProtectionEnabled() { return noobProtectionEnabled; }
    public boolean starterKit() { return starterKit; }
    public boolean killStealsStats() { return killStealsStats; }
    public boolean killOnCombatLogout() { return killOnCombatLogout; }

    /** Runtime toggle for /noobprotect. Persisted on next loadOrDefault
     *  write — we don't auto-save the toggle to disk, but the next time
     *  the file is regenerated it'll pick up the current value. */
    public void setNoobProtectionEnabled(boolean enabled) { this.noobProtectionEnabled = enabled; }

    public static SmpConfig loadOrDefault() {
        SmpConfig c = new SmpConfig();
        try {
            Path file = configPath();
            if (!Files.exists(file)) { writeDefault(c, file); return c; }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                int eq = l.indexOf('=');
                if (eq <= 0) continue;
                String k = l.substring(0, eq).trim();
                String v = l.substring(eq + 1).trim();
                try {
                    switch (k) {
                        case "recomputeSeconds"          -> c.recomputeSeconds          = clamp(Integer.parseInt(v), 5, 3600);
                        case "combatTagSeconds"          -> c.combatTagSeconds          = clamp(Integer.parseInt(v), 1, 120);
                        case "sameVictimCooldownSeconds" -> c.sameVictimCooldownSeconds = clamp(Integer.parseInt(v), 0, 86400);
                        case "effectDurationSeconds"     -> c.effectDurationSeconds     = clamp(Integer.parseInt(v), 5, 3600);
                        case "noobProtectionMinutes"     -> c.noobProtectionMinutes     = clamp(Integer.parseInt(v), 0, 1440);
                        case "noobProtectionEnabled"     -> c.noobProtectionEnabled     = Boolean.parseBoolean(v);
                        case "starterKit"                -> c.starterKit                = Boolean.parseBoolean(v);
                        case "killStealsStats"           -> c.killStealsStats           = Boolean.parseBoolean(v);
                        case "killOnCombatLogout"        -> c.killOnCombatLogout        = Boolean.parseBoolean(v);
                    }
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
        return c;
    }

    private static void writeDefault(SmpConfig c, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String body = """
                # Icey SMP — server-side leaderboard config
                # How often the leaderboard is recomputed and buffs reassigned.
                recomputeSeconds=%d
                # Combat tag duration. While tagged you can't /spawn, and logging
                # out kills you (if killOnCombatLogout=true).
                combatTagSeconds=%d
                # Cooldown per attacker→victim pair before a kill counts again.
                # 0 = same victim NEVER counts a second time (recommended).
                sameVictimCooldownSeconds=%d
                # Length of the buff effect applied each recompute cycle.
                effectDurationSeconds=%d
                # Noob protection: from first join, this many minutes of no-PvP.
                noobProtectionMinutes=%d
                # Master switch for noob protection — flip via /noobprotect on|off.
                noobProtectionEnabled=%b
                # Give iron armor + iron sword/pick/axe on first join.
                starterKit=%b
                # On a PvP kill, transfer all of the victim's stats to the killer.
                killStealsStats=%b
                # If a player disconnects while combat-tagged, kill them.
                killOnCombatLogout=%b
                """.formatted(
                        c.recomputeSeconds, c.combatTagSeconds, c.sameVictimCooldownSeconds,
                        c.effectDurationSeconds, c.noobProtectionMinutes, c.noobProtectionEnabled,
                        c.starterKit, c.killStealsStats, c.killOnCombatLogout);
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve("iceysmp.properties"); }
}
