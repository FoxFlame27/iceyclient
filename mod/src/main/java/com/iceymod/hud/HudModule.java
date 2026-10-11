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
    public final ColorSetting barColor  = new ColorSetting("barColor",  "Bar Color",  0xFF5BC8F5);
    // Look of the standard text box. The defaults draw exactly what the
    // box looked like before these existed.
    public final DoubleSetting scale = new DoubleSetting("scale", "Size", 1.0, 0.5, 2.0, 0.1);
    public final BoolSetting background = new BoolSetting("background", "Background", true);
    public final ColorSetting backgroundColor = new ColorSetting("backgroundColor", "Background Color", 0xFF000000);
    public final IntSetting backgroundOpacity = new IntSetting("backgroundOpacity", "Background Opacity", 55, 0, 100, 5);
    public final BoolSetting accentBar = new BoolSetting("accentBar", "Accent Bar", true);
    public final BoolSetting textShadow = new BoolSetting("textShadow", "Text Shadow", true);
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
                settings.add(accentBar);
                settings.add(textShadow);
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
        if (rainbow.get()) {
            // Colour codes inside the text would win over the rainbow.
            text = text.replaceAll("\u00A7.", "");
            color = rainbowColor(0);
        }
        int pad = padding.get();
        int bar = accentBar.get() ? 2 : 0;
        int textX = bar + pad + 1;
        int textWidth = client.font.width(text);
        this.width = textX + textWidth + pad + 1;
        this.height = 8 + pad * 2;

        if (background.get() && backgroundOpacity.get() > 0) {
            int alpha = Math.round(backgroundOpacity.get() * 2.55f);
            context.fill(x, y, x + width, y + height, (alpha << 24) | (backgroundColor.get() & 0xFFFFFF));
        }
        if (bar > 0) context.fill(x, y, x + bar, y + height, barColor.get());
        context.drawString(client.font, text, x + textX, y + pad, color, textShadow.get());
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
