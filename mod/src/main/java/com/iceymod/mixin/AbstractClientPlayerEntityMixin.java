package com.iceymod.mixin;

import com.iceymod.cape.CapeLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects the user's locally-uploaded cape PNG into the local
 * player's {@link SkinTextures} return value.
 *
 * <p>Cape texture data lives in the {@code SkinTextures} record on
 * 1.21.x:
 * <pre>
 *   record SkinTextures(Identifier texture, String textureUrl,
 *                       Identifier capeTexture,
 *                       Identifier elytraTexture,
 *                       Model model, boolean secure)
 * </pre>
 * We hook {@link AbstractClientPlayerEntity#getSkinTextures()},
 * detect when {@code this == MinecraftClient.player} (i.e. the
 * local player, NOT remote players seen on a server), and rebuild
 * the record with our cape identifier swapped in. Other players
 * keep their normal cape — this is a strictly client-side, local-
 * only override.
 *
 * <p>Why not modify the record in place: records are immutable —
 * the only way to change a field is to construct a new one.
 *
 * <p>{@link CapeLoader} owns the texture lifecycle (file-watch,
 * NativeImage decode, TextureManager registration). This mixin is
 * just the projection point.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void iceymod$injectLocalCape(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            // Local-player-only: untouched for remote players.
            if (((Object) this) != mc.player) return;

            Identifier customCape = CapeLoader.getCapeIdentifier();
            if (customCape == null) return;

            SkinTextures original = cir.getReturnValue();
            if (original == null) return;

            SkinTextures modified = new SkinTextures(
                    original.texture(),
                    original.textureUrl(),
                    customCape,
                    original.elytraTexture(),
                    original.model(),
                    original.secure()
            );
            cir.setReturnValue(modified);
        } catch (Throwable t) {
            // Swallow — better to silently fall through to the
            // original cape than crash player rendering for the
            // entire session if any of the above breaks.
        }
    }
}
