package com.iceymod.mixin;

import com.iceymod.hud.modules.FreecamModule;
import com.iceymod.hud.modules.FreelookModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
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

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    // require=0, expect=0 so this mixin silently no-ops on MC versions
    // where Camera.update's signature changed — game still boots, just
    // without freelook/freecam rendering.
    @Inject(method = "update", at = @At("RETURN"), require = 0, expect = 0)
    private void iceymod$applyFreelook(BlockView world, Entity focused, boolean thirdPerson,
                                       boolean inverseView, float tickDelta, CallbackInfo ci) {
        try {
            // Freecam takes precedence over freelook — both override
            // rotation, but freecam ALSO overrides position.
            if (FreecamModule.isActive()) {
                setPos(FreecamModule.camX(), FreecamModule.camY(), FreecamModule.camZ());
                setRotation(FreecamModule.camYaw(), FreecamModule.camPitch());
                return;
            }
            if (FreelookModule.isRendering() && focused != null) {
                float playerYaw = focused.getYaw(tickDelta);
                float playerPitch = focused.getPitch(tickDelta);
                setRotation(
                    FreelookModule.getRenderYaw(playerYaw),
                    FreelookModule.getRenderPitch(playerPitch)
                );
            }
        } catch (Throwable ignored) {
            // Entity.getYaw / setRotation / setPos could shift between
            // versions — silently skip rather than crash the render pass.
        }
    }
}
