package com.iceymod.screen;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.DoubleSetting;
import com.iceymod.hud.settings.EnumSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.hud.settings.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Per-module settings editor using default vanilla Minecraft buttons.
 * Each setting is a ButtonWidget whose label shows both the name and the
 * current value. Click = cycle/toggle to the next value.
 */
public class ModuleSettingsScreen extends Screen {

    private final HudModule module;
    private final Screen parent;

    public ModuleSettingsScreen(HudModule module, Screen parent) {
        super(Text.literal(module.getName() + " Settings"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<Setting<?>> settings = module.getSettings();
        int btnW = Math.min(300, this.width - 80);
        int btnH = 20;
        int gap = 4;
        int centerX = this.width / 2;

        int totalH = settings.size() * (btnH + gap) + (btnH + gap) * 2 + 20;
        int startY = Math.max(60, this.height / 2 - totalH / 2);
        int y = startY;

        for (Setting<?> s : settings) {
            final Setting<?> setting = s;
            ButtonWidget btn = ButtonWidget.builder(
                    formatLabel(setting),
                    b -> {
                        onClick(setting);
                        b.setMessage(formatLabel(setting));
                    }
            ).dimensions(centerX - btnW / 2, y, btnW, btnH).build();
            addDrawableChild(btn);
            y += btnH + gap;
        }

        y += 12;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Reset to Defaults"),
                b -> { resetAll(); rebuild(); }
        ).dimensions(centerX - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                b -> client.setScreen(parent)
        ).dimensions(centerX - btnW / 2, y, btnW, btnH).build());
    }

    private void onClick(Setting<?> setting) {
        if (setting instanceof BoolSetting bs) {
            bs.set(!bs.get());
        } else if (setting instanceof IntSetting is) {
            int next = is.get() + is.step;
            if (next > is.max) next = is.min;
            is.set(next);
        } else if (setting instanceof DoubleSetting ds) {
            double next = ds.get() + ds.step;
            if (next > ds.max + 1e-6) next = ds.min;
            ds.set(next);
        } else if (setting instanceof ColorSetting cs) {
            cs.cycle();
        } else if (setting instanceof EnumSetting es) {
            es.cycle();
        }
    }

    private Text formatLabel(Setting<?> setting) {
        String label = setting.label;
        String valuePart;
        if (setting instanceof BoolSetting bs) {
            valuePart = bs.get() ? "\u00A7aON" : "\u00A7cOFF";
        } else if (setting instanceof IntSetting is) {
            valuePart = "\u00A7b" + is.get();
        } else if (setting instanceof DoubleSetting ds) {
            valuePart = "\u00A7b" + String.format("%.2f", ds.get());
        } else if (setting instanceof ColorSetting cs) {
            valuePart = chatCodeFor(cs.get()) + cs.colorName();
        } else if (setting instanceof EnumSetting es) {
            valuePart = "\u00A7b" + es.getCurrentOption();
        } else {
            valuePart = "";
        }
        return Text.literal(label + ": " + valuePart);
    }

    /**
     * Approximate the closest Minecraft chat color code for an ARGB value,
     * so the color-name label on the button renders in the chosen color.
     */
    private static String chatCodeFor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        // Gray / white / black
        if (r < 40 && g < 40 && b < 40) return "\u00A70";           // black
        if (r > 220 && g > 220 && b > 220) return "\u00A7f";         // white
        if (Math.abs(r - g) < 30 && Math.abs(g - b) < 30) return "\u00A77"; // gray
        // Primary hues by dominant channel
        if (r > 200 && g > 160 && b < 120) return "\u00A76";         // orange
        if (r > 200 && g > 200 && b < 140) return "\u00A7e";         // yellow
        if (r > 200 && g < 150 && b < 150) return "\u00A7c";         // red
        if (r < 150 && g > 180 && b < 150) return "\u00A7a";         // green
        if (r < 150 && g > 150 && b > 200) return "\u00A7b";         // aqua / icy
        if (r > 150 && g < 180 && b > 180) return "\u00A7d";         // pink / purple
        return "\u00A7f";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void resetAll() {
        for (Setting s : module.getSettings()) s.set(s.getDefault());
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Skip vanilla blur (avoids double-blur issues on recent versions)
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00A7b\u00A7l" + module.getName() + " \u00A77Settings"),
                this.width / 2, 20, 0xFFFFFFFF);
        if (module.getSettings().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u00A77No configurable options"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
