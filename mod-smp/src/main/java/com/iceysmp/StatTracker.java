package com.iceysmp;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentStateManager;

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
 * Owns the per-player counter table + JSON persistence. Increment methods
 * are called from Fabric event callbacks on the server tick thread, so the
 * map is a ConcurrentHashMap purely so commands (chat thread) can safely
 * iterate it.
 */
public final class StatTracker {

    private final Map<UUID, PlayerStats> map = new ConcurrentHashMap<>();

    /** Ores that count toward the Mining leaderboard. Curated to skip
     *  trivially-farmable / common blocks (stone, dirt) and keep the
     *  signal-to-noise high. Wrapped in try/catch via {@link #safeAdd} so
     *  a missing block on some MC version doesn't blow up class init. */
    private static final Set<Block> MINING_BLOCKS = buildMiningBlocks();

    private static Set<Block> buildMiningBlocks() {
        Set<Block> s = new HashSet<>();
        safeAdd(s, () -> Blocks.DIAMOND_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_DIAMOND_ORE);
        safeAdd(s, () -> Blocks.EMERALD_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_EMERALD_ORE);
        safeAdd(s, () -> Blocks.GOLD_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_GOLD_ORE);
        safeAdd(s, () -> Blocks.NETHER_GOLD_ORE);
        safeAdd(s, () -> Blocks.IRON_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_IRON_ORE);
        safeAdd(s, () -> Blocks.COPPER_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_COPPER_ORE);
        safeAdd(s, () -> Blocks.LAPIS_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_LAPIS_ORE);
        safeAdd(s, () -> Blocks.REDSTONE_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_REDSTONE_ORE);
        safeAdd(s, () -> Blocks.COAL_ORE);
        safeAdd(s, () -> Blocks.DEEPSLATE_COAL_ORE);
        safeAdd(s, () -> Blocks.NETHER_QUARTZ_ORE);
        safeAdd(s, () -> Blocks.ANCIENT_DEBRIS);
        return s;
    }

    private static void safeAdd(Set<Block> s, java.util.function.Supplier<Block> sup) {
        try { Block b = sup.get(); if (b != null) s.add(b); } catch (Throwable ignored) {}
    }

    public PlayerStats get(UUID uuid, String name) {
        return map.computeIfAbsent(uuid, u -> new PlayerStats(name));
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
            // Tiny manual JSON parse — no Gson dep needed on the server.
            // Format: {"<uuid>": {"name":"X","mining":N,"pvp":N,"playtime":N}, ...}
            // Keeps dependencies zero so the mod jar stays tiny.
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
                st.mining = extractLong(body, "mining");
                st.pvpKills = extractLong(body, "pvp");
                st.playtimeTicks = extractLong(body, "playtime");
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
                sb.append("\"mining\":").append(s.mining).append(",");
                sb.append("\"pvp\":").append(s.pvpKills).append(",");
                sb.append("\"playtime\":").append(s.playtimeTicks);
                sb.append("}");
            }
            sb.append("\n}\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private static Path statsPath(MinecraftServer server) {
        // Server-side world save dir → world/iceysmp/stats.json. Survives
        // restarts, doesn't leak between server worlds (e.g. multiple SMPs
        // on the same host).
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
        // Mining: count when an ore is broken in survival/adventure
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            try {
                if (!(player instanceof ServerPlayerEntity sp)) return;
                if (sp.isCreative() || sp.isSpectator()) return;
                if (!MINING_BLOCKS.contains(state.getBlock())) return;
                PlayerStats ps = stats.get(sp.getUuid(), sp.getName().getString());
                ps.mining++;
                ps.name = sp.getName().getString();
            } catch (Throwable ignored) {}
        });

        // PvP: hook AFTER_DEATH so we see the kill with the proper damage source
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            try {
                if (!(entity instanceof ServerPlayerEntity victim)) return;
                PlayerEntity attacker = resolveAttacker(source);
                if (!(attacker instanceof ServerPlayerEntity sp)) return;
                if (sp.getUuid().equals(victim.getUuid())) return; // suicide

                // Anti-farm: both must be combat-tagged on each other
                if (!combat.bothTagged(sp.getUuid(), victim.getUuid())) return;
                // Anti-farm: same victim cooldown
                if (!combat.canCountKill(sp.getUuid(), victim.getUuid(), config.sameVictimCooldownSeconds())) return;

                PlayerStats ps = stats.get(sp.getUuid(), sp.getName().getString());
                ps.pvpKills++;
                ps.name = sp.getName().getString();
                combat.recordKill(sp.getUuid(), victim.getUuid());
            } catch (Throwable ignored) {}
        });

        // Combat tag: when one player damages another, tag both
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            try {
                if (!(entity instanceof ServerPlayerEntity victim)) return true;
                PlayerEntity attacker = resolveAttacker(source);
                if (!(attacker instanceof ServerPlayerEntity sp)) return true;
                if (sp.getUuid().equals(victim.getUuid())) return true;
                combat.tag(sp.getUuid(), victim.getUuid());
            } catch (Throwable ignored) {}
            return true; // never cancel damage
        });
    }

    private static PlayerEntity resolveAttacker(DamageSource source) {
        try {
            if (source == null) return null;
            // getAttacker vs getSource — getAttacker is the entity holding the
            // weapon; getSource is the projectile/etc. We want the holder.
            if (source.getAttacker() instanceof PlayerEntity p) return p;
            if (source.getSource() instanceof PlayerEntity p) return p;
        } catch (Throwable ignored) {}
        return null;
    }

    // Note: PersistentStateManager import unused at the moment but kept for v2
    // when we move stats into the world's persistent-state machinery.
    @SuppressWarnings("unused")
    private static final Class<?> _keepImport = PersistentStateManager.class;
}
