package com.iceymod.hud.settings;

public class IntSetting extends Setting<Integer> {
    public final int min, max, step;

    public IntSetting(String id, String label, int def, int min, int max) {
        this(id, label, def, min, max, 1);
    }

    public IntSetting(String id, String label, int def, int min, int max, int step) {
        super(id, label, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void set(Integer v) {
        this.value = Math.max(min, Math.min(max, v));
    }

    @Override
    public String serialize() { return String.valueOf(value); }

    @Override
    public void deserialize(String s) {
        try { set(Integer.parseInt(s.trim())); } catch (Exception ignored) {}
    }
}
