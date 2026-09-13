package com.iceymod.compat;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class HudHook {
    private HudHook() {}
    public static void register(String id, HudRenderer r) {
        HudRenderCallback.EVENT.register((g, dt) -> r.render(new Gfx(g), dt.getGameTimeDeltaPartialTick(true)));
    }
}
