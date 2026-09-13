package com.iceymod.compat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class HudHook {
    private HudHook() {}
    public static void register(String id, HudRenderer r) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("iceymod", id),
                (g, dt) -> r.render(new Gfx(g), dt.getGameTimeDeltaPartialTick(true)));
    }
}
