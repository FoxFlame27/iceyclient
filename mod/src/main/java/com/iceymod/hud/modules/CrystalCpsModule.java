package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CrystalCpsModule extends HudModule {
    private final List<Long> places = new ArrayList<>();
    private boolean wasRightPressed = false;

    public CrystalCpsModule() {
        super("crystalcps", "Crystal CPS", 5, 190);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        ItemStack held = client.player.getMainHandItem();
        boolean rightDown = client.options.keyUse.isDown();
        if (rightDown && !wasRightPressed && held.is(Items.END_CRYSTAL)) {
            places.add(now);
        }
        wasRightPressed = rightDown;
        places.removeIf(t -> now - t > 1000);
    }

    @Override
    public String getText(Minecraft client) {
        return "\u00A7d\u2726 " + places.size() + " cCPS";
    }
}
