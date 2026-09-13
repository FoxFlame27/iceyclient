package com.iceymod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;

/**
 * Replaces the rotating yellow splash text on the title screen with
 * Icey Client–themed one-liners. Cancels the vanilla logic and supplies
 * a fresh random splash each time TitleScreen asks for one.
 */
@Mixin(SplashManager.class)
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

    @Inject(method = "getSplash", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void iceymod$iceySplash(CallbackInfoReturnable<SplashRenderer> cir) {
        try {
            String text = ICEY_SPLASHES[ThreadLocalRandom.current().nextInt(ICEY_SPLASHES.length)];
            // SplashTextRenderer's constructor accepts String on 1.21.8
            // but only Text on 1.21.11. Reflection picks whichever ctor
            // exists at runtime.
            SplashRenderer renderer = buildRenderer(text);
            if (renderer != null) cir.setReturnValue(renderer);
        } catch (Throwable ignored) {
            // Fall through to vanilla.
        }
    }

    private static SplashRenderer buildRenderer(String text) {
        // Try String constructor (1.21.8 and earlier)
        try {
            return SplashRenderer.class.getConstructor(String.class).newInstance(text);
        } catch (Throwable ignored) {}
        // Component constructor (1.21.11+). Component.class is a compile-time
        // reference, so it carries the right runtime name on every version.
        try {
            return SplashRenderer.class.getConstructor(net.minecraft.network.chat.Component.class)
                    .newInstance(net.minecraft.network.chat.Component.literal(text));
        } catch (Throwable ignored) {}
        return null;
    }
}
