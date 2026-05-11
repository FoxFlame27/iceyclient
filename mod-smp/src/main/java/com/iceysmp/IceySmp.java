package com.iceysmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Icey SMP — server-side leaderboard + auto-buff system.
 *
 * Three categories ship in v1: Mining (Haste), PvP (Strength), Playtime
 * (Saturation). All tracked server-side, persisted to JSON, recomputed every
 * 30 seconds. The top 1–2 players in each category get the corresponding
 * effect refreshed for ~60 seconds — so it stays applied while they hold the
 * rank, fades automatically when they drop.
 *
 * Anti-farm:
 *   - PvP victim cooldown: 10 minutes per attacker→victim pair
 *   - Combat tag: both players must have hit each other within 10 seconds
 *     for the kill to count
 *   - Mining only counts the curated ore set (see {@link StatTracker})
 *
 * Works on 1.21.0 – 1.21.11 — the API surfaces we use are stable and we
 * ship a per-version jar built by CI.
 */
public final class IceySmp implements ModInitializer {
    public static final String MOD_ID = "iceysmp";

    public static SmpConfig config;
    public static StatTracker stats;
    public static CombatTracker combat;
    public static LeaderboardManager leaderboard;

    @Override
    public void onInitialize() {
        try {
            config = SmpConfig.loadOrDefault();
            stats = new StatTracker();
            combat = new CombatTracker(config.combatTagSeconds());
            leaderboard = new LeaderboardManager(stats, combat, config);

            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                stats.load(server);
                leaderboard.bind(server);
                System.out.println("[IceySMP] Loaded " + stats.size() + " player stats");
            });
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                stats.save(server);
                System.out.println("[IceySMP] Saved player stats");
            });

            // Tick: 20 per second. We do periodic work (playtime increment,
            // leaderboard recompute, save) gated by mod counters in the
            // leaderboard manager so the hot path stays cheap.
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                try { leaderboard.tick(server); } catch (Throwable t) {
                    System.out.println("[IceySMP] tick error: " + t);
                }
            });

            StatTracker.registerEvents(stats, combat, config);
            SmpCommands.register();

            System.out.println("[IceySMP] Initialized");
        } catch (Throwable t) {
            System.out.println("[IceySMP] Init failed: " + t);
            t.printStackTrace();
        }
    }
}
