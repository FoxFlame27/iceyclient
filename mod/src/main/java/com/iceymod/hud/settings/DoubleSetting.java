package com.iceymod.hud.settings;

public class DoubleSetting extends Setting<Double> {
    public final double min, max, step;

    public DoubleSetting(String id, String label, double def, double min, double max, double step) {
        super(id, label, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void set(Double v) {
        this.value = Math.max(min, Math.min(max, v));
    }

    @Override
    public String serialize() { return String.valueOf(value); }

    @Override
    public void deserialize(String s) {
        try { set(Double.parseDouble(s.trim())); } catch (Exception ignored) {}
    }
}
