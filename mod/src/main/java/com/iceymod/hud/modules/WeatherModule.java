package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

public class WeatherModule extends HudModule {
    public WeatherModule() {
        super("weather", "Weather", 5, 460);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.level == null) return null;
        if (client.level.isThundering()) return "\u00A7e\u26C8 Thunder";
        if (client.level.isRaining()) return "\u00A7b\u2614 Rain";
        return "\u00A7a\u2600 Clear";
    }
}
