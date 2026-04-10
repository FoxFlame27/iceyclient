package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Shows the current in-game day number and time of day.
 */
public class DayCounterModule extends HudModule {
    public DayCounterModule() {
        super("daycounter", "Day Counter", 5, 73);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.world == null) return null;
        long worldTime = client.world.getTimeOfDay();
        long day = worldTime / 24000 + 1;

        // Convert to 12-hour time
        long dayTime = worldTime % 24000;
        int hours = (int) ((dayTime / 1000 + 6) % 24); // MC day starts at 6:00
        int minutes = (int) ((dayTime % 1000) * 60 / 1000);
        String ampm = hours >= 12 ? "PM" : "AM";
        int displayHour = hours % 12;
        if (displayHour == 0) displayHour = 12;

        return "Day " + day + " \u00A77" + String.format("%d:%02d %s", displayHour, minutes, ampm);
    }
}
