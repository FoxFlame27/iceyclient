package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Sets gamma to maximum for full brightness (no caves dark).
 */
public class FullBrightModule extends HudModule {
    private double savedGamma = -1;

    public FullBrightModule() {
        super("fullbright", "Full Bright", 5, 365);
        setEnabled(false);
    }


    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        try {
            if (isEnabled()) {
                if (savedGamma < 0) savedGamma = client.options.getGamma().getValue();
                client.options.getGamma().setValue(15.0);
            } else if (savedGamma >= 0) {
                client.options.getGamma().setValue(savedGamma);
                savedGamma = -1;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public String getText(MinecraftClient client) {
        return "Bright: \u00A7aON";
    }
}
