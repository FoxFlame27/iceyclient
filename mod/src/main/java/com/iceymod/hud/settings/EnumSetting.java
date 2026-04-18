package com.iceymod.hud.settings;

public class EnumSetting extends Setting<Integer> {
    public final String[] options;

    public EnumSetting(String id, String label, String[] options, int defaultIndex) {
        super(id, label, defaultIndex);
        this.options = options;
    }

    public String getCurrentOption() {
        if (value < 0 || value >= options.length) return options[0];
        return options[value];
    }

    public void cycle() {
        value = (value + 1) % options.length;
    }

    @Override
    public String serialize() { return String.valueOf(value); }

    @Override
    public void deserialize(String s) {
        try { value = Math.max(0, Math.min(options.length - 1, Integer.parseInt(s.trim()))); } catch (Exception ignored) {}
    }
}
