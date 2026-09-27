package com.iceymod.hud.modules;

import com.iceymod.Branding;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The client's name with a few live numbers next to it, as PvP clients show in a corner. */
public class WatermarkModule extends HudModule {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public final BoolSetting showFps = addSetting(new BoolSetting("showFps", "Show FPS", true));
    public final BoolSetting showPing = addSetting(new BoolSetting("showPing", "Show Ping", true));
    public final BoolSetting showTime = addSetting(new BoolSetting("showTime", "Show Time", false));
    public final BoolSetting showName = addSetting(new BoolSetting("showName", "Show Player Name", false));

    public WatermarkModule() {
        super("watermark", "Watermark", 5, 5);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        StringBuilder sb = new StringBuilder(Branding.name());
        if (showName.get() && client.player != null) {
            sb.append(" §8|§r ").append(client.player.getName().getString());
        }
        if (showFps.get()) sb.append(" §8|§r ").append(client.getFps()).append(" FPS");
        if (showPing.get() && client.getConnection() != null && client.player != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) sb.append(" §8|§r ").append(info.getLatency()).append(" ms");
        }
        if (showTime.get()) sb.append(" §8|§r ").append(LocalTime.now().format(CLOCK));
        return sb.toString();
    }
}
