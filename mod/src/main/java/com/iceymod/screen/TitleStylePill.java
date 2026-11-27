package com.iceymod.screen;

import com.iceymod.compat.Input;
import com.iceymod.compat.MC;
import com.iceymod.compat.ScreenHook;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The way back: a small "Menu style" button in the top right corner of the
 * normal title screen (the Vanilla one too), which opens the choice between
 * the three main menus again. Without it, someone who picked Vanilla or
 * Icey could only change their mind in the launcher's settings.
 */
public final class TitleStylePill {
    private TitleStylePill() {}

    public static void attach(Screen screen) {
        final boolean[] wasDown = { true };
        final float[] glow = { 0f };
        ScreenHook.afterRender(screen, (g, mouseX, mouseY, delta) -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.font == null) return;
                String label = "Menu style";
                int w = mc.font.width(label) + 12, h = 14;
                int x = g.guiWidth() - w - 4, y = 4;
                boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
                glow[0] += ((hov ? 1f : 0f) - glow[0]) * 0.25f;
                int a = Math.round(90 + 120 * glow[0]);
                g.fill(x, y, x + w, y + h, (a << 24) | 0x0C1220);
                g.fill(x, y, x + w, y + 1, (Math.round(40 + 90 * glow[0]) << 24) | 0xFFFFFF);
                g.drawString(mc.font, label, x + 6, y + 3, (Math.round(150 + 105 * glow[0]) << 24) | 0xFFFFFF, false);
                boolean down = Input.mouseDown(InputConstants.MOUSE_BUTTON_LEFT);
                if (hov && down && !wasDown[0]) MC.setScreen(mc, new MenuChooserScreen());
                wasDown[0] = down;
            } catch (Throwable ignored) {}
        });
    }
}
