package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                client.options.sprintKey.setPressed(false);
            }
        }
        super.setEnabled(enabled);
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options == null) return;
        boolean canSprint = client.options.forwardKey.isPressed()
                && !client.player.isSneaking()
                && !client.player.isTouchingWater()
                && client.player.getHungerManager().getFoodLevel() > 6;
        client.options.sprintKey.setPressed(canSprint);
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
