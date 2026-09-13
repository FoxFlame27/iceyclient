package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

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

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    public void cyclePerspective() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        CameraType current = client.options.getCameraType();
        CameraType next;
        if (current == CameraType.FIRST_PERSON) next = CameraType.THIRD_PERSON_BACK;
        else if (current == CameraType.THIRD_PERSON_BACK) next = CameraType.THIRD_PERSON_FRONT;
        else next = CameraType.FIRST_PERSON;
        client.options.setCameraType(next);
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
