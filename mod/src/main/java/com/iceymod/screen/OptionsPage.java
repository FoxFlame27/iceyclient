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

    private void open(Supplier<Screen> screen) {
        s.leave(() -> {
            try { MC.setScreen(s.mc(), screen.get()); }
            catch (Throwable t) { MC.setScreen(s.mc(), s.options()); }
        }, 0.14f);
    }

    void draw(Gfx g, float x, float y, float w, float h, float al, int accent, float delta) {
        Minecraft mc = s.mc();
        var o = mc.options;
        s.text(g, "§lOptions", x + 18, y + 16, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
        float aw = s.textW("§lAll options", 1f) + 22;
        if (s.pill(g, x + w - 18 - aw, y + 13, aw, 22, "All options", 0xFF334058, false, al)) { open(() -> s.options()); return; }
        g.fill(Math.round(x + 14), Math.round(y + 46), Math.round(x + w - 14), Math.round(y + 47), SceneMenuScreen.a(0x26FFFFFF, al));

        boolean wide = w > 470;
        float colW = wide ? (w - 18 * 2 - 22) * 0.56f : w - 36;
        float cx = x + 18, top = y + 58, bottom = y + h - 10;
        float cy = top - (wide ? 0 : scroll);
        if (!s.down) dragging = null;

        // ── the settings people change most ──
        s.text(g, "QUICK SETTINGS", cx, cy, 1f, SceneMenuScreen.a(accent, al));
        cy += 16;
        try {
            cy = slider(g, "fov", "Field of view", cx, cy, colW, o.fov().get(), 30, 110, 1, v -> o.fov().set(v), v -> v == 70 ? "Normal" : v >= 110 ? "Quake Pro" : String.valueOf(v), al, accent, true);
            cy = slider(g, "rd", "Render distance", cx, cy, colW, o.renderDistance().get(), 2, 32, 1, v -> o.renderDistance().set(v), v -> v + " chunks", al, accent, false);
            cy = slider(g, "fps", "Max frame rate", cx, cy, colW, Math.min(260, o.framerateLimit().get()), 10, 260, 10, v -> o.framerateLimit().set(v), v -> v >= 260 ? "Unlimited" : v + " fps", al, accent, true);
            int gamma = (int) Math.round(o.gamma().get() * 100);
            cy = slider(g, "gamma", "Brightness", cx, cy, colW, gamma, 0, 100, 1, v -> o.gamma().set(v / 100.0), v -> v == 0 ? "Moody" : v >= 100 ? "Bright" : v + "%", al, accent, true);
            OptionInstance<Double> master = o.getSoundSourceOptionInstance(SoundSource.MASTER), music = o.getSoundSourceOptionInstance(SoundSource.MUSIC);
            cy = slider(g, "vol", "Master volume", cx, cy, colW, (int) Math.round(master.get() * 100), 0, 100, 1, v -> master.set(v / 100.0), v -> v == 0 ? "Off" : v + "%", al, accent, true);
            cy = slider(g, "mus", "Music", cx, cy, colW, (int) Math.round(music.get() * 100), 0, 100, 1, v -> music.set(v / 100.0), v -> v == 0 ? "Off" : v + "%", al, accent, true);
            cy = slider(g, "blur", "Blur behind menus", cx, cy, colW, o.getMenuBackgroundBlurriness(), 0, 10, 1, v -> o.menuBackgroundBlurriness().set(v), v -> v == 0 ? "Off" : String.valueOf(v), al, accent, true);
            float half = (colW - 10) / 2f;
            if (toggle(g, "Fullscreen", cx, cy, half, o.fullscreen().get(), al, accent)) { o.fullscreen().set(!o.fullscreen().get()); o.save(); }
            if (toggle(g, "VSync", cx + half + 10, cy, half, o.enableVsync().get(), al, accent)) { o.enableVsync().set(!o.enableVsync().get()); o.save(); }
            cy += 34;
        } catch (Throwable t) {
            s.text(g, "These settings are in All options on this game version.", cx, cy, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
            cy += 20;
        }

        // ── the game's own settings screens ──
        float tx = wide ? cx + colW + 22 : cx, tw = wide ? w - 36 - colW - 22 : colW;
        float ty = wide ? top : cy + 10;
        s.text(g, "MORE", tx, ty, 1f, SceneMenuScreen.a(accent, al));
        ty += 16;
        String[] names = { "Video", "Controls", "Sound", "Language", "Resource packs", "Skin", "Chat", "Accessibility", "Online" };
        String[] hints = { "Graphics, shaders, GUI scale", "Keys and mouse", "Every volume", "The game's language", "Textures and packs", "Cape and skin parts", "Chat look and filters", "Narrator, contrast, motion", "Realms and server lists" };
        int cols = tw >= 210 ? 2 : 1;
        float tileW = (tw - (cols - 1) * 8) / cols;
        boolean roomy = tileW >= 190;                       // room for the second line
        float tileH = roomy ? 34 : 26;
        for (int i = 0; i < names.length; i++) {
            float bx = tx + (i % cols) * (tileW + 8), by = ty + (i / cols) * (tileH + 6);
            if (by + tileH < y + 48 || by > bottom) continue;
            boolean hov = s.over(bx, by, tileW, tileH) && !s.accountOpen && s.my > y + 48;
            float hv = s.ease("opt" + i, hov, 16f);
            s.rect(g, bx, by, tileW, tileH, 6, SceneMenuScreen.a(0xFFFFFFFF, (0.06f + 0.08f * hv) * al));
            if (hv > 0.02f) s.ring(g, bx, by, tileW, tileH, 6, SceneMenuScreen.a(accent, hv * al * 0.8f));
            s.text(g, "§l" + s.fit(names[i], tileW - 26, 1f), bx + 10, by + (roomy ? 7 : 9), 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
            if (roomy) s.text(g, s.fit(hints[i], tileW - 30, 1f), bx + 10, by + 19, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
            s.textR(g, "›", bx + tileW - 10, by + tileH / 2f - 4, 1f, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF9FB0C8, accent, hv), al));
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

    private interface Apply { void set(int v); }
    private interface Label { String of(int v); }

    /** A slider. {@code live}: applies while dragging; otherwise when the mouse lets go (render distance rebuilds the world). */
    private float slider(Gfx g, String key, String name, float x, float y, float w, int value, int min, int max, int step, Apply apply, Label label, float al, int accent, boolean live) {
        float trackX = x + 118, trackW = w - 118 - 64, ty = y + 9;
        Integer pending = held.get(key);
        // A value outside the slider's range (full brightness sets 1500%) stops at its end.
        int shown = Math.max(min, Math.min(max, pending != null ? pending : value));
        boolean hov = s.over(trackX - 6, y, trackW + 12, 22) && !s.accountOpen;
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
        s.text(g, name, x, y + 7, 1f, SceneMenuScreen.a(0xFFE6ECF6, al));
        s.rect(g, trackX, ty, trackW, 5, 2, SceneMenuScreen.a(0x66000000, al));
        s.rect(g, trackX, ty, Math.max(4, trackW * t), 5, 2, SceneMenuScreen.a(accent, al));
        float kx = trackX + trackW * t;
        s.rect(g, kx - 5 - hv, ty - 3 - hv, 10 + hv * 2, 11 + hv * 2, 5, SceneMenuScreen.a(0xFFFFFFFF, al));
        s.textR(g, label.of(shown), x + w, y + 7, 1f, SceneMenuScreen.a(0xFFB8C4D6, al));
        return y + 25;
    }
    private final java.util.Map<String, Integer> held = new java.util.HashMap<>();
    private String lastDragged;

    private boolean toggle(Gfx g, String name, float x, float y, float w, boolean on, float al, int accent) {
        boolean hov = s.over(x, y, w, 26) && !s.accountOpen;
        float hv = s.ease("tg" + name, hov, 16f), k = s.ease("tgon" + name, on, 14f);
        s.rect(g, x, y, w, 26, 6, SceneMenuScreen.a(0xFFFFFFFF, (0.06f + 0.06f * hv) * al));
        s.text(g, name, x + 10, y + 9, 1f, SceneMenuScreen.a(0xFFE6ECF6, al));
        float sx = x + w - 38, sy = y + 7;
        s.rect(g, sx, sy, 28, 12, 6, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF3A4458, accent, k), al));
        s.rect(g, sx + 2 + k * 16, sy + 2, 8, 8, 4, SceneMenuScreen.a(0xFFFFFFFF, al));
        if (hov && s.clicked) { s.click(); s.swallowClick(); return true; }
        return false;
    }
}
