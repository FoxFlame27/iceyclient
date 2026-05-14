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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v1.84.0 — top-level commands. No more {@code /icey X} subcommand tree;
 * each function is its own command so tab-complete is one keystroke
 * shorter and the help GUI is the canonical browse surface.
 *
 * <pre>
 * /skills                    chest GUI with progress bars per category
 * /leaderboard &lt;cat&gt;        top 10 + your rank for a category   (alias /lb)
 * /mystats                   your stats summary
 * /playerstats &lt;player&gt;     another player's stats
 * /daily                     claim daily reward (14h cooldown)
 * /bounty &lt;player&gt; &lt;xp&gt;     pay XP to bounty someone
 * /crate [common|rare|epic]  spawn a loot crate                   (op-2)
 * /reward &lt;cat&gt; &lt;player&gt;    hand-give a max-level reward         (op-2)
 * /noobprotect &lt;on|off&gt;     master switch for noob protection   (op-2)
 * /setspawn                  set world spawn here                 (op-2)
 * /reloadcfg                 reload config from disk              (op-3)
 * /resetstats                wipe all stats                       (op-4)
 * </pre>
 */
public final class SmpCommands {

    /** Combined permission check — real op-2 OR /admin-unlocked. Used by
     *  the op-2 player commands so they work in singleplayer worlds where
     *  /op doesn't grant anything. */
    private static boolean canAdmin(ServerCommandSource src) {
        if (hasPermLevel(src, 2)) return true;
        try {
            ServerPlayerEntity p = src.getPlayer();
            if (p != null && IceySmp.stats != null) {
                PlayerStats ps = IceySmp.stats.peek(p.getUuid());
                if (ps != null && ps.adminAccess) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int rejectNoAdmin(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Admin only. Run §f/admin <password>§c to unlock."), false);
        return 0;
    }

    /** Yarn shuffles {@code hasPermissionLevel} ↔ {@code hasPermission}
     *  across 1.21.x and findVirtual can fail access checks. Resolve via
     *  runtime reflection with a PlayerManager.isOperator fallback. */
    private static boolean hasPermLevel(ServerCommandSource src, int level) {
        if (src == null) return false;
        for (String name : new String[] {"hasPermissionLevel", "hasPermission"}) {
            try {
                java.lang.reflect.Method m = src.getClass().getMethod(name, int.class);
                Object r = m.invoke(src, level);
                if (r instanceof Boolean b) return b;
            } catch (NoSuchMethodException ignored) {}
            catch (Throwable ignored) {}
        }
        try {
            for (java.lang.reflect.Method m : src.getClass().getMethods()) {
                if (m.getReturnType() != boolean.class) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] != int.class) continue;
                String n = m.getName().toLowerCase();
                if (!n.contains("permission")) continue;
                try {
                    Object r = m.invoke(src, level);
                    if (r instanceof Boolean b) return b;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            ServerPlayerEntity p = src.getPlayer();
            MinecraftServer s = src.getServer();
            if (p == null && s != null) return true;
            if (p != null && s != null) {
                Object pm = s.getPlayerManager();
                Object profile = p.getGameProfile();
                for (java.lang.reflect.Method m : pm.getClass().getMethods()) {
                    if (!"isOperator".equals(m.getName())) continue;
                    if (m.getParameterCount() != 1) continue;
                    Class<?> pt = m.getParameterTypes()[0];
                    Object arg = pt.isInstance(profile) ? profile : null;
                    if (arg == null) {
                        try {
                            Object ops = pm.getClass().getMethod("getOpList").invoke(pm);
                            Object entry = ops.getClass().getMethod("get", Object.class).invoke(ops, profile);
                            if (entry != null && pt.isInstance(entry)) arg = entry;
                        } catch (Throwable ignored) {}
                    }
                    if (arg == null) continue;
                    try {
                        Object r = m.invoke(pm, arg);
                        if (r instanceof Boolean b) return b;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /skills — chest GUI primary entrypoint
            dispatcher.register(CommandManager.literal("skills")
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayer();
                        if (p == null) {
                            ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /skills must be run by a player"), false);
                            return 0;
                        }
                        SkillsScreen.open(p);
                        return 1;
                    }));

            // /leaderboard [cat]  — no arg opens the chest GUI; with arg
            // sends the legacy chat-text top-10. (/lb removed per user
            // request — only /leaderboard now.)
            dispatcher.register(CommandManager.literal("leaderboard")
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayer();
                        if (p == null) {
                            ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /leaderboard must be run by a player (or use /leaderboard <category> from console)"), false);
                            return 0;
                        }
                        LeaderboardGui.open(p);
                        return 1;
                    })
                    .then(CommandManager.argument("category", StringArgumentType.word())
                            .suggests((ctx, b) -> { for (String id : LeaderboardManager.categoryIds()) b.suggest(id); return b.buildFuture(); })
                            .executes(ctx -> showTop(ctx.getSource(), StringArgumentType.getString(ctx, "category")))));

            // /mystats
            dispatcher.register(CommandManager.literal("mystats")
                    .executes(ctx -> showSelf(ctx.getSource())));

            // /playerstats <player>
            dispatcher.register(CommandManager.literal("playerstats")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests((ctx, b) -> {
                                MinecraftServer s = ctx.getSource().getServer();
                                if (s != null) for (var p : s.getPlayerManager().getPlayerList()) b.suggest(p.getName().getString());
                                return b.buildFuture();
                            })
                            .executes(ctx -> showStats(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));

            // /daily
            dispatcher.register(CommandManager.literal("daily")
                    .executes(ctx -> doDaily(ctx.getSource())));

            // /bounty <player> <xp>
            dispatcher.register(CommandManager.literal("bounty")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests((ctx, b) -> {
                                MinecraftServer s = ctx.getSource().getServer();
                                if (s != null) for (var p : s.getPlayerManager().getPlayerList()) b.suggest(p.getName().getString());
                                return b.buildFuture();
                            })
                            .then(CommandManager.argument("xp", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                    .executes(ctx -> doBounty(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "player"),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "xp"))))));

            // /crate, /armorcrate, /gearcrate, /foodcrate — each takes
            // optional [common|rare|epic]. (op-2 OR /admin-unlocked)
            registerCrateCommand(dispatcher, "crate",      LootCrate.Theme.GENERAL);
            registerCrateCommand(dispatcher, "armorcrate", LootCrate.Theme.ARMOR);
            registerCrateCommand(dispatcher, "gearcrate",  LootCrate.Theme.GEAR);
            registerCrateCommand(dispatcher, "foodcrate",  LootCrate.Theme.FOOD);

            // /reward <category> <player>   (op-2 OR /admin-unlocked)
            dispatcher.register(CommandManager.literal("reward")
                    .then(CommandManager.argument("category", StringArgumentType.word())
                            .suggests((ctx, b) -> { for (String id : LeaderboardManager.categoryIds()) b.suggest(id); return b.buildFuture(); })
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .suggests((ctx, b) -> {
                                        MinecraftServer s = ctx.getSource().getServer();
                                        if (s != null) for (var p : s.getPlayerManager().getPlayerList()) b.suggest(p.getName().getString());
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        if (!canAdmin(ctx.getSource())) return rejectNoAdmin(ctx.getSource());
                                        String catId = StringArgumentType.getString(ctx, "category");
                                        String name = StringArgumentType.getString(ctx, "player");
                                        MinecraftServer s = ctx.getSource().getServer();
                                        ServerPlayerEntity target = (s != null) ? s.getPlayerManager().getPlayer(name) : null;
                                        if (target == null) { ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c no online player named " + name), false); return 0; }
                                        LeaderboardManager.Category cat = (IceySmp.leaderboard != null) ? IceySmp.leaderboard.categoryById(catId) : null;
                                        if (cat == null) { ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c unknown category: " + catId), false); return 0; }
                                        boolean ok = WeaponDrops.giveReward(target, cat.id(), cat.label());
                                        // Also apply the max-level effect for that category
                                        // — user wants the themed weapon AND the peak buff,
                                        // infinite duration. Mark them as awarded so the
                                        // AFTER_RESPAWN re-apply path keeps the buff after
                                        // any death.
                                        if (ok) {
                                            try {
                                                if (IceySmp.stats != null) {
                                                    PlayerStats tps = IceySmp.stats.get(target.getUuid(), target.getName().getString());
                                                    tps.markFrostfangAwardedFor(cat.id());
                                                }
                                                if (IceySmp.leaderboard != null) {
                                                    IceySmp.leaderboard.applyMaxEffectFor(target, cat.id());
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal(ok
                                                ? "§5§l[§d§lAttribute§7§lSMP§5§l]§r §aReward (" + cat.label() + ") given to §f" + name
                                                : "§5§l[§d§lAttribute§7§lSMP§5§l]§r§c failed to give reward"), true);
                                        return ok ? 1 : 0;
                                    }))));

            // /noobprotect <on|off|toggle>   (op-2 OR /admin-unlocked)
            dispatcher.register(CommandManager.literal("noobprotect")
                    .executes(ctx -> {
                        boolean on = IceySmp.config != null && IceySmp.config.noobProtectionEnabled();
                        ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§7 Noob protection: §" + (on ? "a" : "c") + (on ? "ON" : "OFF")
                                + " §8(/noobprotect on|off|toggle)"), false);
                        return 1;
                    })
                    .then(CommandManager.argument("mode", StringArgumentType.word())
                            .suggests((ctx, b) -> { b.suggest("on"); b.suggest("off"); b.suggest("toggle"); return b.buildFuture(); })
                            .executes(ctx -> {
                                if (!canAdmin(ctx.getSource())) return rejectNoAdmin(ctx.getSource());
                                return doNoobProtect(ctx.getSource(), StringArgumentType.getString(ctx, "mode"));
                            })));

            // /reloadcfg   (op-3)
            dispatcher.register(CommandManager.literal("reloadcfg")
                    .requires(s -> hasPermLevel(s, 3))
                    .executes(ctx -> {
                        IceySmp.config = SmpConfig.loadOrDefault();
                        ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §aConfig reloaded"), true);
                        return 1;
                    }));

            // /resetstats   (op-4)
            dispatcher.register(CommandManager.literal("resetstats")
                    .requires(s -> hasPermLevel(s, 4))
                    .executes(ctx -> {
                        if (IceySmp.stats == null) { ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c not ready"), false); return 0; }
                        int n = IceySmp.stats.size();
                        IceySmp.stats.clear();
                        if (ctx.getSource().getServer() != null) IceySmp.stats.save(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §cWiped " + n + " player stats"), true);
                        return 1;
                    }));

            // /setspawn   (op-2 OR /admin-unlocked).
            dispatcher.register(CommandManager.literal("setspawn")
                    .executes(ctx -> {
                        if (!canAdmin(ctx.getSource())) return rejectNoAdmin(ctx.getSource());
                        return doSetSpawn(ctx.getSource());
                    }));

            // /admin <password>  — password-gated op grant. Password is
            // baked into the mod (not a config secret) — this is meant for
            // a friends-server playstyle, not real security. Brute-force
            // window is irrelevant because the player is in the server's
            // op list afterwards anyway.
            dispatcher.register(CommandManager.literal("admin")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal("§7Usage: §f/admin <password>"), false);
                        return 1;
                    })
                    .then(CommandManager.argument("password", StringArgumentType.word())
                            .executes(ctx -> doAdmin(ctx.getSource(), StringArgumentType.getString(ctx, "password")))));
        });
    }

