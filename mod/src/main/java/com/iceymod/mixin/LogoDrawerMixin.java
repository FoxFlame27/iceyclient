package com.iceymod.mixin;

import com.iceymod.IceyMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "MINECRAFT" logo on the title screen with the
 * Icey Client logo. Cancels both the main logo and the edition subtitle.
 * Texture: assets/iceymod/textures/gui/title/iceyclient.png (612x408 source).
 */
@Mixin(LogoDrawer.class)
public abstract class LogoDrawerMixin {

    private static final Identifier ICEY_LOGO = Identifier.of(IceyMod.MOD_ID, "textures/gui/title/iceyclient.png");

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true)
    private void iceymod$replaceLogo(DrawContext context, int screenWidth, float horizontalAlphaMultiplier, int yOffset, CallbackInfo ci) {
        // Source image is 612x408. Render at a sane size centered horizontally.
        // Use width ~270 px on screen with the original aspect ratio.
        int targetW = 320;
        int targetH = (int) (targetW * (408.0 / 612.0));
        int x = screenWidth / 2 - targetW / 2;
        int y = LogoDrawer.LOGO_BASE_Y + yOffset - 14; // nudge up slightly to match vanilla logo Y feel
        context.drawTexturedQuad(ICEY_LOGO, x, x + targetW, y, y + targetH, 0, 0, 1, 1);
        ci.cancel();
    }
}
