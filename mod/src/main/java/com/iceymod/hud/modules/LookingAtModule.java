package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class LookingAtModule extends HudModule {
    public LookingAtModule() {
        super("lookat", "Looking At", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        HitResult hit = client.hitResult;
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
            BlockState state = client.level.getBlockState(pos);
            String name = state.getBlock().getName().getString();
            return "\u00A7e\u25BA " + name;
        }
        return "\u00A78Nothing";
    }
}
