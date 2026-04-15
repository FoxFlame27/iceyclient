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

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        HitResult r = client.crosshairTarget;
        if (!(r instanceof EntityHitResult)) return "\u00A78No target";
        Entity e = ((EntityHitResult) r).getEntity();
        if (!(e instanceof LivingEntity)) return "\u00A78No target";
        LivingEntity le = (LivingEntity) e;
        float hp = le.getHealth();
        float max = le.getMaxHealth();
        String color = hp > max * 0.66f ? "\u00A7a" : hp > max * 0.33f ? "\u00A7e" : "\u00A7c";
        return color + "\u2764 " + String.format("%.1f", hp) + "/" + String.format("%.0f", max);
    }
}
