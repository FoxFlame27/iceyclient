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
        int cx = this.width / 2;
        int rowW = 280;
        int rowH = 22;
        int gap = 4;

        int totalH = settings.size() * (rowH + gap) + rowH * 2;
        int startY = Math.max(60, this.height / 2 - totalH / 2);
        int y = startY;

        for (Setting<?> s : settings) {
            addSettingRow(s, cx - rowW / 2, y, rowW, rowH);
            y += rowH + gap;
        }

        y += gap * 2;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00A7eReset to Defaults"),
                b -> { resetAll(); rebuild(); }
        ).dimensions(cx - rowW / 2, y, rowW, rowH).build());
        y += rowH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2190 Back"),
                b -> client.setScreen(parent)
        ).dimensions(cx - rowW / 2, y, rowW, rowH).build());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void resetAll() {
        for (Setting s : module.getSettings()) {
            s.set(s.getDefault());
        }
    }

    private void addSettingRow(Setting<?> setting, int x, int y, int w, int h) {
        int labelW = 110;
        int controlW = w - labelW;

        if (setting instanceof BoolSetting bs) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00A77" + setting.label + ": " + (bs.get() ? "\u00A7aON" : "\u00A7cOFF")),
                    b -> { bs.set(!bs.get()); rebuild(); }
            ).dimensions(x, y, w, h).build());

        } else if (setting instanceof IntSetting is) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00A77" + setting.label + ": \u00A7b" + is.get()),
                    b -> { is.set(is.get() + is.step); if (is.get().equals(is.max)) is.set(is.min); rebuild(); }
            ).dimensions(x, y, w, h).build());

        } else if (setting instanceof DoubleSetting ds) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00A77" + setting.label + ": \u00A7b" + String.format("%.2f", ds.get())),
                    b -> {
                        double next = ds.get() + ds.step;
                        if (next > ds.max) next = ds.min;
                        ds.set(next);
                        rebuild();
                    }
            ).dimensions(x, y, w, h).build());

        } else if (setting instanceof ColorSetting cs) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00A77" + setting.label + ": " + colorPrefix(cs.get()) + cs.colorName()),
                    b -> { cs.cycle(); rebuild(); }
            ).dimensions(x, y, w, h).build());

        } else if (setting instanceof EnumSetting es) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00A77" + setting.label + ": \u00A7b" + es.getCurrentOption()),
                    b -> { es.cycle(); rebuild(); }
            ).dimensions(x, y, w, h).build());
        }
    }

    private String colorPrefix(int argb) {
        // Approximate the chat color closest to the ARGB value
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
        if (r > 200 && g > 200 && bl > 200) return "\u00A7f";
        if (r > 200 && g < 100 && bl < 100) return "\u00A7c";
        if (r < 100 && g > 200 && bl < 100) return "\u00A7a";
        if (r > 200 && g > 200 && bl < 100) return "\u00A7e";
        if (r > 200 && g > 100 && bl < 100) return "\u00A76";
        if (r < 100 && g < 200 && bl > 200) return "\u00A7b";
        if (r > 150 && g < 200 && bl > 200) return "\u00A7d";
        if (r < 50 && g < 50 && bl < 50) return "\u00A78";
        return "\u00A77";
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "\u00A7b\u00A7l" + module.getName() + " \u00A77Settings",
                this.width / 2, 20, 0xFFFFFFFF);
        if (module.getSettings().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "\u00A77No configurable options",
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
