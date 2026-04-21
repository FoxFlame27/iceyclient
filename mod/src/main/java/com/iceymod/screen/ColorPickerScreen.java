package com.iceymod.screen;

import com.iceymod.hud.settings.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * RGB color picker. Sliders for R/G/B, a hex text field, a big preview
 * swatch, plus a quick palette row with the ColorSetting.PALETTE colors.
 * Apply writes the ARGB value back to the setting.
 */
public class ColorPickerScreen extends Screen {

    private final ColorSetting setting;
    private final Screen parent;
    private final int originalValue;

    private int r, g, b;
    private int alpha = 0xFF;

    private RgbSlider rSlider, gSlider, bSlider;
    private TextFieldWidget hexField;

    public ColorPickerScreen(ColorSetting setting, Screen parent) {
        super(Text.literal("Color"));
        this.setting = setting;
        this.parent = parent;
        this.originalValue = setting.get();
        unpack(originalValue);
    }

    private void unpack(int argb) {
        alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) alpha = 0xFF; // never let fully transparent slip through
        r = (argb >>> 16) & 0xFF;
        g = (argb >>> 8) & 0xFF;
        b = argb & 0xFF;
    }

    private int pack() { return (alpha << 24) | (r << 16) | (g << 8) | b; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int col = Math.min(320, this.width - 80);
        int x = cx - col / 2;
        int y = 80;
        int sh = 20;
        int gap = 8;

        rSlider = new RgbSlider(x, y, col, sh, "R", r, v -> { r = v; syncHex(); });
        addDrawableChild(rSlider);
        y += sh + gap;

        gSlider = new RgbSlider(x, y, col, sh, "G", g, v -> { g = v; syncHex(); });
        addDrawableChild(gSlider);
        y += sh + gap;

        bSlider = new RgbSlider(x, y, col, sh, "B", b, v -> { b = v; syncHex(); });
        addDrawableChild(bSlider);
        y += sh + gap * 2;

        // Hex input
        hexField = new TextFieldWidget(this.textRenderer, x, y, col, sh, Text.literal("Hex"));
        hexField.setMaxLength(8);
        hexField.setText(String.format("%02X%02X%02X", r, g, b));
        hexField.setChangedListener(this::onHexChanged);
        addDrawableChild(hexField);
        y += sh + gap * 2;

        // Palette quick-pick row — vanilla ButtonWidgets so it fits the rest
        int palCount = ColorSetting.PALETTE.length;
        int palBtnW = Math.max(24, col / palCount - 2);
        for (int i = 0; i < palCount; i++) {
            final int packedPalette = ColorSetting.PALETTE[i];
            ButtonWidget pb = ButtonWidget.builder(
                Text.literal("■"), // filled square glyph, colored via label
                btn -> { unpack(packedPalette); syncSliders(); syncHex(); }
            ).dimensions(x + i * (palBtnW + 2), y, palBtnW, sh).build();
            addDrawableChild(pb);
        }
        y += sh + gap * 2;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Save"),
            btn -> { setting.set(pack()); client.setScreen(parent); }
        ).dimensions(x, y, col, sh).build());
        y += sh + gap;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Reset to Default"),
            btn -> { unpack(setting.getDefault()); syncSliders(); syncHex(); }
        ).dimensions(x, y, col, sh).build());
        y += sh + gap;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Cancel"),
            btn -> { setting.set(originalValue); client.setScreen(parent); }
        ).dimensions(x, y, col, sh).build());
    }

    private void syncSliders() {
        if (rSlider != null) rSlider.setValueQuiet(r);
        if (gSlider != null) gSlider.setValueQuiet(g);
        if (bSlider != null) bSlider.setValueQuiet(b);
    }

    private void syncHex() {
        if (hexField == null) return;
        String current = String.format("%02X%02X%02X", r, g, b);
        if (!current.equals(hexField.getText())) hexField.setText(current);
    }

    private void onHexChanged(String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) { // shorthand #abc -> #aabbcc
            StringBuilder sb = new StringBuilder();
            for (char c : t.toCharArray()) sb.append(c).append(c);
            t = sb.toString();
        }
        if (t.length() != 6) return;
        try {
            int parsed = Integer.parseInt(t, 16);
            int nr = (parsed >> 16) & 0xFF;
            int ng = (parsed >> 8) & 0xFF;
            int nb = parsed & 0xFF;
            if (nr == r && ng == g && nb == b) return;
            r = nr; g = ng; b = nb;
            syncSliders();
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xD0070B14);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Title
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§b§l" + setting.label),
            this.width / 2, 22, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§7Drag sliders or type a hex value"),
            this.width / 2, 38, 0xFFAAAAAA);

        // Big preview swatch under the header
        int swW = Math.min(320, this.width - 80);
        int swX = this.width / 2 - swW / 2;
        int swY = 52;
        int swH = 22;
        int color = pack();
        ctx.fill(swX - 1, swY - 1, swX + swW + 1, swY + swH + 1, 0xFF000000);
        ctx.fill(swX, swY, swX + swW, swY + swH, color);
        // Hex label on the swatch (contrasting)
        String hex = String.format("#%02X%02X%02X", r, g, b);
        int textColor = isLight(color) ? 0xFF000000 : 0xFFFFFFFF;
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hex),
            this.width / 2, swY + 7, textColor);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private static boolean isLight(int argb) {
        int rr = (argb >>> 16) & 0xFF;
        int gg = (argb >>> 8) & 0xFF;
        int bb = argb & 0xFF;
        // perceptual luminance
        return (0.299 * rr + 0.587 * gg + 0.114 * bb) > 160;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── R/G/B slider widget ──────────────────────────────────
    interface ChannelCallback { void accept(int newVal); }

    static class RgbSlider extends SliderWidget {
        private final String channel;
        private final ChannelCallback onChange;

        RgbSlider(int x, int y, int w, int h, String channel, int initialByte, ChannelCallback onChange) {
            super(x, y, w, h, Text.literal(""), initialByte / 255.0);
            this.channel = channel;
            this.onChange = onChange;
            this.updateMessage();
        }

        void setValueQuiet(int byteVal) {
            this.value = Math.max(0, Math.min(255, byteVal)) / 255.0;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int v = (int) Math.round(this.value * 255.0);
            this.setMessage(Text.literal(channel + ": " + v));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(this.value * 255.0);
            if (onChange != null) onChange.accept(v);
        }
    }
}
