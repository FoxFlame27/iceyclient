package com.iceysmp;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.minecraft.stat.Stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * Recompute leaderboards every {@code config.recomputeSeconds}, apply
 * per-player effects based on each player's stat count (count-based
 * scaling — top of leaderboard gets fame via chat but every player's own
 * count determines their personal amp).
 *
 * <p>Scaling: amp 0 at count 1, +1 each at 2 and 3, then +1 each 5 counts
 * up to count 28 (amp 7), then +1 each 15 counts past that. Capped per
 * effect (Strength=5, Resistance=3, Speed=4, JumpBoost=5, others=9).
 *
 * <p>"Top of category" announcement fires bold in chat when the leader
 * changes — easy to miss in regular text-color.
 */
public final class LeaderboardManager {

    private final StatTracker stats;
    private final CombatTracker combat;
    private final SmpConfig config;

    private long tickCounter = 0;
    private final Map<String, Long> lastAnnouncedTop = new HashMap<>();
    // Per-player snapshot of MC StatHandler readings for delta tracking.
    // index: 0=FISH_CAUGHT, 1=WALK_ONE_CM, 2=JUMP, 3=SNEAK_TIME, 4=experienceLevel
    // 0 in any slot means "no prior reading" → first read seeds, doesn't add to counter.
    private final Map<UUID, int[]> statSnapshots = new HashMap<>();

    public LeaderboardManager(StatTracker stats, CombatTracker combat, SmpConfig config) {
        this.stats = stats;
        this.combat = combat;
        this.config = config;
    }

    public void bind(MinecraftServer server) { /* reserved */ }

    public void tick(MinecraftServer server) {
        tickCounter++;

        // Playtime tick for online players.
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerStats ps = stats.get(p.getUuid(), p.getName().getString());
            ps.playtimeTicks++;
            ps.name = p.getName().getString();
        }

