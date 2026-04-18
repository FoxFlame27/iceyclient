package com.iceymod.hud.settings;

/**
 * Base class for a configurable module setting.
 */
public abstract class Setting<T> {
    public final String id;
    public final String label;
    protected T value;
    protected final T defaultValue;

    public Setting(String id, String label, T defaultValue) {
        this.id = id;
        this.label = label;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T get() { return value; }
    public void set(T v) { this.value = v; }
    public T getDefault() { return defaultValue; }

    public abstract String serialize();
    public abstract void deserialize(String s);
}
