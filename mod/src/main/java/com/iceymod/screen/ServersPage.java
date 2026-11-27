package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import com.iceymod.menu.MenuTextures;
import com.iceymod.menu.ServerPing;
import java.util.List;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * The Servers page of the scene menu: the game's own server list (the
 * same servers.dat the vanilla screen edits), with live status, and
 * adding, editing, removing and connecting by address in place.
 */
final class ServersPage {
    private final SceneMenuScreen s;
    private float scroll;
    // 0 = the list, 1 = add, 2 = edit, 3 = direct connect
    private int form;
    private ServerData editing;
    private ServerData confirmDelete;
    private long lastClick;
    private int lastIndex = -1;

    ServersPage(SceneMenuScreen s) { this.s = s; }

    boolean back() {
        if (form != 0) { form = 0; return true; }
        if (confirmDelete != null) { confirmDelete = null; return true; }
        return false;
    }

    void scroll(float step) { scroll -= step; }

    private void openForm(int kind, ServerData d) {
        form = kind; editing = d;
        s.fields[1].setValue(d != null ? d.name : "");
        s.fields[2].setValue(d != null ? d.ip : "");
        s.focus = kind == 3 ? 2 : 1;
    }

    private void save() {
        String name = s.fields[1].getValue().trim(), address = s.fields[2].getValue().trim();
        if (!ServerAddress.isValidAddress(address)) { s.toast("Not a server address", address); return; }
        try {
            if (form == 3) {
                ServerData d = new ServerData(name.isEmpty() ? address : name, address, ServerData.Type.OTHER);
                form = 0;
                s.join(d);
                return;
            }
            ServerList list = new ServerList(s.mc());
            list.load();
            if (form == 2 && editing != null) {
                for (int i = 0; i < list.size(); i++) {
                    ServerData d = list.get(i);
                    if (d.ip.equals(editing.ip) && d.name.equals(editing.name)) { d.name = name.isEmpty() ? address : name; d.ip = address; break; }
                }
            } else {
                list.add(new ServerData(name.isEmpty() ? address : name, address, ServerData.Type.OTHER), false);
            }
            list.save();
            s.loadServers();
            form = 0;
        } catch (Throwable t) {
            s.toast("Could not save the server", String.valueOf(t.getMessage()));
        }
    }

    private void delete(ServerData target) {
        try {
            ServerList list = new ServerList(s.mc());
            list.load();
            for (int i = 0; i < list.size(); i++) {
                ServerData d = list.get(i);
                if (d.ip.equals(target.ip) && d.name.equals(target.name)) { list.remove(d); break; }
            }
            list.save();
            s.loadServers();
        } catch (Throwable t) {
            s.toast("Could not remove the server", String.valueOf(t.getMessage()));
        }
        confirmDelete = null;
    }

