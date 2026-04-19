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

import java.util.ArrayList;
import java.util.List;

/**
 * Per-module settings editor. Rows are drawn manually (not via ButtonWidget's
 * default rendering) so labels stay high-contrast, color settings show the
 * actual selected color as a swatch + colored name, and toggle states read
 * clearly.
 */
public class ModuleSettingsScreen extends Screen {

    private final HudModule module;
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    private int contentX, contentY, contentW;
    private static final int ROW_H = 34;
    private static final int ROW_GAP = 6;

    public ModuleSettingsScreen(HudModule module, Screen parent) {
        super(Text.literal(module.getName() + " Settings"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        List<Setting<?>> settings = module.getSettings();
        int targetW = Math.min(560, this.width - 80);
        contentW = targetW;
        contentX = this.width / 2 - targetW / 2;

        int blockH = settings.size() * (ROW_H + ROW_GAP) + ROW_H * 2 + 12;
        contentY = Math.max(70, this.height / 2 - blockH / 2);
        int y = contentY;

        for (Setting<?> s : settings) {
            rows.add(new Row(s, y, null));
            y += ROW_H + ROW_GAP;
        }

        // Reset button — real widget
        y += 6;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00A7eReset to Defaults"),
                b -> { resetAll(); rebuild(); }
        ).dimensions(contentX, y, contentW, ROW_H - 2).build());
        y += ROW_H + ROW_GAP;

        // Back button — real widget
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2190 Back"),
                b -> client.setScreen(parent)
        ).dimensions(contentX, y, contentW, ROW_H - 2).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Row r : rows) {
                if (mouseX >= contentX && mouseX <= contentX + contentW
                        && mouseY >= r.y && mouseY <= r.y + ROW_H) {
                    onRowClick(r.setting);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onRowClick(Setting<?> setting) {
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
        // No full rebuild needed — the next render call reads the new value.
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
        context.fill(0, 0, this.width, this.height, 0xD0070B14);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Header
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00A7b\u00A7l" + module.getName()),
                this.width / 2, 26, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00A77Settings"),
                this.width / 2, 42, 0xFFAAAAAA);

        // Manual row rendering
        for (Row r : rows) {
            boolean hover = mouseX >= contentX && mouseX <= contentX + contentW
                    && mouseY >= r.y && mouseY <= r.y + ROW_H;
            drawRow(context, r.setting, r.y, hover);
        }

        super.render(context, mouseX, mouseY, delta);

        if (module.getSettings().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u00A77No configurable options"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
        }
    }

    private void drawRow(DrawContext ctx, Setting<?> setting, int y, boolean hover) {
        int x = contentX;
        int w = contentW;

        // Card background + border
        int bg = hover ? 0xFF162033 : 0xFF0E1626;
        int border = hover ? 0xFF5BC8F5 : 0x40000000;
        ctx.fill(x, y, x + w, y + ROW_H, bg);
        // 1px border
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + ROW_H - 1, x + w, y + ROW_H, border);
        ctx.fill(x, y, x + 1, y + ROW_H, border);
        ctx.fill(x + w - 1, y, x + w, y + ROW_H, border);

        // Label on the left (always high-contrast white)
        int textY = y + (ROW_H - 8) / 2;
        ctx.drawTextWithShadow(this.textRenderer, setting.label, x + 14, textY, 0xFFFFFFFF);

        // Value on the right
        drawValue(ctx, setting, x + w - 14, textY);
    }

    private void drawValue(DrawContext ctx, Setting<?> setting, int rightX, int textY) {
        if (setting instanceof BoolSetting bs) {
            String label = bs.get() ? "ON" : "OFF";
            int color = bs.get() ? 0xFF4ADE80 : 0xFFF87171;
            int w = this.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(this.textRenderer, label, rightX - w, textY, color);
            return;
        }
        if (setting instanceof IntSetting is) {
            String label = String.valueOf(is.get());
            int w = this.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(this.textRenderer, label, rightX - w, textY, 0xFF5BC8F5);
            return;
        }
        if (setting instanceof DoubleSetting ds) {
            String label = String.format("%.2f", ds.get());
            int w = this.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(this.textRenderer, label, rightX - w, textY, 0xFF5BC8F5);
            return;
        }
        if (setting instanceof ColorSetting cs) {
            int color = cs.get();
            String name = cs.colorName();
            int textW = this.textRenderer.getWidth(name);
            int swatchW = 14;
            int swatchX = rightX - textW - 6 - swatchW;
            int swatchTop = textY - 2;
            int swatchBot = textY + 10;
            // Outline for visibility over any bg
            ctx.fill(swatchX - 1, swatchTop - 1, swatchX + swatchW + 1, swatchBot + 1, 0xFF000000);
            ctx.fill(swatchX, swatchTop, swatchX + swatchW, swatchBot, color);
            // Name in that same color
            ctx.drawTextWithShadow(this.textRenderer, name, rightX - textW, textY, color);
            return;
        }
        if (setting instanceof EnumSetting es) {
            String label = es.getCurrentOption();
            int w = this.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(this.textRenderer, label, rightX - w, textY, 0xFF5BC8F5);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    private static final class Row {
        final Setting<?> setting;
        final int y;
        Row(Setting<?> s, int y, Object unused) { this.setting = s; this.y = y; }
    }
}
