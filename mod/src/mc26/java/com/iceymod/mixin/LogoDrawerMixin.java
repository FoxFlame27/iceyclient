package com.iceymod.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x: replaces the vanilla title-screen logo with the Icey Client / Skiflame logo. */
@Mixin(LogoRenderer.class)
public abstract class LogoDrawerMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$replaceLogo(GuiGraphicsExtractor context, int screenWidth, float horizontalAlphaMultiplier, int yOffset, CallbackInfo ci) {
        try {
            int targetW = com.iceymod.Branding.logoTargetWidth(screenWidth);
            int targetH = (int) ((long) targetW * com.iceymod.Branding.logoHeight() / com.iceymod.Branding.logoWidth());
            int x = Math.max(4, screenWidth / 2 - targetW / 2);
            int y = com.iceymod.Branding.isSkiflame()
                    ? Math.max(4, LogoRenderer.DEFAULT_HEIGHT_OFFSET + yOffset - 20)
                    : Math.max(4, LogoRenderer.DEFAULT_HEIGHT_OFFSET + yOffset - 55);
            context.blit(RenderPipelines.GUI_TEXTURED, com.iceymod.Branding.logo(), x, y, 0f, 0f, targetW, targetH, targetW, targetH);
            ci.cancel();
        } catch (Throwable ignored) {
            // Let vanilla draw its logo rather than crash the title screen.
        }
    }
}
