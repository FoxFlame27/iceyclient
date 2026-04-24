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
    private static final int SRC_W = 1536;
    private static final int SRC_H = 1024;

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$replaceLogo(DrawContext context, int screenWidth, float horizontalAlphaMultiplier, int yOffset, CallbackInfo ci) {
        try {
            int targetW = Math.min(200, Math.max(64, screenWidth - 40));
            int targetH = (int) ((long) targetW * SRC_H / SRC_W);
            int x = Math.max(4, screenWidth / 2 - targetW / 2);
            int y = Math.max(4, LogoDrawer.LOGO_BASE_Y + yOffset - 55);
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    ICEY_LOGO,
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
