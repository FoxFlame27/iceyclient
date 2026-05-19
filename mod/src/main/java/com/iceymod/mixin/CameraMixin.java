package com.iceymod.mixin;

import com.iceymod.hud.modules.FreecamModule;
import com.iceymod.hud.modules.FreelookModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    // No captured params — Camera.update's first arg changed from
    // BlockView (1.21.8) to World (1.21.11). If we capture by type the
    // mixin descriptor stops matching and silently no-ops on the new
    // version. By only taking CallbackInfo we target by method name only,
    // so the injection fires regardless of param-type drift.
    @Inject(method = "update", at = @At("RETURN"), require = 0, expect = 0)
    private void iceymod$applyFreelook(CallbackInfo ci) {
        if (!loggedFirstInject) {
            System.out.println("[IceyMod] CameraMixin@update RETURN fired (mixin IS bound)");
            loggedFirstInject = true;
        }
        try {
            // Freecam takes precedence over freelook — both override
            // rotation, but freecam ALSO overrides position. Run the
            // per-frame movement update HERE (not in tick) so motion
            // is smooth at render rate instead of stepping at 20 Hz.
            if (FreecamModule.isActive()) {
                if (!loggedFirstFreecamApply) {
                    System.out.println("[IceyMod] CameraMixin: applying freecam (first frame)");
                    loggedFirstFreecamApply = true;
                }
                FreecamModule.updatePerFrame();
                setPos(FreecamModule.camX(), FreecamModule.camY(), FreecamModule.camZ());
                setRotation(FreecamModule.camYaw(), FreecamModule.camPitch());
                return;
            }
            if (FreelookModule.isRendering()) {
                if (!loggedFirstFreelookApply) {
                    System.out.println("[IceyMod] CameraMixin: applying freelook (first frame)");
                    loggedFirstFreelookApply = true;
                }
                // Get the focused entity via the client. Camera's focused
                // entity is the same as MinecraftClient.getCameraEntity().
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                Entity focused = (mc != null) ? mc.getCameraEntity() : null;
                if (focused != null) {
                    // Use untweened yaw/pitch — close enough for freelook,
                    // and avoids needing the tickDelta param.
                    float playerYaw = focused.getYaw();
                    float playerPitch = focused.getPitch();
                    setRotation(
                        FreelookModule.getRenderYaw(playerYaw),
                        FreelookModule.getRenderPitch(playerPitch)
                    );
                }
            }
        } catch (Throwable t) {
            if (!loggedFirstError) {
                System.out.println("[IceyMod] CameraMixin body threw (suppressing): " + t);
                loggedFirstError = true;
            }
        }
    }

    @Unique private static boolean loggedFirstInject = false;
    @Unique private static boolean loggedFirstFreecamApply = false;
    @Unique private static boolean loggedFirstFreelookApply = false;
    @Unique private static boolean loggedFirstError = false;
}
