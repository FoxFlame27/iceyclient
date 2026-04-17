package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.mixin.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Forces gamma high enough to see in caves without torches. Uses a
 * SimpleOption accessor to bypass the gamma validator's 0-1 cap.
 */
public class FullBrightModule extends HudModule {
    private static final double FULLBRIGHT_VALUE = 15.0;
    private Double savedGamma = null;

    public FullBrightModule() {
        super("fullbright", "Full Bright", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && savedGamma != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                try {
                    ((SimpleOptionAccessor) (Object) client.options.getGamma()).iceymod$setRawValue(savedGamma);
                } catch (Throwable ignored) {}
            }
            savedGamma = null;
        }
        super.setEnabled(enabled);
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        SimpleOption<Double> gamma = client.options.getGamma();
        if (savedGamma == null) savedGamma = gamma.getValue();
        if (!Double.valueOf(FULLBRIGHT_VALUE).equals(gamma.getValue())) {
            try {
                ((SimpleOptionAccessor) (Object) gamma).iceymod$setRawValue(FULLBRIGHT_VALUE);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
