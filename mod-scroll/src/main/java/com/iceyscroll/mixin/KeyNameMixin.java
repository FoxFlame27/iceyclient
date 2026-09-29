package com.iceyscroll.mixin;

import com.iceyscroll.ScrollKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows the two scroll keys by name instead of "Button 101". Done here
 * and not with a language file, which would need Fabric API to load.
 */
@Mixin(InputConstants.Key.class)
public class KeyNameMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void iceyscroll$name(CallbackInfoReturnable<Component> cir) {
        String name = ScrollKeys.nameOf((InputConstants.Key) (Object) this);
        if (name != null) cir.setReturnValue(Component.literal(name));
    }
}
