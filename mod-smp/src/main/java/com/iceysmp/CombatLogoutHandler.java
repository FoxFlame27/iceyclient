package com.iceysmp;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Combat logout deterrent. When a combat-tagged player disconnects, fire
 * {@code /kill <name>} via the server command pipeline. Vanilla /kill
 * handles inventory drop, death stats, and AFTER_DEATH (which routes the
 * PvP credit if the last damager was a player) for free.
 *
 * <p>DISCONNECT fires before the player is removed from the player manager,
 * so the command target resolves correctly. Going via the command pipeline
 * (instead of damage()/kill() on the entity) sidesteps yarn signature drift
 * — VersionShim.executeServerCommand already has the three-path fallback.
 */
public final class CombatLogoutHandler {

    private CombatLogoutHandler() {}

    public static void register(CombatTracker combat, SmpConfig config) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                if (!config.killOnCombatLogout()) return;
                ServerPlayerEntity p = handler.player;
                if (p == null) return;
                if (!combat.isInCombat(p.getUuid())) return;
                String name = p.getName().getString();
                System.out.println("[IceySMP] combat-logout: /kill " + name);
                VersionShim.executeServerCommand(server, "kill " + name);
            } catch (Throwable t) {
                System.out.println("[IceySMP] CombatLogout handler failed: " + t);
            }
        });
    }
}
