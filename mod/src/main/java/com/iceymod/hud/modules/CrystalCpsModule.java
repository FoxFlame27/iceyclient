package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        ItemStack held = client.player.getMainHandStack();
        boolean rightDown = client.options.useKey.isPressed();
        if (rightDown && !wasRightPressed && held.isOf(Items.END_CRYSTAL)) {
            places.add(now);
        }
        wasRightPressed = rightDown;
        places.removeIf(t -> now - t > 1000);
    }

    @Override
    public String getText(MinecraftClient client) {
        return "\u00A7d\u2726 " + places.size() + " cCPS";
    }
}
