package com.iceysmp;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * Display branding for the mod's user-facing chat / GUI labels.
 * The internal mod_id stays {@code iceysmp} (changing that would force
 * a rename of the config file, data dir, and Fabric mod metadata which
 * would break existing servers' state). What this class controls is
 * purely the in-game label players see.
 *
 * <p>Per user: "from iceysmp to AttributeSMP in nice color ok? purple
 * and black a nice fade." {@link #prefix()} returns a bracketed bold
 * "AttributeSMP" with each character HEX-colored to interpolate from
 * {@code 0xC040FF} (bright purple) at the left bracket to {@code 0x1A0033}
 * (near-black with a purple tint so the end is still readable on dark
 * chat backgrounds) at the right bracket.
 */
public final class Brand {
    private Brand() {}

    public static final String NAME = "AttributeSMP";

    /** Bright purple. */
    private static final int FADE_START = 0xC040FF;
    /** Very dark purple — readable on the default chat background. */
    private static final int FADE_END   = 0x1A0033;

    /** The labeled prefix "[AttributeSMP] " with a purple→black gradient.
     *  Trailing space is included so the call site can just append the
     *  rest of the message text. */
    public static MutableText prefix() {
        return gradient("[" + NAME + "]", FADE_START, FADE_END, true)
                .append(Text.literal(" ").setStyle(Style.EMPTY.withItalic(false)));
    }

    /** Build a Text with a per-character RGB gradient. */
    public static MutableText gradient(String text, int startRgb, int endRgb, boolean bold) {
        MutableText out = Text.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = (n > 1) ? (float) i / (n - 1) : 0f;
            int r = lerp((startRgb >> 16) & 0xFF, (endRgb >> 16) & 0xFF, t);
            int g = lerp((startRgb >> 8)  & 0xFF, (endRgb >> 8)  & 0xFF, t);
            int b = lerp(startRgb         & 0xFF, endRgb         & 0xFF, t);
            int rgb = (r << 16) | (g << 8) | b;
            out.append(Text.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(rgb))
                            .withBold(bold)
                            .withItalic(false)));
        }
        return out;
    }

    private static int lerp(int a, int b, float t) {
        return Math.max(0, Math.min(255, (int) (a * (1 - t) + b * t)));
    }

    /** Convenience: prefix() followed by a plain-text body. The body is
     *  rendered with section-code parsing intact, so callers can keep
     *  using §c, §a, etc. inside the body string. */
    public static MutableText say(String body) {
        return prefix().append(Text.literal(body));
    }
}
