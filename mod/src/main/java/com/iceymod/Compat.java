package com.iceymod;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Version-portable accessors for MC API methods that got renamed between
 * 1.21.8 and 1.21.11. Each helper tries the known method names in order,
 * then falls back to reading the underlying field via reflection if no
 * accessor matches.
 *
 * <h2>Why this exists</h2>
 * The client mod is built against 1.21.8 yarn but ships as a single jar
 * the launcher installs for any MC version. 1.21.11 renamed (or removed)
 * several methods the rendering code relies on:
 *
 * <ul>
 *   <li>{@code Camera.getPos()} → field {@code Camera.pos} (no replacement accessor)
 *   <li>{@code Entity.getPos()} → {@code Entity.getSyncedPos()} / {@code getLastRenderPos()}
 *   <li>{@code RenderLayer.getLines()} → moved package, name unchanged
 *   <li>{@code VertexRendering.drawBox(...)} → signature changed
 *   <li>{@code ClientWorld.getSpawnPos()} → removed entirely
 *   <li>{@code GameOptions.getGraphicsMode()} → removed entirely
 * </ul>
 *
 * <p>Wrapping these in reflection keeps the 1.21.8-built jar runnable on
 * 1.21.11 — direct compile-time method references would NoSuchMethodError
 * at runtime.
 */
public final class Compat {

    private Compat() {}

    /** Camera position. 1.21.8 had {@code getPos()}; 1.21.11 removed it.
     *  Falls back to reading the {@code pos} field directly. */
    public static Vec3d cameraPos(Camera cam) {
        if (cam == null) return Vec3d.ZERO;
        // Method first (faster than field lookup on hot path)
        try {
            Object v = cam.getClass().getMethod("getPos").invoke(cam);
            if (v instanceof Vec3d vd) return vd;
        } catch (Throwable ignored) {}
        // Field fallback — Camera.pos is private but accessible via reflection
        try {
            for (Field f : Camera.class.getDeclaredFields()) {
                if (f.getType() == Vec3d.class) {
                    f.setAccessible(true);
                    Object v = f.get(cam);
                    if (v instanceof Vec3d vd) return vd;
                }
            }
        } catch (Throwable ignored) {}
        return Vec3d.ZERO;
    }

    /** Entity position. 1.21.8 used {@code getPos()}; 1.21.11 split it
     *  into {@code getSyncedPos()} / {@code getLastRenderPos()}. Try
     *  each, fall through to the {@code pos} field. */
    public static Vec3d entityPos(Entity entity) {
        if (entity == null) return Vec3d.ZERO;
        for (String name : new String[] {"getPos", "getSyncedPos", "getLastRenderPos"}) {
            try {
                Method m = entity.getClass().getMethod(name);
                Object v = m.invoke(entity);
                if (v instanceof Vec3d vd) return vd;
            } catch (Throwable ignored) {}
        }
        // Walk inheritance chain for the `pos` field (declared on Entity).
        try {
            Class<?> c = entity.getClass();
            while (c != null) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == Vec3d.class && "pos".equals(f.getName())) {
                        f.setAccessible(true);
                        Object v = f.get(entity);
                        if (v instanceof Vec3d vd) return vd;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return Vec3d.ZERO;
    }

    /** World spawn position (overworld). {@code ClientWorld.getSpawnPos()}
     *  was removed in 1.21.11. Tries the method, falls back to (0, 64, 0)
     *  which is a reasonable default if the call site can't find it. */
    public static BlockPos worldSpawnPos(Object world) {
        if (world == null) return new BlockPos(0, 64, 0);
        try {
            Object v = world.getClass().getMethod("getSpawnPos").invoke(world);
            if (v instanceof BlockPos bp) return bp;
        } catch (Throwable ignored) {}
        // Try via LevelProperties getter
        for (String getter : new String[] {"getLevelProperties", "getProperties", "getLevelData"}) {
            try {
                Object props = world.getClass().getMethod(getter).invoke(world);
                if (props == null) continue;
                Object v = props.getClass().getMethod("getSpawnPos").invoke(props);
                if (v instanceof BlockPos bp) return bp;
            } catch (Throwable ignored) {}
        }
        return new BlockPos(0, 64, 0);
    }
}
