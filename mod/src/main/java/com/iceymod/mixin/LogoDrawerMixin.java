package com.iceymod.mixin;

import com.iceymod.IceyMod;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "MINECRAFT" logo on the title screen with the
 * Icey Client logo. Uses DrawContext.drawTexture with the standard
 * GUI_TEXTURED render pipeline — the reliable 1.21.8 way.
 */
@Mixin(LogoDrawer.class)
public abstract class LogoDrawerMixin {

    private static final Identifier ICEY_LOGO = Identifier.of(IceyMod.MOD_ID, "textures/gui/title/iceyclient.png");
    private static final int SRC_W = 612;
    private static final int SRC_H = 408;

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true)
    private void iceymod$replaceLogo(DrawContext context, int screenWidth, float horizontalAlphaMultiplier, int yOffset, CallbackInfo ci) {
        // Smaller than before and sits ABOVE the Single Player button.
        int targetW = 200;
        int targetH = (int) ((long) targetW * SRC_H / SRC_W);
        int x = screenWidth / 2 - targetW / 2;
        // LOGO_BASE_Y is where vanilla puts the top of the MINECRAFT logo.
        // Start a bit higher so the whole logo is clearly above the button row.
        int y = LogoDrawer.LOGO_BASE_Y + yOffset - 30;
        if (y < 4) y = 4;
        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                ICEY_LOGO,
                x, y,
                0f, 0f,
                targetW, targetH,
                targetW, targetH
        );
        ci.cancel();
    }
}
