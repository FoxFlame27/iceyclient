package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Auto-swaps to your mace when you start falling, so you never miss a
 * smash because you forgot to switch weapons. Only swaps if you have a
 * mace somewhere in the hotbar and aren't already holding it.
 */
public class AutoMaceSwapModule extends HudModule {
    private int previousSlot = -1;
    private boolean wasAirborne = false;

    public AutoMaceSwapModule() {
        super("automaceswap", "Auto Mace Swap", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        boolean airborne = !client.player.isOnGround() && client.player.fallDistance > 1.0f;

        if (airborne && !wasAirborne) {
            // Started falling — look for a mace to swap to
            if (!client.player.getMainHandStack().isOf(Items.MACE)) {
                var inv = client.player.getInventory();
                int currentSlot = client.player.getInventory().getSelectedSlot();
                for (int i = 0; i < 9; i++) {
                    ItemStack s = inv.getStack(i);
                    if (s.isOf(Items.MACE)) {
                        previousSlot = currentSlot;
                        inv.setSelectedSlot(i);
                        break;
                    }
                }
            }
        } else if (!airborne && wasAirborne && previousSlot >= 0 && previousSlot < 9) {
            // Landed — restore previous slot
            client.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }

        wasAirborne = airborne;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
