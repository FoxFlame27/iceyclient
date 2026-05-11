package com.iceysmp;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain-text config in {@code config/iceysmp.properties}. Tiny — no toml/
 * gson dependency needed. Reload-able via the {@code /icey reload} command.
 *
 * Keep the field set small so a server admin opening this file isn't lost.
 * If we add more knobs later, keep them grouped by category.
 */
public final class SmpConfig {

    private int recomputeSeconds = 30;
    private int combatTagSeconds = 10;
    private int sameVictimCooldownSeconds = 600; // 10 minutes
    private int effectDurationSeconds = 60;      // refreshed on each recompute

    public int recomputeSeconds() { return recomputeSeconds; }
    public int combatTagSeconds() { return combatTagSeconds; }
    public int sameVictimCooldownSeconds() { return sameVictimCooldownSeconds; }
    public int effectDurationSeconds() { return effectDurationSeconds; }

    public static SmpConfig loadOrDefault() {
        SmpConfig c = new SmpConfig();
        try {
            Path file = configPath();
            if (!Files.exists(file)) {
                writeDefault(c, file);
                return c;
            }
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
                        case "effectDurationSeconds"    -> c.effectDurationSeconds    = clamp(Integer.parseInt(v), 5, 3600);
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
                # How long a player stays "in combat" after damaging another.
                combatTagSeconds=%d
                # Cooldown per attacker→victim pair before a kill counts again.
                sameVictimCooldownSeconds=%d
                # Length of the buff effect applied each recompute cycle.
                # Should be ≥ recomputeSeconds so the effect never visibly fades.
                effectDurationSeconds=%d
                """.formatted(c.recomputeSeconds, c.combatTagSeconds, c.sameVictimCooldownSeconds, c.effectDurationSeconds);
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("iceysmp.properties");
    }
}
