package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;

/** How far away the last thing you hit was. A read-out only: it changes nothing about reach. */
public class ReachDisplayModule extends HudModule {
    public final IntSetting decimals = addSetting(new IntSetting("decimals", "Decimals", 2, 1, 3));
    public final IntSetting holdSeconds = addSetting(new IntSetting("holdSeconds", "Reset After (s)", 3, 1, 10));

    private double lastReach = -1;
    private long lastHitAt;
    private boolean wasPressed;

    public ReachDisplayModule() {
        super("reach", "Reach", 5, 80);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        boolean pressed = client.options.keyAttack.isDown();
        if (pressed && !wasPressed && client.hitResult instanceof EntityHitResult hit) {
            lastReach = hit.getLocation().distanceTo(client.player.getEyePosition());
            lastHitAt = System.currentTimeMillis();
        }
        wasPressed = pressed;
        if (lastReach >= 0 && System.currentTimeMillis() - lastHitAt > holdSeconds.get() * 1000L) lastReach = -1;
    }

    @Override
    public String getText(Minecraft client) {
        if (lastReach < 0) return "§8No hit";
        return String.format("%." + decimals.get() + "f", lastReach) + " blocks";
    }
}
