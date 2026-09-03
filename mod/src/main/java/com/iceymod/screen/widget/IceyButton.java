package com.iceymod.screen.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.SoundManager;

/**
 * Flat, modern replacement for the vanilla button look used across every
 * Icey Client screen.
 *
 * <h2>Why it extends {@link ClickableWidget} and not {@code ButtonWidget}</h2>
 * On 1.21.11 {@code PressableWidget#renderWidget} became {@code final} and a
 * new abstract {@code drawIcon} hook appeared, so a {@code ButtonWidget}
 * subclass is physically unable to replace the vanilla sprite; {@code
 * ButtonWidget} also gained a nested {@code ButtonWidget.Text} type that
 * shadows {@code net.minecraft.text.Text} inside any subclass. Extending
 * {@code ClickableWidget} directly avoids both problems — {@code
 * ClickableWidget#renderWidget} is abstract on 1.21.8 and 1.21.11 alike.
 *
 * <p>The press hook is {@link #playDownSound(SoundManager)}: on both versions
 * {@code ClickableWidget#mouseClicked} calls it (after the interactable /
 * valid-button / hit tests pass) immediately before dispatching to
 * {@code onClick}, whose signature <em>does</em> differ between the two
 * mapping sets — {@code onClick(double,double)} vs {@code onClick(Click,boolean)} —
 * and therefore cannot be overridden portably.
 *
 * <p>All drawing uses plain {@link DrawContext#fill(int, int, int, int, int)}
 * rectangles and {@code drawTextWithShadow}, both stable across versions — no
 * sprite atlases and no {@code fillGradient} (bands are used instead).
 */
public class IceyButton extends ClickableWidget {

    /** Version-independent press callback. */
    @FunctionalInterface
    public interface Action {
        void onPress(IceyButton button);
    }

    // ---- Icey Client palette -------------------------------------------------
    public static final int ACCENT        = 0xFF5BC8F5;
    public static final int ACCENT_SOFT   = 0x405BC8F5;
    public static final int TEXT          = 0xFFE6EDF3;
    public static final int TEXT_MUTED    = 0xFF8B98A8;
    public static final int TEXT_DIM      = 0xFF56616F;
    public static final int CARD          = 0xE0161B22;
    public static final int CARD_HOVER    = 0xF01F2732;
    public static final int BORDER        = 0xFF2A3440;
    public static final int PANEL_BG      = 0xD00C1119;
    public static final int PANEL_BORDER  = 0xFF1D2733;
    public static final int BACKDROP_TOP  = 0xE0070B12;
    public static final int BACKDROP_BOT  = 0xE00B1220;
    public static final int ON_GREEN      = 0xFF4ADE80;
    public static final int ON_GREEN_SOFT = 0x384ADE80;
    public static final int OFF_RED       = 0xFFF87171;
    public static final int OFF_GRAY      = 0xFF6B7280;
    public static final int OFF_GRAY_SOFT = 0x30FFFFFF;
    public static final int DANGER        = 0xFFF87171;
    public static final int DANGER_SOFT   = 0x30F87171;
    public static final int TOP_SHEEN     = 0x14FFFFFF;
    public static final int DIVIDER       = 0xFF1B242F;

    public enum Style {
        /** Neutral dark card. */
        NORMAL,
        /** Primary action (Done) — accent fill + accent border. */
        ACCENT_PRIMARY,
        /** Module enabled — accent tinted card. */
        TOGGLE_ON,
        /** Module disabled — neutral card. */
        TOGGLE_OFF,
        /** Destructive action (Reset to Defaults). */
        DANGER_STYLE,
        /** Category filter chip. */
        CHIP,
        /** No card at all, just the label (page indicator). */
        PLAIN
    }

    private final Action action;

    private Style style = Style.NORMAL;
    private boolean highlighted;

    /** When set the label is drawn left-aligned instead of centered. */
    private String leftText;
    /** Right-aligned value text (settings rows). */
    private String rightText;
    private int rightTextColor = ACCENT;
    /** Right-aligned pill (ON / OFF). */
    private String pillText;
    private int pillFg = ON_GREEN;
    private int pillBg = ON_GREEN_SOFT;
    /** Right-aligned ARGB colour swatch; 0 = none. */
    private int swatch;

    public IceyButton(int x, int y, int width, int height, net.minecraft.text.Text message, Action action) {
        super(x, y, width, height, message);
        this.action = action;
    }

    public static IceyButton of(int x, int y, int w, int h, String label, Action action) {
        return new IceyButton(x, y, w, h, net.minecraft.text.Text.literal(label), action);
    }

    // ---- fluent configuration ------------------------------------------------

