package com.iceymod.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 26.x: Screen.render/renderBackground became extractRenderState/extractBackground. */
public abstract class IceyScreen extends Screen {
    protected IceyScreen(Component title) { super(title); }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        renderScreen(new Gfx(g), mouseX, mouseY, delta);
    }

    @Override
    public final void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        renderScreenBackground(new Gfx(g), mouseX, mouseY, delta);
    }

    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) { superRender(g, mouseX, mouseY, delta); }
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) { superRenderBackground(g, mouseX, mouseY, delta); }
    protected final void superRender(Gfx g, int mouseX, int mouseY, float delta) { super.extractRenderState(g.raw(), mouseX, mouseY, delta); }
    protected final void superRenderBackground(Gfx g, int mouseX, int mouseY, float delta) { super.extractBackground(g.raw(), mouseX, mouseY, delta); }
}
