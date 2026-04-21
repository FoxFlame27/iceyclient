package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Draws wireframe bounding boxes around every entity in the world, using
 * a fully-customizable color. The actual rendering lives in
 * HitboxRenderer (hooked into WorldRenderEvents.AFTER_ENTITIES) — this
 * class just holds the settings and the on/off state.
 */
public class HitboxModule extends HudModule {
    public final ColorSetting color = addSetting(new ColorSetting("color", "Color", 0xFFFFFFFF));
    public final IntSetting range = addSetting(new IntSetting("range", "Max Range", 64, 8, 256, 8));
    public final BoolSetting showSelf = addSetting(new BoolSetting("showSelf", "Include Self", false));
    public final BoolSetting onlyLiving = addSetting(new BoolSetting("onlyLiving", "Only Living Entities", false));

    public HitboxModule() {
        super("hitboxes", "Hitboxes", 5, 200);
        setEnabled(false);
    }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
