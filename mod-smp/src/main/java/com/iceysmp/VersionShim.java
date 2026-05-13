package com.iceysmp;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Version-compat shims. Mojang/Yarn keep shuffling the signatures of
 * {@code teleport}, {@code damage}, {@code kill} between 1.21.x patch
 * versions — sometimes adding a {@code ServerWorld} parameter, sometimes
 * adding a {@code resetCamera} boolean, sometimes removing the
 * {@code Set<PositionFlag>}. Since we build the same source against four
 * different yarn mappings (1.21 / 1.21.5 / 1.21.8 / 1.21.11), any
 * literal name+signature reference in the source can fail on the half of
 * the matrix that has a different overload set.
 *
 * <p>Each helper tries every known signature in descending order of
 * (probable) recency, returning on first success. Reflection lookup is
 * once per call — could cache MethodHandles if it ever became a hot path,
 * but these are infrequent.
 */
public final class VersionShim {
    private VersionShim() {}

    /** Teleport a player. Tries 8-arg → 7-arg → 6-arg → 4-arg fallback. */
    public static void teleportSafe(ServerPlayerEntity p, ServerWorld world,
                                    double x, double y, double z,
                                    float yaw, float pitch) {
        // 8-arg: (ServerWorld, x, y, z, Set, yaw, pitch, resetCamera)
        if (invokeQuietly(p, "teleport",
                new Class<?>[] {ServerWorld.class, double.class, double.class, double.class, Set.class, float.class, float.class, boolean.class},
                new Object[]   {world, x, y, z, Set.of(), yaw, pitch, false})) return;
        // 7-arg: (ServerWorld, x, y, z, Set, yaw, pitch)
        if (invokeQuietly(p, "teleport",
                new Class<?>[] {ServerWorld.class, double.class, double.class, double.class, Set.class, float.class, float.class},
                new Object[]   {world, x, y, z, Set.of(), yaw, pitch})) return;
        // 6-arg: (ServerWorld, x, y, z, yaw, pitch)
        if (invokeQuietly(p, "teleport",
                new Class<?>[] {ServerWorld.class, double.class, double.class, double.class, float.class, float.class},
                new Object[]   {world, x, y, z, yaw, pitch})) return;
        // 4-arg LivingEntity fallback: (x, y, z, resetCamera)
        invokeQuietly(p, "teleport",
                new Class<?>[] {double.class, double.class, double.class, boolean.class},
                new Object[]   {x, y, z, false});
    }

    /** Damage. Tries 3-arg (world, source, amount) → 2-arg (source, amount). */
    public static void damageSafe(ServerPlayerEntity p, ServerWorld world, DamageSource src, float amount) {
        if (invokeQuietly(p, "damage",
                new Class<?>[] {ServerWorld.class, DamageSource.class, float.class},
                new Object[]   {world, src, amount})) return;
        invokeQuietly(p, "damage",
                new Class<?>[] {DamageSource.class, float.class},
                new Object[]   {src, amount});
    }

    /** Execute a server command via Brigadier directly. Goes through
     *  {@code CommandManager.getDispatcher().execute(String, S)} — the
     *  brigadier API itself is stable across yarn versions (it's a
     *  Mojang-shipped lib, not subject to mapping renames). Returns
     *  TRUE only if Brigadier parsed and ran the command without a
     *  CommandSyntaxException — meaning callers can use the result to
     *  decide whether to retry with a fallback syntax. */
    public static boolean executeServerCommand(MinecraftServer server, String cmd) {
        if (server == null || cmd == null) return false;
        try {
            Object cm = server.getCommandManager();
            Object disp = cm.getClass().getMethod("getDispatcher").invoke(cm);
            ServerCommandSource src = server.getCommandSource();
            for (Method m : disp.getClass().getMethods()) {
                if (!"execute".equals(m.getName())) continue;
                if (m.getParameterCount() != 2) continue;
                if (m.getParameterTypes()[0] != String.class) continue;
                try {
                    Object r = m.invoke(disp, cmd, src);
                    // Brigadier returns int: >0 success, 0 "no result"
                    // (command parsed but didn't do anything useful, e.g.
                    // unknown player). Caller wants to know if something
                    // actually happened — return false on 0 so fallbacks
                    // can try a different syntax.
                    if (r instanceof Integer i) return i > 0;
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }
        } catch (Throwable t) {
            System.out.println("[IceySMP] executeServerCommand setup failed: " + t);
        }
        return false;
    }

    /** Kill. Tries 1-arg (ServerWorld) → 0-arg. */
    public static void killSafe(ServerPlayerEntity p, ServerWorld world) {
        if (invokeQuietly(p, "kill",
                new Class<?>[] {ServerWorld.class},
                new Object[]   {world})) return;
        invokeQuietly(p, "kill",
                new Class<?>[] {},
                new Object[]   {});
    }

    private static boolean invokeQuietly(Object target, String name, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m = findMethodIncludingInherited(target.getClass(), name, paramTypes);
            if (m == null) return false;
            m.setAccessible(true);
            m.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethodIncludingInherited(Class<?> cls, String name, Class<?>[] paramTypes) {
        Class<?> c = cls;
        while (c != null) {
            try { return c.getDeclaredMethod(name, paramTypes); }
            catch (NoSuchMethodException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }
}
