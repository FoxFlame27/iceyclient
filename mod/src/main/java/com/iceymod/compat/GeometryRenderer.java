package com.iceymod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Emits vertices for one world-space draw (see WorldRenderHook.Ctx#geometry). */
@FunctionalInterface
public interface GeometryRenderer {
    void render(PoseStack.Pose pose, VertexConsumer vc);
}
