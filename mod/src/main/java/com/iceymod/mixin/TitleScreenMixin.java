package com.iceymod.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder mixin — we no longer override renderBackground, since the
 * nether panorama resource pack provides the textures and we want the
 * vanilla rotating cubemap renderer to use them.
 * Kept as a mixin target for future title-screen tweaks.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }
}
