package com.iceymod.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class HudModule {
    private final String id;
    private final String name;
    private boolean enabled;
    private int x;
    private int y;
    protected int width = 80;
    protected int height = 14;

    public HudModule(String id, String name, int defaultX, int defaultY) {
        this.id = id;
        this.name = name;
        this.x = defaultX;
        this.y = defaultY;
        this.enabled = true;
    }

    /**
     * Return the text to display, or null if this module uses custom rendering.
     */
    public abstract String getText(MinecraftClient client);

    /**
     * Render this module on screen. Override for custom drawing (keystrokes, armor, etc).
     */
    public void render(DrawContext context, MinecraftClient client) {
        if (!enabled) return;
        String text = getText(client);
        if (text == null) return;

        int textWidth = client.textRenderer.getWidth(text);
        this.width = textWidth + 10;
        this.height = 14;

        // Semi-transparent background
        context.fill(x, y, x + width, y + height, 0x90000000);
        // Left accent bar (icy blue)
        context.fill(x, y, x + 2, y + height, 0xFF5BC8F5);
        // Text with shadow
        context.drawTextWithShadow(client.textRenderer, text, x + 6, y + 3, 0xFFFFFFFF);
    }

    public void tick() {}

    // --- Getters / Setters ---
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggle() { this.enabled = !enabled; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
