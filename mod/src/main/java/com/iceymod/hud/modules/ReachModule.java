package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

/**
 * Tracks the distance to the last entity hit.
 */
public class ReachModule extends HudModule {
    private static double lastReach = 0;
    private static long lastHitTime = 0;
    private static boolean registered = false;

    public ReachModule() {
        super("reach", "Reach", 5, 107);
        setEnabled(false);
        registerCallback();
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    private static void registerCallback() {
        if (registered) return;
        registered = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            try {
                lastReach = player.distanceTo(entity);
                lastHitTime = System.currentTimeMillis();
            } catch (Exception e) {
                // ignore
            }
            return ActionResult.PASS;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        // Show last hit reach for 5 seconds, then idle "0.0 blocks"
        if (System.currentTimeMillis() - lastHitTime > 5000) {
            return "0.00 blocks";
        }
        return String.format("%.2f blocks", lastReach);
    }
}
