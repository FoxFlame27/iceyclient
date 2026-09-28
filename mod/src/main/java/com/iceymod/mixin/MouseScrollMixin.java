package com.iceymod.mixin;

import com.iceymod.hud.modules.ScrollBindsModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the scroll wheel to Scroll Keybinds before the hotbar gets it. */
@Mixin(MouseHandler.class)
public class MouseScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void icey$scrollBinds(long window, double xOffset, double yOffset, CallbackInfo ci) {
        try {
            if (ScrollBindsModule.onScroll(yOffset)) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
