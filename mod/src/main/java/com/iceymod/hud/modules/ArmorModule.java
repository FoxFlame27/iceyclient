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

    public ArmorModule() {
        super("armor", "Armor", 5, 150);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        return null;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || client.player == null) return;
        int bx = getX();
        int by = getY();
        this.width = 18;

        int drawn = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = client.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                int itemY = by + drawn * 18;
                context.fill(bx - 1, itemY - 1, bx + 17, itemY + 17, 0x60000000);
                context.drawItem(stack, bx, itemY);
                if (stack.isDamageable()) {
                    int maxDmg = stack.getMaxDamage();
                    int dmg = stack.getDamage();
                    float ratio = 1.0f - (float) dmg / maxDmg;
                    int barColor;
                    if (ratio > 0.6f) barColor = 0xFF4ADE80;
                    else if (ratio > 0.3f) barColor = 0xFFFBBF24;
                    else barColor = 0xFFF87171;
                    int barW = (int) (16 * ratio);
                    context.fill(bx, itemY + 15, bx + barW, itemY + 17, barColor);
                }
                drawn++;
            }
        }

        ItemStack held = client.player.getMainHandStack();
        if (!held.isEmpty()) {
            int itemY = by + drawn * 18;
            context.fill(bx - 1, itemY - 1, bx + 17, itemY + 17, 0x60000000);
            context.drawItem(held, bx, itemY);
            drawn++;
        }

        this.height = Math.max(drawn * 18, 18);
    }
}
