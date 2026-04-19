package com.iceymod.mixin;

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

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void iceymod$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!FreelookModule.isActive()) return;
        Entity self = (Entity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && self == client.player) {
            FreelookModule.applyDelta(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
