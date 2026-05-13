package com.iceysmp;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SmpCommands {

    /** Yarn renamed hasPermissionLevel ↔ hasPermission across 1.21.x; resolve once. */
    private static final MethodHandle PERM_CHECK = resolvePermCheck();

    private static MethodHandle resolvePermCheck() {
        MethodHandles.Lookup l = MethodHandles.lookup();
        MethodType mt = MethodType.methodType(boolean.class, int.class);
        for (String name : new String[] {"hasPermissionLevel", "hasPermission"}) {
            try { return l.findVirtual(ServerCommandSource.class, name, mt); }
            catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean hasPermLevel(ServerCommandSource src, int level) {
        if (PERM_CHECK == null) return false;
        try { return (boolean) PERM_CHECK.invoke(src, level); }
        catch (Throwable t) { return false; }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("icey")
                .then(CommandManager.literal("top")
                    .then(CommandManager.argument("category", StringArgumentType.word())
                        .suggests((ctx, b) -> { for (String id : LeaderboardManager.categoryIds()) b.suggest(id); return b.buildFuture(); })
                        .executes(ctx -> showTop(ctx.getSource(), StringArgumentType.getString(ctx, "category")))))
                .then(CommandManager.literal("me")
                    .executes(ctx -> showSelf(ctx.getSource())))
                .then(CommandManager.literal("help")
                    .executes(ctx -> showHelp(ctx.getSource())))
                .then(CommandManager.literal("version")
                    .executes(ctx -> {
                        // Hardcoded version string — bumped manually each release.
                        // If a user reports "/icey doesn't have feature X", first
                        // ask them to run this so we know what build they're on.
                        ctx.getSource().sendFeedback(() -> Text.literal(
                                "§b§l[Icey SMP] §rserver mod version §a§l1.80.24"), false);
                        return 1;
                    }))
                .then(CommandManager.literal("stats")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            if (s != null) for (var p : s.getPlayerManager().getPlayerList()) b.suggest(p.getName().getString());
                            return b.buildFuture();
                        })
                        .executes(ctx -> showStats(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(CommandManager.literal("reload")
                    .requires(s -> hasPermLevel(s, 3))
                    .executes(ctx -> {
                        IceySmp.config = SmpConfig.loadOrDefault();
                        ctx.getSource().sendFeedback(() -> Text.literal("§b[Icey SMP] §aConfig reloaded"), true);
                        return 1;
                    }))
                .then(CommandManager.literal("reset")
                    .requires(s -> hasPermLevel(s, 4))
                    .executes(ctx -> {
                        if (IceySmp.stats == null) { ctx.getSource().sendFeedback(() -> Text.literal("§c[Icey SMP] not ready"), false); return 0; }
                        int n = IceySmp.stats.size();
                        IceySmp.stats.clear();
                        if (ctx.getSource().getServer() != null) IceySmp.stats.save(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> Text.literal("§b[Icey SMP] §cWiped " + n + " player stats"), true);
                        return 1;
                    }))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§b§l[Icey SMP] §rcommands:"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /icey help §8— effect each category gives + your progress"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /icey top <category> §8— leaderboard for a category"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /icey me §8— your stats + rank across all categories"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /icey stats <player> §8— another player's stats"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /setspawn §8— set world spawn here §7(op-2)"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /icey reload §8— reload config §7(op-3)"), false);
                    ctx.getSource().sendFeedback(() -> Text.literal("§7  /icey reset §8— wipe all stats §7(op-4)"), false);
                    return 1;
                })
            );

            // /spawn removed per user request — vanilla servers already
            // have /spawnpoint, and the in-combat block / out-of-overworld
            // fallback wasn't worth the maintenance.

            // Set world spawn to current position — admin (op-2) only.
            // Just a thin wrapper around setSpawnPos with proper perm gating.
            dispatcher.register(CommandManager.literal("setspawn")
                .requires(s -> hasPermLevel(s, 2))
                .executes(ctx -> doSetSpawn(ctx.getSource())));

            // Server-side /lb fallback. Users with an old iceymod client
            // (or no iceymod at all) won't have the client-side /lb that
            // opens the leaderboard screen — this catches the typo and
            // routes them to /icey help so they at least see something.
            dispatcher.register(CommandManager.literal("lb")
                .executes(ctx -> showHelp(ctx.getSource())));
        });
    }

    private static int doSetSpawn(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        MinecraftServer server = src.getServer();
        if (server == null) return 0;
        ServerWorld overworld = server.getOverworld();
        // Use the player's x/y/z (works even if they're cross-dimension —
        // we apply those coords to overworld spawn). For console, fall
        // back to current world spawn.
        BlockPos pos = (p != null) ? p.getBlockPos() : resolveWorldSpawn(overworld);
        // Yarn variation: setSpawnPos(BlockPos, float) vs setSpawnPos(BlockPos, float, boolean, boolean).
        // Try via reflection so this compiles cleanly across the matrix.
        try {
            for (java.lang.reflect.Method m : overworld.getClass().getMethods()) {
                if (!m.getName().equals("setSpawnPos")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length >= 1 && params[0] == BlockPos.class) {
                    Object[] args = new Object[params.length];
                    args[0] = pos;
                    for (int i = 1; i < params.length; i++) {
                        Class<?> pc = params[i];
                        if (pc == float.class) args[i] = 0f;
                        else if (pc == boolean.class) args[i] = false;
                        else args[i] = null;
                    }
                    m.invoke(overworld, args);
                    final BlockPos pp = pos;
                    src.sendFeedback(() -> Text.literal("§b§l[Icey SMP] §aWorld spawn set to §f"
                            + pp.getX() + ", " + pp.getY() + ", " + pp.getZ()), true);
                    return 1;
                }
            }
        } catch (Throwable t) {
            System.out.println("[IceySMP] /setspawn failed: " + t);
        }
        src.sendFeedback(() -> Text.literal("§c[Icey SMP] /setspawn unavailable on this MC version"), false);
        return 0;
    }

    private static int showHelp(ServerCommandSource src) {
        if (IceySmp.leaderboard == null) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] not ready yet"), false);
            return 0;
        }
        ServerPlayerEntity me = src.getPlayer();
        PlayerStats ps = (me != null && IceySmp.stats != null) ? IceySmp.stats.peek(me.getUuid()) : null;
        src.sendFeedback(() -> Text.literal("§b§l[Icey SMP] §rCategories — §7each level doubles the previous threshold"), false);
        for (LeaderboardManager.Category cat : IceySmp.leaderboard.allCategories()) {
            long count = (ps != null) ? cat.field().applyAsLong(ps) : 0;
            String effectName = effectDisplayName(cat);
            String progress;
            if (ps == null) {
                progress = "§8need " + formatForCategory(cat.id(), cat.divisor()) + " for Lv 1";
            } else {
                double normalized = count / (double) cat.divisor();
                int level = normalized < 1.0 ? 0 : (int)(Math.log(normalized) / Math.log(2)) + 1;
                long next = LeaderboardManager.nextLevelThreshold(count, cat.divisor());
                progress = "§7Lv §b" + level + "§7 — §f" + formatForCategory(cat.id(), count)
                        + "§7/§f" + formatForCategory(cat.id(), next);
            }
            final String line = "  §f" + cat.label() + " §8→ §a" + effectName + " §8| " + progress;
            src.sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    /** Display a raw count in human-friendly units per category.
     *  Playtime: ticks → minutes/hours. Walking: cm → m/km.
     *  Damage taken: ×10 stored → divide by 10 to show HP. Else raw. */
    private static String formatForCategory(String catId, long count) {
        return switch (catId) {
            case "playtime" -> formatTicksHuman(count);
            case "walking"  -> formatCmHuman(count);
            case "dmgtaken" -> String.format("%.1f HP", count / 10.0);
            default         -> String.format("%,d", count);
        };
    }

    private static String formatTicksHuman(long ticks) {
        long sec = ticks / 20;
        if (sec < 60) return sec + "s";
        long m = sec / 60;
        if (m < 60) return m + "m";
        long h = m / 60;
        long mm = m % 60;
        return mm == 0 ? h + "h" : h + "h" + mm + "m";
    }

    private static String formatCmHuman(long cm) {
        if (cm < 100_000) return String.format("%.1fm", cm / 100.0);
        return String.format("%.2fkm", cm / 100_000.0);
    }

    private static String effectDisplayName(LeaderboardManager.Category cat) {
        return switch (cat.id()) {
            case "mining" -> "Haste";
            case "pvp" -> "Strength";
            case "playtime" -> "Saturation";
            case "fishing" -> "Luck";
            case "walking" -> "Speed";
            case "jumps" -> "Jump Boost";
            case "dmgtaken" -> "Resistance";
            default -> "?";
        };
    }

    private static int showTop(ServerCommandSource src, String category) {
        if (IceySmp.leaderboard == null) { src.sendFeedback(() -> Text.literal("§c[Icey SMP] not ready yet"), false); return 0; }
        List<LeaderboardManager.Ranked> ranked = IceySmp.leaderboard.top(category);
        if (ranked.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] unknown category: " + category + " §8(use Tab to autocomplete)"), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("§b§l[Icey SMP] §7Top §b" + category + "§7:"), false);
        int show = Math.min(10, ranked.size());
        boolean any = false;
        for (int i = 0; i < show; i++) {
            LeaderboardManager.Ranked r = ranked.get(i);
            if (r.value == 0) break;
            any = true;
            final int rank = i;
            src.sendFeedback(() -> Text.literal(medalFor(rank) + " §f" + r.name + " §8— §b" + formatValue(category, r.value)), false);
        }
        if (!any) { src.sendFeedback(() -> Text.literal("§7  (no entries yet)"), false); return 1; }
        // Show requester's rank at the bottom if they're not already in the visible top
        ServerPlayerEntity me = src.getPlayer();
        if (me != null) {
            int myRank = -1;
            long myValue = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).uuid.equals(me.getUuid())) { myRank = i; myValue = ranked.get(i).value; break; }
            }
            if (myRank >= 0 && myRank >= show) {
                src.sendFeedback(() -> Text.literal("§8──────────────"), false);
                final int rIdx = myRank; final long v = myValue;
                src.sendFeedback(() -> Text.literal("§7You: §6#" + (rIdx + 1) + " §8— §f" + formatValue(category, v)), false);
            }
        }
        return 1;
    }

    private static int showSelf(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendFeedback(() -> Text.literal("§c[Icey SMP] /icey me must be run by a player"), false); return 0; }
        return showStatsFor(src, p.getUuid(), p.getName().getString());
    }

    private static int showStats(ServerCommandSource src, String playerName) {
        MinecraftServer server = src.getServer();
        if (server == null) return 0;
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerName);
        UUID uuid = (target != null) ? target.getUuid() : findUuidByName(playerName);
        if (uuid == null) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] no player named " + playerName + " on record"), false);
            return 0;
        }
        return showStatsFor(src, uuid, playerName);
    }

    private static UUID findUuidByName(String name) {
        if (IceySmp.stats == null) return null;
        for (Map.Entry<UUID, PlayerStats> e : IceySmp.stats.all().entrySet()) {
            if (name.equalsIgnoreCase(e.getValue().name)) return e.getKey();
        }
        return null;
    }

    private static int showStatsFor(ServerCommandSource src, UUID uuid, String displayName) {
        if (IceySmp.leaderboard == null || IceySmp.stats == null) { src.sendFeedback(() -> Text.literal("§c[Icey SMP] not ready yet"), false); return 0; }
        PlayerStats ps = IceySmp.stats.peek(uuid);
        if (ps == null) { src.sendFeedback(() -> Text.literal("§7No stats yet for §f" + displayName), false); return 0; }
        src.sendFeedback(() -> Text.literal("§b§l[Icey SMP] §rStats for §f§l" + displayName + "§r:"), false);
        for (String catId : LeaderboardManager.categoryIds()) {
            List<LeaderboardManager.Ranked> ranked = IceySmp.leaderboard.top(catId);
            int rank = -1;
            long value = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).uuid.equals(uuid)) { rank = i; value = ranked.get(i).value; break; }
            }
            if (value == 0) continue; // skip categories with no progress
            final int rIdx = rank; final long v = value;
            src.sendFeedback(() -> Text.literal(
                    "§7" + padLabel(catId) + " §f" + formatValue(catId, v) + " §8— §6#" + (rIdx + 1)), false);
        }
        return 1;
    }

    private static String padLabel(String catId) {
        String label = (catId + ":").toLowerCase();
        while (label.length() < 13) label += " ";
        return label;
    }

    private static String medalFor(int rank) {
        return switch (rank) {
            case 0 -> "§e§l1.";
            case 1 -> "§7§l2.";
            case 2 -> "§6§l3.";
            default -> "§7" + (rank + 1) + ".";
        };
    }

    /** Format the value for display, picking a per-category format. */
    private static String formatValue(String category, long value) {
        return switch (category) {
            case "playtime" -> formatTicks(value);
            case "walking"  -> String.format("%.1f km", value / 100_000.0);
            case "dmgdealt", "dmgtaken" -> String.format("%.1f HP", value / 10.0);
            case "sneak"    -> formatTicks(value); // sneak time also in ticks
            default -> formatWithCommas(value);
        };
    }

    private static String formatTicks(long ticks) {
        long sec = ticks / 20;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return sec + "s";
    }

    private static String formatWithCommas(long n) {
        return String.format("%,d", n);
    }

    private static BlockPos resolveWorldSpawn(ServerWorld world) {
        Object viaDirect = tryInvoke(world, "getSpawnPos");
        if (viaDirect instanceof BlockPos bp) return bp;
        for (String containerMethod : new String[] {"getLevelProperties", "getProperties", "getLevelData"}) {
            Object container = tryInvoke(world, containerMethod);
            if (container == null) continue;
            Object pos = tryInvoke(container, "getSpawnPos");
            if (pos instanceof BlockPos bp) return bp;
        }
        return new BlockPos(0, 64, 0);
    }

    private static Object tryInvoke(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }
}
