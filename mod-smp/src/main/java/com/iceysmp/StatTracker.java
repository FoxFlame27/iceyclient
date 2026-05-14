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
                st.frostfangAwardedFor = extractString(body, "ffAwarded", "");
                st.bountyXp = (int) extractLong(body, "bounty");
                st.lastDailyMs = extractLong(body, "lastDaily");
                st.kitCooldowns = extractString(body, "kitCooldowns", "");
                st.distanceInWaterCm = extractLong(body, "waterCm");
                st.adminAccess = extractLong(body, "adminAccess") == 1L;
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
                sb.append("\"sneak\":").append(s.sneakTimeTicks).append(",");
                sb.append("\"ffAwarded\":\"").append(escape(s.frostfangAwardedFor == null ? "" : s.frostfangAwardedFor)).append("\",");
                sb.append("\"bounty\":").append(s.bountyXp).append(",");
                sb.append("\"lastDaily\":").append(s.lastDailyMs).append(",");
                sb.append("\"kitCooldowns\":\"").append(escape(s.kitCooldowns == null ? "" : s.kitCooldowns)).append("\",");
                sb.append("\"waterCm\":").append(s.distanceInWaterCm).append(",");
                sb.append("\"adminAccess\":").append(s.adminAccess ? 1 : 0);
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
                        // Bounty payout — ALWAYS pays out on any PvP kill,
                        // regardless of combat-tag gates. Previously it
                        // was buried inside the `else` branch below, so a
                        // kill that didn't pass the combat-tag check (e.g.
                        // one-shot ambush, repeat kill on same victim)
                        // would not pay the bounty even though the victim
                        // actually died. User report: "bounty works but
                        // when i kill some1 with a bounty i dnt get the
                        // bounty." Fix is to hoist this above the gates.
                        int bountyClaimed = vs.bountyXp;
                        if (bountyClaimed > 0) {
                            vs.bountyXp = 0;
                            try { sp.addExperienceLevels(bountyClaimed); } catch (Throwable ignored) {}
                            try {
                                if (IceySmp.server != null) IceySmp.server.getPlayerManager().broadcast(
                                        net.minecraft.text.Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §a§l" + sp.getName().getString()
                                                + " §rclaimed a §6§l" + bountyClaimed + " XP §rbounty on §c§l"
                                                + victim.getName().getString()), false);
                            } catch (Throwable ignored) {}
                        }

                        // PvP kill anti-farm gates — these only gate the
                        // stat-steal + pvpKills counter, NOT bounty above.
                        if (NoobProtection.isProtected(stats.get(sp.getUuid(), sp.getName().getString()), config)
                                || NoobProtection.isProtected(vs, config)) {
                            // Protected players don't earn/give stat credit
                        } else if (!combat.bothTagged(sp.getUuid(), victim.getUuid())) {
                            // not actively fighting → no stat credit, no steal
                        } else if (!combat.canCountKill(sp.getUuid(), victim.getUuid(), config.sameVictimCooldownSeconds())) {
                            // already-killed-this-player → no stat credit, no steal
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
                                StringBuilder msg = new StringBuilder("§5§l[§d§lAttribute§7§lSMP§5§l]§r §c§l");
                                msg.append(sp.getName().getString()).append(" §rkilled §c§l").append(victim.getName().getString());
                                if (stolen > 0) msg.append(" §7and stole §b§l").append(stolen).append("§r§7 stats");
                                msg.append("§7!");
                                if (IceySmp.server != null) {
                                    IceySmp.server.getPlayerManager().broadcast(
                                            net.minecraft.text.Text.literal(msg.toString()), false);
                                }
                            } catch (Throwable ignored) {}

                            // Death-cam title for the victim — shows the killer's
                            // name in a big red banner for 5 sec while they're on
                            // the death screen. Not a true camera switch (that
                            // requires invasive gamemode swaps); just the title.
                            try {
                                victim.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(5, 100, 20));
                                victim.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                                        net.minecraft.text.Text.literal("§4§lYOU DIED")));
                                victim.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                                        net.minecraft.text.Text.literal("§7Killed by §c§l" + sp.getName().getString())));
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

        // Damage: combat tag, damage dealt, damage taken, noob protection cancel.
        // Combat tag fires ONLY when the damage source is another living
        // entity (player or mob) — environmental damage (fall, fire, lava,
        // drowning, cactus, magma block, etc.) doesn't trigger the tag or
        // the boss bar.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            try {
                PlayerEntity attacker = resolveAttacker(source);
                net.minecraft.entity.LivingEntity livingAttacker = resolveLivingAttacker(source);

                // PvP — both are players
                if (entity instanceof ServerPlayerEntity victim
                        && attacker instanceof ServerPlayerEntity sp
                        && !sp.getUuid().equals(victim.getUuid())) {
                    PlayerStats vs = stats.get(victim.getUuid(), victim.getName().getString());
                    PlayerStats as = stats.get(sp.getUuid(), sp.getName().getString());
                    if (NoobProtection.isProtected(vs, config) || NoobProtection.isProtected(as, config)) {
                        return false; // cancel damage entirely
                    }
                    combat.tag(sp.getUuid(), victim.getUuid());
                    as.damageDealt += (long) (amount * 10);
                    vs.damageTaken += (long) (amount * 10);
                }
                // Mob hits player — credit damage-taken but DON'T combat-tag.
                // Per user request: "only get comabt tagged by players not
                // mobs." Removed the combat.tagOne call so zombies, skeletons,
                // creepers, etc. don't trigger the boss bar / combat-log
                // death.
                else if (entity instanceof ServerPlayerEntity victim
                        && livingAttacker != null
                        && !(livingAttacker instanceof PlayerEntity)
                        && !livingAttacker.getUuid().equals(victim.getUuid())) {
                    PlayerStats vs = stats.get(victim.getUuid(), victim.getName().getString());
                    vs.damageTaken += (long) (amount * 10);
                }
                // Player hits mob — tag attacker only
                else if (attacker instanceof ServerPlayerEntity sp
                        && !(entity instanceof ServerPlayerEntity)) {
                    PlayerStats as = stats.get(sp.getUuid(), sp.getName().getString());
                    as.damageDealt += (long) (amount * 10);
                    combat.tagOne(sp.getUuid());
                }
                // Environmental damage on a player (fall, lava, fire, etc.)
                // — only track damage-taken counter, no combat tag.
                else if (entity instanceof ServerPlayerEntity victim) {
                    PlayerStats vs = stats.get(victim.getUuid(), victim.getName().getString());
                    vs.damageTaken += (long) (amount * 10);
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

    /** Resolve the LivingEntity attacker (player or mob) from a damage
     *  source — returns null for environmental damage (fall, fire, lava,
     *  drowning, etc.). Used to gate combat-tag triggering. */
    private static net.minecraft.entity.LivingEntity resolveLivingAttacker(DamageSource source) {
        try {
            if (source == null) return null;
            if (source.getAttacker() instanceof net.minecraft.entity.LivingEntity le) return le;
            if (source.getSource() instanceof net.minecraft.entity.LivingEntity le) return le;
        } catch (Throwable ignored) {}
        return null;
    }
}