    /** Baked-in admin password — see /admin comments. */
    private static final String ADMIN_PASSWORD = "2705";

    private static int doAdmin(ServerCommandSource src, String password) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /admin must be run by a player"), false);
            return 0;
        }
        if (!ADMIN_PASSWORD.equals(password)) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Wrong admin password"), false);
            return 0;
        }
        if (IceySmp.stats == null) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c not ready yet"), false);
            return 0;
        }
        MinecraftServer s = src.getServer();
        String name = p.getName().getString();
        PlayerStats ps = IceySmp.stats.get(p.getUuid(), name);

        if (canAdmin(src)) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §aYou already have admin access — /reward, /crate, /setspawn, /noobprotect all available."), false);
            return 1;
        }

        // Flag-based grant only — no /op. Unlocks the iceymod+ admin
        // commands (/reward, /crate, /setspawn, /noobprotect) without
        // giving the player real operator perms (so they can't /gamemode,
        // /tp, etc.). Works identically on dedicated and singleplayer.
        ps.adminAccess = true;

        src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §a✓ Admin access granted. You can now use §f/reward §a, §f/crate §a, §f/setspawn §a, §f/noobprotect§a."), false);
        try {
            if (s != null) s.getPlayerManager().broadcast(
                    Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §f" + name + " §7used §f/admin §7— AttributeSMP admin commands unlocked."), false);
        } catch (Throwable ignored) {}
        return 1;
    }

    /** Wire a /<name> [tier] crate command for one Theme — all four
     *  themes share identical shape (argless = random tier, optional
     *  tier word arg with suggestions). */
    private static void registerCrateCommand(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher,
                                             String name, LootCrate.Theme theme) {
        dispatcher.register(CommandManager.literal(name)
                .executes(ctx -> {
                    if (!canAdmin(ctx.getSource())) return rejectNoAdmin(ctx.getSource());
                    return LootCrate.spawnNearCaller(ctx.getSource(), theme, LootCrate.Tier.pickRandom()) ? 1 : 0;
                })
                .then(CommandManager.argument("tier", StringArgumentType.word())
                        .suggests((ctx, b) -> { b.suggest("common"); b.suggest("rare"); b.suggest("epic"); return b.buildFuture(); })
                        .executes(ctx -> {
                            if (!canAdmin(ctx.getSource())) return rejectNoAdmin(ctx.getSource());
                            String t = StringArgumentType.getString(ctx, "tier").toUpperCase();
                            LootCrate.Tier tier;
                            try { tier = LootCrate.Tier.valueOf(t); }
                            catch (IllegalArgumentException e) {
                                ctx.getSource().sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c unknown tier — use common, rare, or epic"), false);
                                return 0;
                            }
                            return LootCrate.spawnNearCaller(ctx.getSource(), theme, tier) ? 1 : 0;
                        })));
    }

    private static int doNoobProtect(ServerCommandSource src, String mode) {
        if (IceySmp.config == null) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c config not loaded"), false);
            return 0;
        }
        boolean newVal;
        switch (mode.toLowerCase()) {
            case "on"     -> newVal = true;
            case "off"    -> newVal = false;
            case "toggle" -> newVal = !IceySmp.config.noobProtectionEnabled();
            default -> {
                src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c usage: /noobprotect on|off|toggle"), false);
                return 0;
            }
        }
        IceySmp.config.setNoobProtectionEnabled(newVal);
        final boolean state = newVal;
        src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §aNoob protection §" + (state ? "a" : "c") + (state ? "ENABLED" : "DISABLED")), true);
        return 1;
    }

    private static int doSetSpawn(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        MinecraftServer server = src.getServer();
        if (server == null) return 0;
        ServerWorld overworld = server.getOverworld();
        BlockPos pos = (p != null) ? p.getBlockPos() : resolveWorldSpawn(overworld);
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
                    src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §aWorld spawn set to §f"
                            + pp.getX() + ", " + pp.getY() + ", " + pp.getZ()), true);
                    return 1;
                }
            }
        } catch (Throwable t) {
            System.out.println("[IceySMP] /setspawn failed: " + t);
        }
        src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /setspawn unavailable on this MC version"), false);
        return 0;
    }

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
        double m = cm / 100.0;
        if (m < 1000) return String.format("%.1f m", m);
        return String.format("%,.0f m", m);
    }

    private static int doDaily(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /daily must be run by a player"), false); return 0; }
        if (IceySmp.stats == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c not ready yet"), false); return 0; }
        PlayerStats ps = IceySmp.stats.get(p.getUuid(), p.getName().getString());
        long remain = DailyRewards.cooldownRemainingMs(ps);
        if (remain > 0) {
            long h = remain / 3_600_000L;
            long m = (remain % 3_600_000L) / 60_000L;
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Daily already rolled — next in §f" + h + "h " + m + "m"), false);
            return 0;
        }
        DailyRewards.roll(p, ps);
        return 1;
    }

    private static int doBounty(ServerCommandSource src, String targetName, int xp) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /bounty must be run by a player"), false); return 0; }
        MinecraftServer server = src.getServer();
        if (server == null || IceySmp.stats == null) return 0;
        if (p.getName().getString().equalsIgnoreCase(targetName)) {
            p.sendMessage(Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c Can't bounty yourself"), false);
            return 0;
        }
        if (p.experienceLevel < xp) {
            p.sendMessage(Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c You only have " + p.experienceLevel + " XP levels"), false);
            return 0;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c no online player named " + targetName), false); return 0; }
        p.addExperienceLevels(-xp);
        PlayerStats tps = IceySmp.stats.get(target.getUuid(), target.getName().getString());
        tps.bountyXp += xp;
        server.getPlayerManager().broadcast(
                Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §c§l" + p.getName().getString()
                        + " §r§7put a §6§l" + xp + " XP§r§7 bounty on §c§l" + target.getName().getString()
                        + " §7(total: §6§l" + tps.bountyXp + "§r§7)"),
                false);
        return 1;
    }

    private static int showTop(ServerCommandSource src, String category) {
        if (IceySmp.leaderboard == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c not ready yet"), false); return 0; }
        List<LeaderboardManager.Ranked> ranked = IceySmp.leaderboard.top(category);
        if (ranked.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c unknown category: " + category + " §8(use Tab to autocomplete)"), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §7Top §b" + category + "§7:"), false);
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
        if (p == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c /mystats must be run by a player"), false); return 0; }
        return showStatsFor(src, p.getUuid(), p.getName().getString());
    }

    private static int showStats(ServerCommandSource src, String playerName) {
        MinecraftServer server = src.getServer();
        if (server == null) return 0;
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerName);
        UUID uuid = (target != null) ? target.getUuid() : findUuidByName(playerName);
        if (uuid == null) {
            src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c no player named " + playerName + " on record"), false);
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
        if (IceySmp.leaderboard == null || IceySmp.stats == null) { src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r§c not ready yet"), false); return 0; }
        PlayerStats ps = IceySmp.stats.peek(uuid);
        if (ps == null) { src.sendFeedback(() -> Text.literal("§7No stats yet for §f" + displayName), false); return 0; }
        src.sendFeedback(() -> Text.literal("§5§l[§d§lAttribute§7§lSMP§5§l]§r §rStats for §f§l" + displayName + "§r:"), false);
        for (String catId : LeaderboardManager.categoryIds()) {
            List<LeaderboardManager.Ranked> ranked = IceySmp.leaderboard.top(catId);
            int rank = -1;
            long value = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).uuid.equals(uuid)) { rank = i; value = ranked.get(i).value; break; }
            }
            if (value == 0) continue;
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

    private static String formatValue(String category, long value) {
        return switch (category) {
            case "playtime" -> formatTicks(value);
            case "walking"  -> formatCmHuman(value);
            case "dmgtaken" -> String.format("%.1f HP", value / 10.0);
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
