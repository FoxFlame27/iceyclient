package com.iceymod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Version-compat shim for KeyBinding construction.
 *
 * 1.21.8 and earlier:  new KeyBinding(String, InputUtil.Type, int, String)
 * 1.21.9+:             new KeyBinding(String, InputUtil.Type, int, KeyBinding.Category)
 *
 * We build against 1.21.8 but ship into newer installs. Earlier attempts
 * used Class.forName("net.minecraft.client.option.KeyBinding$Category"),
 * which silently fails in production: Loom remaps compile-time class
 * references but NOT string literals, so at runtime that class is named
 * "net.minecraft.class_304$class_11900" — the Class.forName lookup falls
 * through, mode becomes BROKEN, and no keybinds get registered.
 *
 * This rewrite avoids string-based reflection entirely. It enumerates
 * KeyBinding's public constructors (the Class&lt;?&gt; object IS remapped),
 * finds one with 4 parameters matching (String, InputUtil.Type, int, ???),
 * and if the 4th is String uses the legacy path. Otherwise the 4th
 * parameter's type IS the Category class — we then find any built-in
 * category instance by iterating its public static fields of that type.
 * No class-name string lookup needed, so this works in both dev and
 * production regardless of obfuscation.
 */
public final class KeyBindingCompat {

    private enum Mode { UNKNOWN, LEGACY_STRING, NEW_CATEGORY, BROKEN }

    private static Mode mode = Mode.UNKNOWN;
    private static Constructor<KeyMapping> legacyCtor;
    private static Constructor<KeyMapping> newCtor;
    private static Object defaultCategoryInstance;

    private KeyBindingCompat() {}

    @SuppressWarnings("unchecked")
    private static synchronized void probe() {
        if (mode != Mode.UNKNOWN) return;
        try {
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != 4) continue;
                if (params[0] != String.class) continue;
                if (params[1] != InputConstants.Type.class) continue;
                if (params[2] != int.class) continue;

                if (params[3] == String.class) {
                    legacyCtor = (Constructor<KeyMapping>) ctor;
                    mode = Mode.LEGACY_STRING;
                    return;
                }

                // params[3] IS the Category class (whatever its runtime name).
                // Preferred: make our OWN category so every Icey key sits
                // under "Icey Client" in Controls. 1.21.9+ exposes a static
                // factory Category.create(Identifier) — find it by shape
                // (static, returns Category, takes exactly one Identifier);
                // its translation key is "key.categories.<ns>.<path>".
                Class<?> categoryClass = params[3];
                defaultCategoryInstance = createOwnCategory(categoryClass);

                // Fallback: any built-in category instance (Movement/Misc/…)
                // so the keys at least show up somewhere.
                if (defaultCategoryInstance == null) {
                    for (Field f : categoryClass.getFields()) {
                        if (!Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType() != categoryClass) continue;
                        try {
                            Object value = f.get(null);
                            if (value != null) {
                                defaultCategoryInstance = value;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                if (defaultCategoryInstance != null) {
                    newCtor = (Constructor<KeyMapping>) ctor;
                    mode = Mode.NEW_CATEGORY;
                    return;
                }
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] KeyBindingCompat probe threw: " + t);
        }

        mode = Mode.BROKEN;
        System.out.println("[IceyMod] KeyBindingCompat: no matching KeyBinding constructor found — keybinds disabled");
    }

    /** Identifier for our Controls category → lang key "key.categories.iceymod.iceyclient". */
    private static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("iceymod", "iceyclient");

    private static Object createOwnCategory(Class<?> categoryClass) {
        // Static factory taking an Identifier (Category.create(Identifier) on 1.21.9+).
        for (Method m : categoryClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (m.getReturnType() != categoryClass) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 1 || p[0] != Identifier.class) continue;
            try {
                Object created = m.invoke(null, CATEGORY_ID);
                if (created != null) return created;
            } catch (Throwable t) {
                System.out.println("[IceyMod] Category factory " + m.getName() + " failed: " + t);
            }
        }
        // Public constructor taking an Identifier, if the record exposes one.
        for (Constructor<?> c : categoryClass.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length != 1 || p[0] != Identifier.class) continue;
            try {
                return c.newInstance(CATEGORY_ID);
            } catch (Throwable t) {
                System.out.println("[IceyMod] Category constructor failed: " + t);
            }
        }
        return null;
    }

    public static KeyMapping create(String translationKey, InputConstants.Type type, int code, String categoryKey) {
        if (mode == Mode.UNKNOWN) probe();
        try {
            switch (mode) {
                case LEGACY_STRING -> {
                    return legacyCtor.newInstance(translationKey, type, code, categoryKey);
                }
                case NEW_CATEGORY -> {
                    return newCtor.newInstance(translationKey, type, code, defaultCategoryInstance);
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
