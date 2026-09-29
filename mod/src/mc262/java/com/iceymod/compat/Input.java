package com.iceymod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/** ERA: keyboard/mouse polling and the odd entity call whose shape drifts between versions (26.2: GLFW). */
public final class Input {
    private Input() {}
    /** The key type for a plain keyboard key. */
    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

    /** Is the key with this InputConstants.KEY_* code held right now? */
    public static boolean keyDown(int code) {
        try {
            long handle = Minecraft.getInstance().getWindow().handle();
            return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
        } catch (Throwable t) { return false; }
    }

    /** Is the mouse button with this InputConstants.MOUSE_BUTTON_* code held right now? */
    public static boolean mouseDown(int button) {
        try {
            long handle = Minecraft.getInstance().getWindow().handle();
            return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
        } catch (Throwable t) { return false; }
    }

    public static void swingMainHand(Player p) { p.swing(InteractionHand.MAIN_HAND); }
}
