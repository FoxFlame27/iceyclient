package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeModule extends HudModule {
    private final SimpleDateFormat fmt = new SimpleDateFormat("h:mm a");

    public TimeModule() {
        super("time", "Real Time", 5, 260);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        return fmt.format(new Date());
    }
}
