package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class OffhandItemModule extends HudModule {
    public OffhandItemModule() {
        super("offhand", "Off-Hand Item", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack s = client.player.getOffHandStack();
        if (s.isEmpty()) return "\u00A78Off-hand: empty";
        return "\u00A7b\u25C4 " + s.getName().getString() + " x" + s.getCount();
    }
}
