package com.iceymod.hud.modules;

import com.iceymod.compat.Gfx;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Name, health and distance of whoever is under the crosshair, kept on
 * screen for a moment after looking away. Only shows what the game
 * already knows about an entity in view.
 */
public class TargetHudModule extends HudModule {
    public final IntSetting holdSeconds = addSetting(new IntSetting("holdSeconds", "Stay Visible (s)", 3, 0, 10));
    public final BoolSetting playersOnly = addSetting(new BoolSetting("playersOnly", "Players Only", false));
    public final BoolSetting showNumber = addSetting(new BoolSetting("showNumber", "Show Health Number", true));
    public final BoolSetting showDistance = addSetting(new BoolSetting("showDistance", "Show Distance", true));
    public final BoolSetting healthColors = addSetting(new BoolSetting("healthColors", "Colour by Health", true));
    public final BoolSetting showWhenEmpty = addSetting(new BoolSetting("showWhenEmpty", "Show Without Target", false));

    private LivingEntity target;
    private long lastSeen;

    public TargetHudModule() {
        super("targethud", "Target HUD", 5, 60);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) { target = null; return; }
        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le
                && (!playersOnly.get() || le instanceof Player)) {
            target = le;
            lastSeen = System.currentTimeMillis();
        }
        if (target != null && (!target.isAlive() || target.isRemoved()
                || System.currentTimeMillis() - lastSeen > holdSeconds.get() * 1000L)) {
            target = null;
        }
    }

    @Override
    public void render(Gfx g, Minecraft client) {
        if (!isEnabled()) return;
        this.width = 124;
        this.height = 34;
        LivingEntity t = target;
        if (t == null && !showWhenEmpty.get()) return;

        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, 0xA0000000);
        g.fill(x, y, x + 2, y + height, barColor.get());
        if (t == null || client.player == null) {
            g.drawString(client.font, "No target", x + 8, y + 13, 0xFF9CA3AF);
            return;
        }

        float max = Math.max(1f, t.getMaxHealth());
        float health = Math.max(0f, Math.min(max, t.getHealth()));
        float ratio = health / max;

        String name = t.getName().getString();
        int room = width - 16;
        if (client.font.width(name) > room) name = client.font.plainSubstrByWidth(name, room - 6) + "…";
        g.drawString(client.font, name, x + 8, y + 5, textColor.get());

        StringBuilder info = new StringBuilder();
        if (showNumber.get()) info.append(String.format("%.1f", health)).append(" HP");
        if (showDistance.get()) {
            if (info.length() > 0) info.append("  ");
            info.append(String.format("%.1f", client.player.distanceTo(t))).append(" m");
        }
        if (info.length() > 0) g.drawString(client.font, info.toString(), x + 8, y + 15, 0xFFB8C0CC);

        int barX = x + 8, barY = y + 27, barW = width - 16;
        int fill = healthColors.get()
                ? (ratio > 0.6f ? 0xFF4ADE80 : ratio > 0.3f ? 0xFFFBBF24 : 0xFFF87171)
                : barColor.get();
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF2A2F3A);
        g.fill(barX, barY, barX + Math.round(barW * ratio), barY + 3, fill);
    }
}
