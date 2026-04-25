package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.structure.BiomeTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Same shape as StructureLocatorModule but for biomes. Owns a
 * BoolSetting per biome type and renders a HUD list of nearest
 * detected biomes with distance + direction arrow.
 */
public class BiomeLocatorModule extends HudModule {

    private final Map<BiomeTracker.BiomeType, BoolSetting> typeSettings = new EnumMap<>(BiomeTracker.BiomeType.class);

    public final BoolSetting autoWaypoint = addSetting(new BoolSetting("autoWaypoint", "Auto-Waypoint on Discovery", true));
    public final IntSetting maxShown      = addSetting(new IntSetting("maxShown", "Max Shown", 4, 1, 10));

    public BiomeLocatorModule() {
        super("biomelocator", "Biome Locator", 0, 0);
        setEnabled(false);

        // Register a BoolSetting for each biome type. Default OFF for
        // common biomes (jungle, savanna, badlands), ON for rare ones.
        for (BiomeTracker.BiomeType t : BiomeTracker.BiomeType.values()) {
            boolean rare = switch (t) {
                case CHERRY_GROVE, MUSHROOM_FIELDS, ICE_SPIKES, SUNFLOWER_PLAINS,
                     ERODED_BADLANDS, DEEP_DARK, PALE_GARDEN, DEEP_FROZEN_OCEAN -> true;
                default -> false;
            };
            BoolSetting s = addSetting(new BoolSetting("biome_" + t.name().toLowerCase(), t.label, rare));
            typeSettings.put(t, s);
        }
    }

    public boolean isTypeEnabled(BiomeTracker.BiomeType type) {
        BoolSetting s = typeSettings.get(type);
        return s != null && s.get();
    }

    public BoolSetting getTypeSetting(BiomeTracker.BiomeType type) {
        return typeSettings.get(type);
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || client.player == null) return;

        List<BiomeTracker.Found> all = BiomeTracker.getSortedByDistance();
        if (all.isEmpty()) {
            String empty = "§7Scanning biomes…";
            int tw = client.textRenderer.getWidth(empty);
            this.width = tw + 10;
            this.height = 14;
            context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x90000000);
            context.fill(getX(), getY(), getX() + 2, getY() + this.height, 0xFF77BC3D);
            context.drawTextWithShadow(client.textRenderer, empty, getX() + 6, getY() + 3, 0xFFFFFFFF);
            return;
        }

        int limit = Math.min(all.size(), maxShown.get());
        int lineH = 14, gap = 2, rowH = lineH + gap;
        String[] texts = new String[limit];
        int[] colors = new int[limit];
        int maxWidth = 0;
        float yaw = client.player.getYaw();

        for (int i = 0; i < limit; i++) {
            BiomeTracker.Found f = all.get(i);
            double dx = f.pos.getX() - client.player.getX();
            double dz = f.pos.getZ() - client.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
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
