package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import com.iceymod.compat.MC;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/**
 * The Options page of the scene menu: the settings people change most as
 * sliders and switches right here, and a tile for each of the game's own
 * detailed settings screens (they open over the menu and come back to it).
 */
final class OptionsPage {
    private final SceneMenuScreen s;
    private String dragging;
    private float scroll;

    OptionsPage(SceneMenuScreen s) { this.s = s; }

    boolean back() { return false; }
    void scroll(float step) { scroll -= step; }

    /** How tall the pane wants to be for what is on this page. */
    float wantH() { return contentH <= 0 ? 1000 : 64 + contentH + 10; }

    private void open(Supplier<Screen> screen) {
        s.leave(() -> {
            try { MC.setScreen(s.mc(), screen.get()); }
            catch (Throwable t) { MC.setScreen(s.mc(), s.options()); }
        }, 0.14f);
    }

    private float contentH;

    void draw(Gfx g, float x, float y, float w, float h, float al, int accent, float delta) {
        Minecraft mc = s.mc();
        var o = mc.options;
        s.pageHead(g, x, y, Icons.SETTINGS, "Options", "The settings you change most, and the game's own", accent, al);
        float aw = s.buttonW(Icons.MODS, "All options");
        if (s.button(g, x + w - 18 - aw, y + 16, aw, 24, Icons.MODS, "All options", 0, al)) { open(() -> s.options()); return; }
        g.fill(Math.round(x + 14), Math.round(y + 56), Math.round(x + w - 14), Math.round(y + 57), SceneMenuScreen.a(0x22FFFFFF, al));

        boolean wide = w > 520;
        float top = y + 64, bottom = y + h - 8;
        float max = Math.max(0, contentH - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll));
        float colW = wide ? (float) Math.floor((w - 36 - 12) * 0.54f) : w - 36;
        float cx = x + 18, cy = top - scroll;
        if (!s.down) dragging = null;
        clipTop = top; clipBottom = bottom;

