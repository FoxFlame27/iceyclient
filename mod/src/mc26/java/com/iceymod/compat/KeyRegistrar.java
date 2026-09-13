package com.iceymod.compat;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyRegistrar {
    private KeyRegistrar() {}
    public static KeyMapping register(KeyMapping k) { return KeyMappingHelper.registerKeyMapping(k); }
}
