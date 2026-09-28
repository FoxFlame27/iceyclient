package com.iceymod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Scroll Keybinds press a key mapping the way a real key press does. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int icey$getClickCount();

    @Accessor("clickCount")
    void icey$setClickCount(int count);

    @Accessor("key")
    InputConstants.Key icey$getKey();
}
