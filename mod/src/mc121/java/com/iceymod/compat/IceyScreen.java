package com.iceymod.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.x: adapts Screen.render/renderBackground to the era-neutral renderScreen/renderScreenBackground. */
public abstract class IceyScreen extends Screen {
    protected IceyScreen(Component title) { super(title); }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderScreen(new Gfx(g), mouseX, mouseY, delta);
    }

    @Override
    public final void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderScreenBackground(new Gfx(g), mouseX, mouseY, delta);
    }

    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) { superRender(g, mouseX, mouseY, delta); }
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) { superRenderBackground(g, mouseX, mouseY, delta); }
    protected final void superRender(Gfx g, int mouseX, int mouseY, float delta) { super.render(g.raw(), mouseX, mouseY, delta); }
    protected final void superRenderBackground(Gfx g, int mouseX, int mouseY, float delta) { super.renderBackground(g.raw(), mouseX, mouseY, delta); }
}
