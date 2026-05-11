package com.iceysmp;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Combat logout deterrent. When a player who is currently combat-tagged
 * disconnects, we kill them on the spot (their inventory drops, their
 * stats get stolen by whoever last tagged them if there's a kill record
 * established by AFTER_DEATH).
 *
 * <p>Hook: {@code ServerPlayConnectionEvents.DISCONNECT}. We get the
 * player handle while they're still semi-attached. Calling kill() in this
 * window correctly fires AFTER_DEATH on the server thread.
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
                // Damage source: out-of-world / generic — the AFTER_DEATH handler
                // already routes PvP credit via the lastDamageTaker if it was a
                // player hit recently, so we don't need to fake an attacker here.
                VersionShim.damageSafe(p, server.getOverworld(),
                        server.getOverworld().getDamageSources().outOfWorld(),
                        Float.MAX_VALUE);
                if (p.isAlive()) VersionShim.killSafe(p, server.getOverworld());
                p.sendMessage(Text.literal("§c§l[Icey SMP] §rYou combat-logged. You died."), false);
            } catch (Throwable t) {
                System.out.println("[IceySMP] CombatLogout death failed: " + t);
            }
        });
    }
}
