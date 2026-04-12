package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Shows total loaded entity count in the world.
 */
public class EntityCountModule extends HudModule {
    public EntityCountModule() {
        super("entitycount", "Entity Count", 5, 560);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.world == null) return null;
        int count = 0;
        try {
            for (Entity e : client.world.getEntities()) {
                count++;
            }
        } catch (Exception ex) {
            return null;
        }
        return "\u00A7b" + count + " entities";
    }
}
