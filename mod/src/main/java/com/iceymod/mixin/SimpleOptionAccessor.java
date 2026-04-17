package com.iceymod.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Bypass the SimpleOption validator — needed for FullBright since the
 * gamma validator caps at 1.0 which isn't actually bright enough.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("value")
    @Mutable
    void iceymod$setRawValue(Object value);
}
