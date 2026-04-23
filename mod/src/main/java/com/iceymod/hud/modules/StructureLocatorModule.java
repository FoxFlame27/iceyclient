package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.structure.StructureTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * HUD widget that lists nearby detected structures (trial chambers,
 * strongholds, player bases) with distance + direction arrow. The actual
 * detection lives in {@link StructureTracker}; this module just owns the
 * toggles and renders what the tracker found.
 */
public class StructureLocatorModule extends HudModule {
    public final BoolSetting trialChambers = addSetting(new BoolSetting("trialChambers", "Trial Chambers", true));
    public final BoolSetting strongholds   = addSetting(new BoolSetting("strongholds",   "Strongholds",    true));
    public final BoolSetting playerBases   = addSetting(new BoolSetting("playerBases",   "Player Bases",   true));
    public final BoolSetting autoWaypoint  = addSetting(new BoolSetting("autoWaypoint",  "Auto-Waypoint on Discovery", true));
    public final IntSetting maxShown       = addSetting(new IntSetting("maxShown",       "Max Shown", 4, 1, 10));

    public StructureLocatorModule() {
        super("structurelocator", "Structure Locator", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || client.player == null) return;

        List<StructureTracker.Found> all = StructureTracker.getSortedByDistance();
        if (all.isEmpty()) {
            // Minimal "scanning…" row so the user can tell the module is on and working
            String empty = "§7Scanning chunks…";
            int tw = client.textRenderer.getWidth(empty);
            this.width = tw + 10;
            this.height = 14;
            context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x90000000);
            context.fill(getX(), getY(), getX() + 2, getY() + this.height, 0xFF5BC8F5);
            context.drawTextWithShadow(client.textRenderer, empty, getX() + 6, getY() + 3, 0xFFFFFFFF);
            return;
        }

        int limit = Math.min(all.size(), maxShown.get());
        int lineH = 14;
        int gap = 2;
        int rowH = lineH + gap;

        String[] texts = new String[limit];
        int maxWidth = 0;
        int[] colors = new int[limit];

        float yaw = client.player.getYaw();
        for (int i = 0; i < limit; i++) {
            StructureTracker.Found f = all.get(i);
            double dx = f.pos.getX() - client.player.getX();
            double dy = f.pos.getY() - client.player.getY();
            double dz = f.pos.getZ() - client.player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            double angle = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = ((angle - yaw) % 360 + 540) % 360 - 180;
            String arrow;
            if      (rel > -22.5  && rel <=  22.5)  arrow = "↑";
            else if (rel >  22.5  && rel <=  67.5)  arrow = "↗";
            else if (rel >  67.5  && rel <= 112.5)  arrow = "→";
            else if (rel > 112.5  && rel <= 157.5)  arrow = "↘";
            else if (rel > 157.5  || rel <= -157.5) arrow = "↓";
            else if (rel > -157.5 && rel <= -112.5) arrow = "↙";
            else if (rel > -112.5 && rel <=  -67.5) arrow = "←";
            else                                    arrow = "↖";

            texts[i] = arrow + " " + f.type.label + " " + (int) dist + "m";
            colors[i] = f.type.color;
            int tw = client.textRenderer.getWidth(texts[i]);
            if (tw > maxWidth) maxWidth = tw;
        }
        this.width = maxWidth + 10;

        int x = getX(), y = getY();
        for (int i = 0; i < limit; i++) {
            int lineY = y + i * rowH;
            context.fill(x, lineY, x + this.width, lineY + lineH, 0x90000000);
            context.fill(x, lineY, x + 2, lineY + lineH, colors[i]);
            context.drawTextWithShadow(client.textRenderer, texts[i], x + 6, lineY + 3, 0xFFFFFFFF);
        }
        this.height = limit * rowH - gap;
    }
}
