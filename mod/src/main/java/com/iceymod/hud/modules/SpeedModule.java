package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class SpeedModule extends HudModule {
    private double lastX, lastY, lastZ;
    private long lastTime;
    private double currentSpeed;

    public SpeedModule() {
        super("speed", "Speed", 5, 305);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.MOVEMENT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        if (lastTime != 0) {
            double dt = (now - lastTime) / 1000.0;
            if (dt > 0) {
                double dx = x - lastX;
                double dz = z - lastZ;
                // Horizontal speed in blocks/sec
                currentSpeed = Math.sqrt(dx * dx + dz * dz) / dt;
            }
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        lastTime = now;
    }

    @Override
    public String getText(MinecraftClient client) {
        return String.format("%.2f m/s", currentSpeed);
    }
}
