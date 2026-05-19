package com.iceymod.mixin;

import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Replaces the rotating yellow splash text on the title screen with
 * Icey Client–themed one-liners. Cancels the vanilla logic and supplies
 * a fresh random splash each time TitleScreen asks for one.
 */
@Mixin(SplashTextResourceSupplier.class)
public abstract class SplashTextMixin {

    private static final String[] ICEY_SPLASHES = {
        "icecold!",
        "brrrr!",
        "chilled to perfection!",
        "frostbite ready!",
        "no two snowflakes alike!",
        "permafrost certified!",
        "winter is coming!",
        "freeze tag!",
        "ice ice baby!",
        "subzero!",
        "arctic grade!",
        "Frosty the Snowman approves",
        "snow way!",
        "glacier pace!",
        "hypothermia incoming!",
        "iced out!",
        "cold as space!",
        "frozen solid!",
        "blizzard mode!",
        "icicle approved!",
        "powder day!",
        "cryo-ready!",
        "penguins love it!",
        "northern lights!",
        "quick freeze!",
        "cooler than you!",
        "cold takes only!",
        "minus forty!",
        "keep your cool!"
    };

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$iceySplash(CallbackInfoReturnable<SplashTextRenderer> cir) {
        try {
            String text = ICEY_SPLASHES[ThreadLocalRandom.current().nextInt(ICEY_SPLASHES.length)];
            // SplashTextRenderer's constructor accepts String on 1.21.8
            // but only Text on 1.21.11. Reflection picks whichever ctor
            // exists at runtime.
            SplashTextRenderer renderer = buildRenderer(text);
            if (renderer != null) cir.setReturnValue(renderer);
        } catch (Throwable ignored) {
            // Fall through to vanilla.
        }
    }

    private static SplashTextRenderer buildRenderer(String text) {
        // Try String constructor (1.21.8 and earlier)
        try {
            return SplashTextRenderer.class.getConstructor(String.class).newInstance(text);
        } catch (Throwable ignored) {}
        // Try Text constructor (1.21.11+) via reflection
        try {
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Object t = textClass.getMethod("literal", String.class).invoke(null, text);
            return SplashTextRenderer.class.getConstructor(textClass).newInstance(t);
        } catch (Throwable ignored) {}
        return null;
    }
}
