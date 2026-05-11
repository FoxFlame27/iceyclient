package com.iceysmp;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Drives the periodic work: tick playtime, recompute leaderboard rankings,
 * apply auto-buffs, save stats to disk, prune combat tracker state.
 *
 * All called from {@code ServerTickEvents.END_SERVER_TICK} so everything is
 * on the server thread — safe to touch ServerPlayerEntity state.
 */
public final class LeaderboardManager {

    private final StatTracker stats;
    private final CombatTracker combat;
    private final SmpConfig config;

    private MinecraftServer server;
    private long tickCounter = 0;
    private long lastAnnounceMining = -1;
    private long lastAnnouncePvp = -1;
    private long lastAnnouncePlaytime = -1;

    public LeaderboardManager(StatTracker stats, CombatTracker combat, SmpConfig config) {
        this.stats = stats;
        this.combat = combat;
        this.config = config;
    }

    public void bind(MinecraftServer server) { this.server = server; }

    public void tick(MinecraftServer server) {
        this.server = server;
        tickCounter++;

        // 1) Playtime: count one tick per online player per tick.
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerStats ps = stats.get(p.getUuid(), p.getName().getString());
            ps.playtimeTicks++;
            ps.name = p.getName().getString();
        }

        // 2) Recompute + apply buffs every config.recomputeSeconds.
        long recomputePeriodTicks = Math.max(1, config.recomputeSeconds() * 20L);
        if (tickCounter % recomputePeriodTicks == 0) {
            applyAllBuffs(server);
        }

        // 3) Auto-save every 5 min.
        if (tickCounter % (5 * 60 * 20) == 0) {
            stats.save(server);
        }

        // 4) Prune combat tracker once a minute.
        if (tickCounter % (60 * 20) == 0) {
            combat.prune();
        }
    }

    private void applyAllBuffs(MinecraftServer server) {
        List<Ranked> miningTop = rank(p -> p.mining);
        List<Ranked> pvpTop = rank(p -> p.pvpKills);
        List<Ranked> playTop = rank(p -> p.playtimeTicks);

        // Announce new #1 in chat (skip on initial boot when lastAnnounceX == -1).
        announceIfChanged(server, "Mining", miningTop, lastAnnounceMining);
        announceIfChanged(server, "PvP",    pvpTop,    lastAnnouncePvp);
        announceIfChanged(server, "Playtime", playTop, lastAnnouncePlaytime);
        if (!miningTop.isEmpty()) lastAnnounceMining = miningTop.get(0).value;
        if (!pvpTop.isEmpty())    lastAnnouncePvp    = pvpTop.get(0).value;
        if (!playTop.isEmpty())   lastAnnouncePlaytime = playTop.get(0).value;

        int duration = config.effectDurationSeconds() * 20;

        // Mining → Haste (#1 = II, #2 = I)
        applyEffect(server, miningTop, 0, StatusEffects.HASTE, 1, duration);
        applyEffect(server, miningTop, 1, StatusEffects.HASTE, 0, duration);
        // PvP → Strength
        applyEffect(server, pvpTop, 0, StatusEffects.STRENGTH, 1, duration);
        applyEffect(server, pvpTop, 1, StatusEffects.STRENGTH, 0, duration);
        // Playtime → Saturation (passive, less powerful, only #1)
        applyEffect(server, playTop, 0, StatusEffects.SATURATION, 0, duration);
    }

    private void announceIfChanged(MinecraftServer server, String label, List<Ranked> top, long prevTopValue) {
        if (top.isEmpty()) return;
        Ranked first = top.get(0);
        if (first.value == 0) return;
        if (first.value == prevTopValue || prevTopValue < 0) return; // unchanged, or first run
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(first.uuid);
        String name = (p != null ? p.getName().getString() : first.name);
        server.getPlayerManager().broadcast(
                Text.literal("§b[Icey SMP] §a" + name + " §7is now top of §b" + label + " §7(" + first.value + ")"),
                false);
    }

    private void applyEffect(MinecraftServer server, List<Ranked> rankList, int idx,
                             net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect,
                             int amplifier, int durationTicks) {
        if (idx >= rankList.size()) return;
        Ranked r = rankList.get(idx);
        if (r.value == 0) return; // Don't reward zero-score positions
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(r.uuid);
        if (p == null) return; // offline — only buff online players
        try {
            p.addStatusEffect(new StatusEffectInstance(effect, durationTicks, amplifier, false, false, true));
        } catch (Throwable ignored) {}
    }

    private List<Ranked> rank(ToLongFunction<PlayerStats> field) {
        List<Ranked> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStats> e : stats.all().entrySet()) {
            list.add(new Ranked(e.getKey(), e.getValue().name, field.applyAsLong(e.getValue())));
        }
        list.sort(Comparator.comparingLong((Ranked r) -> r.value).reversed());
        return list;
    }

    public List<Ranked> top(String category) {
        return switch (category) {
            case "mining" -> rank(p -> p.mining);
            case "pvp"    -> rank(p -> p.pvpKills);
            case "playtime" -> rank(p -> p.playtimeTicks);
            default -> List.of();
        };
    }

    public static final class Ranked {
        public final UUID uuid;
        public final String name;
        public final long value;
        public Ranked(UUID uuid, String name, long value) {
            this.uuid = uuid; this.name = name; this.value = value;
        }
    }
}
