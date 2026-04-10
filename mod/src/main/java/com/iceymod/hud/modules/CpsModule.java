package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class CpsModule extends HudModule {
    private final List<Long> leftClicks = new ArrayList<>();
    private final List<Long> rightClicks = new ArrayList<>();
    private boolean wasLeftPressed = false;
    private boolean wasRightPressed = false;

    public CpsModule() {
        super("cps", "CPS", 5, 56);
    }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        // Detect new left clicks (attack)
        boolean leftDown = client.options.attackKey.isPressed();
        if (leftDown && !wasLeftPressed) leftClicks.add(now);
        wasLeftPressed = leftDown;

        // Detect new right clicks (use)
        boolean rightDown = client.options.useKey.isPressed();
        if (rightDown && !wasRightPressed) rightClicks.add(now);
        wasRightPressed = rightDown;

        // Remove clicks older than 1 second
        leftClicks.removeIf(t -> now - t > 1000);
        rightClicks.removeIf(t -> now - t > 1000);
    }

    @Override
    public String getText(MinecraftClient client) {
        return leftClicks.size() + " | " + rightClicks.size() + " CPS";
    }
}
