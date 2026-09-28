package com.iceymod.hud.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iceymod.compat.Gfx;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.EnumSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Scroll Keybinds: scroll up and scroll down each act as a key for one
 * chosen action. One notch of the wheel is one press. Left on "Hotbar",
 * a direction keeps doing what it does in the vanilla game.
 *
 * Switched on in the launcher (Settings → Mods); the actions are picked
 * here, in the in-game menu.
 */
public class ScrollBindsModule extends HudModule {
    private static final String[] ACTIONS = {
        "Hotbar", "Jump", "Attack", "Use", "Sneak", "Sprint", "Drop", "Swap Hands", "Pick Block"
    };

    private static ScrollBindsModule instance;

    public final EnumSetting scrollUp = addSetting(new EnumSetting("scrollUp", "Scroll Up", ACTIONS, 0));
    public final EnumSetting scrollDown = addSetting(new EnumSetting("scrollDown", "Scroll Down", ACTIONS, 0));
    public final IntSetting holdTicks = addSetting(new IntSetting("holdTicks", "Press Length (ticks)", 2, 1, 10));

    // Trackpads scroll in small fractions; a press happens per whole notch.
    private double carry;
    private KeyMapping held;
    private int heldFor;

    public ScrollBindsModule() {
        super("scrollbinds", "Scroll Keybinds", 0, 0);
        instance = this;
    }

    /** True when the launcher has Scroll Keybinds switched on (or the mod runs without the launcher). */
    public static boolean isSwitchedOn() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("iceymod-launcher.json");
            if (!Files.exists(p)) return true;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("scrollBindsEnabled") && o.get("scrollBindsEnabled").getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    /** Called for every wheel movement. Returns true when the movement was used up here. */
    public static boolean onScroll(double amount) {
        ScrollBindsModule m = instance;
        if (m == null || !m.isEnabled() || amount == 0) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || com.iceymod.compat.MC.screen(client) != null) return false;
        if (!client.mouseHandler.isMouseGrabbed()) return false;

        boolean up = amount > 0;
        KeyMapping target = m.mappingFor(client, (up ? m.scrollUp : m.scrollDown).get());
        if (target == null) return false; // "Hotbar": leave it to the game

        if (Math.signum(m.carry) != Math.signum(amount)) m.carry = 0;
        m.carry += amount;
        int presses = (int) Math.abs(m.carry);
        if (presses > 0) {
            m.carry -= Math.signum(m.carry) * presses;
            m.press(target, Math.min(presses, 4));
        }
        return true;
    }

    private KeyMapping mappingFor(Minecraft client, int action) {
        var o = client.options;
        return switch (action) {
            case 1 -> o.keyJump;
            case 2 -> o.keyAttack;
            case 3 -> o.keyUse;
            case 4 -> o.keyShift;
            case 5 -> o.keySprint;
            case 6 -> o.keyDrop;
            case 7 -> o.keySwapOffhand;
            case 8 -> o.keyPickItem;
            default -> null;
        };
    }

    private void press(KeyMapping mapping, int times) {
        KeyMappingAccessor access = (KeyMappingAccessor) mapping;
        access.icey$setClickCount(access.icey$getClickCount() + times);
        mapping.setDown(true);
        held = mapping;
        heldFor = 0;
    }

    @Override
    public void tick() {
        if (held == null) return;
        if (++heldFor < holdTicks.get()) return;
        // Let go, unless the real key for this action is being held.
        if (!physicallyDown(held)) held.setDown(false);
        held = null;
    }

    private static boolean physicallyDown(KeyMapping mapping) {
        try {
            InputConstants.Key key = ((KeyMappingAccessor) mapping).icey$getKey();
            long handle = Minecraft.getInstance().getWindow().handle();
            if (key.getType() == InputConstants.Type.MOUSE) {
                return GLFW.glfwGetMouseButton(handle, key.getValue()) == GLFW.GLFW_PRESS;
            }
            if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0) {
                return GLFW.glfwGetKey(handle, key.getValue()) == GLFW.GLFW_PRESS;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(Gfx context, Minecraft client) {}
}
