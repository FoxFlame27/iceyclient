package com.iceyscroll.mixin;

import com.iceyscroll.ScrollKeys;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the key binds screen, so the wheel can be bound in it. */
@Mixin(KeyBindsScreen.class)
public class KeyBindsScreenMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void iceyscroll$opened(CallbackInfo ci) {
        ScrollKeys.onBindScreenOpened((KeyBindsScreen) (Object) this);
    }
}
