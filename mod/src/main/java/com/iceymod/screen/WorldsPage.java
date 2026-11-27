package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import com.iceymod.compat.MC;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** The Worlds page of the scene menu: the saved worlds, newest first, to play or delete, and a way to make a new one. */
final class WorldsPage {
    private final SceneMenuScreen s;
    private float scroll;
    private String confirmDelete;
    private long lastClick;
    private int lastIndex = -1;

    WorldsPage(SceneMenuScreen s) { this.s = s; }

    boolean back() {
        if (confirmDelete != null) { confirmDelete = null; return true; }
        return false;
    }

    void scroll(float step) { scroll -= step; }

    /** The game's own "create world" screen (how it is opened changed between game versions). */
    private void create() {
        s.leave(() -> {
            Minecraft mc = s.mc();
            try {
                for (Method m : CreateWorldScreen.class.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 2 || m.getParameterTypes()[0] != Minecraft.class) continue;
                    Class<?> second = m.getParameterTypes()[1];
                    if (second == Screen.class) { m.invoke(null, mc, s); return; }
                    if (second == Runnable.class) { m.invoke(null, mc, (Runnable) () -> MC.setScreen(mc, s)); return; }
                }
            } catch (Throwable ignored) {}
            MC.setScreen(mc, new SelectWorldScreen(s));
        }, 0.14f);
    }

    private void delete(String id) {
        try (var access = s.mc().getLevelSource().createAccess(id)) {
            access.deleteLevel();
        } catch (Throwable t) {
            s.toast("Could not delete the world", String.valueOf(t.getMessage()));
        }
        confirmDelete = null;
        try { s.loadWorlds(); } catch (Throwable ignored) {}
    }

    void draw(Gfx g, float x, float y, float w, float h, float al, int accent, float delta) {
        s.text(g, "§lWorlds", x + 18, y + 16, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
        List<SceneMenuScreen.Entry> list = s.worlds;
        s.text(g, list.size() == 1 ? "1 world" : list.size() + " worlds", x + 18 + s.textW("§lWorlds", 1.5f) + 10, y + 21, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
        float cw = s.textW("§lCreate new world", 1f) + 22;
        if (s.pill(g, x + w - 18 - cw, y + 13, cw, 22, "Create new world", accent, true, al)) { create(); return; }
        g.fill(Math.round(x + 14), Math.round(y + 46), Math.round(x + w - 14), Math.round(y + 47), SceneMenuScreen.a(0x26FFFFFF, al));

        float top = y + 52, bottom = y + h - 10, rowH = 40;
        float max = Math.max(0, list.size() * rowH - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll));
        if (list.isEmpty()) {
            s.textC(g, s.worldsLoaded ? "§lNo worlds yet" : "§lLoading worlds…", x + w / 2f, (top + bottom) / 2f - 14, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
            if (s.worldsLoaded) s.textC(g, "Make your first one with the button at the top right.", x + w / 2f, (top + bottom) / 2f + 8, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
            return;
        }
        g.enableScissor(Math.round(x + 4), Math.round(top), Math.round(x + w - 4), Math.round(bottom));
        try {
            for (int i = 0; i < list.size(); i++) {
                SceneMenuScreen.Entry e = list.get(i);
                float ry = top + i * rowH - scroll;
                if (ry + rowH < top || ry > bottom) continue;
                boolean inView = s.my >= top && s.my < bottom;
                boolean hov = inView && s.over(x + 10, ry, w - 20, rowH) && !s.accountOpen;
                float hv = s.ease("wrow" + i, hov, 16f);
                s.rect(g, x + 10, ry + 2, w - 20, rowH - 4, 6, SceneMenuScreen.a(0xFFFFFFFF, (0.05f + 0.07f * hv) * al));
                s.icon(g, e, x + 18, ry + 6, 28, al);
                float right = x + w - 22;
                if (e.worldId.equals(confirmDelete)) {
                    s.text(g, "§l" + s.fit(e.name, w - 320, 1f), x + 56, ry + 9, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                    s.text(g, "Delete this world for good?", x + 56, ry + 21, 1f, SceneMenuScreen.a(0xFFE0A070, al));
                    if (s.pill(g, right - 54, ry + 10, 54, 20, "Delete", 0xFFE5484D, false, al)) { delete(e.worldId); return; }
                    if (s.pill(g, right - 54 - 8 - 46, ry + 10, 46, 20, "Keep", 0xFF334058, false, al)) confirmDelete = null;
                    continue;
                }
                float textRight = right;
                if (hv > 0.5f) {
                    if (!e.disabled && s.pill(g, right - 44, ry + 10, 44, 20, "Play", accent, true, al)) { s.playWorld(e); return; }
                    if (s.pill(g, right - 44 - 6 - 54, ry + 10, 54, 20, "Delete", 0xFF334058, false, al)) confirmDelete = e.worldId;
                    textRight = right - 44 - 6 - 54 - 10;
                }
                float tw = textRight - (x + 56);
                s.text(g, "§l" + s.fit(e.name, tw, 1f), x + 56, ry + 9, 1f, SceneMenuScreen.a(e.disabled ? 0xFF8C96A8 : 0xFFFFFFFF, al));
                s.text(g, s.fit(e.disabled ? "Cannot be opened by this game version" : e.sub, tw, 1f), x + 56, ry + 21, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
                if (hov && s.clicked && !e.disabled) {
                    long now = System.currentTimeMillis();
                    if (lastIndex == i && now - lastClick < 350) { s.click(); s.playWorld(e); return; }
                    lastIndex = i; lastClick = now;
                }
            }
        } finally { g.disableScissor(); }
        if (max > 0) {
            float view = bottom - top, bar = Math.max(18, view * view / (list.size() * rowH));
            s.rect(g, x + w - 7, top + (view - bar) * (scroll / max), 3, bar, 1, SceneMenuScreen.a(0x66FFFFFF, al));
        }
    }
}
