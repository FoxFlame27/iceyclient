package com.iceymod.compat;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Version-compat shim for KeyBinding construction. The 4-arg (String, Type, int, String)
 * constructor existed through 1.21.8 but was replaced in 1.21.9+ with (String, Type, int,
 * KeyBinding.Category). We build against 1.21.8 but the mod ships into newer installs too,
 * so construction is probed once via reflection and cached.
 *
 * Returns null if no constructor path works — callers must null-check and skip the bind.
 */
public final class KeyBindingCompat {

    private enum Mode { UNKNOWN, LEGACY_STRING, NEW_CATEGORY, BROKEN }

    private static Mode mode = Mode.UNKNOWN;
    private static Constructor<KeyBinding> legacyCtor;
    private static Constructor<KeyBinding> newCtor;
    private static Method categoryCreate;
    private static Class<?> categoryClass;

    private KeyBindingCompat() {}

    @SuppressWarnings("unchecked")
    private static synchronized void probe() {
        if (mode != Mode.UNKNOWN) return;

        // 1.21.8 path: (String, InputUtil.Type, int, String)
        try {
            legacyCtor = (Constructor<KeyBinding>) KeyBinding.class.getConstructor(
                    String.class, InputUtil.Type.class, int.class, String.class);
            mode = Mode.LEGACY_STRING;
            return;
        } catch (NoSuchMethodException ignored) {}

        // 1.21.9+ path: (String, InputUtil.Type, int, KeyBinding$Category)
        try {
            categoryClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
            categoryCreate = categoryClass.getMethod("create", Identifier.class);
            newCtor = (Constructor<KeyBinding>) KeyBinding.class.getConstructor(
                    String.class, InputUtil.Type.class, int.class, categoryClass);
            mode = Mode.NEW_CATEGORY;
            return;
        } catch (Throwable ignored) {}

        mode = Mode.BROKEN;
    }

    public static KeyBinding create(String translationKey, InputUtil.Type type, int code, String categoryKey) {
        if (mode == Mode.UNKNOWN) probe();
        try {
            switch (mode) {
                case LEGACY_STRING -> {
                    return legacyCtor.newInstance(translationKey, type, code, categoryKey);
                }
                case NEW_CATEGORY -> {
                    Identifier id = Identifier.of("iceymod", "main");
                    Object category = categoryCreate.invoke(null, id);
                    return newCtor.newInstance(translationKey, type, code, category);
                }
                default -> {
                    return null;
                }
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] KeyBinding construction failed for " + translationKey + ": " + t);
            return null;
        }
    }
}
