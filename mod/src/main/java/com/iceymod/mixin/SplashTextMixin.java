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
        "colder than your ex's heart",
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

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void iceymod$iceySplash(CallbackInfoReturnable<SplashTextRenderer> cir) {
        String text = ICEY_SPLASHES[ThreadLocalRandom.current().nextInt(ICEY_SPLASHES.length)];
        cir.setReturnValue(new SplashTextRenderer(text));
    }
}
