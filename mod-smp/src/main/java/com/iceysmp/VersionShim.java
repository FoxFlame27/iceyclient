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

    /** Execute a server command. Tries three paths in order:
     *
     *  <ol>
     *   <li>{@code CommandManager.executeWithPrefix(ServerCommandSource, String)}
     *       — the yarn-canonical method on most variants. Returns void; we
     *       assume success if it doesn't throw.
     *   <li>{@code CommandManager.execute(ServerCommandSource, String)}
     *       — alternate name in some yarn variants.
     *   <li>Brigadier dispatcher path: find the {@code CommandDispatcher}
     *       on the command manager (via getter method, field, or
     *       field-by-type scan), then call its {@code execute(String, S)}.
     *       This path surfaces parse errors as exceptions so the caller
     *       can detect failure and retry with a fallback syntax.
     *  </ol>
     *
     *  Returns true if any path ran the command without exception.
     *  Brigadier {@code execute} returns {@code int} — caller can read the
     *  return as a hint, but the contract is just "ran/didn't run".
     */
    public static boolean executeServerCommand(MinecraftServer server, String cmd) {
        if (server == null || cmd == null) return false;
        Object cm = server.getCommandManager();
        ServerCommandSource src = server.getCommandSource();

        // Path 1: executeWithPrefix(ServerCommandSource, String) — most common
        try {
            Method m = cm.getClass().getMethod("executeWithPrefix", ServerCommandSource.class, String.class);
            m.invoke(cm, src, cmd);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) { /* try next */ }

        // Path 2: execute(ServerCommandSource, String)
        try {
            Method m = cm.getClass().getMethod("execute", ServerCommandSource.class, String.class);
            m.invoke(cm, src, cmd);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) { /* try next */ }

        // Path 3: find dispatcher → execute(String, S)
        Object disp = findDispatcher(cm);
        if (disp != null) {
            for (Method m : disp.getClass().getMethods()) {
                if (!"execute".equals(m.getName())) continue;
                if (m.getParameterCount() != 2) continue;
                if (m.getParameterTypes()[0] != String.class) continue;
                try {
                    Object r = m.invoke(disp, cmd, src);
                    if (r instanceof Integer i) return i > 0;
                    return true;
                } catch (Throwable t) { return false; }
            }
        }
        System.out.println("[IceySMP] executeServerCommand: no execution path worked for: " + cmd);
        return false;
    }

    /** Walk method names, then field names, then any field of type
     *  {@code CommandDispatcher} to extract the dispatcher from a
     *  {@code CommandManager} instance. */
    private static Object findDispatcher(Object cm) {
        for (String name : new String[] {"getDispatcher", "getCommandDispatcher"}) {
            try { return cm.getClass().getMethod(name).invoke(cm); }
            catch (NoSuchMethodException ignored) {}
            catch (Throwable ignored) {}
        }
        for (String name : new String[] {"dispatcher", "commandDispatcher"}) {
            try {
                java.lang.reflect.Field f = cm.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(cm);
            } catch (NoSuchFieldException ignored) {}
            catch (Throwable ignored) {}
        }
        // Last resort: any field whose declared type is CommandDispatcher
        try {
            for (java.lang.reflect.Field f : cm.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("CommandDispatcher")) {
                    f.setAccessible(true);
                    return f.get(cm);
                }
            }
        } catch (Throwable ignored) {}
        return null;
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
