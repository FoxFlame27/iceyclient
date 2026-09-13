package com.iceymod.mixin;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x: the FOV now lives on Camera.getFov(); scale it by the zoom factor. (Class name kept for the mixin list.) */
@Mixin(Camera.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0, expect = 0)
    private void iceymod$applyZoom(CallbackInfoReturnable<Float> cir) {
        try {
            for (HudModule m : HudManager.getModules()) {
                if (m instanceof ZoomModule && m.isEnabled()) {
                    float factor = ((ZoomModule) m).getZoomFactor();
                    if (factor < 1.0f) cir.setReturnValue(cir.getReturnValue() * factor);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }
}
