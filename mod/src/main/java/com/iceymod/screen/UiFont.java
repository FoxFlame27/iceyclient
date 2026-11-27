package com.iceymod.screen;

import com.iceymod.IceyMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The smooth typeface of the scene menu: Outfit, the one the launcher
 * uses, shipped with the mod (SIL Open Font License, see
 * assets/iceymod/font/outfit-OFL.txt) in a regular and a bold cut.
 * Letters it does not have come from Minecraft's own font. If the
 * typeface did not load, everything falls back to the game's font.
 */
final class UiFont {
    private UiFont() {}

    private static final Identifier REGULAR = Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "ui");
    private static final Identifier BOLD = Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "ui_bold");
    private static Style regular, bold;
    private static Boolean usable;
    private static final Map<String, Component> CACHE = new HashMap<>();

    static Style style(boolean isBold) {
        if (regular == null) {
            regular = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(REGULAR));
            bold = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(BOLD));
        }
        return isBold ? bold : regular;
    }

    /** False when the typeface is missing: then every letter has the same width (the "missing" box). */
    static boolean usable(Font font) {
        if (usable == null) {
            try {
                int narrow = font.width(Component.literal("iiii").withStyle(style(false)));
                int wide = font.width(Component.literal("WWWW").withStyle(style(false)));
                usable = wide > narrow + 4;
            } catch (Throwable t) { usable = false; }
            if (!usable) System.out.println("[IceyMod] menu typeface not loaded, using the game's font");
        }
        return usable;
    }

    /** Text to draw; a leading §l asks for the bold cut. */
    static Component of(Font font, String text) {
        Component c = CACHE.get(text);
        if (c != null) return c;
        if (CACHE.size() > 3000) CACHE.clear();
        boolean isBold = text.startsWith("§l");
        if (usable(font)) c = Component.literal(isBold ? text.substring(2) : text).withStyle(style(isBold));
        else c = Component.literal(text);
        CACHE.put(text, c);
        return c;
    }
}
