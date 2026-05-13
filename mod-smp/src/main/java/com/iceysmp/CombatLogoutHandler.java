package com.iceysmp;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat logout deterrent.
 *
 * <p>DISCONNECT fires AFTER the player entity has detached from the world,
 * so calling damage()/kill() there is a no-op — the player just respawns
 * fine on next login. Two-phase approach instead:
 *
 * <ol>
 *   <li>DISCONNECT: if combat-tagged, drop their entire inventory into the
 *       world at their last position (works because we still have the
 *       ServerPlayerEntity handle with valid pos/world) and flag the UUID
 *       in {@link #pendingDeath}.
 *   <li>JOIN: if UUID is flagged, kill the player on the next tick (so the
 *       client is fully attached) and clear the flag.
 * </ol>
 *
 * <p>State is in-memory — if the server restarts between disconnect and
 * rejoin, the combat-logger escapes. That matches CombatTracker's existing
 * "tags reset on restart" semantic.
 */
public final class CombatLogoutHandler {

    private static final Set<UUID> pendingDeath = ConcurrentHashMap.newKeySet();

    private CombatLogoutHandler() {}

    public static void register(CombatTracker combat, SmpConfig config) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                if (!config.killOnCombatLogout()) return;
                ServerPlayerEntity p = handler.player;
                if (p == null) return;
                if (!combat.isInCombat(p.getUuid())) return;

                // Drop their stuff right now while we still have the entity in-world.
                dropInventory(p);
                pendingDeath.add(p.getUuid());
                System.out.println("[IceySMP] combat-logout flagged: " + p.getName().getString());

                // Best-effort death attempt too — sometimes this catches them
                // before the save snapshot, sometimes it doesn't.
                try {
                    VersionShim.damageSafe(p, server.getOverworld(),
                            server.getOverworld().getDamageSources().outOfWorld(),
                            Float.MAX_VALUE);
                    if (p.isAlive()) VersionShim.killSafe(p, server.getOverworld());
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                System.out.println("[IceySMP] CombatLogout DISCONNECT failed: " + t);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((netHandler, sender, server) -> {
            try {
                ServerPlayerEntity p = netHandler.player;
                if (p == null) return;
                if (!pendingDeath.remove(p.getUuid())) return;
                System.out.println("[IceySMP] combat-logger rejoined, killing: " + p.getName().getString());
                // Run on the next tick so the join packet sequence completes first.
                final MinecraftServer s = server;
                final ServerPlayerEntity pl = p;
                s.execute(() -> {
                    try {
                        VersionShim.damageSafe(pl, s.getOverworld(),
                                s.getOverworld().getDamageSources().outOfWorld(),
                                Float.MAX_VALUE);
                        if (pl.isAlive()) VersionShim.killSafe(pl, s.getOverworld());
                        s.getPlayerManager().broadcast(
                                Text.literal("§c§l[Icey SMP] §f" + pl.getName().getString()
                                        + " §rcombat-logged and died on rejoin."), false);
                    } catch (Throwable t) {
                        System.out.println("[IceySMP] rejoin-kill failed: " + t);
                    }
                });
            } catch (Throwable t) {
                System.out.println("[IceySMP] CombatLogout JOIN handler failed: " + t);
            }
        });
    }

    /** Empty the player's inventory at their current position. Reflection-
     *  driven so we tolerate yarn renames of dropItem / dropAll / etc. */
    private static void dropInventory(ServerPlayerEntity p) {
        try {
            Object inv = p.getClass().getMethod("getInventory").invoke(p);
            if (inv == null) return;
            // Most yarn variants: PlayerInventory.dropAll() drops every slot.
            try {
                inv.getClass().getMethod("dropAll").invoke(inv);
                return;
            } catch (NoSuchMethodException ignored) {}
            catch (Throwable ignored) {}
            // Fallback: walk size() + getStack(i) + setStack(i, EMPTY) + drop via player.dropItem
            try {
                int size = (int) inv.getClass().getMethod("size").invoke(inv);
                for (int i = 0; i < size; i++) {
                    try {
                        Object stack = inv.getClass().getMethod("getStack", int.class).invoke(inv, i);
                        if (stack == null) continue;
                        Object empty = stack.getClass().getField("EMPTY").get(null);
                        boolean isEmpty = (boolean) stack.getClass().getMethod("isEmpty").invoke(stack);
                        if (isEmpty) continue;
                        // Drop and clear
                        try {
                            p.getClass().getMethod("dropItem", stack.getClass().getSuperclass(), boolean.class, boolean.class)
                                    .invoke(p, stack, true, false);
                        } catch (Throwable ignored) {}
                        inv.getClass().getMethod("setStack", int.class, stack.getClass()).invoke(inv, i, empty);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            System.out.println("[IceySMP] dropInventory failed: " + t);
        }
    }
}