    public IceyButton setStyle(Style s) { this.style = s; return this; }
    public Style getStyle() { return style; }

    public IceyButton setHighlighted(boolean b) { this.highlighted = b; return this; }
    public boolean isHighlighted() { return highlighted; }

    public IceyButton setLeftText(String s) { this.leftText = s; return this; }

    public IceyButton setRightText(String s, int color) {
        this.rightText = s;
        this.rightTextColor = color;
        return this;
    }

    public IceyButton setPill(String s, int fg, int bg) {
        this.pillText = s;
        this.pillFg = fg;
        this.pillBg = bg;
        return this;
    }

    public IceyButton clearRight() {
        this.rightText = null;
        this.pillText = null;
        this.swatch = 0;
        return this;
    }

    public IceyButton setSwatch(int argb) { this.swatch = argb; return this; }

    /** Convenience: mark this row/tile as ON or OFF with the standard pill. */
    public IceyButton setToggleState(boolean on) {
        this.style = on ? Style.TOGGLE_ON : Style.TOGGLE_OFF;
        return setPill(on ? "ON" : "OFF",
                on ? ON_GREEN : OFF_GRAY,
                on ? ON_GREEN_SOFT : OFF_GRAY_SOFT);
    }

    // ---- input ---------------------------------------------------------------

    /**
     * Press hook — see the class javadoc. {@code ClickableWidget#mouseClicked}
     * calls this exactly once per accepted click on both 1.21.8 and 1.21.11.
     */
    @Override
    public void playDownSound(SoundManager soundManager) {
        super.playDownSound(soundManager);
        if (action != null) {
            try { action.onPress(this); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    // ---- rendering -----------------------------------------------------------

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        if (w <= 0 || h <= 0) return;

        // Own hover test: the inherited hover flag has moved around between
        // versions, this is stable everywhere.
        boolean hovered = this.active
                && mouseX >= x && mouseX < x + w
                && mouseY >= y && mouseY < y + h;
        boolean lit = hovered || highlighted;

        TextRenderer tr = textRenderer();
        int bg;
        int border;
        int fg;

        switch (style) {
            case ACCENT_PRIMARY -> {
                bg = hovered ? 0x605BC8F5 : ACCENT_SOFT;
                border = ACCENT;
                fg = hovered ? 0xFFFFFFFF : ACCENT;
            }
            case TOGGLE_ON -> {
                bg = hovered ? 0x555BC8F5 : ACCENT_SOFT;
                border = lit ? ACCENT : 0xFF2F6C86;
                fg = 0xFFFFFFFF;
            }
            case TOGGLE_OFF -> {
                bg = hovered ? CARD_HOVER : CARD;
                border = lit ? ACCENT : BORDER;
                fg = hovered ? TEXT : TEXT_MUTED;
            }
            case DANGER_STYLE -> {
                bg = hovered ? 0x45F87171 : DANGER_SOFT;
                border = hovered ? DANGER : 0xFF6E3235;
                fg = hovered ? 0xFFFFFFFF : DANGER;
            }
            case CHIP -> {
                if (highlighted) {
                    bg = ACCENT_SOFT;
                    border = ACCENT;
                    fg = 0xFFFFFFFF;
                } else {
                    bg = hovered ? CARD_HOVER : 0xC0121820;
                    border = hovered ? 0xFF3C4C5C : BORDER;
                    fg = hovered ? TEXT : TEXT_MUTED;
                }
            }
            case PLAIN -> {
                bg = 0;
                border = 0;
                fg = TEXT_MUTED;
            }
            default -> {
                bg = hovered ? CARD_HOVER : CARD;
                border = lit ? ACCENT : BORDER;
                fg = hovered ? 0xFFFFFFFF : TEXT;
            }
        }

        boolean plain = style == Style.PLAIN;
        if (!this.active && !plain) {
            bg = 0x60101720;
            border = 0xFF212A34;
            fg = TEXT_DIM;
        } else if (!this.active) {
            fg = TEXT_DIM;
        }

        if (!plain) {
            drawPanel(context, x, y, w, h, bg, border);
            // subtle top sheen so the card reads as raised
            if (h > 3 && w > 2) context.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_SHEEN);
            // accent rail down the left edge of a keyboard-selected tile
            if (highlighted && style != Style.CHIP && h > 4) {
                context.fill(x + 1, y + 1, x + 2, y + h - 1, ACCENT);
            }
        }

        int textY = y + (h - 8) / 2;
        int cy = y + h / 2;
        int rightEdge = x + w - 6;

        // ---- right-hand adornments (drawn first so the label can be trimmed)
        if (swatch != 0) {
            int sw = Math.min(12, h - 6);
            if (sw > 2) {
                int sx = rightEdge - sw;
                int sy = cy - sw / 2;
                context.fill(sx - 1, sy - 1, sx + sw + 1, sy + sw + 1, 0xFF0A0F16);
                context.fill(sx, sy, sx + sw, sy + sw, 0xFF000000);
                context.fill(sx, sy, sx + sw, sy + sw, swatch);
                rightEdge = sx - 4;
            }
        }
        if (pillText != null && !pillText.isEmpty() && w > 46) {
            int tw = tr.getWidth(pillText);
            int pw = tw + 8;
            int ph = Math.min(11, h - 4);
            int px = rightEdge - pw;
            int py = cy - ph / 2;
            if (this.active) context.fill(px, py, px + pw, py + ph, pillBg);
            context.drawTextWithShadow(tr, pillText, px + 4, cy - 4,
                    this.active ? pillFg : TEXT_DIM);
            rightEdge = px - 4;
        }
        if (rightText != null && !rightText.isEmpty()) {
            int tw = tr.getWidth(rightText);
            int rx = rightEdge - tw;
            context.drawTextWithShadow(tr, rightText, rx, textY,
                    this.active ? rightTextColor : TEXT_DIM);
            rightEdge = rx - 4;
        }

        // ---- label
        String label = leftText != null ? leftText : this.getMessage().getString();
        if (label != null && !label.isEmpty()) {
            if (leftText != null) {
                int maxW = Math.max(0, rightEdge - (x + 7));
                context.drawTextWithShadow(tr, fit(tr, label, maxW), x + 7, textY, fg);
            } else {
                int avail = Math.max(0, w - 10);
                context.drawCenteredTextWithShadow(tr, fit(tr, label, avail), x + w / 2, textY, fg);
            }
        }
    }

    private static String fit(TextRenderer tr, String s, int maxW) {
        if (maxW <= 0) return "";
        if (tr.getWidth(s) <= maxW) return s;
        return tr.trimToWidth(s, Math.max(0, maxW - 6)) + "…";
    }

    // ---- shared drawing helpers ---------------------------------------------

    private static TextRenderer textRenderer() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Filled rect with a 1px inset border. Either colour may be 0 to skip it. */
    public static void drawPanel(DrawContext context, int x, int y, int w, int h, int bg, int border) {
        if (w <= 0 || h <= 0) return;
        if (bg != 0) context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, border);
    }

