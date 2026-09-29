package com.iceyscroll.mixin;

import com.iceyscroll.ScrollKeys;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wheel goes to Scroll Keybinds first; what it doesn't use, the game gets. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void iceyscroll$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        try {
            if (ScrollKeys.onScroll(yOffset)) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
