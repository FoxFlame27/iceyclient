package com.iceymod.mixin;

import com.iceymod.IceyMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla "MINECRAFT" logo on the title screen with the
 * Icey Client logo. Uses DrawContext.drawTexture with the standard
 * GUI_TEXTURED render pipeline — the reliable 1.21.8 way.
 */
@Mixin(LogoRenderer.class)
public abstract class LogoDrawerMixin {


    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$replaceLogo(GuiGraphics context, int screenWidth, float horizontalAlphaMultiplier, int yOffset, CallbackInfo ci) {
        if (com.iceymod.MenuPrefs.isVanillaTitle()) return;      // the player chose Minecraft's own menu
        try {
            // Icey Client or Skiflame logo, depending on the launcher's mode.
            int targetW = com.iceymod.Branding.logoTargetWidth(screenWidth);
            int targetH = (int) ((long) targetW * com.iceymod.Branding.logoHeight() / com.iceymod.Branding.logoWidth());
            int x = Math.max(4, screenWidth / 2 - targetW / 2);
            int y = com.iceymod.Branding.isSkiflame()
                    ? Math.max(4, LogoRenderer.DEFAULT_HEIGHT_OFFSET + yOffset - 20)
                    : Math.max(4, LogoRenderer.DEFAULT_HEIGHT_OFFSET + yOffset - 55);
            if (!com.iceymod.Branding.isSkiflame()) {
                // The head sits above the wordmark; its texture is 64x64 whatever skin it shows.
                int hs = com.iceymod.Branding.headSize(targetW);
                y = com.iceymod.Branding.logoTop(hs + 3 + targetH, 12);
                context.blit(RenderPipelines.GUI_TEXTURED, com.iceymod.Branding.head(), screenWidth / 2 - hs / 2, y, 0f, 0f, hs, hs, hs, hs);
                y += hs + 3;
            } else {
                // The Skiflame artwork has glow round the letters, so it may sit closer
                y = com.iceymod.Branding.logoTop(targetH, 4);
            }
            context.blit(
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
