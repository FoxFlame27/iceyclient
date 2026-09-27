package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Auto-swaps a Totem of Undying into your off-hand if you have one in
 * your main inventory and nothing useful is there already.
 *
 * The swap is the same inventory click the game sends when you hover an
 * item and press the off-hand key. The client shows the result at once
 * and the server confirms it afterwards; when the two disagree you get a
 * "ghost" totem that isn't really there. So the module only swaps when
 * the inventory is in a state the server will agree with, and it checks
 * the outcome: a swap the server took back is not repeated straight
 * away, it waits longer each time instead of hammering the server.
 */
public class AutoTotemModule extends HudModule {
    /** How long the server gets to confirm (or undo) a swap before it is judged. */
    private static final long CONFIRM_MS = 600;
    private static final long MAX_BACKOFF_MS = 8000;

    public final IntSetting minHealth = addSetting(
            new IntSetting("minHealth", "Swap Below HP", 20, 1, 20));
    public final IntSetting delayMs = addSetting(
            new IntSetting("delayMs", "Swap Delay (ms)", 400, 100, 2000, 100));
    public final BoolSetting keepShield = addSetting(
            new BoolSetting("keepShield", "Keep Shield", true));
    public final BoolSetting replaceItems = addSetting(
            new BoolSetting("replaceItems", "Replace Other Off-hand Items", true));
    public final BoolSetting hotbarToo = addSetting(
            new BoolSetting("hotbarToo", "Take From Hotbar", true));
    public final BoolSetting notifyEmpty = addSetting(
            new BoolSetting("notifyEmpty", "Warn When Out of Totems", true));

    private long lastSwapAt = 0;
    private long backoffMs = 0;
    private boolean awaitingConfirm = false;
    private boolean warnedEmpty = false;

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
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.gameMode == null) return;
        if (com.iceymod.compat.MC.screen(client) != null) return;

        // States in which an inventory click would not match the server:
        // dead or just (re)spawned, creative (its inventory works on other
        // packets), spectator, another container open, or an item on the cursor.
        if (!player.isAlive() || player.tickCount < 20) return;
        if (player.isCreative() || player.isSpectator()) return;
        if (player.containerMenu != player.inventoryMenu) return;
        if (!player.inventoryMenu.getCarried().isEmpty()) return;

        long now = System.currentTimeMillis();
        ItemStack off = player.getOffhandItem();
        boolean holding = off.is(Items.TOTEM_OF_UNDYING);

        if (awaitingConfirm && now - lastSwapAt >= CONFIRM_MS) {
            awaitingConfirm = false;
            // Still there after the server had its say: it worked. Gone
            // again: the server undid it, so slow down.
            backoffMs = holding ? 0 : Math.min(MAX_BACKOFF_MS, Math.max(1000, backoffMs * 2));
        }
        if (holding) { warnedEmpty = false; return; }
        if (awaitingConfirm) return;
        if (now - lastSwapAt < Math.max(delayMs.get(), backoffMs)) return;
        if (player.getHealth() > minHealth.get()) return;

        if (off.is(Items.SHIELD) && keepShield.get()) return;
        if (!off.isEmpty() && !off.is(Items.SHIELD) && !replaceItems.get()) return;

        var inv = player.getInventory();
        // 0-8 hotbar, 9-35 the rest. Armour and off-hand slots come after
        // and have other numbers in the menu, so they are left out.
        for (int i = hotbarToo.get() ? 0 : 9; i < 36; i++) {
            if (!inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) continue;
            int syncId = player.inventoryMenu.containerId;
            int sourceSlot = i < 9 ? 36 + i : i;
            try {
                com.iceymod.compat.InventoryCompat.swapToOffhand(client, syncId, sourceSlot);
                lastSwapAt = now;
                awaitingConfirm = true;
            } catch (Throwable ignored) {}
            return;
        }

        if (notifyEmpty.get() && !warnedEmpty) {
            warnedEmpty = true;
            try {
                com.iceymod.compat.Chat.message(
                        net.minecraft.network.chat.Component.literal("§b[Icey] §cNo totems left"), true);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