        // Once a second, read MC's StatHandler for each online player and
        // add the delta-since-last-tick to our counters. Cheaper than
        // hooking individual events for fishing / jumps / sneak / walk
        // and works because MC tracks these natively per-player.
        if (tickCounter % 20 == 0) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                snapshotAndAddDelta(p);
            }
        }

        long recomputePeriodTicks = Math.max(1, config.recomputeSeconds() * 20L);
        if (tickCounter % recomputePeriodTicks == 0) recompute(server);

        if (tickCounter % (5 * 60 * 20) == 0) stats.save(server);
        if (tickCounter % (60 * 20) == 0) combat.prune();
    }

    private void recompute(MinecraftServer server) {
        int duration = config.effectDurationSeconds() * 20;

        // For every category, broadcast leader-change + apply per-player effect
        for (Category cat : Category.values()) {
            // Sort to find #1 — used for chat announce only.
            List<Ranked> rk = rank(cat.field);
            if (!rk.isEmpty() && rk.get(0).value > 0) {
                Long prev = lastAnnouncedTop.get(cat.id);
                if (prev == null || prev != rk.get(0).value) {
                    server.getPlayerManager().broadcast(
                            Text.literal("§b§l[Icey SMP] §a§l" + rk.get(0).name
                                    + " §r§7is now top of §b§l" + cat.label
                                    + " §7(" + rk.get(0).value + ")"),
                            false);
                    lastAnnouncedTop.put(cat.id, rk.get(0).value);
                }
            }

            RegistryEntry<StatusEffect> effect = cat.effect();
            if (effect == null) continue; // effect ref unavailable on this MC version

            // Per-player amp from count
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                PlayerStats ps = stats.peek(p.getUuid());
                if (ps == null) continue;
                long count = cat.field.applyAsLong(ps) / cat.divisor;
                int amp = ampForCount(count, effect);
                if (amp < 0) continue;
                try {
                    p.addStatusEffect(new StatusEffectInstance(effect, duration, amp, false, false, true));
                } catch (Throwable ignored) {}
            }
        }
    }

    private void snapshotAndAddDelta(ServerPlayerEntity p) {
        try {
            int curFish  = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.FISH_CAUGHT));
            int curWalk  = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM));
            int curJump  = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP));
            int curSneak = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.SNEAK_TIME));
            int curXp    = p.experienceLevel;

            int[] last = statSnapshots.computeIfAbsent(p.getUuid(), u -> new int[5]);
            PlayerStats ps = stats.get(p.getUuid(), p.getName().getString());
            // Only add delta if we have a prior snapshot (last[*] != 0).
            // This avoids counting a player's pre-existing MC stats as fresh
            // gains the moment they connect to a fresh iceysmp install.
            if (last[0] > 0) ps.fishCaught       += Math.max(0, curFish  - last[0]);
            if (last[1] > 0) ps.distanceWalkedCm += Math.max(0, curWalk  - last[1]);
            if (last[2] > 0) ps.jumps            += Math.max(0, curJump  - last[2]);
            if (last[3] > 0) ps.sneakTimeTicks   += Math.max(0, curSneak - last[3]);
            if (last[4] > 0 && curXp > last[4]) ps.xpLevelsGained += (curXp - last[4]);
            last[0] = curFish; last[1] = curWalk; last[2] = curJump; last[3] = curSneak; last[4] = curXp;
        } catch (Throwable ignored) {}
    }

    /** Count-based amplifier scaling. */
    static int ampForCount(long count, RegistryEntry<StatusEffect> effect) {
        if (count <= 0) return -1;
        int amp;
        if (count <= 3)        amp = (int) (count - 1);              // 1→0, 2→1, 3→2
        else if (count <= 28)  amp = 2 + (int) ((count - 3) / 5);     // each +5 = +1 up to amp 7 at count 28
        else                   amp = 7 + (int) ((count - 28) / 15);   // each +15 = +1 thereafter
        return Math.min(amp, capFor(effect));
    }

    /** Per-effect amplifier cap so resistance can't exceed god-mode, etc. */
    static int capFor(RegistryEntry<StatusEffect> e) {
        try {
            if (e == StatusEffects.STRENGTH)   return 5;
            if (e == StatusEffects.RESISTANCE) return 3;
            if (e == StatusEffects.SPEED)      return 4;
            if (e == StatusEffects.JUMP_BOOST) return 5;
            if (e == StatusEffects.HASTE)      return 9;
            if (e == StatusEffects.SATURATION) return 4;
            if (e == StatusEffects.REGENERATION) return 3;
        } catch (Throwable ignored) {}
        return 9;
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
        for (Category c : Category.values()) {
            if (c.id.equals(category)) return rank(c.field);
        }
        return List.of();
    }

    public static List<String> categoryIds() {
        return Arrays.stream(Category.values()).map(c -> c.id).toList();
    }

    public static final class Ranked {
        public final UUID uuid;
        public final String name;
        public final long value;
        public Ranked(UUID uuid, String name, long value) {
            this.uuid = uuid; this.name = name; this.value = value;
        }
    }

    /** Category descriptors — each one has a stat-field accessor, an effect,
     *  and a divisor (lets us put huge raw counts like playtime ticks or
     *  damage-x10 onto the same amp scaling as small counts like kills). */
    /**
     * Categories. Effect refs are lazy {@link Supplier}s — if a yarn build in
     * the matrix has renamed (or doesn't yet have) a particular
     * {@code StatusEffects.X} field, evaluating the lambda throws at apply
     * time and that one category just doesn't get a buff applied. The enum
     * class itself still class-loads cleanly, so the rest of the mod (stat
     * tracking, command registration, etc.) keeps working.
     */
    enum Category {
        MINING       ("mining",      "Mining",        ps -> ps.mining,        () -> StatusEffects.HASTE,        1),
        PVP          ("pvp",         "PvP",           ps -> ps.pvpKills,      () -> StatusEffects.STRENGTH,     1),
        PLAYTIME     ("playtime",    "Playtime",      ps -> ps.playtimeTicks, () -> StatusEffects.SATURATION,   72000L),
        MOB_KILLS    ("mobkills",    "Mob Kills",     ps -> ps.mobKills,      () -> StatusEffects.RESISTANCE,   1),
        ANIMAL_KILLS ("animalkills", "Animal Kills",  ps -> ps.animalKills,   () -> StatusEffects.NIGHT_VISION, 1),
        CROPS        ("crops",       "Farming",       ps -> ps.crops,         () -> StatusEffects.HASTE,        5),
        DIAMONDS     ("diamonds",    "Diamonds",      ps -> ps.diamonds,      () -> StatusEffects.SPEED,        1),
        WOOD         ("wood",        "Wood Chopped",  ps -> ps.woodChopped,   () -> StatusEffects.HASTE,        5),
        DAMAGE_DEALT ("dmgdealt",    "Damage Dealt",  ps -> ps.damageDealt,   () -> StatusEffects.STRENGTH,     200L),
        DAMAGE_TAKEN ("dmgtaken",    "Damage Taken",  ps -> ps.damageTaken,   () -> StatusEffects.RESISTANCE,   200L),
        DEATHS       ("deaths",      "Deaths",        ps -> ps.deaths,        () -> StatusEffects.REGENERATION, 1),
        FISHING      ("fishing",     "Fishing",       ps -> ps.fishCaught,       () -> StatusEffects.LUCK,                1),
        WALKING      ("walking",     "Distance",      ps -> ps.distanceWalkedCm, () -> StatusEffects.SPEED,               100_000L),
        JUMPS        ("jumps",       "Jumps",         ps -> ps.jumps,            () -> StatusEffects.JUMP_BOOST,          50L),
        XP_LEVELS    ("xplevels",    "XP Levels",     ps -> ps.xpLevelsGained,   () -> StatusEffects.HERO_OF_THE_VILLAGE, 1),
        SNEAK_TIME   ("sneak",       "Sneak Time",    ps -> ps.sneakTimeTicks,   () -> StatusEffects.SLOW_FALLING,        1200L);

        final String id;
        final String label;
        final ToLongFunction<PlayerStats> field;
        final Supplier<RegistryEntry<StatusEffect>> effectSupplier;
        final long divisor;

        private RegistryEntry<StatusEffect> cached;
        private boolean resolved;

        Category(String id, String label, ToLongFunction<PlayerStats> field,
                 Supplier<RegistryEntry<StatusEffect>> effectSupplier, long divisor) {
            this.id = id;
            this.label = label;
            this.field = field;
            this.effectSupplier = effectSupplier;
            this.divisor = divisor;
        }

        RegistryEntry<StatusEffect> effect() {
            if (!resolved) {
                try { cached = effectSupplier.get(); }
                catch (Throwable t) { System.out.println("[IceySMP] Effect for category " + id + " unavailable: " + t); }
                resolved = true;
            }
            return cached;
        }
    }
}
