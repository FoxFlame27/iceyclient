package com.iceymod.mixin;

import com.iceymod.hud.modules.FreecamModule;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While freecam is active, zero out the keyboard-driven player input so
 * vanilla doesn't try to walk the real player around (W still feeds the
 * camera in FreecamModule.tick — both can read the same key state, but
 * only the camera should ACT on it). Otherwise the player would walk
 * out from under the freecam, possibly into lava.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"), require = 0, expect = 0)
    private void iceymod$freecamSuppressInput(CallbackInfo ci) {
        try {
            if (FreecamModule.isActive()) {
                this.keyPresses = Input.EMPTY;
                this.moveVector = Vec2.ZERO;
            }
        } catch (Throwable ignored) {}
    }
}
