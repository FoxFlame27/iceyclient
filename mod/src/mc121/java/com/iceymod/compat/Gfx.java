package com.iceymod.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/** 1.21.x: thin wrapper over GuiGraphics (same method names, so shared code compiles on both eras). */
public final class Gfx {
    private final GuiGraphics g;
    public Gfx(GuiGraphics g) { this.g = g; }
    public GuiGraphics raw() { return g; }

    public void fill(int x1, int y1, int x2, int y2, int color) { g.fill(x1, y1, x2, y2, color); }
    public void drawString(Font f, String s, int x, int y, int color) { g.drawString(f, s, x, y, color); }
    public void drawString(Font f, String s, int x, int y, int color, boolean shadow) { g.drawString(f, s, x, y, color, shadow); }
    public void drawString(Font f, Component c, int x, int y, int color) { g.drawString(f, c, x, y, color); }
    public void drawCenteredString(Font f, String s, int x, int y, int color) { g.drawCenteredString(f, s, x, y, color); }
    public void drawCenteredString(Font f, Component c, int x, int y, int color) { g.drawCenteredString(f, c, x, y, color); }
    public void blit(Identifier tex, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, texW, texH);
    }
    public void renderItem(ItemStack stack, int x, int y) { g.renderItem(stack, x, y); }
    public Matrix3x2fStack pose() { return g.pose(); }
    public int guiWidth() { return g.guiWidth(); }
    public int guiHeight() { return g.guiHeight(); }
    public void enableScissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }
    public void disableScissor() { g.disableScissor(); }
}
