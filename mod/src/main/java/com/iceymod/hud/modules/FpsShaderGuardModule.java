package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.SocialNoticeHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Invisible FPS booster: Icey Shaders are on by default, and on a weak
 * graphics card they can take the game under 40 frames a second. When the
 * frame rate has stayed that low for 15 seconds of actual play, this turns
 * the shaders off and says so, once per session. The shaders key (N unless
 * rebound) turns them back on, and after that this leaves them alone.
 * Only acts on Icey Shaders: a pack the player picked themselves is theirs.
 */
public class FpsShaderGuardModule extends HudModule {
    private static final int FLOOR_FPS = 40;
    private static final int SAMPLES = 15;        // one a second
    private int grace = 400;                      // 20 s after joining: chunks are still building
    private int ticks, samples;
    private long sum;
    private boolean acted;

    public FpsShaderGuardModule() {
        super("fpsboost_shaderguard", "FPS: Shader Guard", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        if (acted) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) { grace = 400; samples = 0; sum = 0; return; }
        // A menu, or the game in the background, is not play: its frame rate says nothing.
        if (com.iceymod.compat.MC.screen(client) != null || !client.isWindowActive()) { samples = 0; sum = 0; return; }
        if (grace > 0) { grace--; return; }
        if (++ticks % 20 != 0) return;
        sum += client.getFps();
        if (++samples < SAMPLES) return;
        long avg = sum / samples;
        samples = 0; sum = 0;
        // Someone who capped the game at 30 is not having a slow game.
        int limit = 260;
        try { limit = client.options.framerateLimit().get(); } catch (Throwable ignored) {}
        if (avg >= Math.min(FLOOR_FPS, limit * 7 / 10)) return;
        try {
            Object pack = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
            if (!"IceyShaders".equals(pack)) return;
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            if (!Boolean.TRUE.equals(api.getClass().getMethod("isShaderPackInUse").invoke(api))) return;
            Object config = api.getClass().getMethod("getConfig").invoke(api);
            config.getClass().getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, false);
        } catch (Throwable noIris) {
            acted = true;                          // no Iris, or one that works differently: nothing to guard
            return;
        }
        acted = true;
        String key = "N";
        try {
            for (KeyMapping k : client.options.keyMappings) {
                if ("iris.keybind.toggleShaders".equals(k.getName())) { key = k.getTranslatedKeyMessage().getString().toUpperCase(); break; }
            }
        } catch (Throwable ignored) {}
        SocialNoticeHud.show("Shaders off: " + avg + " FPS was too low", key + " turns them back on");
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
