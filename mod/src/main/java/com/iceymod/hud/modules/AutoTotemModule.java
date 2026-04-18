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
    public final com.iceymod.hud.settings.IntSetting minHealth = addSetting(
            new com.iceymod.hud.settings.IntSetting("minHealth", "Swap Below HP", 20, 1, 20));
    public final com.iceymod.hud.settings.IntSetting delayMs = addSetting(
            new com.iceymod.hud.settings.IntSetting("delayMs", "Swap Delay (ms)", 400, 100, 2000, 100));
    private long lastSwapAt = 0;

    public AutoTotemModule() {
        super("autototem", "Auto Totem", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;
        if (client.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (now - lastSwapAt < delayMs.get()) return;
        if (client.player.getHealth() > minHealth.get()) return;

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
