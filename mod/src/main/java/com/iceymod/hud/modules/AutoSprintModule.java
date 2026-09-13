package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

/**
 * Always sprints when moving forward. Works by holding the sprint key
 * virtually, which is what the movement code checks.
 */
public class AutoSprintModule extends HudModule {
    public AutoSprintModule() {
        super("autosprint", "Auto Sprint", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.options != null) {
                client.options.keySprint.setDown(false);
            }
        }
        super.setEnabled(enabled);
    }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options == null) return;
        boolean canSprint = client.options.keyUp.isDown()
                && !client.player.isShiftKeyDown()
                && !client.player.isInWater()
                && client.player.getFoodData().getFoodLevel() > 6;
        client.options.keySprint.setDown(canSprint);
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
