package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;

/**
 * Auto-clicks the respawn button when you die. Saves the extra click in
 * PvP so you can get back in the fight faster.
 */
public class AutoRespawnModule extends HudModule {
    public AutoRespawnModule() {
        super("autorespawn", "Auto Respawn", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof DeathScreen && client.player != null) {
            client.player.requestRespawn();
            client.setScreen(null);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
