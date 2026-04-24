package com.iceymod.mixin;

import com.iceymod.hud.modules.FreelookModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the camera's rotation with FreelookModule's values after
 * Camera.update() so the rendered view uses the free look direction
 * instead of the entity's real yaw/pitch.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    // require=0, expect=0 so this mixin silently no-ops on MC versions
    // where Camera.update's signature changed — game still boots, just
    // without freelook rendering.
    @Inject(method = "update", at = @At("RETURN"), require = 0, expect = 0)
    private void iceymod$applyFreelook(BlockView world, Entity focused, boolean thirdPerson,
                                       boolean inverseView, float tickDelta, CallbackInfo ci) {
        try {
            if (FreelookModule.isRendering() && focused != null) {
                float playerYaw = focused.getYaw(tickDelta);
                float playerPitch = focused.getPitch(tickDelta);
                setRotation(
                    FreelookModule.getRenderYaw(playerYaw),
                    FreelookModule.getRenderPitch(playerPitch)
                );
            }
        } catch (Throwable ignored) {
            // Entity.getYaw / setRotation could shift between versions —
            // silently skip freelook rather than crash the render pass.
        }
    }
}
