package com.iceymod.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Reflection bridge for Fabric API's {@code WorldRenderEvents}.
 *
 * <p>Fabric API moved the package path between MC 1.21.8 and 1.21.11:
 * <pre>
 *   1.21.8 →  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
 *   1.21.11 → net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
 * </pre>
 *
 * <p>Same story for {@code WorldRenderContext}. Direct imports of either
 * path would fail to compile against the other matrix variant and ALSO
 * fail at runtime if the user runs a different MC version than the
 * client mod was built against. This helper resolves the class at
 * runtime via {@link Class#forName} (tries both paths), builds a
 * {@link Proxy} implementing the listener interface, and exposes a
 * {@link Ctx} wrapper that calls {@code matrixStack()/camera()/
 * consumers()} on the underlying context object reflectively.
 *
 * <p>Callers write code like:
 * <pre>{@code
 *   WorldRenderHook.registerAfterEntities(ctx -> {
 *       MatrixStack ms = ctx.matrixStack();
 *       Camera cam = ctx.camera();
 *       ...
 *   });
 * }</pre>
 *
 * Same shape as the original Fabric API but version-agnostic.
 */
public final class WorldRenderHook {

    private WorldRenderHook() {}

    private static final String[] EVENTS_PATHS = new String[] {
            "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents", // 1.21.11+
            "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",       // 1.21.0-1.21.8
    };

    public static boolean registerAfterEntities(Consumer<Ctx> handler) {
        return register("AFTER_ENTITIES", handler);
    }

    public static boolean registerAfterTranslucent(Consumer<Ctx> handler) {
        return register("AFTER_TRANSLUCENT", handler);
    }

    private static boolean register(String fieldName, Consumer<Ctx> handler) {
        Class<?> eventsClass = null;
        for (String path : EVENTS_PATHS) {
            try { eventsClass = Class.forName(path); break; }
            catch (ClassNotFoundException ignored) {}
        }
        if (eventsClass == null) {
            System.out.println("[IceyMod] WorldRenderHook: no WorldRenderEvents class found in either path");
            return false;
        }

        try {
            Field eventField = eventsClass.getField(fieldName);
            Object event = eventField.get(null); // Fabric Event<X>

            // The listener interface is a nested class of WorldRenderEvents
            // named after the event (AfterEntities, AfterTranslucent, ...).
            // Convention: fieldName=AFTER_ENTITIES → nested=AfterEntities.
            String nested = toCamelCase(fieldName);
            Class<?> listenerType = Class.forName(eventsClass.getName() + "$" + nested);

            Object proxy = Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[] { listenerType },
                    (instance, method, args) -> {
                        if (args == null || args.length < 1) return null;
                        try { handler.accept(new Ctx(args[0])); }
                        catch (Throwable t) { System.out.println("[IceyMod] WorldRenderHook callback threw: " + t); }
                        // Method returns void.
                        return null;
                    });

            // Get register() from the PUBLIC Event interface, not the
            // impl class. event.getClass() returns ArrayBackedEvent which
            // lives in net.fabricmc.fabric.impl.base.event — a package
            // the Java module system blocks reflective access to (even
            // for public members). Routing through the Event interface
            // sidesteps that. Real-world failure observed: "Illegal
            // AccessException: class WorldRenderHook cannot access a
            // member of class ArrayBackedEvent with modifiers public".
            Method registerMethod = null;
            try {
                Class<?> eventInterface = Class.forName("net.fabricmc.fabric.api.event.Event");
                registerMethod = eventInterface.getMethod("register", Object.class);
            } catch (Throwable ignored) {}
            // Fallback: walk the impl class hierarchy looking for a
            // register() that takes the listener type.
            if (registerMethod == null) {
                for (Method m : event.getClass().getMethods()) {
                    if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> p = m.getParameterTypes()[0];
                        if (p.isAssignableFrom(listenerType)) { registerMethod = m; break; }
                    }
                }
            }
            if (registerMethod == null) {
                System.out.println("[IceyMod] WorldRenderHook: no register(listener) on Event");
                return false;
            }
            try { registerMethod.setAccessible(true); } catch (Throwable ignored) {}
            registerMethod.invoke(event, proxy);
            return true;
        } catch (Throwable t) {
            System.out.println("[IceyMod] WorldRenderHook.register('" + fieldName + "') failed: " + t);
            return false;
        }
    }

    /** AFTER_ENTITIES → AfterEntities. */
    private static String toCamelCase(String upperUnderscore) {
        String[] parts = upperUnderscore.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Reflection-based facade over a {@code WorldRenderContext} instance.
     *  Methods are looked up by name on whatever class the underlying
     *  object happens to be — works regardless of which class-path
     *  variant Fabric API is shipping. */
    public static final class Ctx {
        private final Object context;
        Ctx(Object context) { this.context = context; }

        public MatrixStack matrixStack() {
            return invoke(MatrixStack.class, "matrixStack");
        }
        public Camera camera() {
            return invoke(Camera.class, "camera");
        }
        public VertexConsumerProvider consumers() {
            return invoke(VertexConsumerProvider.class, "consumers");
        }
        public float tickDelta() {
            try {
                Object v = context.getClass().getMethod("tickCounter").invoke(context);
                if (v != null) {
                    // RenderTickCounter has getTickProgress(boolean) on 1.21.5+.
                    try {
                        Object f = v.getClass().getMethod("getTickProgress", boolean.class).invoke(v, true);
                        if (f instanceof Float ff) return ff;
                    } catch (Throwable ignored) {}
                    try {
                        Object f = v.getClass().getMethod("getTickDelta", boolean.class).invoke(v, true);
                        if (f instanceof Float ff) return ff;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            // Older API exposed tickDelta() directly on the context.
            Float legacy = invoke(Float.class, "tickDelta");
            return legacy == null ? 0f : legacy;
        }

        @SuppressWarnings("unchecked")
        private <T> T invoke(Class<T> type, String method) {
            try {
                Object v = context.getClass().getMethod(method).invoke(context);
                if (v == null) return null;
                if (type.isInstance(v)) return (T) v;
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
