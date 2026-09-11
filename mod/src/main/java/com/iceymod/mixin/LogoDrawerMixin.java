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


    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$replaceLogo(DrawContext context, int screenWidth, float horizontalAlphaMultiplier, int yOffset, CallbackInfo ci) {
        try {
            // Icey Client or Skiflame logo, depending on the launcher's mode.
            int targetW = com.iceymod.Branding.logoTargetWidth(screenWidth);
            int targetH = (int) ((long) targetW * com.iceymod.Branding.logoHeight() / com.iceymod.Branding.logoWidth());
            int x = Math.max(4, screenWidth / 2 - targetW / 2);
            int y = com.iceymod.Branding.isSkiflame()
                    ? Math.max(4, LogoDrawer.LOGO_BASE_Y + yOffset - 20)
                    : Math.max(4, LogoDrawer.LOGO_BASE_Y + yOffset - 55);
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    com.iceymod.Branding.logo(),
                    x, y,
                    0f, 0f,
                    targetW, targetH,
                    targetW, targetH
            );
            ci.cancel();
        } catch (Throwable ignored) {
            // drawTexture signature changed between versions — let vanilla
            // render the default logo rather than crash the title screen.
        }
    }
}
