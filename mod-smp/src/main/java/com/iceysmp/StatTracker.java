package com.iceysmp;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-player counter table + JSON persistence + event wiring.
 *
 * <p>All increment paths run on the server thread via Fabric event callbacks.
 * The map is ConcurrentHashMap purely so command-thread reads are safe.
 *
 * <p>Categories tracked: mining, pvpKills, playtimeTicks (existing) +
 * mobKills, animalKills, crops, diamonds, woodChopped, damageDealt,
 * damageTaken, deaths (new in v1.81). Everything is stealable on PvP kill.
 */
public final class StatTracker {

    private final Map<UUID, PlayerStats> map = new ConcurrentHashMap<>();

    // Block category sets — wrapped in safeAdd so a missing block on some MC
    // version doesn't blow up class init; we just lose that one binding.
    private static final Set<Block> MINING_BLOCKS = buildMiningSet();
    private static final Set<Block> DIAMOND_BLOCKS= buildDiamondSet();

    private static Set<Block> buildMiningSet() {
        Set<Block> s = new HashSet<>();
        safeAdd(s, () -> Blocks.DIAMOND_ORE);     safeAdd(s, () -> Blocks.DEEPSLATE_DIAMOND_ORE);
        safeAdd(s, () -> Blocks.EMERALD_ORE);     safeAdd(s, () -> Blocks.DEEPSLATE_EMERALD_ORE);
        safeAdd(s, () -> Blocks.GOLD_ORE);        safeAdd(s, () -> Blocks.DEEPSLATE_GOLD_ORE);
        safeAdd(s, () -> Blocks.NETHER_GOLD_ORE);
        safeAdd(s, () -> Blocks.IRON_ORE);        safeAdd(s, () -> Blocks.DEEPSLATE_IRON_ORE);
        safeAdd(s, () -> Blocks.COPPER_ORE);      safeAdd(s, () -> Blocks.DEEPSLATE_COPPER_ORE);
        safeAdd(s, () -> Blocks.LAPIS_ORE);       safeAdd(s, () -> Blocks.DEEPSLATE_LAPIS_ORE);
        safeAdd(s, () -> Blocks.REDSTONE_ORE);    safeAdd(s, () -> Blocks.DEEPSLATE_REDSTONE_ORE);
        safeAdd(s, () -> Blocks.COAL_ORE);        safeAdd(s, () -> Blocks.DEEPSLATE_COAL_ORE);
        safeAdd(s, () -> Blocks.NETHER_QUARTZ_ORE);
        safeAdd(s, () -> Blocks.ANCIENT_DEBRIS);
        return s;
    }
    private static Set<Block> buildDiamondSet() {
        Set<Block> s = new HashSet<>();
        safeAdd(s, () -> Blocks.DIAMOND_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_DIAMOND_ORE);
        return s;
    }

    private static void safeAdd(Set<Block> s, java.util.function.Supplier<Block> sup) {
        try { Block b = sup.get(); if (b != null) s.add(b); } catch (Throwable ignored) {}
    }

    public PlayerStats get(UUID uuid, String name) {
        return map.computeIfAbsent(uuid, u -> { PlayerStats p = new PlayerStats(name); p.firstJoinTimestamp = System.currentTimeMillis(); return p; });
    }
    public PlayerStats peek(UUID uuid) { return map.get(uuid); }
    public Map<UUID, PlayerStats> all() { return map; }
    public int size() { return map.size(); }
    public void clear() { map.clear(); }

