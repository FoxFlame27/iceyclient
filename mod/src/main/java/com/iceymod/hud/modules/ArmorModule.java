package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public class ArmorModule extends HudModule {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Per-slot sizing
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
    public String getText(MinecraftClient client) {
        return null;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || client.player == null) return;
        int bx = getX();
        int by = getY();
        this.width = ITEM_SIZE;

        int drawn = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = client.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                drawSlot(context, stack, bx, by + drawn * (SLOT_SIZE + SLOT_GAP));
                drawn++;
            }
        }

        // Held item too
        if (showHeldItem.get()) {
            ItemStack held = client.player.getMainHandStack();
            if (!held.isEmpty()) {
                drawSlot(context, held, bx, by + drawn * (SLOT_SIZE + SLOT_GAP));
                drawn++;
            }
        }

        this.height = Math.max(drawn * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP, SLOT_SIZE);
    }

    private void drawSlot(DrawContext context, ItemStack stack, int x, int y) {
        // Item icon
        context.drawItem(stack, x, y);

        // Durability bar directly under the item
        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            int remaining = max - dmg;
            float ratio = (float) remaining / max;
            int color = getDurabilityColor(ratio);

            int barY = y + ITEM_SIZE + 1;
            int bh = barHeight.get();
            context.fill(x, barY, x + BAR_WIDTH, barY + bh, 0xFF222222);
            int fillW = Math.max(1, (int) (BAR_WIDTH * ratio));
            context.fill(x, barY, x + fillW, barY + bh, color);
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
