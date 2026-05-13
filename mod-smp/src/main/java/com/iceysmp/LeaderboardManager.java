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
    // Per-player last-seen level per category. Used to detect level-ups so
    // we only broadcast "X is now Level N in Mining" when the level actually
    // increases (previously we broadcasted any change in the leaderboard
    // #1 score, which fired noisily every 30 seconds with raw count
    // numbers instead of meaningful level transitions).
    private final Map<UUID, Map<String, Integer>> lastPlayerLevels = new HashMap<>();
    // Per-player snapshot of MC StatHandler readings for delta tracking.
    // index: 0=FISH_CAUGHT, 1=WALK_ONE_CM, 2=JUMP, 3=SNEAK_TIME, 4=experienceLevel
    private final Map<UUID, int[]> statSnapshots = new HashMap<>();
    // Players whose snapshot has been seeded at least once. Without this
    // we used `last[i] > 0` as the gate, which drops the FIRST 0→1
    // transition (e.g. player's first fish ever) because last[0] stays
    // at 0 across that tick. Tracking a "seen at least one tick" flag
    // makes the gate only fire on truly fresh players, not on every
    // newly-incremented stat.
    private final java.util.Set<UUID> snapshotSeeded = new java.util.HashSet<>();

    public LeaderboardManager(StatTracker stats, CombatTracker combat, SmpConfig config) {
        this.stats = stats;
        this.combat = combat;
        this.config = config;
    }

    public void bind(MinecraftServer server) { /* reserved */ }

    public void tick(MinecraftServer server) {
        tickCounter++;

        // Pump the global tick scheduler (daily-roll animations, etc.) and
        // the combat boss bar updater — both run at every tick because
        // they need 50ms-precision updates.
        Scheduler.tick(server);
        if (IceySmp.combatBossBar != null && tickCounter % 5 == 0) {
            IceySmp.combatBossBar.tick(server);
        }

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

        for (Category cat : Category.values()) {
            RegistryEntry<StatusEffect> effect = cat.effect();
            if (effect == null) continue; // effect ref unavailable on this MC version

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                PlayerStats ps = stats.peek(p.getUuid());
                if (ps == null) continue;
                double normalized = cat.field.applyAsLong(ps) / (double) cat.divisor;
                int amp = ampForNormalized(normalized, effect);
                // newLevel: 0 = no buff, 1 = Lv I (amp 0), 2 = Lv II (amp 1), etc.
                int newLevel = (amp < 0) ? 0 : amp + 1;

                // Broadcast on level-up only — not every recompute.
                Map<String, Integer> playerLevels = lastPlayerLevels.computeIfAbsent(p.getUuid(), u -> new HashMap<>());
                Integer prev = playerLevels.get(cat.id);
                if (prev != null && newLevel > prev) {
                    server.getPlayerManager().broadcast(
                            Text.literal("§b§l[Icey SMP] §a§l" + p.getName().getString()
                                    + " §r§7is now §b§lLevel " + newLevel
                                    + " §r§7in §b§l" + cat.label + "§7!"),
                            false);
                    try {
                        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 50, 20));
                        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                                Text.literal("§e§lLEVEL UP")));
                        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                                Text.literal("§aLevel " + newLevel + " §rin §b§l" + cat.label)));
                    } catch (Throwable ignored) {}

                }
                playerLevels.put(cat.id, newLevel);

                // Weapon-threshold reward: per-category fixed count
                // (1000 mining, 25 PvP, 50h playtime, etc.). One-shot per
                // (player, category). Checked every recompute, not just
                // on level-up, so the reward fires the cycle AFTER the
                // threshold is crossed even if no level boundary moved.
                long currentCount = cat.field.applyAsLong(ps);
                if (currentCount >= cat.weaponThreshold && !ps.wasAwardedFrostfangFor(cat.id)) {
                    WeaponDrops.giveReward(p, cat.id, cat.label);
                    ps.markFrostfangAwardedFor(cat.id);
                }

                if (amp >= 0) {
                    try {
                        p.addStatusEffect(new StatusEffectInstance(effect, duration, amp, false, false, true));
                    } catch (Throwable ignored) {}
                }
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

            UUID uid = p.getUuid();
            int[] last = statSnapshots.computeIfAbsent(uid, u -> new int[5]);
            PlayerStats ps = stats.get(uid, p.getName().getString());
            // Gate on "have we seen this player at least once in this
            // session" — the FIRST tick after a player connects just
            // seeds the snapshot to whatever MC has (so pre-existing
            // stats aren't counted as fresh gains). Every tick after
            // that, compute the real delta. Without this we used
            // last[i] > 0 as the gate, which silently dropped the
            // player's very first fish/jump/km because last[i] stayed
            // at 0 across the 0→1 transition.
            if (snapshotSeeded.contains(uid)) {
                ps.fishCaught       += Math.max(0, curFish  - last[0]);
                ps.distanceWalkedCm += Math.max(0, curWalk  - last[1]);
                ps.jumps            += Math.max(0, curJump  - last[2]);
                ps.sneakTimeTicks   += Math.max(0, curSneak - last[3]);
                if (curXp > last[4]) ps.xpLevelsGained += (curXp - last[4]);
            } else {
                snapshotSeeded.add(uid);
            }
            last[0] = curFish; last[1] = curWalk; last[2] = curJump; last[3] = curSneak; last[4] = curXp;
        } catch (Throwable ignored) {}
    }

    /**
     * Exponential progression: 1× divisor → Level 1 (amp 0), 2× → Level 2,
     * 4× → Level 3, 8× → Level 4, … Each level takes double the previous
     * level's threshold to reach. Per-category divisor is calibrated so
     * one level ≈ one hour of typical play.
     */
    static int ampForNormalized(double normalized, RegistryEntry<StatusEffect> effect) {
        if (normalized < 1.0) return -1;
        int amp = (int) (Math.log(normalized) / Math.log(2));
        return Math.min(Math.max(amp, 0), capFor(effect));
    }

    /** Count needed for the next level given the current count + divisor. */
    static long nextLevelThreshold(long count, long divisor) {
        double normalized = count / (double) divisor;
        if (normalized < 1.0) return divisor; // first threshold
        int currentAmp = (int) (Math.log(normalized) / Math.log(2));
        return (long) (divisor * Math.pow(2, currentAmp + 1));
    }

    /** Per-effect amplifier cap so resistance / strength don't reach
     *  god-mode tiers. Caps are in amplifier units (amp 2 = Level III). */
    static int capFor(RegistryEntry<StatusEffect> e) {
        try {
            if (e == StatusEffects.STRENGTH)   return 2; // Level III max — user-tuned, was super OP higher
            if (e == StatusEffects.RESISTANCE) return 2; // Level III max
            if (e == StatusEffects.SPEED)      return 3;
            if (e == StatusEffects.HASTE)      return 5;
            if (e == StatusEffects.SATURATION) return 3;
        } catch (Throwable ignored) {}
        return 5;
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

    /** Look up a Category by id. Returns null if no match. Used by /icey help. */
    public Category categoryById(String id) {
        for (Category c : Category.values()) if (c.id.equals(id)) return c;
        return null;
    }
    public Category[] allCategories() { return Category.values(); }

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
    /**
     * Categories — eight curated stat tracks. Divisor is calibrated so a
     * normalized value of 1 means "you've done about an hour of this
     * activity at typical play rate". Level 1 (Haste I etc.) unlocks at
     * normalized = 1, Level 2 at 2 (≈2h cumulative), Level 3 at 4 (≈4h),
     * etc. — exponential.
     */
    enum Category {
        // Last column = weaponThreshold: raw count at which the themed
        // custom weapon is awarded (one-shot per player). Tuned so they
        // come earlier than the old "max-amp-level" gate did and at
        // round numbers (per user request — "500 or 1000 depending").
        MINING       ("mining",      "Mining",        "ores",         ps -> ps.mining,           () -> StatusEffects.HASTE,        200L,    1_000L),
        PVP          ("pvp",         "PvP",           "kills",        ps -> ps.pvpKills,         () -> StatusEffects.STRENGTH,     2L,         25L),
        PLAYTIME     ("playtime",    "Playtime",      "hours",        ps -> ps.playtimeTicks,    () -> StatusEffects.SATURATION,   72000L, 3_600_000L), // 50h in ticks
        FISHING      ("fishing",     "Fishing",       "fish",         ps -> ps.fishCaught,       () -> StatusEffects.LUCK,         30L,       100L),
        WALKING      ("walking",     "Distance",      "km × 6",       ps -> ps.distanceWalkedCm, () -> StatusEffects.SPEED,        600_000L, 1_000_000L), // 10 km in cm
        JUMPS        ("jumps",       "Jumps",         "jumps",        ps -> ps.jumps,            () -> StatusEffects.JUMP_BOOST,   500L,    1_000L),
        // damageTaken is stored ×10 in PlayerStats. Threshold 5000 = 500 HP.
        DMG_TAKEN    ("dmgtaken",    "Damage Taken",  "HP × 10",       ps -> ps.damageTaken,      () -> StatusEffects.RESISTANCE,   500L,    5_000L);

        final String id;
        final String label;
        final String unit;
        final ToLongFunction<PlayerStats> field;
        final Supplier<RegistryEntry<StatusEffect>> effectSupplier;
        final long divisor;
        final long weaponThreshold;

        private RegistryEntry<StatusEffect> cached;
        private boolean resolved;

        Category(String id, String label, String unit, ToLongFunction<PlayerStats> field,
                 Supplier<RegistryEntry<StatusEffect>> effectSupplier, long divisor, long weaponThreshold) {
            this.id = id;
            this.label = label;
            this.unit = unit;
            this.field = field;
            this.effectSupplier = effectSupplier;
            this.divisor = divisor;
            this.weaponThreshold = weaponThreshold;
        }

        public String id() { return id; }
        public String label() { return label; }
        public String unit() { return unit; }
        public ToLongFunction<PlayerStats> field() { return field; }
        public long divisor() { return divisor; }
        public long weaponThreshold() { return weaponThreshold; }

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
