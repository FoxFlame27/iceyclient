package com.iceymod.hud;

import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.DoubleSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.hud.settings.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;

public abstract class HudModule {
    public enum Category {
        INFO, COMBAT, OPTIMIZATION
    }

    private final String id;
    private final String name;
    private boolean enabled;
    private int x;
    private int y;
    protected int width = 80;
    protected int height = 14;

    // Settings list — subclasses add their own options here.
    protected final List<Setting<?>> settings = new ArrayList<>();
    // textColor / barColor are conditionally registered: invisible modules
    // (Full Bright, Auto Sprint, etc.) don't need them, so they don't show up
    // in the settings screen. Instances still exist for modules that DO
    // use the default render().
    public final ColorSetting textColor = new ColorSetting("textColor", "Text Color", 0xFFFFFFFF);
    public final ColorSetting barColor  = new ColorSetting("barColor",  "Bar Color",  Paint.ACCENT);
    // Look of the standard box: navy glass, rounded, hairline, accent pill.
    // Widgets that draw themselves read the same fields through box().
    public final DoubleSetting scale = new DoubleSetting("scale", "Size", 1.0, 0.5, 2.0, 0.1);
    public final BoolSetting background = new BoolSetting("background", "Background", true);
    public final ColorSetting backgroundColor = new ColorSetting("backgroundColor", "Background Color", 0xFF000000 | Paint.PANEL);
    public final IntSetting backgroundOpacity = new IntSetting("backgroundOpacity", "Background Opacity", 72, 0, 100, 5);
    public final IntSetting corners = new IntSetting("corners", "Corners", Paint.RADIUS, 0, 6);
    public final BoolSetting outline = new BoolSetting("outline", "Outline", true);
    public final BoolSetting accentBar = new BoolSetting("accentBar", "Accent Bar", true);
    public final BoolSetting textShadow = new BoolSetting("textShadow", "Text Shadow", true);
    public final BoolSetting mutedLabel = new BoolSetting("mutedLabel", "Muted Label", true);
    public final BoolSetting rainbow = new BoolSetting("rainbow", "Rainbow Text", false);
    public final IntSetting padding = new IntSetting("padding", "Padding", 3, 1, 8);

    public HudModule(String id, String name, int defaultX, int defaultY) {
        this.id = id;
        this.name = name;
        this.x = defaultX;
        this.y = defaultY;
        this.enabled = true;
        if (shouldShowStyleSettings()) {
            settings.add(textColor);
            settings.add(barColor);
            settings.add(scale);
            // The box options only mean something to modules that use the
            // standard box; ones that draw themselves get Size alone.
            if (usesStandardBox()) {
                settings.add(background);
                settings.add(backgroundColor);
                settings.add(backgroundOpacity);
                settings.add(corners);
                settings.add(outline);
                settings.add(accentBar);
                settings.add(textShadow);
                settings.add(mutedLabel);
                settings.add(rainbow);
                settings.add(padding);
            }
        }
    }

