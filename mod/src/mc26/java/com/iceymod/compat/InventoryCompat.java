package com.iceymod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

public final class InventoryCompat {
    private InventoryCompat() {}
    /** Swap the item in {@code slot} of the player's own inventory menu with the off-hand (slot 40). */
    public static void swapToOffhand(Minecraft c, int syncId, int slot) {
        c.gameMode.handleContainerInput(syncId, slot, 40, ContainerInput.SWAP, c.player);
    }
}
