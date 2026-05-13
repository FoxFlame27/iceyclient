package com.iceysmp;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Red boss bar countdown that shows during the combat-tag window. Ticked
 * once a second from {@link LeaderboardManager#tick} — when a player is
 * tagged we get-or-create their bar, when the tag expires we remove it.
 *
 * <p>BossBar API has been stable since 1.14 — names ({@code ServerBossBar},
 * {@code BossBar.Color}, {@code BossBar.Style}) shouldn't drift across our
 * 1.21.x yarn matrix. Wrapped in try/catch anyway because yarn surprises.
 */
public final class CombatBossBar {

    private final CombatTracker combat;
    private final Map<UUID, ServerBossBar> bars = new HashMap<>();

    public CombatBossBar(CombatTracker combat) { this.combat = combat; }

    public void tick(MinecraftServer server) {
        try {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID uid = p.getUuid();
                boolean inCombat = combat.isInCombat(uid);
                ServerBossBar bar = bars.get(uid);
                if (inCombat) {
                    if (bar == null) {
                        bar = new ServerBossBar(Text.literal("§c§lIn Combat"),
                                BossBar.Color.RED, BossBar.Style.PROGRESS);
                        bars.put(uid, bar);
                    }
                    if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
                    long remainMs = combat.timeRemainingMs(uid);
                    float pct = Math.max(0f, Math.min(1f, remainMs / (float) combat.tagDurationMs()));
                    bar.setPercent(pct);
                    long sec = Math.max(1, (remainMs + 999) / 1000);
                    bar.setName(Text.literal("§c§lCombat — " + sec + "s"));
                } else if (bar != null) {
                    bar.removePlayer(p);
                    bars.remove(uid);
                }
            }
            // Cleanup bars for offline players
            bars.entrySet().removeIf(e -> server.getPlayerManager().getPlayer(e.getKey()) == null);
        } catch (Throwable t) { System.out.println("[IceySMP] CombatBossBar tick error: " + t); }
    }
}
