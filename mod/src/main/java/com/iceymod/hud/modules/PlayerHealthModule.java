package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Toggles the in-world health nameplate above OTHER PLAYERS. Replaces
 * the v1.86 "TargetHealthModule" (which was crosshair-only and rendered
 * a fixed-position HUD widget). Now the renderer lives in
 * {@code EntityHealthRenderer} and reads this module's enabled flag.
 *
 * <p>Default ON — appears immediately for any nearby player.
 */
public class PlayerHealthModule extends HudModule {
    public PlayerHealthModule() {
        super("playerhealth", "Player Health", 5, 130);
        setEnabled(true);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    /** No HUD-text widget — everything renders in 3D world space above
     *  each nearby player's head via {@code EntityHealthRenderer}. */
    @Override
    public String getText(MinecraftClient client) { return null; }
}
