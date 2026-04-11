package com.iceymod.mixin;

import com.iceymod.IceyMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the rotating panorama on the title screen with a flat icy background.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final Identifier PANORAMA_TEXTURE = Identifier.of(IceyMod.MOD_ID, "textures/gui/panorama.png");

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void iceymod$replaceBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Draw the icy background image stretched to fill the screen
        context.drawTexturedQuad(PANORAMA_TEXTURE, 0, this.width, 0, this.height, 0, 0, 1, 1);
        // Apply the standard darkening overlay on top
        this.renderDarkening(context);
        ci.cancel();
    }
}
