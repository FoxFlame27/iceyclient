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
import net.minecraft.util.math.Vec3d;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Set;

public final class SmpCommands {

    /** Yarn renamed hasPermissionLevel → hasPermission somewhere in 1.21.x;
     *  we look up whichever exists at class init and cache. */
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
                    String cats = String.join("|", LeaderboardManager.categoryIds());
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "§b[Icey SMP] §7/icey top <" + cats + ">, /icey reload, /icey reset"), false);
                    return 1;
                })
            );

            // /spawn — teleport to world spawn, blocked while combat-tagged.
            dispatcher.register(CommandManager.literal("spawn")
                .executes(ctx -> doSpawn(ctx.getSource())));
        });
    }

    private static int doSpawn(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendFeedback(() -> Text.literal("§c[Icey SMP] /spawn must be run by a player"), false);
            return 0;
        }
        if (IceySmp.combat != null && IceySmp.combat.isInCombat(p.getUuid())) {
            p.sendMessage(Text.literal("§c§l[Icey SMP] §rCan't /spawn while combat-tagged."), false);
            return 0;
        }
        MinecraftServer server = src.getServer();
        if (server == null) return 0;
        ServerWorld overworld = server.getOverworld();
        BlockPos spawn = resolveWorldSpawn(overworld);
        VersionShim.teleportSafe(p, overworld,
                spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                p.getYaw(), p.getPitch());
        p.sendMessage(Text.literal("§b§l[Icey SMP] §aTeleported to spawn."), false);
        return 1;
    }

    /** Yarn moved {@code getSpawnPos} between {@code ServerWorld},
     *  {@code WorldProperties} (via {@code getLevelProperties}), and a few
     *  intermediary aliases across 1.21.x. Walk a list of candidate paths
     *  via reflection so the source compiles cleanly against every matrix
     *  entry. Falls back to (0,64,0) if nothing resolves. */
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
        src.sendFeedback(() -> Text.literal("§b§l[Icey SMP] §7Top §b" + category + "§7:"), false);
        int show = Math.min(10, ranked.size());
        boolean any = false;
        for (int i = 0; i < show; i++) {
            LeaderboardManager.Ranked r = ranked.get(i);
            if (r.value == 0) break;
            any = true;
            String medal = switch (i) {
                case 0 -> "§e§l1.";
                case 1 -> "§7§l2.";
                case 2 -> "§6§l3.";
                default -> "§7" + (i + 1) + ".";
            };
            String displayValue = "playtime".equals(category) ? formatTicks(r.value) : String.valueOf(r.value);
            src.sendFeedback(() -> Text.literal(medal + " §f" + r.name + " §8— §b" + displayValue), false);
        }
        if (!any) src.sendFeedback(() -> Text.literal("§7  (no entries yet)"), false);
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
