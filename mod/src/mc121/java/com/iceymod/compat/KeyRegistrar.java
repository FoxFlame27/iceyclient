package com.iceymod.compat;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyRegistrar {
    private KeyRegistrar() {}
    public static KeyMapping register(KeyMapping k) { return KeyBindingHelper.registerKeyBinding(k); }
}
