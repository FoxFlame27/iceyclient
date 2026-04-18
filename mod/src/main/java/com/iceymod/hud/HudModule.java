package com.iceymod.hud;

import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

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

    public HudModule(String id, String name, int defaultX, int defaultY) {
        this.id = id;
        this.name = name;
        this.x = defaultX;
        this.y = defaultY;
        this.enabled = true;
        if (shouldShowStyleSettings()) {
            settings.add(textColor);
            settings.add(barColor);
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
    public abstract String getText(MinecraftClient client);

    /**
     * Render this module on screen. Override for custom drawing.
     */
    public void render(DrawContext context, MinecraftClient client) {
        if (!enabled) return;
        String text = getText(client);
        if (text == null) return;

        int textWidth = client.textRenderer.getWidth(text);
        this.width = textWidth + 10;
        this.height = 14;

        context.fill(x, y, x + width, y + height, 0x90000000);
        context.fill(x, y, x + 2, y + height, barColor.get());
        context.drawTextWithShadow(client.textRenderer, text, x + 6, y + 3, textColor.get());
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
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
