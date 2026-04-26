package com.iceymod.mixin;

import com.iceymod.hud.modules.ItemGlowModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Make selected dropped items glow client-side. Server-side glow flag
 * is unchanged — this just overrides the local return value of
 * {@code Entity.isGlowing()} so the vanilla outline-render kicks in
 * for our targets. Same on-screen effect as a Glowing potion or a
 * spectral arrow hit, but local-only and free.
 */
@Mixin(Entity.class)
public abstract class EntityIsGlowingMixin {

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$forceGlow(CallbackInfoReturnable<Boolean> cir) {
        try {
            Entity self = (Entity) (Object) this;
            if (self instanceof ItemEntity item && ItemGlowModule.shouldGlow(item)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // Item / Items class drift across versions could throw —
            // never let glow logic kill the render frame.
        }
    }
}