    private boolean usesStandardBox() {
        try {
            return getClass().getMethod("render", Gfx.class, Minecraft.class).getDeclaringClass() == HudModule.class;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether this module should expose the universal Text Color / Bar Color
     * settings. Invisible modules (FullBright, AutoSprint, Net*, FpsBoost*, etc.)
     * override to false.
     */
    protected boolean shouldShowStyleSettings() { return true; }

    protected <S extends Setting<?>> S addSetting(S s) {
        settings.add(s);
        return s;
    }

    public List<Setting<?>> getSettings() { return settings; }

    public Category getCategory() {
        return Category.INFO;
    }

    /**
     * Return the text to display, or null if this module uses custom rendering.
     */
    public abstract String getText(Minecraft client);

    /**
     * Render this module on screen. Override for custom drawing.
     */
    public void render(Gfx context, Minecraft client) {
        if (!enabled) return;
        String text = getText(client);
        if (text == null) return;

        int color = textColor.get();
        boolean rb = rainbow.get();
        if (rb) {
            // Colour codes inside the text would win over the rainbow.
            text = text.replaceAll("§.", "");
            color = rainbowColor(0);
        }
        int pad = padding.get();
        int textX = textInset();
        int textWidth = client.font.width(text);
        this.width = textX + textWidth + pad + 1;
        this.height = 8 + pad * 2;

        box(context, x, y, width, height, barColor.get());
        drawText(context, client, text, x + textX, y + pad, color, !rb && mutedLabel.get());
    }

    // ---- the themed look, shared with widgets that draw themselves ----

    /** Where text starts inside a themed box: after the accent pill and padding. */
    protected int textInset() {
        return (accentBar.get() ? 6 : 1) + padding.get();
    }

    /**
     * Draws a themed box: glass background, hairline, accent pill on the left
     * in {@code accent} (0 for none). Reads this module's style settings, which
     * are the defaults for widgets that don't expose them.
     */
    protected void box(Gfx g, int bx, int by, int w, int h, int accent) {
        int r = corners.get();
        int a = Math.round(backgroundOpacity.get() * 2.55f);
        if (background.get() && a > 0) Paint.fill(g, bx, by, w, h, r, Paint.alpha(backgroundColor.get(), a));
        if (outline.get()) Paint.outline(g, bx, by, w, h, r, Paint.HAIRLINE);
        if (accent != 0 && accentBar.get()) Paint.fill(g, bx + 3, by + 3, 2, h - 6, 1, accent);
    }

    /**
     * Draws a line of HUD text. With {@code muted}, a "Label: value" line gets
     * its label dimmed so the value stands out.
     */
    protected void drawText(Gfx g, Minecraft client, String text, int tx, int ty, int color, boolean muted) {
        boolean shadow = textShadow.get();
        if (muted) {
            int i = text.indexOf(": ");
            if (i > 0 && i <= 24 && text.lastIndexOf('§', i) < 0) {
                String label = text.substring(0, i + 1);
                g.drawString(client.font, label, tx, ty, mutedOf(color), shadow);
                g.drawString(client.font, text.substring(i + 2), tx + client.font.width(label + " "), ty, color, shadow);
                return;
            }
        }
        g.drawString(client.font, text, tx, ty, color, shadow);
    }

    /** A dimmer version of a text colour for labels. */
    protected static int mutedOf(int color) {
        return (color & 0xFFFFFF) | 0xA6000000;
    }

    /** Colour of the rainbow right now; {@code offset} shifts it, so lines of a list differ. */
    public static int rainbowColor(int offset) {
        float hue = ((System.currentTimeMillis() + offset * 120L) % 4000L) / 4000f;
        // Hue to RGB at saturation 0.55, full brightness. Done by hand:
        // java.awt must not be touched inside the game on macOS.
        float h = hue * 6f;
        float f = h - (float) Math.floor(h);
        float lo = 1f - 0.55f, down = 1f - 0.55f * f, up = 1f - 0.55f * (1f - f);
        float r, g, b;
        switch ((int) h % 6) {
            case 0 -> { r = 1f; g = up; b = lo; }
            case 1 -> { r = down; g = 1f; b = lo; }
            case 2 -> { r = lo; g = 1f; b = up; }
            case 3 -> { r = lo; g = down; b = 1f; }
            case 4 -> { r = up; g = lo; b = 1f; }
            default -> { r = 1f; g = lo; b = down; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** Draws the module at its chosen Size. Use this, not render(), to put a module on screen. */
    public final void renderScaled(Gfx context, Minecraft client) {
        float s = scaleFactor();
        if (s == 1f) {
            render(context, client);
            return;
        }
        context.pose().pushMatrix();
        try {
            context.pose().translate(x, y);
            context.pose().scale(s, s);
            context.pose().translate(-x, -y);
            render(context, client);
        } finally {
            context.pose().popMatrix();
        }
    }

    private float scaleFactor() {
        if (!settings.contains(scale)) return 1f;
        float s = scale.get().floatValue();
        return Math.abs(s - 1f) < 0.01f ? 1f : s;
    }

    public void tick() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggle() { setEnabled(!enabled); }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    /** Size on screen, with the Size setting applied. */
    public int getWidth() { return Math.round(width * scaleFactor()); }
    public int getHeight() { return Math.round(height * scaleFactor()); }
}
