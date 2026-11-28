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

    /** How tall the pane wants to be for what is on this page. */
    float wantH() { return form != 0 ? 300 : s.servers.isEmpty() ? 250 : 63 + s.servers.size() * 52 + 12; }

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
        List<SceneMenuScreen.Entry> list = s.servers;
        s.pageHead(g, x, y, "Servers", list.isEmpty() ? "Nothing saved yet" : list.size() == 1 ? "1 saved server" : list.size() + " saved servers", al);
        if (form == 0) {
            float bx = x + w - 18;
            float aw = s.buttonW(Icons.PLUS, "Add server"), dw = s.buttonW(Icons.PLUG, "Direct connect");
            if (s.button(g, bx - aw, y + 16, aw, 24, Icons.PLUS, "Add server", 1, al)) openForm(1, null);
            if (w > 470 && s.button(g, bx - aw - 8 - dw, y + 16, dw, 24, Icons.PLUG, "Direct connect", 0, al)) openForm(3, null);
            if (w > 510 && s.iconButton(g, bx - aw - 8 - dw - 8 - 24, y + 16, 24, Icons.REFRESH, "Refresh", false, 1f, al)) { ServerPing.forget(); s.loadServers(); }
        }
        g.fill(Math.round(x + 14), Math.round(y + 56), Math.round(x + w - 14), Math.round(y + 57), SceneMenuScreen.a(0x22FFFFFF, al));

        if (form != 0) { form(g, x, y + 58, w, h - 58, al, accent, delta); return; }

        float top = y + 63, bottom = y + h - 10, rowH = 52;
        float max = Math.max(0, list.size() * rowH - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll));
        if (list.isEmpty()) {
            float cy = (top + bottom) / 2f;
            s.textC(g, "§lNo servers yet", x + w / 2f, cy + 2, 1.5f, SceneMenuScreen.a(0xFFFFFFFF, al));
            s.textC(g, "Save the servers you play on and join them from here.", x + w / 2f, cy + 22, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
            float aw = s.buttonW(Icons.PLUS, "Add server");
            if (s.button(g, x + w / 2f - aw / 2f, cy + 42, aw, 24, Icons.PLUS, "Add server", 1, al)) openForm(1, null);
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
                float hv = s.ease("srow" + i, hov, 16f);
                s.rect(g, x + 12, ry, w - 24, rowH - 6, 8, SceneMenuScreen.a(0xFFFFFFFF, (0.055f + 0.065f * hv) * al));
                s.ring(g, x + 12, ry, w - 24, rowH - 6, 8, SceneMenuScreen.a(SceneMenuScreen.mix(0x1CFFFFFF, 0x55FFFFFF, hv), al));
                ServerPing.Result ping = ServerPing.get(e.server.ip);
                if (e.icon == null && ping.icon != null) e.icon = MenuTextures.fromBytes(e.server.ip, ping.icon);
                s.icon(g, e, x + 20, ry + 7, 32, al);
                float right = x + w - 22, mid = ry + (rowH - 6) / 2f;
                if (confirmDelete == e.server) {
                    s.text(g, "§l" + s.fit(e.title(), w - 330, 1f), x + 62, mid - 11, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                    s.text(g, "Remove this server from the list?", x + 62, mid + 2, 1f, SceneMenuScreen.a(0xFFF0A878, al));
                    float rw = s.buttonW(Icons.TRASH, "Remove"), kw = s.buttonW((char) 0, "Keep");
                    if (s.button(g, right - rw, mid - 12, rw, 24, Icons.TRASH, "Remove", 2, al)) { delete(e.server); return; }
                    if (s.button(g, right - rw - 8 - kw, mid - 12, kw, 24, (char) 0, "Keep", 0, al)) confirmDelete = null;
                    continue;
                }
                // From the right: Join, edit and remove (plain until the mouse is on the row), then how the server is doing.
                float jw = s.buttonW(Icons.PLAY, "Join");
                if (s.button(g, right - jw, mid - 12, jw, 24, Icons.PLAY, "Join", hv > 0.5f ? 1 : 0, al)) { s.join(e.server); return; }
                float bx = right - jw - 8;
                if (s.iconButton(g, bx - 24, mid - 12, 24, Icons.TRASH, "Remove", true, 0.35f + 0.65f * hv, al)) { confirmDelete = e.server; }
                if (s.iconButton(g, bx - 24 - 4 - 24, mid - 12, 24, Icons.PENCIL, "Edit", false, 0.35f + 0.65f * hv, al)) { openForm(2, e.server); return; }
                float statusRight = bx - 24 - 4 - 24 - 14;
                int col = ping.state == 1 ? (ping.ms < 80 ? 0xFF4ADE80 : ping.ms < 180 ? 0xFFE8C860 : 0xFFF08A7A) : ping.state == 2 ? 0xFFF08A7A : 0xFF9FB0C8;
                String line1 = ping.state == 0 ? "Checking…" : ping.state == 2 ? "No answer" : ping.online + " / " + ping.max;
                String line2 = ping.state == 1 ? ping.ms + " ms" : "";
                float sw = 0;
                if (w > 430) {
                    sw = Math.max(s.textW(line1, 1f), s.textW(line2, 1f));
                    s.textR(g, line1, statusRight, line2.isEmpty() ? mid - 4 : mid - 11, 1f, SceneMenuScreen.a(ping.state == 1 ? 0xFFFFFFFF : col, al));
                    if (!line2.isEmpty()) s.textR(g, line2, statusRight, mid + 2, 1f, SceneMenuScreen.a(col, al));
                }
                float tw = statusRight - sw - 12 - (x + 62);
                s.text(g, "§l" + s.fit(e.title(), tw, 1f), x + 62, mid - 11, 1f, SceneMenuScreen.a(0xFFFFFFFF, al));
                String sub = ping.state == 1 && !ping.motd.isEmpty() ? ping.motd : e.server.ip;
                s.text(g, s.fit(sub, tw, 1f), x + 62, mid + 2, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
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
        float fw = Math.min(380, w - 48), fh = form == 3 ? 150 : 198;
        float fx = x + (w - fw) / 2f, fy = y + Math.max(8, (h - fh) / 2f - 16);
        s.rect(g, fx - 18, fy - 16, fw + 36, fh + 28, 10, SceneMenuScreen.a(0x33060A14, al));
        s.ring(g, fx - 18, fy - 16, fw + 36, fh + 28, 10, SceneMenuScreen.a(0x24FFFFFF, al));
        String title = form == 1 ? "Add a server" : form == 2 ? "Edit server" : "Direct connect";
        String sub = form == 3 ? "Join a server once, without saving it." : "It is saved to the game's own server list.";
        s.text(g, "§l" + title, fx, fy + 4, 1.25f, SceneMenuScreen.a(0xFFFFFFFF, al));
        s.text(g, sub, fx, fy + 19, 1f, SceneMenuScreen.a(0xFF9FB0C8, al));
        float cy = fy + 44;
        if (form != 3) {
            s.text(g, "NAME", fx, cy, 1f, SceneMenuScreen.a(0xFF8E9AB0, al));
            input(g, 1, Icons.PENCIL, fx, cy + 12, fw, "Minecraft Server", accent, al, delta);
            cy += 48;
        }
        s.text(g, "ADDRESS", fx, cy, 1f, SceneMenuScreen.a(0xFF8E9AB0, al));
        input(g, 2, Icons.LINK, fx, cy + 12, fw, "play.example.net", accent, al, delta);
        cy += 52;
        String ok = form == 3 ? "Join" : "Save";
        float ow = Math.max(84, s.buttonW(form == 3 ? Icons.PLAY : Icons.CHECK, ok)), cw = s.buttonW((char) 0, "Cancel");
        if (s.button(g, fx + fw - ow, cy, ow, 26, form == 3 ? Icons.PLAY : Icons.CHECK, ok, 1, al) || (s.enterHit && (s.focus == 1 || s.focus == 2))) { save(); return; }
        if (s.button(g, fx + fw - ow - 8 - cw, cy, cw, 26, (char) 0, "Cancel", 0, al)) form = 0;
    }

    private void input(Gfx g, int slot, char icon, float x, float y, float w, String hint, int accent, float al, float delta) {
        boolean on = s.focus == slot;
        float fv = s.ease("in" + slot, on, 16f);
        s.rect(g, x, y, w, 26, 6, SceneMenuScreen.a(0x70000000, al));
        s.ring(g, x, y, w, 26, 6, SceneMenuScreen.a(SceneMenuScreen.mix(0x33FFFFFF, 0xFF000000 | accent, fv), al));
        boolean icons = s.iconsUsable();
        if (icons) s.icon(g, icon, x + 14, y + 13, 12, SceneMenuScreen.a(SceneMenuScreen.mix(0xFF8E9AB0, 0xFFFFFFFF, fv), al));
        s.field(g, slot, x + (icons ? 28 : 9), y + 9, w - (icons ? 36 : 18), hint, delta);
    }
}
