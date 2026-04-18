package com.iceymod.hud.settings;

/**
 * 32-bit ARGB color setting. Cycles through a fixed palette for simplicity.
 */
public class ColorSetting extends Setting<Integer> {
    public static final int[] PALETTE = {
        0xFFFFFFFF, // white
        0xFF5BC8F5, // icy blue
        0xFF4ADE80, // green
        0xFFFBBF24, // yellow
        0xFFFB923C, // orange
        0xFFF87171, // red
        0xFFA78BFA, // purple
        0xFFEC4899, // pink
        0xFF000000, // black
        0xFF9CA3AF, // gray
    };
    public static final String[] NAMES = {
        "White", "Icy Blue", "Green", "Yellow", "Orange", "Red", "Purple", "Pink", "Black", "Gray"
    };

    public ColorSetting(String id, String label, int def) {
        super(id, label, def);
    }

    public String colorName() {
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == value) return NAMES[i];
        return String.format("#%08X", value);
    }

    public void cycle() {
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == value) { idx = i; break; }
        value = PALETTE[(idx + 1) % PALETTE.length];
    }

    @Override
    public String serialize() { return String.format("#%08X", value); }

    @Override
    public void deserialize(String s) {
        if (s == null) return;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        try { value = (int) Long.parseLong(t, 16); } catch (Exception ignored) {}
    }
}
