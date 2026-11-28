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

    /** How tall the pane wants to be for what is on this page. */
    float wantH() { return s.worlds.isEmpty() ? 250 : 63 + s.worlds.size() * 52 + 12; }

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
        List<SceneMenuScreen.Entry> list = s.worlds;
        s.pageHead(g, x, y, "Worlds", !s.worldsLoaded ? "Looking for your worlds…" : list.isEmpty() ? "Nothing here yet" : list.size() == 1 ? "1 world" : list.size() + " worlds", al);
        float cw = s.buttonW(Icons.PLUS, "Create new world");
        if (s.button(g, x + w - 18 - cw, y + 16, cw, 24, Icons.PLUS, "Create new world", 1, al)) { create(); return; }
        g.fill(Math.round(x + 14), Math.round(y + 56), Math.round(x + w - 14), Math.round(y + 57), SceneMenuScreen.a(0x22FFFFFF, al));

        float top = y + 63, bottom = y + h - 10, rowH = 52;
        float max = Math.max(0, list.size() * rowH - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll));
        if (list.isEmpty()) {
            float cy = (top + bottom) / 2f;
            s.textC(g, s.worldsLoaded ? "§lNo worlds yet" : "§lLoading worlds…", x + w / 2f, cy + 2, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
            if (s.worldsLoaded) {
                s.textC(g, "Your singleplayer worlds show up here.", x + w / 2f, cy + 22, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
                if (s.button(g, x + w / 2f - cw / 2f, cy + 42, cw, 24, Icons.PLUS, "Create new world", 1, al)) { create(); return; }
            }
            return;
        }
        g.enableScissor(Math.round(x + 4), Math.round(top), Math.round(x + w - 4), Math.round(bottom));
        try {
            for (int i = 0; i < list.size(); i++) {
                SceneMenuScreen.Entry e = list.get(i);
                float ry = top + i * rowH - scroll;
                if (ry + rowH < top || ry > bottom) continue;
                boolean inView = s.my >= top && s.my < bottom;
                boolean hov = inView && s.over(x + 12, ry, w - 24, rowH - 4) && !s.accountOpen;
                float hv = s.ease("wrow" + i, hov, 16f);
                s.rect(g, x + 12, ry, w - 24, rowH - 6, 8, SceneMenuScreen.a(0xFFFFFFFF, (0.055f + 0.065f * hv) * al));
                s.ring(g, x + 12, ry, w - 24, rowH - 6, 8, SceneMenuScreen.a(SceneMenuScreen.mix(0x1CFFFFFF, 0x55FFFFFF, hv), al));
                s.icon(g, e, x + 20, ry + 7, 32, al);
                float right = x + w - 22, mid = ry + (rowH - 6) / 2f;
                if (e.worldId.equals(confirmDelete)) {
                    s.text(g, "§l" + s.fit(e.name, w - 330, 1f), x + 62, mid - 11, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                    s.text(g, "Delete this world for good?", x + 62, mid + 2, 1f, SceneMenuScreen.a(0xFFF0A878, al));
                    float dw = s.buttonW(Icons.TRASH, "Delete"), kw = s.buttonW((char) 0, "Keep");
                    if (s.button(g, right - dw, mid - 12, dw, 24, Icons.TRASH, "Delete", 2, al)) { delete(e.worldId); return; }
                    if (s.button(g, right - dw - 8 - kw, mid - 12, kw, 24, (char) 0, "Keep", 0, al)) confirmDelete = null;
                    continue;
                }
                float pw = s.buttonW(Icons.PLAY, "Play");
                float bx = right;
                if (!e.disabled) {
                    if (s.button(g, right - pw, mid - 12, pw, 24, Icons.PLAY, "Play", hv > 0.5f ? 1 : 0, al)) { s.playWorld(e); return; }
                    bx = right - pw - 8;
                }
                if (s.iconButton(g, bx - 24, mid - 12, 24, Icons.TRASH, "Delete", true, 0.35f + 0.65f * hv, al)) confirmDelete = e.worldId;
                float tw = bx - 24 - 14 - (x + 62);
                s.text(g, "§l" + s.fit(e.name, tw, 1f), x + 62, mid - 11, 1f, SceneMenuScreen.a(e.disabled ? 0xFF8C96A8 : 0xFFFFFFFF, al));
                String sub = e.disabled ? "Cannot be opened by this game version" : e.sub;
                s.text(g, s.fit(sub, tw, 1f), x + 62, mid + 2, 1f, SceneMenuScreen.a(e.disabled ? 0xFFF0A878 : 0xFF9FB0C8, al));
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
