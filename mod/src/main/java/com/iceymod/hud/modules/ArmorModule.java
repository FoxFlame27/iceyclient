package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.Paint;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorModule extends HudModule {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Per-slot sizing: a 20px glass tile holding a 16px item, the durability
    // bar sits inside the tile under the item.
    private static final int SLOT_SIZE = 20;
    private static final int ITEM_SIZE = 16;
    private static final int BAR_WIDTH = 16;
    private static final int SLOT_GAP = 2;

    public final com.iceymod.hud.settings.IntSetting barHeight = addSetting(
            new com.iceymod.hud.settings.IntSetting("barHeight", "Bar Height", 2, 1, 4));
    public final com.iceymod.hud.settings.BoolSetting showHeldItem = addSetting(
            new com.iceymod.hud.settings.BoolSetting("showHeld", "Show Held Item", true));

    public ArmorModule() {
        super("armor", "Armor", 5, 150);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        return null;
    }

    private int slotHeight() { return SLOT_SIZE + barHeight.get() + 3; }

    @Override
    public void render(Gfx context, Minecraft client) {
        if (!isEnabled() || client.player == null) return;
        int bx = getX();
        int by = getY();
        this.width = SLOT_SIZE;
        int sh = slotHeight();

        int drawn = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = client.player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                drawSlot(context, stack, bx, by + drawn * (sh + SLOT_GAP));
                drawn++;
            }
        }

        // Held item too
        if (showHeldItem.get()) {
            ItemStack held = client.player.getMainHandItem();
            if (!held.isEmpty()) {
                drawSlot(context, held, bx, by + drawn * (sh + SLOT_GAP));
                drawn++;
            }
        }

        this.height = Math.max(drawn * (sh + SLOT_GAP) - SLOT_GAP, sh);
    }

    private void drawSlot(Gfx context, ItemStack stack, int x, int y) {
        int sh = slotHeight();
        box(context, x, y, SLOT_SIZE, sh, 0);
        context.renderItem(stack, x + 2, y + 2);

        // Durability bar under the item, inside the tile
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            int dmg = stack.getDamageValue();
            int remaining = max - dmg;
            float ratio = (float) remaining / max;
            int color = getDurabilityColor(ratio);

            int barY = y + 2 + ITEM_SIZE + 1;
            int bh = barHeight.get();
            Paint.fill(context, x + 2, barY, BAR_WIDTH, bh, 1, 0x66000000);
            int fillW = Math.max(1, Math.round(BAR_WIDTH * ratio));
            Paint.fill(context, x + 2, barY, fillW, bh, 1, color);
        }
    }

    /**
     * Color gradient:
     * 100-75% → green
     * 75-50%  → yellow
     * 50-25%  → orange
     * 25-0%   → red
     */
    private int getDurabilityColor(float ratio) {
        if (ratio > 0.75f) return 0xFF4ADE80;       // green
        else if (ratio > 0.50f) return 0xFFFBBF24;  // yellow
        else if (ratio > 0.25f) return 0xFFFB923C;  // orange
        else return 0xFFF87171;                      // red
    }
}