    public void load(MinecraftServer server) {
        try {
            Path file = statsPath(server);
            if (!Files.exists(file)) return;
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            for (String line : raw.split("\n")) {
                String l = line.trim();
                if (!l.startsWith("\"")) continue;
                int colon = l.indexOf("\":");
                if (colon < 0) continue;
                String uuidStr = l.substring(1, colon);
                int objStart = l.indexOf('{', colon);
                int objEnd = l.indexOf('}', objStart);
                if (objStart < 0 || objEnd < 0) continue;
                String body = l.substring(objStart + 1, objEnd);
                PlayerStats st = new PlayerStats(extractString(body, "name", "?"));
                st.firstJoinTimestamp = extractLong(body, "firstJoin");
                if (st.firstJoinTimestamp == 0) st.firstJoinTimestamp = System.currentTimeMillis();
                st.mining        = extractLong(body, "mining");
                st.pvpKills      = extractLong(body, "pvp");
                st.playtimeTicks = extractLong(body, "playtime");
                st.mobKills      = extractLong(body, "mobKills");
                st.animalKills   = extractLong(body, "animalKills");
                st.crops         = extractLong(body, "crops");
                st.diamonds      = extractLong(body, "diamonds");
                st.woodChopped   = extractLong(body, "wood");
                st.damageDealt   = extractLong(body, "dmgDealt");
                st.damageTaken   = extractLong(body, "dmgTaken");
                st.deaths        = extractLong(body, "deaths");
                st.fishCaught       = extractLong(body, "fish");
                st.distanceWalkedCm = extractLong(body, "walkCm");
                st.jumps            = extractLong(body, "jumps");
                st.xpLevelsGained   = extractLong(body, "xpLevels");
                st.sneakTimeTicks   = extractLong(body, "sneak");
                try { map.put(UUID.fromString(uuidStr), st); } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
    }

    public void save(MinecraftServer server) {
        try {
            Path file = statsPath(server);
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<UUID, PlayerStats> e : map.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                PlayerStats s = e.getValue();
                sb.append("  \"").append(e.getKey()).append("\": {");
                sb.append("\"name\":\"").append(escape(s.name)).append("\",");
                sb.append("\"firstJoin\":").append(s.firstJoinTimestamp).append(",");
                sb.append("\"mining\":").append(s.mining).append(",");
                sb.append("\"pvp\":").append(s.pvpKills).append(",");
                sb.append("\"playtime\":").append(s.playtimeTicks).append(",");
                sb.append("\"mobKills\":").append(s.mobKills).append(",");
                sb.append("\"animalKills\":").append(s.animalKills).append(",");
                sb.append("\"crops\":").append(s.crops).append(",");
                sb.append("\"diamonds\":").append(s.diamonds).append(",");
                sb.append("\"wood\":").append(s.woodChopped).append(",");
                sb.append("\"dmgDealt\":").append(s.damageDealt).append(",");
                sb.append("\"dmgTaken\":").append(s.damageTaken).append(",");
                sb.append("\"deaths\":").append(s.deaths).append(",");
                sb.append("\"fish\":").append(s.fishCaught).append(",");
                sb.append("\"walkCm\":").append(s.distanceWalkedCm).append(",");
                sb.append("\"jumps\":").append(s.jumps).append(",");
                sb.append("\"xpLevels\":").append(s.xpLevelsGained).append(",");
                sb.append("\"sneak\":").append(s.sneakTimeTicks);
                sb.append("}");
            }
            sb.append("\n}\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private static Path statsPath(MinecraftServer server) {
        Path world = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
        return world.resolve("iceysmp").resolve("stats.json");
    }

    private static String extractString(String body, String key, String fallback) {
        int idx = body.indexOf("\"" + key + "\":\"");
        if (idx < 0) return fallback;
        int start = idx + key.length() + 4;
        int end = body.indexOf('"', start);
        return end < 0 ? fallback : body.substring(start, end);
    }

    private static long extractLong(String body, String key) {
        int idx = body.indexOf("\"" + key + "\":");
        if (idx < 0) return 0;
        int start = idx + key.length() + 3;
        int end = start;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) end++;
        try { return Long.parseLong(body.substring(start, end)); } catch (Exception e) { return 0; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── Event wiring ───────────────────────────────────────────────────

    public static void registerEvents(StatTracker stats, CombatTracker combat, SmpConfig config) {
        // Block breaks: mining + crops + wood + diamonds (all from one hook)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            try {
                if (!(player instanceof ServerPlayerEntity sp)) return;
                if (sp.isCreative() || sp.isSpectator()) return;
                Block b = state.getBlock();
                PlayerStats ps = stats.get(sp.getUuid(), sp.getName().getString());
                ps.name = sp.getName().getString();
                if (MINING_BLOCKS.contains(b)) ps.mining++;
                if (DIAMOND_BLOCKS.contains(b)) ps.diamonds++;
            } catch (Throwable ignored) {}
        });

        // Deaths: PvP kill steal, mob kill bucket, animal kill bucket, own death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            try {
                PlayerEntity attacker = resolveAttacker(source);

                if (entity instanceof ServerPlayerEntity victim) {
                    // Victim's own death counter
                    PlayerStats vs = stats.get(victim.getUuid(), victim.getName().getString());
                    vs.deaths++;
                    vs.name = victim.getName().getString();

                    if (attacker instanceof ServerPlayerEntity sp && !sp.getUuid().equals(victim.getUuid())) {
                        // PvP kill anti-farm gates
                        if (NoobProtection.isProtected(stats.get(sp.getUuid(), sp.getName().getString()), config)
                                || NoobProtection.isProtected(vs, config)) {
                            // Protected players don't earn/give credit
                        } else if (!combat.bothTagged(sp.getUuid(), victim.getUuid())) {
                            // not actively fighting → no credit, no steal
                        } else if (!combat.canCountKill(sp.getUuid(), victim.getUuid(), config.sameVictimCooldownSeconds())) {
                            // already-killed-this-player → no credit, no steal
                        } else {
                            PlayerStats attackerStats = stats.get(sp.getUuid(), sp.getName().getString());
                            attackerStats.pvpKills++;
                            attackerStats.name = sp.getName().getString();
                            long stolen = 0;
                            if (config.killStealsStats()) {
                                stolen = stealTotal(vs);
                                attackerStats.absorbFrom(vs);
                            }
                            combat.recordKill(sp.getUuid(), victim.getUuid());
                            // Server-wide broadcast — bold so it doesn't get missed.
                            try {
                                String msg = "§b§l[Icey SMP] §c§l" + sp.getName().getString()
                                        + " §rkilled §c§l" + victim.getName().getString()
                                        + (stolen > 0 ? " §7and stole §b§l" + stolen + "§r§7 stats!" : "§7!");
                                if (IceySmp.server != null) {
                                    IceySmp.server.getPlayerManager().broadcast(
                                            net.minecraft.text.Text.literal(msg), false);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    combat.clearCombat(victim.getUuid());
                } else if (entity instanceof LivingEntity le) {
                    // Mob/animal kill credit for the attacker
                    if (attacker instanceof ServerPlayerEntity sp) {
                        PlayerStats ps = stats.get(sp.getUuid(), sp.getName().getString());
                        ps.name = sp.getName().getString();
                        if (le instanceof HostileEntity) ps.mobKills++;
                        else if (le instanceof PassiveEntity) ps.animalKills++;
                    }
                }
            } catch (Throwable ignored) {}
        });

        // Damage: combat tag, damage dealt, damage taken, noob protection cancel
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            try {
                PlayerEntity attacker = resolveAttacker(source);

                // Noob protection: cancel any PvP damage to/from protected players
                if (entity instanceof ServerPlayerEntity victim
                        && attacker instanceof ServerPlayerEntity sp
                        && !sp.getUuid().equals(victim.getUuid())) {
                    PlayerStats vs = stats.get(victim.getUuid(), victim.getName().getString());
                    PlayerStats as = stats.get(sp.getUuid(), sp.getName().getString());
                    if (NoobProtection.isProtected(vs, config) || NoobProtection.isProtected(as, config)) {
                        return false; // cancel damage
                    }
                    combat.tag(sp.getUuid(), victim.getUuid());
                    as.damageDealt += (long) (amount * 10);
                    vs.damageTaken += (long) (amount * 10);
                } else if (entity instanceof ServerPlayerEntity victim) {
                    // Damage from non-player source: just mark victim in combat for /spawn gating
                    combat.tagOne(victim.getUuid());
                    PlayerStats vs = stats.get(victim.getUuid(), victim.getName().getString());
                    vs.damageTaken += (long) (amount * 10);
                } else if (attacker instanceof ServerPlayerEntity sp) {
                    // Player hitting mob: counts toward damage-dealt + their combat tag
                    PlayerStats as = stats.get(sp.getUuid(), sp.getName().getString());
                    as.damageDealt += (long) (amount * 10);
                    combat.tagOne(sp.getUuid());
                }
            } catch (Throwable ignored) {}
            return true;
        });
    }

    /** Sum of all stealable counters on a victim — used for the kill
     *  broadcast (`stole 47 stats!`). Includes legacy fields (crops,
     *  woodChopped) for completeness even though they're not tracked
     *  any more — old save data may still have non-zero values that
     *  transfer on kill. */
    private static long stealTotal(PlayerStats vs) {
        return vs.mining + vs.pvpKills + vs.playtimeTicks + vs.mobKills + vs.animalKills
             + vs.diamonds + vs.damageDealt + vs.damageTaken + vs.deaths
             + vs.fishCaught + vs.distanceWalkedCm + vs.jumps + vs.xpLevelsGained + vs.sneakTimeTicks
             + vs.crops + vs.woodChopped;
    }

    private static PlayerEntity resolveAttacker(DamageSource source) {
        try {
            if (source == null) return null;
            if (source.getAttacker() instanceof PlayerEntity p) return p;
            if (source.getSource() instanceof PlayerEntity p) return p;
        } catch (Throwable ignored) {}
        return null;
    }
}
