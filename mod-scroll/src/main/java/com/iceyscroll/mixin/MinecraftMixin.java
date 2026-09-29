package com.iceyscroll.mixin;

import com.iceyscroll.ScrollKeys;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Releases a scroll key a moment after the notch that pressed it. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void iceyscroll$tick(CallbackInfo ci) {
        try { ScrollKeys.tick(); } catch (Throwable ignored) {}
    }
}
