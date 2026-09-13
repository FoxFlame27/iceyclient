package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class OffhandItemModule extends HudModule {
    public OffhandItemModule() {
        super("offhand", "Off-Hand Item", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        ItemStack s = client.player.getOffhandItem();
        if (s.isEmpty()) return "\u00A78Off-hand: empty";
        return "\u00A7b\u25C4 " + s.getHoverName().getString() + " x" + s.getCount();
    }
}
