package com.iceymod.render;

import com.iceymod.compat.GeometryRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 1.21.x world-render hook over Fabric's WorldRenderEvents, resolved
 * reflectively because the event class moved packages (…rendering.v1 →
 * …rendering.v1.world) and renamed its phases (AFTER_TRANSLUCENT/LAST →
 * END_MAIN) between 1.21.8 and 1.21.11, while one jar has to serve
 * 1.21.8 through 1.21.10.
 */
public final class WorldRenderHook {
    private WorldRenderHook() {}

    private static final String[] EVENTS_PATHS = {
            "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents", // 1.21.11
            "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",       // 1.21.8-1.21.10
    };

    public static boolean registerAfterEntities(Consumer<Ctx> handler) { return register(handler, "AFTER_ENTITIES"); }
    public static boolean registerAfterTranslucent(Consumer<Ctx> handler) { return register(handler, "AFTER_TRANSLUCENT", "END_MAIN"); }

    private static boolean register(Consumer<Ctx> handler, String... fieldNames) {
        Class<?> eventsClass = null;
        for (String path : EVENTS_PATHS) {
            try { eventsClass = Class.forName(path); break; } catch (ClassNotFoundException ignored) {}
        }
        if (eventsClass == null) { System.out.println("[IceyMod] WorldRenderHook: no WorldRenderEvents class found"); return false; }
        String fieldName = null; Field eventField = null;
        for (String candidate : fieldNames) {
            try { eventField = eventsClass.getField(candidate); fieldName = candidate; break; } catch (NoSuchFieldException ignored) {}
        }
        if (eventField == null) { System.out.println("[IceyMod] WorldRenderHook: none of " + String.join("/", fieldNames) + " exist"); return false; }
        try {
            Object event = eventField.get(null);
            Class<?> listenerType = Class.forName(eventsClass.getName() + "$" + toCamelCase(fieldName));
            Object proxy = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] { listenerType }, (instance, method, args) -> {
                if (args == null || args.length < 1) return null;
                try { handler.accept(new Ctx(args[0])); }
                catch (Throwable t) { System.out.println("[IceyMod] WorldRenderHook callback threw: " + t); }
                return null;
            });
            // register() via the public Event interface: the impl lives in a package the module system hides.
            Method registerMethod = Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class);
            registerMethod.invoke(event, proxy);
            return true;
        } catch (Throwable t) {
            System.out.println("[IceyMod] WorldRenderHook.register('" + fieldName + "') failed: " + t);
            return false;
        }
    }

    private static String toCamelCase(String upperUnderscore) {
        StringBuilder sb = new StringBuilder();
        for (String p : upperUnderscore.split("_")) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static final class Ctx {
        private final Object context;
        Ctx(Object context) { this.context = context; }

        public PoseStack poseStack() {
            PoseStack ps = invoke(PoseStack.class, "matrixStack"); // 1.21.8
            if (ps != null) return ps;
            return invoke(PoseStack.class, "matrices");            // 1.21.11
        }
        public Camera camera() { return Minecraft.getInstance().gameRenderer.getMainCamera(); }
        public float tickDelta() { return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true); }
        private MultiBufferSource consumers() { return invoke(MultiBufferSource.class, "consumers"); }

        public void geometry(RenderType type, GeometryRenderer r) {
            MultiBufferSource vcp = consumers();
            PoseStack ps = poseStack();
            if (vcp == null || ps == null || type == null) return;
            r.render(ps.last(), vcp.getBuffer(type));
        }

        public void worldText(Component text, boolean seeThrough, int color, int backgroundColor) {
            MultiBufferSource vcp = consumers();
            PoseStack ps = poseStack();
            if (vcp == null || ps == null) return;
            Minecraft.getInstance().font.drawInBatch(text, 0f, 0f, color, false, ps.last().pose(), vcp,
                    seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, backgroundColor, 0xF000F0);
        }

        /** Beacon-beam layer. Direct call for the version this jar was built for; the 1.21.8 jar also runs on
         *  1.21.9/1.21.10 where the factory moved to RenderTypes (intermediary class_12249), hence the fallback. */
        public RenderType beaconBeam(Identifier texture, boolean translucent) {
            try { return RenderTypes.beaconBeam(texture, translucent); } catch (Throwable ignored) {}
            return (RenderType) callStatic(new String[] { "beaconBeam", "getBeaconBeam", "method_23592", "method_75988" },
                    new Class<?>[] { Identifier.class, boolean.class }, texture, translucent);
        }
        public RenderType lines() {
            try { return RenderTypes.lines(); } catch (Throwable ignored) {}
            return (RenderType) callStatic(new String[] { "lines", "getLines", "method_23594" }, new Class<?>[0]);
        }

        private static Object callStatic(String[] names, Class<?>[] params, Object... args) {
            java.util.List<Class<?>> holders = new java.util.ArrayList<>();
            holders.add(RenderType.class);
            for (String n : new String[] { "net.minecraft.client.renderer.rendertype.RenderTypes", "net.minecraft.class_12249" }) {
                try { holders.add(Class.forName(n)); } catch (Throwable ignored) {}
            }
            java.util.Set<String> wanted = java.util.Set.of(names);
            for (Class<?> h : holders) {
                for (Method m : h.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) || !wanted.contains(m.getName())) continue;
                    if (!java.util.Arrays.equals(m.getParameterTypes(), params) || m.getReturnType() != RenderType.class) continue;
                    try { return m.invoke(null, args); } catch (Throwable ignored) {}
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private <T> T invoke(Class<T> type, String method) {
            try {
                Object v = context.getClass().getMethod(method).invoke(context);
                if (type.isInstance(v)) return (T) v;
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
