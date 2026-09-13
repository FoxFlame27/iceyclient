package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

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
        Minecraft client = Minecraft.getInstance();
        if (com.iceymod.compat.MC.screen(client) instanceof DeathScreen && client.player != null) {
            client.player.respawn();
            com.iceymod.compat.MC.setScreen(client, null);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
