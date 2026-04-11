package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Shows JVM memory usage (used/max).
 */
public class MemoryModule extends HudModule {
    public MemoryModule() {
        super("memory", "Memory", 5, 170);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory() / (1024 * 1024);
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        int percent = (int) ((used * 100) / max);
        String color;
        if (percent < 50) color = "\u00A7a";
        else if (percent < 80) color = "\u00A7e";
        else color = "\u00A7c";
        return color + used + " \u00A77/ " + max + " MB \u00A78(" + percent + "%)";
    }
}