        g.enableScissor(Math.round(x + 4), Math.round(top), Math.round(x + w - 4), Math.round(bottom));
        try {
            // ── What is on screen ──
            float y0 = cy;
            try {
                cy = group(g, Icons.MONITOR, "DISPLAY", cx, cy, colW, 6 * 25 + 4, accent, al);
                float ix = cx + 10, iw = colW - 20, ry = cy;
                ry = slider(g, "fov", Icons.EYE, "Field of view", ix, ry, iw, o.fov().get(), 30, 110, 1, v -> o.fov().set(v), v -> v == 70 ? "Normal" : v >= 110 ? "Quake Pro" : String.valueOf(v), al, accent, true);
                ry = slider(g, "rd", Icons.MOUNTAIN, "Render distance", ix, ry, iw, o.renderDistance().get(), 2, 32, 1, v -> o.renderDistance().set(v), v -> v + " chunks", al, accent, false);
                ry = slider(g, "fps", Icons.GAUGE, "Max frame rate", ix, ry, iw, Math.min(260, o.framerateLimit().get()), 10, 260, 10, v -> o.framerateLimit().set(v), v -> v >= 260 ? "Unlimited" : v + " fps", al, accent, true);
                int gamma = (int) Math.round(o.gamma().get() * 100);
                ry = slider(g, "gamma", Icons.SUN, "Brightness", ix, ry, iw, gamma, 0, 100, 1, v -> o.gamma().set(v / 100.0), v -> v == 0 ? "Moody" : v >= 100 ? "Bright" : v + "%", al, accent, true);
                ry = toggle(g, Icons.FULLSCREEN, "Fullscreen", ix, ry, iw, o.fullscreen().get(), al, accent, () -> { o.fullscreen().set(!o.fullscreen().get()); o.save(); });
                ry = toggle(g, Icons.VSYNC, "VSync", ix, ry, iw, o.enableVsync().get(), al, accent, () -> { o.enableVsync().set(!o.enableVsync().get()); o.save(); });
                cy = ry + 14;

                // ── What is heard ──
                cy = group(g, Icons.VOLUME, "SOUND", cx, cy, colW, 2 * 25 + 4, accent, al);
                ry = cy;
                OptionInstance<Double> master = o.getSoundSourceOptionInstance(SoundSource.MASTER), music = o.getSoundSourceOptionInstance(SoundSource.MUSIC);
                ry = slider(g, "vol", Icons.VOLUME, "Master volume", ix, ry, iw, (int) Math.round(master.get() * 100), 0, 100, 1, v -> master.set(v / 100.0), v -> v == 0 ? "Off" : v + "%", al, accent, true);
                ry = slider(g, "mus", Icons.MUSIC, "Music", ix, ry, iw, (int) Math.round(music.get() * 100), 0, 100, 1, v -> music.set(v / 100.0), v -> v == 0 ? "Off" : v + "%", al, accent, true);
                cy = ry + 14;

                // ── The menus ──
                cy = group(g, Icons.LAYERS, "MENUS", cx, cy, colW, 25 + 4, accent, al);
                ry = slider(g, "blur", Icons.BLUR, "Blur behind menus", ix, cy, iw, o.getMenuBackgroundBlurriness(), 0, 10, 1, v -> o.menuBackgroundBlurriness().set(v), v -> v == 0 ? "Off" : String.valueOf(v), al, accent, true);
                cy = ry + 10;
            } catch (Throwable t) {
                s.text(g, "These settings are in All options on this game version.", cx, cy + 4, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
                cy += 24;
            }
            float leftEnd = cy;

            // ── The game's own settings screens ──
            float tx = wide ? cx + colW + 12 : cx, tw = wide ? w - 36 - colW - 12 : colW;
            float ty = wide ? y0 : leftEnd + 6;
            s.text(g, "MORE SETTINGS", tx + 2, ty + 1, 1f, SceneMenuScreen.a(0xFF8E9AB0, al));
            ty += 17;
            String[] names = { "Video", "Controls", "Sound", "Language", "Resource packs", "Skin", "Chat", "Accessibility", "Online" };
            String[] hints = { "Graphics, shaders, GUI scale", "Keys and mouse", "Every volume", "The game's language", "Textures and packs", "Cape and skin parts", "Chat look and filters", "Narrator, contrast, motion", "Realms and server lists" };
            char[] icons = { Icons.VIDEO, Icons.CONTROLS, Icons.VOLUME, Icons.LANGUAGE, Icons.PACKS, Icons.SKIN, Icons.CHAT, Icons.ACCESSIBILITY, Icons.ONLINE };
            float tileH = 38;
            for (int i = 0; i < names.length; i++) {
                float bx = tx, by = ty + i * (tileH + 6);
                if (by + tileH >= top && by <= bottom) {
                    boolean hov = s.over(bx, by, tw, tileH) && !s.accountOpen && s.my >= top && s.my < bottom;
                    float hv = s.ease("opt" + i, hov, 16f);
                    s.rect(g, bx, by, tw, tileH, 8, SceneMenuScreen.a(0xFFFFFFFF, (0.055f + 0.075f * hv) * al));
                    s.ring(g, bx, by, tw, tileH, 8, SceneMenuScreen.a(SceneMenuScreen.mix(0x1CFFFFFF, 0xFF000000 | accent, hv * 0.8f), al));
                    boolean has = s.iconsUsable();
                    if (has) {
                        s.rect(g, bx + 7, by + 7, 24, 24, 6, SceneMenuScreen.a(SceneMenuScreen.mix(0x1EFFFFFF, SceneMenuScreen.withAlpha(accent, 0x50), hv), al));
                        s.icon(g, icons[i], bx + 19, by + 19, 13, SceneMenuScreen.a(SceneMenuScreen.mix(0xFFC8D2E2, 0xFFFFFFFF, hv), al));
                    }
                    float lx = bx + (has ? 40 : 11);
                    s.text(g, "§l" + s.fit(names[i], tw - (lx - bx) - 22, 1f), lx, by + 8, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                    s.text(g, s.fit(hints[i], tw - (lx - bx) - 22, 1f), lx, by + 21, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
                    if (has) s.icon(g, Icons.CHEVRON, bx + tw - 13 + hv * 2f, by + tileH / 2f, 11, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF8E9AB0, 0xFF000000 | accent, hv), al));
                    else s.textR(g, "›", bx + tw - 10, by + tileH / 2f - 4, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
                    if (hov && s.clicked) {
                        s.click();
                        final int which = i;
                        open(() -> switch (which) {
                            case 0 -> new VideoSettingsScreen(s, mc, o);
                            case 1 -> new ControlsScreen(s, o);
                            case 2 -> new SoundOptionsScreen(s, o);
                            case 3 -> new LanguageSelectScreen(s, o, mc.getLanguageManager());
                            case 4 -> new PackSelectionScreen(mc.getResourcePackRepository(), repo -> { o.updateResourcePacks(repo); MC.setScreen(mc, s); }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title"));
                            case 5 -> new SkinCustomizationScreen(s, o);
                            case 6 -> new ChatOptionsScreen(s, o);
                            case 7 -> new AccessibilityOptionsScreen(s, o);
                            default -> new OnlineOptionsScreen(s, o);
                        });
                        s.swallowClick();
                        return;
                    }
                }
            }
            float rightEnd = ty + names.length * (tileH + 6);
            contentH = Math.max(leftEnd, rightEnd) + scroll - top + 4;
        } finally { g.disableScissor(); }
        if (max > 0) {
            float view = bottom - top, bar = Math.max(18, view * view / contentH);
            s.rect(g, x + w - 7, top + (view - bar) * (scroll / max), 3, bar, 1, SceneMenuScreen.a(0x66FFFFFF, al));
        }
    }

    private float clipTop, clipBottom;

    /** The frame of a group of settings with its name on top; returns where its first row starts. */
    private float group(Gfx g, char icon, String name, float x, float y, float w, float bodyH, int accent, float al) {
        boolean has = s.iconsUsable();
        if (has) s.icon(g, icon, x + 8, y + 5, 11, SceneMenuScreen.a(SceneMenuScreen.lighter(accent), al));
        s.text(g, name, x + (has ? 18 : 2), y + 1, 1f, SceneMenuScreen.a(SceneMenuScreen.lighter(accent), al));
        s.rect(g, x, y + 15, w, bodyH + 8, 8, SceneMenuScreen.a(0x0FFFFFFF, al));
        s.ring(g, x, y + 15, w, bodyH + 8, 8, SceneMenuScreen.a(0x1AFFFFFF, al));
        return y + 21;
    }

    private interface Apply { void set(int v); }
    private interface Label { String of(int v); }

    /** A slider. {@code live}: applies while dragging; otherwise when the mouse lets go (render distance rebuilds the world). */
    private float slider(Gfx g, String key, char icon, String name, float x, float y, float w, int value, int min, int max, int step, Apply apply, Label label, float al, int accent, boolean live) {
        boolean has = s.iconsUsable();
        float nameX = x + (has ? 19 : 0);
        float trackX = x + Math.min(132, w * 0.42f), trackW = w - (trackX - x) - 66, ty = y + 10;
        Integer pending = held.get(key);
        // A value outside the slider's range (full brightness sets 1500%) stops at its end.
        int shown = Math.max(min, Math.min(max, pending != null ? pending : value));
        boolean inClip = s.my >= clipTop && s.my < clipBottom;
        boolean hov = inClip && s.over(trackX - 6, y + 1, trackW + 12, 22) && !s.accountOpen;
        if (hov && s.clicked) { dragging = key; s.swallowClick(); }
        if (key.equals(dragging)) {
            float t = Math.max(0f, Math.min(1f, (s.mx - trackX) / trackW));
            int v = min + Math.round(t * (max - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            if (v != shown) {
                shown = v;
                if (live) { try { apply.set(v); } catch (Throwable ignored) {} }
                else held.put(key, v);
            }
        } else if (pending != null) {
            held.remove(key);
            try { apply.set(pending); s.mc().options.save(); } catch (Throwable ignored) {}
        } else if (key.equals(lastDragged)) {
            lastDragged = null;
            try { s.mc().options.save(); } catch (Throwable ignored) {}
        }
        if (key.equals(dragging)) lastDragged = key;
        float hv = s.ease("sl" + key, hov || key.equals(dragging), 16f);
        float t = Math.max(0f, Math.min(1f, (shown - min) / (float) (max - min)));
        if (has) s.icon(g, icon, x + 7, y + 12, 12, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF8E9AB0, 0xFFFFFFFF, hv), al));
        s.text(g, s.fit(name, trackX - nameX - 6, 1f), nameX, y + 8, 1f, SceneMenuScreen.a(0xFFE6ECF6, al));
        s.rect(g, trackX, ty, trackW, 5, 2, SceneMenuScreen.a(0x80000000, al));
        if (t > 0.01f) s.rect(g, trackX, ty, Math.max(5, trackW * t), 5, 2, SceneMenuScreen.a(0xFF000000 | accent, al));
        float kx = trackX + trackW * t;
        if (hv > 0.02f) s.rect(g, kx - 8, ty - 6, 16, 17, 8, SceneMenuScreen.a(SceneMenuScreen.withAlpha(accent, 0x55), hv * al));
        s.rect(g, kx - 5, ty - 3, 10, 11, 5, SceneMenuScreen.a(0xFFFFFFFF, al));
        s.textR(g, label.of(shown), x + w, y + 8, 1f, SceneMenuScreen.a(SceneMenuScreen.mix(0xFFB8C4D6, 0xFFFFFFFF, hv), al));
        return y + 25;
    }
    private final java.util.Map<String, Integer> held = new java.util.HashMap<>();
    private String lastDragged;

    private float toggle(Gfx g, char icon, String name, float x, float y, float w, boolean on, float al, int accent, Runnable flip) {
        boolean has = s.iconsUsable();
        boolean inClip = s.my >= clipTop && s.my < clipBottom;
        boolean hov = inClip && s.over(x - 4, y + 1, w + 8, 23) && !s.accountOpen;
        float hv = s.ease("tg" + name, hov, 16f), k = s.ease("tgon" + name, on, 14f);
        if (hv > 0.02f) s.rect(g, x - 4, y + 1, w + 8, 23, 6, SceneMenuScreen.a(0x14FFFFFF, hv * al));
        if (has) s.icon(g, icon, x + 7, y + 12, 12, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF8E9AB0, 0xFFFFFFFF, hv), al));
        s.text(g, name, x + (has ? 19 : 0), y + 8, 1f, SceneMenuScreen.a(0xFFE6ECF6, al));
        float sx = x + w - 30, sy = y + 6;
        s.rect(g, sx, sy, 30, 14, 7, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF3A4458, 0xFF000000 | accent, k), al));
        s.rect(g, sx + 2 + k * 16, sy + 2, 10, 10, 5, SceneMenuScreen.a(0xFFFFFFFF, al));
        if (hov && s.clicked) { s.click(); s.swallowClick(); try { flip.run(); } catch (Throwable ignored) {} }
        return y + 25;
    }
}
