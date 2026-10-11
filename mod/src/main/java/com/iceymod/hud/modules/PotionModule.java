package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Shows active potion effects with remaining duration.
 */
public class PotionModule extends HudModule {
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

        Collection<MobEffectInstance> effects = client.player.getActiveEffects();
        if (effects.isEmpty()) {
            this.width = client.font.width("No effects") + 10;
            this.height = 14;
            context.fill(getX(), getY(), getX() + width, getY() + height, 0x90000000);
            context.fill(getX(), getY(), getX() + 2, getY() + height, 0xFF5BC8F5);
            context.drawString(client.font, "No effects", getX() + 6, getY() + 3, 0xFF888888);
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

            String line = name + ampStr + " \u00A77" + duration;
            int lineW = client.font.width(line) + 10;
            if (lineW > maxWidth) maxWidth = lineW;

            int lineY = by + row * 14;
            context.fill(bx, lineY, bx + lineW, lineY + 13, 0x90000000);
            context.fill(bx, lineY, bx + 2, lineY + 13, color);
            context.drawString(client.font, line, bx + 6, lineY + 3, color);
            row++;
        }

        this.width = maxWidth;
        this.height = Math.max(row * 14, 14);
    }

    private String formatDuration(int ticks) {
        if (ticks < 0) return "\u221E";
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
