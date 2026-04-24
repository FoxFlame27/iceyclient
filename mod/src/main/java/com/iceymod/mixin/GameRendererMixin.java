package com.iceymod.mixin;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.ZoomModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplies the FOV returned by GameRenderer.getFov by the zoom module's
 * current factor (1.0 = no zoom, <1.0 = zoomed in). This is the same
 * approach OptiFine and WI Zoom use — no game options are modified.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0, expect = 0)
    private void iceymod$applyZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof ZoomModule && m.isEnabled()) {
                float factor = ((ZoomModule) m).getZoomFactor();
                if (factor < 1.0f) {
                    cir.setReturnValue(cir.getReturnValue() * factor);
                }
                return;
            }
        }
    }
}
