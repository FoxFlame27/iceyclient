package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Shows the block the player is standing on (Lunar Client style).
 */
public class BlockAboveModule extends HudModule {
    public BlockAboveModule() {
        super("blockunder", "Block Below", 5, 350);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        BlockPos below = client.player.getBlockPos().down();
        var state = client.world.getBlockState(below);
        if (state.isOf(Blocks.AIR)) return "Air";
        return state.getBlock().getName().getString();
    }
}