    /** 1px rectangle outline. */
    public static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        if (color == 0 || w <= 0 || h <= 0) return;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * Vertical gradient emulated with horizontal bands — {@code fillGradient}
     * is not guaranteed to keep the same signature between mapping sets.
     */
    public static void drawVerticalGradient(DrawContext context, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int bands = Math.min(32, Math.max(1, h));
        for (int i = 0; i < bands; i++) {
            int y0 = y + (int) ((long) h * i / bands);
            int y1 = y + (int) ((long) h * (i + 1) / bands);
            if (y1 <= y0) continue;
            float t = bands == 1 ? 0f : (float) i / (float) (bands - 1);
            context.fill(x, y0, x + w, y1, lerpColor(top, bottom, t));
        }
    }

    /** Full-screen Icey backdrop: vertical gradient plus a soft accent glow band on top. */
    public static void drawGradientBackdrop(DrawContext context, int width, int height) {
        drawVerticalGradient(context, 0, 0, width, height, BACKDROP_TOP, BACKDROP_BOT);
        // faint accent halo behind the header
        int glowH = Math.min(90, Math.max(0, height / 4));
        drawVerticalGradient(context, 0, 0, width, glowH, 0x1A5BC8F5, 0x005BC8F5);
        // hairline at the very top
        context.fill(0, 0, width, 1, 0x405BC8F5);
    }

    /** Standard content panel: translucent body, 1px border, top sheen. */
    public static void drawContentPanel(DrawContext context, int x, int y, int w, int h) {
        drawPanel(context, x, y, w, h, PANEL_BG, PANEL_BORDER);
        if (h > 3 && w > 2) context.fill(x + 1, y + 1, x + w - 1, y + 2, 0x10FFFFFF);
    }

    /** Horizontal 1px separator. */
    public static void drawDivider(DrawContext context, int x, int y, int w) {
        context.fill(x, y, x + w, y + 1, DIVIDER);
    }

    /** Small dark pill with a text label; returns its width. */
    public static int drawTag(DrawContext context, String text, int x, int y, int fg, int bg) {
        TextRenderer tr = textRenderer();
        int tw = tr.getWidth(text);
        context.fill(x, y, x + tw + 8, y + 12, bg);
        context.drawTextWithShadow(tr, text, x + 4, y + 2, fg);
        return tw + 8;
    }

    public static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
