package com.iceyscroll;

import com.iceyscroll.mixin.KeyBindsScreenAccessor;
import com.iceyscroll.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import java.lang.ref.WeakReference;

/**
 * Scroll Up and Scroll Down as keys.
 *
 * The game has no key for the wheel, so the two directions borrow two
 * mouse button numbers no real mouse has. That makes them ordinary keys
 * to the rest of the game: they can be bound to any action in Options >
 * Controls > Key Binds, show up there by name, and are saved in
 * options.txt like any other binding.
 *
 * A direction with nothing bound to it keeps switching hotbar slots.
 */
public final class ScrollKeys {
    public static final InputConstants.Key UP = InputConstants.Type.MOUSE.getOrCreate(100);
    public static final InputConstants.Key DOWN = InputConstants.Type.MOUSE.getOrCreate(101);

    /** How many ticks a notch counts as the key being held down. */
    private static final int HOLD_TICKS = 2;

    private static WeakReference<KeyBindsScreen> bindScreen = new WeakReference<>(null);
    // Trackpads scroll in small fractions; a press happens per whole notch.
    private static double carry;
    private static InputConstants.Key held;
    private static int heldFor;

    private ScrollKeys() {}

    public static String nameOf(InputConstants.Key key) {
        return key == UP ? "Scroll Up" : key == DOWN ? "Scroll Down" : null;
    }

    public static void onBindScreenOpened(KeyBindsScreen screen) {
        bindScreen = new WeakReference<>(screen);
    }

    /** Called for every wheel movement. Returns true when it was used up here. */
    public static boolean onScroll(double amount) {
        if (amount == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = amount > 0 ? UP : DOWN;

        if (!mc.mouseHandler.isMouseGrabbed()) {
            // A screen is open. If it is the key binds screen and it is
            // waiting for a key, the wheel is that key.
            KeyBindsScreen screen = bindScreen.get();
            if (screen == null || screen.selectedKey == null) return false;
            screen.selectedKey.setKey(key);
            screen.selectedKey = null;
            ((KeyBindsScreenAccessor) screen).iceyscroll$list().resetMappingAndUpdateButtons();
            mc.options.save();
            return true;
        }

        if (mc.player == null || !isBound(mc, key)) return false;
        if (Math.signum(carry) != Math.signum(amount)) carry = 0;
        carry += amount;
        int presses = Math.min(4, (int) Math.abs(carry));
        if (presses > 0) {
            carry -= Math.signum(carry) * (int) Math.abs(carry);
            // Let go of the other direction first, so the two never overlap.
            if (held != null && held != key) KeyMapping.set(held, false);
            for (int i = 0; i < presses; i++) KeyMapping.click(key);
            KeyMapping.set(key, true);
            held = key;
            heldFor = 0;
        }
        return true;
    }

    public static void tick() {
        if (held == null) return;
        if (++heldFor < HOLD_TICKS) return;
        KeyMapping.set(held, false);
        held = null;
    }

    private static boolean isBound(Minecraft mc, InputConstants.Key key) {
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (key.equals(((KeyMappingAccessor) mapping).iceyscroll$key())) return true;
        }
        return false;
    }
}
