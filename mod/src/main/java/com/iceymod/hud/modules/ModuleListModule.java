package com.iceymod.hud.modules;

import com.iceymod.compat.Gfx;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.EnumSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Everything that is switched on, as a list: longest name first, the way PvP clients show it. */
public class ModuleListModule extends HudModule {
    public final EnumSetting sort = addSetting(new EnumSetting("sort", "Sort", new String[] { "Length", "A-Z" }, 0));
    public final EnumSetting align = addSetting(new EnumSetting("align", "Align", new String[] { "Right", "Left" }, 0));
    public final BoolSetting listBackground = addSetting(new BoolSetting("listBackground", "Background", true));
    public final BoolSetting listBar = addSetting(new BoolSetting("listBar", "Side Bar", true));
    public final BoolSetting listRainbow = addSetting(new BoolSetting("listRainbow", "Rainbow", false));
    public final BoolSetting showPerformance = addSetting(new BoolSetting("showPerformance", "Show Performance Modules", false));
    public final IntSetting lineHeight = addSetting(new IntSetting("lineHeight", "Line Height", 11, 9, 16));

    public ModuleListModule() {
        super("modulelist", "Module List", 5, 40);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(Gfx g, Minecraft client) {
        if (!isEnabled()) return;
        List<String> names = new ArrayList<>();
        for (HudModule m : HudManager.getModules()) {
            if (m == this || !m.isEnabled()) continue;
            if (m.getCategory() == Category.OPTIMIZATION && !showPerformance.get()) continue;
            names.add(m.getName());
        }
        if (sort.get() == 0) names.sort(Comparator.comparingInt((String n) -> client.font.width(n)).reversed());
        else names.sort(String.CASE_INSENSITIVE_ORDER);

        int lh = lineHeight.get();
        int widest = 0;
        for (String n : names) widest = Math.max(widest, client.font.width(n));
        this.width = Math.max(40, widest + 8);
        this.height = Math.max(lh, names.size() * lh);

        boolean right = align.get() == 0;
        int x = getX(), y = getY();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            int w = client.font.width(n) + 8;
            int lx = right ? x + width - w : x;
            int ly = y + i * lh;
            int color = listRainbow.get() ? rainbowColor(i) : textColor.get();
            if (listBackground.get()) g.fill(lx, ly, lx + w, ly + lh, 0x90000000);
            if (listBar.get()) {
                int bx = right ? lx + w - 2 : lx;
                g.fill(bx, ly, bx + 2, ly + lh, listRainbow.get() ? color : barColor.get());
            }
            g.drawString(client.font, n, lx + (right ? 3 : 5), ly + (lh - 8) / 2, color);
        }
    }
}
