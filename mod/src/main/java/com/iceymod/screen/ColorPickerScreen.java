package com.iceymod.screen;

import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * RGB color picker. Sliders for R/G/B, a hex text field, a big preview
 * swatch, plus a quick palette row with the ColorSetting.PALETTE colors.
 * Apply writes the ARGB value back to the setting.
 */
public class ColorPickerScreen extends IceyScreen {

    private final ColorSetting setting;
    private final Screen parent;
    private final int originalValue;
    /** Callback for the standalone-color path (waypoints). null when bound to a setting. */
    private final java.util.function.IntConsumer onApply;
    private final String displayLabel;

    private int r, g, b;
    private int alpha = 0xFF;

    private RgbSlider rSlider, gSlider, bSlider;
    private EditBox hexField;

    public ColorPickerScreen(ColorSetting setting, Screen parent) {
        super(Component.literal("Color"));
        this.setting = setting;
        this.parent = parent;
        this.onApply = null;
        this.displayLabel = setting.label;
        this.originalValue = setting.get();
        unpack(originalValue);
    }

    /**
     * Standalone color picker not tied to a ColorSetting — used when
     * picking a waypoint color, where the value lives in WaypointManager
     * instead of a Setting.
     */
    public ColorPickerScreen(int initialColor, String label,
                             java.util.function.IntConsumer onApply, Screen parent) {
        super(Component.literal(label));
        this.setting = null;
        this.parent = parent;
        this.onApply = onApply;
        this.displayLabel = label;
        this.originalValue = initialColor;
        unpack(initialColor);
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
        addRenderableWidget(rSlider);
        y += sh + gap;

        gSlider = new RgbSlider(x, y, col, sh, "G", g, v -> { g = v; syncHex(); });
        addRenderableWidget(gSlider);
        y += sh + gap;

        bSlider = new RgbSlider(x, y, col, sh, "B", b, v -> { b = v; syncHex(); });
        addRenderableWidget(bSlider);
        y += sh + gap * 2;

        // Hex input
        hexField = new EditBox(this.font, x, y, col, sh, Component.literal("Hex"));
        hexField.setMaxLength(8);
        hexField.setValue(String.format("%02X%02X%02X", r, g, b));
        hexField.setResponder(this::onHexChanged);
        addRenderableWidget(hexField);
        y += sh + gap * 2;

        // Palette quick-pick row removed — user feedback: too cluttered,
        // and the hex field above is already a faster way to type a
        // known value. Save button now sits directly under the hex
        // input, taller (24px) so it's clearly the primary action.
        int saveH = 24;
        addRenderableWidget(Button.builder(
            Component.literal("§a§lSave"),
            btn -> {
                int packed = pack();
                if (setting != null) setting.set(packed);
                if (onApply != null) onApply.accept(packed);
                // Persist to disk — without this the color is held in
                // memory only and reverts on next MC launch.
                try { com.iceymod.hud.HudManager.save(); } catch (Throwable ignored) {}
                com.iceymod.compat.MC.setScreen(minecraft, parent);
            }
        ).bounds(x, y, col, saveH).build());
        y += saveH + gap;

        Button resetBtn = Button.builder(
            Component.literal("Reset to Default"),
            btn -> {
                int def = setting != null ? setting.getDefault() : originalValue;
                unpack(def); syncSliders(); syncHex();
            }
        ).bounds(x, y, col, sh).build();
        // Hide the "Reset to Default" button on the standalone path —
        // there's no meaningful "default" for a waypoint color.
        if (setting != null) addRenderableWidget(resetBtn);
        if (setting != null) y += sh + gap;

        addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            btn -> {
                if (setting != null) setting.set(originalValue);
                com.iceymod.compat.MC.setScreen(minecraft, parent);
            }
        ).bounds(x, y, col, sh).build());
    }

    private void syncSliders() {
        if (rSlider != null) rSlider.setValueQuiet(r);
        if (gSlider != null) gSlider.setValueQuiet(g);
        if (bSlider != null) bSlider.setValueQuiet(b);
    }

    private void syncHex() {
        if (hexField == null) return;
        String current = String.format("%02X%02X%02X", r, g, b);
        if (!current.equals(hexField.getValue())) hexField.setValue(current);
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
    protected void renderScreenBackground(Gfx context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xD0070B14);
    }

    @Override
    protected void renderScreen(Gfx ctx, int mouseX, int mouseY, float delta) {
        // Title
        ctx.drawCenteredString(this.font,
            Component.literal("§b§l" + displayLabel),
            this.width / 2, 22, 0xFFFFFFFF);
        ctx.drawCenteredString(this.font,
            Component.literal("§7Drag sliders or type a hex value"),
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
        ctx.drawCenteredString(this.font, Component.literal(hex),
            this.width / 2, swY + 7, textColor);

        superRender(ctx, mouseX, mouseY, delta);
    }

    private static boolean isLight(int argb) {
        int rr = (argb >>> 16) & 0xFF;
        int gg = (argb >>> 8) & 0xFF;
        int bb = argb & 0xFF;
        // perceptual luminance
        return (0.299 * rr + 0.587 * gg + 0.114 * bb) > 160;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── R/G/B slider widget ──────────────────────────────────
    interface ChannelCallback { void accept(int newVal); }

    static class RgbSlider extends AbstractSliderButton {
        private final String channel;
        private final ChannelCallback onChange;

        RgbSlider(int x, int y, int w, int h, String channel, int initialByte, ChannelCallback onChange) {
            super(x, y, w, h, Component.literal(""), initialByte / 255.0);
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
            this.setMessage(Component.literal(channel + ": " + v));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(this.value * 255.0);
            if (onChange != null) onChange.accept(v);
        }
    }
}
