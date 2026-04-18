package com.iceymod.hud.settings;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String id, String label, boolean def) {
        super(id, label, def);
    }

    @Override
    public String serialize() { return value ? "true" : "false"; }

    @Override
    public void deserialize(String s) {
        if (s == null) return;
        value = "true".equalsIgnoreCase(s.trim());
    }
}