    void draw(Gfx g, float x, float y, float w, float h, float al, int accent, float delta) {
        s.text(g, "§lServers", x + 18, y + 16, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
        List<SceneMenuScreen.Entry> list = s.servers;
        s.text(g, list.size() == 1 ? "1 saved" : list.size() + " saved", x + 18 + s.textW("§lServers", 1.5f) + 10, y + 21, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
        if (form == 0) {
            float bx = x + w - 18;
            float aw = s.textW("§lAdd server", 1f) + 22, dw = s.textW("§lDirect connect", 1f) + 22, rw = s.textW("§lRefresh", 1f) + 22;
            if (s.pill(g, bx - aw, y + 13, aw, 22, "Add server", accent, true, al)) openForm(1, null);
            if (s.pill(g, bx - aw - 8 - dw, y + 13, dw, 22, "Direct connect", 0xFF334058, false, al)) openForm(3, null);
            if (s.pill(g, bx - aw - 8 - dw - 8 - rw, y + 13, rw, 22, "Refresh", 0xFF334058, false, al)) { ServerPing.forget(); s.loadServers(); }
        }
        g.fill(Math.round(x + 14), Math.round(y + 46), Math.round(x + w - 14), Math.round(y + 47), SceneMenuScreen.a(0x26FFFFFF, al));

        if (form != 0) { form(g, x, y + 56, w, h - 56, al, accent, delta); return; }

        float top = y + 52, bottom = y + h - 10, rowH = 40;
        float max = Math.max(0, list.size() * rowH - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll));
        if (list.isEmpty()) {
            s.textC(g, "§lNo servers yet", x + w / 2f, (top + bottom) / 2f - 14, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
            s.textC(g, "Add one with the button at the top right.", x + w / 2f, (top + bottom) / 2f + 8, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
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
                float hv = s.ease("srow" + i, hov, 16f);
                s.rect(g, x + 10, ry + 2, w - 20, rowH - 4, 6, SceneMenuScreen.a(0xFFFFFFFF, (0.05f + 0.07f * hv) * al));
                ServerPing.Result ping = ServerPing.get(e.server.ip);
                if (e.icon == null && ping.icon != null) e.icon = MenuTextures.fromBytes(e.server.ip, ping.icon);
                s.icon(g, e, x + 18, ry + 6, 28, al);
                float right = x + w - 22;
                if (confirmDelete == e.server) {
                    s.text(g, "§l" + s.fit(e.title(), w - 320, 1f), x + 56, ry + 9, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                    s.text(g, "Remove this server from the list?", x + 56, ry + 21, 1f, SceneMenuScreen.a(0xFFE0A070, al));
                    if (s.pill(g, right - 56, ry + 10, 56, 20, "Remove", 0xFFE5484D, false, al)) { delete(e.server); return; }
                    if (s.pill(g, right - 56 - 8 - 46, ry + 10, 46, 20, "Keep", 0xFF334058, false, al)) confirmDelete = null;
                    continue;
                }
                // status on the right; the buttons take its place under the mouse
                float textRight = right;
                if (hv > 0.5f) {
                    if (s.pill(g, right - 44, ry + 10, 44, 20, "Join", accent, true, al)) { s.join(e.server); return; }
                    if (s.pill(g, right - 44 - 6 - 40, ry + 10, 40, 20, "Edit", 0xFF334058, false, al)) { openForm(2, e.server); return; }
                    if (s.pill(g, right - 44 - 6 - 40 - 6 - 22, ry + 10, 22, 20, "×", 0xFF334058, false, al)) { confirmDelete = e.server; }
                    textRight = right - 44 - 6 - 40 - 6 - 22 - 10;
                } else {
                    String st = ping.state == 0 ? "Checking…" : ping.state == 2 ? "No answer" : ping.online + " / " + ping.max;
                    int col = ping.state == 1 ? 0xFFFFFFFF : ping.state == 2 ? 0xFFE08A7A : 0xFF9FB0C8;
                    s.textR(g, st, right, ry + 9, 1f, SceneMenuScreen.a(col, al));
                    if (ping.state == 1) s.textR(g, ping.ms + " ms", right, ry + 21, 1f, SceneMenuScreen.a(ping.ms < 80 ? 0xFF4ADE80 : ping.ms < 180 ? 0xFFE8C860 : 0xFFE08A7A, al));
                    textRight = right - 76;
                }
                float tw = textRight - (x + 56);
                s.text(g, "§l" + s.fit(e.title(), tw, 1f), x + 56, ry + 9, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                String sub = ping.state == 1 && !ping.motd.isEmpty() ? ping.motd : e.server.ip;
                s.text(g, s.fit(sub, tw, 1f), x + 56, ry + 21, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
                if (hov && s.clicked) {
                    long now = System.currentTimeMillis();
                    if (lastIndex == i && now - lastClick < 350) { s.click(); s.join(e.server); return; }
                    lastIndex = i; lastClick = now;
                }
            }
        } finally { g.disableScissor(); }
        if (max > 0) {
            float view = bottom - top, bar = Math.max(18, view * view / (list.size() * rowH));
            s.rect(g, x + w - 7, top + (view - bar) * (scroll / max), 3, bar, 1, SceneMenuScreen.a(0x66FFFFFF, al));
        }
    }

    private void form(Gfx g, float x, float y, float w, float h, float al, int accent, float delta) {
        float fw = Math.min(360, w - 60), fx = x + (w - fw) / 2f, fy = y + Math.max(10, (h - 190) / 2f - 20);
        String title = form == 1 ? "Add a server" : form == 2 ? "Edit server" : "Direct connect";
        s.text(g, "§l" + title, fx, fy, 1.25f, SceneMenuScreen.a(0xFFFFFFFF, al));
        float cy = fy + 26;
        if (form != 3) {
            s.text(g, "Name", fx, cy, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
            s.rect(g, fx, cy + 12, fw, 24, 5, SceneMenuScreen.a(0x66000000, al));
            s.ring(g, fx, cy + 12, fw, 24, 5, SceneMenuScreen.a(s.focus == 1 ? accent : 0x33FFFFFF, al));
            s.field(g, 1, fx + 8, cy + 20, fw - 16, "Minecraft Server", delta);
            cy += 46;
        }
        s.text(g, "Address", fx, cy, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
        s.rect(g, fx, cy + 12, fw, 24, 5, SceneMenuScreen.a(0x66000000, al));
        s.ring(g, fx, cy + 12, fw, 24, 5, SceneMenuScreen.a(s.focus == 2 ? accent : 0x33FFFFFF, al));
        s.field(g, 2, fx + 8, cy + 20, fw - 16, "play.example.net", delta);
        cy += 50;
        String ok = form == 3 ? "Join" : "Save";
        if (s.pill(g, fx + fw - 86, cy, 86, 24, ok, accent, true, al) || (s.enterHit && (s.focus == 1 || s.focus == 2))) { save(); return; }
        if (s.pill(g, fx + fw - 86 - 8 - 70, cy, 70, 24, "Cancel", 0xFF334058, false, al)) form = 0;
    }
}
