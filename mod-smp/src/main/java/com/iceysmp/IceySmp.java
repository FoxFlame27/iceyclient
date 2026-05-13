package com.iceysmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Icey SMP — server-side leaderboard + auto-buff + PvP guardrails.
 *
 * <p>Init order is intentional: commands register BEFORE any state setup
 * that could throw on a foreign Yarn version. If config / stats / combat /
 * leaderboard fail to initialize, /icey still exists — it just reports
 * "not ready" so users can see something went wrong instead of "command
 * not found" with no signal.
 */
public final class IceySmp implements ModInitializer {
    public static final String MOD_ID = "iceysmp";

    public static SmpConfig config;
    public static StatTracker stats;
    public static CombatTracker combat;
    public static LeaderboardManager leaderboard;
    public static CombatBossBar combatBossBar;
    /** Set in SERVER_STARTED, cleared in SERVER_STOPPING. Lets non-event
     *  code (e.g. StatTracker's death broadcast) reach the server without
     *  passing it through every call site. */
    public static net.minecraft.server.MinecraftServer server;

    @Override
    public void onInitialize() {
        System.out.println("[IceySMP] onInitialize start");

        // 1. Register commands FIRST so /icey exists even if downstream init throws.
        try {
            SmpCommands.register();
            System.out.println("[IceySMP] commands registered");
        } catch (Throwable t) {
            System.out.println("[IceySMP] command registration failed: " + t);
            t.printStackTrace();
        }

        // 2. Config
        try {
            config = SmpConfig.loadOrDefault();
            System.out.println("[IceySMP] config loaded");
        } catch (Throwable t) {
            System.out.println("[IceySMP] config load failed: " + t);
            config = new SmpConfig(); // default — fields keep their initializers
        }

        // 3. State
        try { stats = new StatTracker(); System.out.println("[IceySMP] stats tracker ready"); }
        catch (Throwable t) { System.out.println("[IceySMP] stats init failed: " + t); }
        try {
            int tagSec = (config != null) ? config.combatTagSeconds() : 25;
            combat = new CombatTracker(tagSec);
            combatBossBar = new CombatBossBar(combat);
            System.out.println("[IceySMP] combat tracker + boss bar ready");
        } catch (Throwable t) { System.out.println("[IceySMP] combat init failed: " + t); }
        try {
            if (stats != null && combat != null) {
                leaderboard = new LeaderboardManager(stats, combat, config);
                System.out.println("[IceySMP] leaderboard ready");
            }
        } catch (Throwable t) { System.out.println("[IceySMP] leaderboard init failed: " + t); }

        // 4. Server-lifecycle hooks — EACH registration in its own try/catch.
        // If any one fabric event class is missing/renamed on a yarn variant,
        // the rest still register cleanly. Previously a single throw in this
        // block could skip StatTracker.registerEvents (i.e. no mining/pvp
        // tracking would ever happen) which gave the "everything shows 0"
        // symptom that's been hard to reproduce.
        try { ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            IceySmp.server = server;
            if (stats != null) stats.load(server);
            if (leaderboard != null) leaderboard.bind(server);
            System.out.println("[IceySMP] SERVER_STARTED: " + (stats == null ? 0 : stats.size()) + " players in stats");
            try {
                server.execute(() -> server.getPlayerManager().broadcast(
                        net.minecraft.text.Text.literal("§b§l[iceymod+] §aLoaded! §7Type §f/icey§7 or press §fN§7 to see commands."),
                        false));
            } catch (Throwable ignored) {}
        }); System.out.println("[IceySMP] SERVER_STARTED hook installed"); }
        catch (Throwable t) { System.out.println("[IceySMP] SERVER_STARTED hook failed: " + t); }

        try { ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (stats != null) stats.save(server);
            IceySmp.server = null;
        }); System.out.println("[IceySMP] SERVER_STOPPING hook installed"); }
        catch (Throwable t) { System.out.println("[IceySMP] SERVER_STOPPING hook failed: " + t); }

        try { ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (leaderboard == null) return;
            try { leaderboard.tick(server); } catch (Throwable t) { System.out.println("[IceySMP] tick error: " + t); }
        }); System.out.println("[IceySMP] tick hook installed"); }
        catch (Throwable t) { System.out.println("[IceySMP] tick hook failed: " + t); }

        try { ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                var p = handler.player;
                if (p == null || stats == null) return;
                PlayerStats ps = stats.get(p.getUuid(), p.getName().getString());
                StarterKit.giveIfFirstJoin(p, ps, config);
                if (NoobProtection.isProtected(ps, config)) {
                    p.sendMessage(net.minecraft.text.Text.literal(
                            "§b§l[Icey SMP] §aYou have §l" + NoobProtection.remainingMinutes(ps, config)
                            + " min§r§a of noob protection — no PvP damage to or from you."), false);
                }
            } catch (Throwable t) { System.out.println("[IceySMP] JOIN handler failed: " + t); }
        }); System.out.println("[IceySMP] JOIN hook installed"); }
        catch (Throwable t) { System.out.println("[IceySMP] JOIN hook failed: " + t); }

        try {
            if (stats != null && combat != null) {
                StatTracker.registerEvents(stats, combat, config);
                System.out.println("[IceySMP] stat-tracker event hooks installed (mining, pvp, mob kills, damage)");
            } else {
                System.out.println("[IceySMP] WARN stat-tracker hooks SKIPPED (stats=" + (stats != null) + " combat=" + (combat != null) + ")");
            }
        } catch (Throwable t) { System.out.println("[IceySMP] stat-tracker hook setup failed: " + t); }

        try { if (combat != null) CombatLogoutHandler.register(combat, config); }
        catch (Throwable t) { System.out.println("[IceySMP] CombatLogoutHandler failed: " + t); }

        System.out.println("[IceySMP] onInitialize complete");
    }
}
