package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Shows the block the player is standing on (Lunar Client style).
 */
public class BlockAboveModule extends HudModule {
    public BlockAboveModule() {
        super("blockunder", "Block Below", 5, 350);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        BlockPos below = client.player.blockPosition().below();
        var state = client.level.getBlockState(below);
        if (state.is(Blocks.AIR)) return "Air";
        return state.getBlock().getName().getString();
    }
}
