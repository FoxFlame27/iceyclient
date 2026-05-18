package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Toggles the in-world health nameplate above NON-PLAYER LivingEntities
 * (zombies, skeletons, villagers, animals, etc.). Pairs with
 * {@link PlayerHealthModule} so users can pick which entity type's
 * health to show — both, one, or neither.
 *
 * <p>Default ON for symmetry with PlayerHealth.
 */
public class MobHealthModule extends HudModule {
    public MobHealthModule() {
        super("mobhealth", "Mob Health", 5, 145);
        setEnabled(true);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    /** Render is purely 3D — no HUD-text widget. */
    @Override
    public String getText(MinecraftClient client) { return null; }
}
