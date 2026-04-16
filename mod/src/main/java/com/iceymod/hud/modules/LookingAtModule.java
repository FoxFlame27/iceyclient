package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public class LookingAtModule extends HudModule {
    public LookingAtModule() {
        super("lookat", "Looking At", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        HitResult hit = client.crosshairTarget;
        if (hit == null) return "\u00A78Nothing";
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            String name = e.getName().getString();
            if (e instanceof LivingEntity le) {
                return "\u00A7b\u25BA " + name + " \u00A77" + String.format("%.1f", le.getHealth()) + "HP";
            }
            return "\u00A7b\u25BA " + name;
        }
        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            String name = state.getBlock().getName().getString();
            return "\u00A7e\u25BA " + name;
        }
        return "\u00A78Nothing";
    }
}
