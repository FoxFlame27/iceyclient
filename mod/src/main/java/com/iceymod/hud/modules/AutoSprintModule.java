package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Always sprints when moving forward. No more double-tapping W.
 */
public class AutoSprintModule extends HudModule {
    public AutoSprintModule() {
        super("autosprint", "Auto Sprint", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options == null) return;
        if (client.options.forwardKey.isPressed() && !client.player.isSneaking() && !client.player.isSprinting()
                && client.player.getHungerManager().getFoodLevel() > 6) {
            client.player.setSprinting(true);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
