package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * Shows the current held item name + count.
 */
public class HotbarTextModule extends HudModule {
    public HotbarTextModule() {
        super("hotbartext", "Held Item", 5, 410);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) return null;
        String name = stack.getName().getString();
        int count = stack.getCount();
        return "\u00A7b" + name + (count > 1 ? " \u00A77x" + count : "");
    }
}
