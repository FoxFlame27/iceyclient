package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Auto-sneaks when you're about to walk off a ledge 3+ blocks high so
 * you don't accidentally yeet yourself off in bridge/pvp situations.
 */
public class SafeWalkModule extends HudModule {
    public final com.iceymod.hud.settings.IntSetting minDrop = addSetting(
            new com.iceymod.hud.settings.IntSetting("minDrop", "Min Drop Blocks", 3, 2, 10));

    public SafeWalkModule() {
        super("safewalk", "Safe Walk", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (!client.player.onGround()) return;

        int px = (int) Math.floor(client.player.getX());
        int py = (int) Math.floor(client.player.getY());
        int pz = (int) Math.floor(client.player.getZ());

        // Check 4 cardinal directions for a nearby drop
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        boolean nearEdge = false;
        for (int[] d : dirs) {
            int dx = px + d[0];
            int dz = pz + d[1];
            int drop = 0;
            for (int y = py - 1; y >= py - 4; y--) {
                BlockState st = client.level.getBlockState(new BlockPos(dx, y, dz));
                if (st.isAir()) drop++;
                else break;
            }
            if (drop >= minDrop.get()) { nearEdge = true; break; }
        }

        if (nearEdge) {
            client.options.keyShift.setDown(true);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
