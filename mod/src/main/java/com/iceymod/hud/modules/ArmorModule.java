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
        this.width = 60;

        int drawn = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = client.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                int itemY = by + drawn * 18;
                // Background row
                context.fill(bx - 1, itemY - 1, bx + width, itemY + 17, 0x90000000);
                // Item icon
                context.drawItem(stack, bx, itemY);
                // Durability text next to item
                String dura;
                int color;
                if (stack.isDamageable()) {
                    int max = stack.getMaxDamage();
                    int dmg = stack.getDamage();
                    int remaining = max - dmg;
                    float ratio = (float) remaining / max;
                    if (ratio > 0.6f) color = 0xFF4ADE80;
                    else if (ratio > 0.3f) color = 0xFFFBBF24;
                    else color = 0xFFF87171;
                    dura = remaining + "/" + max;
                } else {
                    color = 0xFFAAAAAA;
                    dura = "—";
                }
                context.drawTextWithShadow(client.textRenderer, dura, bx + 20, itemY + 5, color);
                drawn++;
            }
        }

        // Held item
        ItemStack held = client.player.getMainHandStack();
        if (!held.isEmpty()) {
            int itemY = by + drawn * 18;
            context.fill(bx - 1, itemY - 1, bx + width, itemY + 17, 0x90000000);
            context.drawItem(held, bx, itemY);
            String dura;
            int color;
            if (held.isDamageable()) {
                int max = held.getMaxDamage();
                int dmg = held.getDamage();
                int remaining = max - dmg;
                float ratio = (float) remaining / max;
                if (ratio > 0.6f) color = 0xFF4ADE80;
                else if (ratio > 0.3f) color = 0xFFFBBF24;
                else color = 0xFFF87171;
                dura = remaining + "/" + max;
            } else {
                color = 0xFFAAAAAA;
                dura = "x" + held.getCount();
            }
            context.drawTextWithShadow(client.textRenderer, dura, bx + 20, itemY + 5, color);
            drawn++;
        }

        this.height = Math.max(drawn * 18, 18);
    }
}
