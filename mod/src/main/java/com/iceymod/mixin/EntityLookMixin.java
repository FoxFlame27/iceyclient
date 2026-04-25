package com.iceymod.mixin;

import com.iceymod.hud.modules.FreecamModule;
import com.iceymod.hud.modules.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While freelook is active, reroute mouse-delta updates away from the
 * client player's yaw/pitch and into FreelookModule's camera state.
 * Non-player entities are unaffected.
 */
@Mixin(Entity.class)
public abstract class EntityLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || self != client.player) return;

            // Freecam takes priority — mouse rotates the detached camera,
            // not the player.
            if (FreecamModule.isActive()) {
                FreecamModule.applyDelta(cursorDeltaX, cursorDeltaY);
                ci.cancel();
                return;
            }
            if (FreelookModule.isActive()) {
                FreelookModule.applyDelta(cursorDeltaX, cursorDeltaY);
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
