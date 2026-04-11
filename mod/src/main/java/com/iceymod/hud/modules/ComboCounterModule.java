package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Shows hit combo counter like Lunar Client.
 * Increments on each left-click, resets after 2 seconds of no clicking.
 */
public class ComboCounterModule extends HudModule {
    private int combo = 0;
    private long lastHitTime = 0;
    private boolean wasPressed = false;

    public ComboCounterModule() {
        super("combo", "Combo", 5, 245);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean pressed = client.options.attackKey.isPressed();
        long now = System.currentTimeMillis();

        // Detect new click (fresh press)
        if (pressed && !wasPressed) {
            combo++;
            lastHitTime = now;
        }
        wasPressed = pressed;

        // Reset combo after 2 seconds of no clicks
        if (combo > 0 && now - lastHitTime > 2000) {
            combo = 0;
        }
    }

    @Override
    public String getText(MinecraftClient client) {
        if (combo == 0) return "\u00A780 Combo";
        String color;
        if (combo >= 20) color = "\u00A7c";
        else if (combo >= 10) color = "\u00A76";
        else if (combo >= 5) color = "\u00A7e";
        else color = "\u00A7a";
        return color + combo + " Combo";
    }
}
