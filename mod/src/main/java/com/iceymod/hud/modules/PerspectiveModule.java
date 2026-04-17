package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Freelook-style perspective cycling. Press the keybind (R) to cycle
 * through 1st person -> 3rd person back -> 3rd person front.
 */
public class PerspectiveModule extends HudModule {

    public PerspectiveModule() {
        super("perspective", "Perspective", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    public void cyclePerspective() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        Perspective current = client.options.getPerspective();
        Perspective next;
        if (current == Perspective.FIRST_PERSON) next = Perspective.THIRD_PERSON_BACK;
        else if (current == Perspective.THIRD_PERSON_BACK) next = Perspective.THIRD_PERSON_FRONT;
        else next = Perspective.FIRST_PERSON;
        client.options.setPerspective(next);
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
