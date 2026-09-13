package com.iceymod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Chat {
    private Chat() {}
    /** Show a message to the local player: action bar overlay, or chat. */
    public static void message(Component text, boolean actionBar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (actionBar) mc.player.sendOverlayMessage(text); else mc.player.sendSystemMessage(text);
    }
}
