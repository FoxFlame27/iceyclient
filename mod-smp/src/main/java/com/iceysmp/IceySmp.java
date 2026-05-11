package com.iceysmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Icey SMP — server-side leaderboard + auto-buff + PvP guardrails.
 *
 * Stats tracked + applied as MC status effects: see {@link LeaderboardManager.Category}.
 * Effect amplifier scales with each player's count via a non-linear curve
 * (fast at low counts, slow at high counts) capped per-effect.
 *
 * PvP guardrails: combat tag, /spawn block during combat, kill on logout
 * during combat, full-stat-steal on legitimate kill, 10-minute noob
 * protection from first join, iron starter kit on first join.
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

            ServerTickEvents.END_SERVER_TICK.register(server -> {
                try { leaderboard.tick(server); } catch (Throwable t) {
                    System.out.println("[IceySMP] tick error: " + t);
                }
            });

            // Player join: create stats row if absent (sets firstJoinTimestamp),
            // then grant starter kit if their stats entry is brand new.
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
                try {
                    var p = handler.player;
                    if (p == null) return;
                    PlayerStats ps = stats.get(p.getUuid(), p.getName().getString());
                    StarterKit.giveIfFirstJoin(p, ps, config);
                    if (NoobProtection.isProtected(ps, config)) {
                        p.sendMessage(net.minecraft.text.Text.literal(
                                "§b§l[Icey SMP] §aYou have §l" + NoobProtection.remainingMinutes(ps, config)
                                + " min§r§a of noob protection — no PvP damage to or from you."), false);
                    }
                } catch (Throwable t) {
                    System.out.println("[IceySMP] JOIN handler failed: " + t);
                }
            });

            StatTracker.registerEvents(stats, combat, config);
            CombatLogoutHandler.register(combat, config);
            SmpCommands.register();

            System.out.println("[IceySMP] Initialized");
        } catch (Throwable t) {
            System.out.println("[IceySMP] Init failed: " + t);
            t.printStackTrace();
        }
    }
}
