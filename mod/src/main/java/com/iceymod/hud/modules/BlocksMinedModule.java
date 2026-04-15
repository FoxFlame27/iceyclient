package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.MinecraftClient;

public class BlocksMinedModule extends HudModule {
    private static int mined = 0;
    private static boolean registered = false;

    public BlocksMinedModule() {
        super("blocksmined", "Blocks Mined", 5, 490);
        setEnabled(false);
        registerTracker();
    }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && player == client.player) mined++;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        return "\u00A76\u26CF " + mined + " mined";
    }
}
