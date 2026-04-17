package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Auto-swaps a Totem of Undying into your off-hand if you have one in
 * your main inventory and nothing useful is there already.
 */
public class AutoTotemModule extends HudModule {
    private long lastSwapAt = 0;

    public AutoTotemModule() {
        super("autototem", "Auto Totem", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;
        if (client.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (now - lastSwapAt < 400) return;

        ItemStack off = client.player.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) return;
        if (off.isOf(Items.SHIELD)) return; // don't steal shield slot

        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                int syncId = client.player.playerScreenHandler.syncId;
                // Slot 40 is the off-hand in the player screen handler
                int sourceSlot = i < 9 ? 36 + i : i;
                try {
                    client.interactionManager.clickSlot(syncId, sourceSlot, 40, SlotActionType.SWAP, client.player);
                    lastSwapAt = now;
                } catch (Throwable ignored) {}
                return;
            }
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
