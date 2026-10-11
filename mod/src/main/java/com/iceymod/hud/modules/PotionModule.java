package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.Paint;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Shows active potion effects with remaining duration: one themed row per
 * effect, the pill green for beneficial and red for harmful.
 */
public class PotionModule extends HudModule {
    private static final int ROW_H = 14;
    private static final int ROW_GAP = 2;

    public PotionModule() {
        super("potions", "Potions", 5, 124);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        return null; // custom rendering
    }

    @Override
    public void render(Gfx context, Minecraft client) {
        if (!isEnabled() || client.player == null) return;
        int inset = textInset();

        Collection<MobEffectInstance> effects = client.player.getActiveEffects();
        if (effects.isEmpty()) {
            String empty = "No effects";
            this.width = inset + client.font.width(empty) + 4;
            this.height = ROW_H;
            box(context, getX(), getY(), width, height, barColor.get());
            context.drawString(client.font, empty, getX() + inset, getY() + 3, Paint.MUTED, textShadow.get());
            return;
        }

        int bx = getX();
        int by = getY();
        int maxWidth = 0;
        int row = 0;

        for (MobEffectInstance effect : effects) {
            Holder<MobEffect> effectType = effect.getEffect();
            String name = effectType.value().getDisplayName().getString();
            int amp = effect.getAmplifier();
            String ampStr = amp > 0 ? " " + toRoman(amp + 1) : "";
            String duration = formatDuration(effect.getDuration());

            // Green for beneficial, red for harmful
            int color;
            try {
                color = effectType.value().isBeneficial() ? 0xFF4ADE80 : 0xFFF87171;
            } catch (Exception e) {
                color = 0xFFCCCCCC;
            }

            String label = name + ampStr;
            int lineW = inset + client.font.width(label + "  " + duration) + 4;
            if (lineW > maxWidth) maxWidth = lineW;

            int lineY = by + row * (ROW_H + ROW_GAP);
            box(context, bx, lineY, lineW, ROW_H, color);
            context.drawString(client.font, label, bx + inset, lineY + 3, textColor.get(), textShadow.get());
            context.drawString(client.font, duration, bx + inset + client.font.width(label + "  "), lineY + 3, Paint.MUTED, textShadow.get());
            row++;
        }

        this.width = maxWidth;
        this.height = Math.max(row * (ROW_H + ROW_GAP) - ROW_GAP, ROW_H);
    }

    private String formatDuration(int ticks) {
        if (ticks < 0) return "∞";
        int secs = ticks / 20;
        int min = secs / 60;
        int sec = secs % 60;
        return String.format("%d:%02d", min, sec);
    }

    private String toRoman(int n) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n > 0 && n < romans.length) return romans[n];
        return String.valueOf(n);
    }
}
