package com.iceymod.render;

import com.iceymod.compat.GeometryRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 26.x world-render hook. The level renderer is now a submit/extract
 * pipeline: everything we draw is handed to the SubmitNodeCollector
 * during {@code COLLECT_SUBMITS} and Minecraft draws it in the right pass.
 */
public final class WorldRenderHook {
    private WorldRenderHook() {}

    public static boolean registerAfterTranslucent(Consumer<Ctx> handler) { return register(handler); }
    public static boolean registerAfterEntities(Consumer<Ctx> handler) { return register(handler); }

    private static boolean register(Consumer<Ctx> handler) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            try { handler.accept(new Ctx(context)); }
            catch (Throwable t) { System.out.println("[IceyMod] WorldRenderHook callback threw: " + t); }
        });
        return true;
    }

    public static final class Ctx {
        private final LevelRenderContext c;
        Ctx(LevelRenderContext c) { this.c = c; }

        public PoseStack poseStack() { return c.poseStack(); }
        public Camera camera() { return Minecraft.getInstance().gameRenderer.mainCamera(); }
        public float tickDelta() { return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true); }

        /** Draw custom vertices with the current pose of {@link #poseStack()}. */
        public void geometry(RenderType type, GeometryRenderer r) {
            c.submitNodeCollector().submitCustomGeometry(c.poseStack(), type, (pose, vc) -> r.render(pose, vc));
        }

        /** Text at the origin of the current pose (callers translate/rotate/scale the stack first). */
        public void worldText(Component text, boolean seeThrough, int color, int backgroundColor) {
            c.submitNodeCollector().submitText(c.poseStack(), 0f, 0f, text.getVisualOrderText(), false,
                    seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0xF000F0, color, backgroundColor, 0);
        }

        public RenderType beaconBeam(Identifier texture, boolean translucent) { return RenderTypes.beaconBeam(texture, translucent); }
        public RenderType lines() { return RenderTypes.lines(); }
    }
}
