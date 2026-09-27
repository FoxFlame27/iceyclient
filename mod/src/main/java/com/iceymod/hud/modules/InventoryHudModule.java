package com.iceymod.hud.modules;

import com.iceymod.compat.Gfx;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** What's in the inventory, shown on the HUD so it doesn't have to be opened to check. */
public class InventoryHudModule extends HudModule {
    private static final int CELL = 18;

    public final BoolSetting showHotbar = addSetting(new BoolSetting("showHotbar", "Include Hotbar", false));
    public final BoolSetting showCounts = addSetting(new BoolSetting("showCounts", "Show Amounts", true));
    public final BoolSetting invBackground = addSetting(new BoolSetting("invBackground", "Background", true));
    public final IntSetting invOpacity = addSetting(new IntSetting("invOpacity", "Background Opacity", 55, 0, 100, 5));
    public final BoolSetting slotLines = addSetting(new BoolSetting("slotLines", "Slot Outlines", true));
    public final BoolSetting hideEmpty = addSetting(new BoolSetting("hideEmpty", "Hide When Empty", false));

    public InventoryHudModule() {
        super("inventoryhud", "Inventory", 5, 100);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(Gfx g, Minecraft client) {
        if (!isEnabled() || client.player == null) return;
        var inv = client.player.getInventory();
        int rows = showHotbar.get() ? 4 : 3;
        this.width = 9 * CELL + 4;
        this.height = rows * CELL + 4;

        if (hideEmpty.get()) {
            boolean any = false;
            for (int i = showHotbar.get() ? 0 : 9; i < 36 && !any; i++) any = !inv.getItem(i).isEmpty();
            if (!any) return;
        }

        int x = getX(), y = getY();
        if (invBackground.get() && invOpacity.get() > 0) {
            g.fill(x, y, x + width, y + height, Math.round(invOpacity.get() * 2.55f) << 24);
        }
        g.fill(x, y, x + width, y + 1, barColor.get());

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                // Inventory rows first (slots 9..35), the hotbar (0..8) as the last row.
                int slot = row < 3 ? 9 + row * 9 + col : col;
                int cx = x + 2 + col * CELL;
                int cy = y + 2 + row * CELL;
                if (slotLines.get()) g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0x22FFFFFF);
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty()) continue;
                g.renderItem(stack, cx, cy);
                if (showCounts.get() && stack.getCount() > 1) {
                    String n = String.valueOf(stack.getCount());
                    g.drawString(client.font, n, cx + CELL - 2 - client.font.width(n), cy + 9, 0xFFFFFFFF);
                }
            }
        }
    }
}
