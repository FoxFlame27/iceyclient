package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Shows the distance to the entity or block you're looking at (reach display).
 */
public class ReachModule extends HudModule {
    private double lastReach = 0;
    private long lastHitTime = 0;

    public ReachModule() {
        super("reach", "Reach", 5, 107);
        setEnabled(false);
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.crosshairTarget != null && client.player != null) {
            if (client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                EntityHitResult ehr = (EntityHitResult) client.crosshairTarget;
                Entity target = ehr.getEntity();
                lastReach = client.player.distanceTo(target);
                lastHitTime = System.currentTimeMillis();
            } else if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                lastReach = client.crosshairTarget.getPos().distanceTo(client.player.getEyePos());
                lastHitTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public String getText(MinecraftClient client) {
        // Fade out after 3 seconds of not looking at anything
        if (System.currentTimeMillis() - lastHitTime > 3000) return null;
        return String.format("%.2f blocks", lastReach);
    }
}
