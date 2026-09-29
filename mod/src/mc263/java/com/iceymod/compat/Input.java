package com.iceymod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import org.lwjgl.sdl.SDLMouse;

/**
 * ERA: keyboard/mouse polling and the odd entity call whose shape drifts
 * between versions. 26.3 moved from GLFW to SDL: key codes are SDL
 * scancodes (InputConstants.KEY_*), there is no window handle to poll
 * with, and mouse buttons come from SDL's state mask.
 */
public final class Input {
    private Input() {}
    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;

    public static boolean keyDown(int code) {
        try { return InputConstants.isKeyDown(code); } catch (Throwable t) { return false; }
    }

    public static boolean mouseDown(int button) {
        try {
            int mask = SDLMouse.nSDL_GetMouseState(0L, 0L);
            int bit = button == InputConstants.MOUSE_BUTTON_LEFT ? SDLMouse.SDL_BUTTON_LMASK
                    : button == InputConstants.MOUSE_BUTTON_RIGHT ? SDLMouse.SDL_BUTTON_RMASK
                    : SDLMouse.SDL_BUTTON_MMASK;
            return (mask & bit) != 0;
        } catch (Throwable t) { return false; }
    }

    public static void swingMainHand(Player p) { p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false); }
}
