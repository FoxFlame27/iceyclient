package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Xaero-style minimap. This module only owns state + settings; the
 * actual pixel sampling, biome coloring, and overlay drawing lives in
 * {@link com.iceymod.render.MinimapRenderer}, which reads these values
 * each frame.
 */
public class MinimapModule extends HudModule {
    public final IntSetting size       = addSetting(new IntSetting("size",       "Size (px)",          96, 64, 192, 8));
    public final IntSetting radius     = addSetting(new IntSetting("radius",     "Radius (blocks)",    64, 16, 192, 8));
    public final BoolSetting waypoints = addSetting(new BoolSetting("waypoints", "Show Waypoints",     true));
    public final BoolSetting coords    = addSetting(new BoolSetting("coords",    "Show Coords",        true));
    public final BoolSetting northTag  = addSetting(new BoolSetting("north",     "North Indicator",    true));
    public final BoolSetting biomeTint = addSetting(new BoolSetting("biomeTint", "Biome-Tinted Colors", true));
    public final BoolSetting heightShade = addSetting(new BoolSetting("heightShade", "Height Shading",  true));

    public MinimapModule() {
        super("minimap", "Minimap", 0, 0);
        setEnabled(false);
    }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        // The renderer (registered at HudRenderCallback time in IceyMod.java)
        // draws the actual map. We just expose bounds so the drag-to-move
        // logic in ModuleLayoutScreen knows how big the widget is.
        this.width = size.get();
        this.height = size.get() + (coords.get() ? 10 : 0);
    }
}
