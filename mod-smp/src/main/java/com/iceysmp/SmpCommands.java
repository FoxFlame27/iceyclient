package com.iceysmp;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Slash commands for users + admins. Top-level command is {@code /icey} so
 * it doesn't collide with vanilla {@code /stats} or {@code /leaderboard}
 * (some servers define those).
 *
 *   /icey top mining       - top 10 miners
 *   /icey top pvp          - top 10 pvp kills
 *   /icey top playtime     - top 10 playtime (formatted h:m)
 *   /icey reload           - reload config (admin)
 *   /icey reset            - wipe all stats (admin, op level 4)
 */
public final class SmpCommands {

    /**
     * Yarn renamed {@code ServerCommandSource.hasPermissionLevel(int)} to
     * {@code hasPermission(int)} somewhere in the 1.21.x line — and the
     * exact MC version where it flipped differs by Yarn build. Hard-coding
     * either name fails to compile against the other half of our matrix
     * (1.21 / 1.21.5 / 1.21.8 / 1.21.11). We look up the method by name at
     * class-init time, fall back across the two possibilities, and cache
     * a MethodHandle for fast invocation in the command predicate.
     */
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
                        .suggests((ctx, b) -> { b.suggest("mining"); b.suggest("pvp"); b.suggest("playtime"); return b.buildFuture(); })
                        .executes(ctx -> showTop(ctx.getSource(), StringArgumentType.getString(ctx, "category")))))
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
                        int n = IceySmp.stats.size();
                        IceySmp.stats.clear();
                        if (ctx.getSource().getServer() != null) IceySmp.stats.save(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> Text.literal("§b[Icey SMP] §cWiped " + n + " player stats"), true);
                        return 1;
                    }))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "§b[Icey SMP] §7commands: §f/icey top <mining|pvp|playtime>§7, §f/icey reload§7, §f/icey reset"),
                            false);
                    return 1;
                })
            );
        });
    }

    private static int showTop(ServerCommandSource src, String category) {
        if (IceySmp.leaderboard == null) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] not ready yet"), false);
            return 0;
        }
        List<LeaderboardManager.Ranked> ranked = IceySmp.leaderboard.top(category);
        if (ranked.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] unknown category: " + category), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("§b[Icey SMP] §7Top §b" + category + "§7:"), false);
        int show = Math.min(10, ranked.size());
        for (int i = 0; i < show; i++) {
            LeaderboardManager.Ranked r = ranked.get(i);
            if (r.value == 0) break;
            String medal = switch (i) {
                case 0 -> "§e1.";
                case 1 -> "§71.";
                case 2 -> "§61.";
                default -> "§7" + (i + 1) + ".";
            };
            String displayValue = "playtime".equals(category) ? formatTicks(r.value) : String.valueOf(r.value);
            final int rank = i;
            src.sendFeedback(() -> Text.literal(
                    medal + " §f" + r.name + " §8— §b" + displayValue), false);
        }
        return 1;
    }

    private static String formatTicks(long ticks) {
        long sec = ticks / 20;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
