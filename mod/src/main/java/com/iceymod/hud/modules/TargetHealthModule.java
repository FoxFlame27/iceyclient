package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class TargetHealthModule extends HudModule {
    public TargetHealthModule() {
        super("targethealth", "Target Health", 5, 130);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    /** Per user, the target-health display is now rendered as a 3D
     *  nameplate above the targeted player's head (see
     *  {@code TargetHealthRenderer}). The on-screen HUD text is gone \u2014
     *  this module's role is just the on/off toggle the renderer reads. */
    @Override
    public String getText(MinecraftClient client) {
        return null;
    }
}
