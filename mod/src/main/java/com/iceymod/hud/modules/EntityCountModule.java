package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Shows total loaded entity count in the world.
 */
public class EntityCountModule extends HudModule {
    public EntityCountModule() {
        super("entitycount", "Entity Count", 5, 560);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.level == null) return null;
        int count = 0;
        try {
            for (Entity e : client.level.entitiesForRendering()) {
                count++;
            }
        } catch (Exception ex) {
            return null;
        }
        return "\u00A7b" + count + " entities";
    }
}
