package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class SessionTimeModule extends HudModule {
    private final long startTime = System.currentTimeMillis();

    public SessionTimeModule() {
        super("session", "Session Time", 5, 515);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long hours = elapsed / 3600;
        long mins = (elapsed % 3600) / 60;
        long secs = elapsed % 60;
        if (hours > 0) {
            return String.format("\u23f1 %d:%02d:%02d", hours, mins, secs);
        }
        return String.format("\u23f1 %d:%02d", mins, secs);
    }
}
