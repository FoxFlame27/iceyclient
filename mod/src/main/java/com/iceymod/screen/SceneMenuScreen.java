package com.iceymod.screen;

import com.google.gson.JsonObject;
import com.iceymod.Branding;
import com.iceymod.IceyMod;
import com.iceymod.MenuPrefs;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.Input;
import com.iceymod.compat.MC;
import com.iceymod.menu.AccountSwitch;
import com.iceymod.menu.MenuBridge;
import com.iceymod.menu.MenuTextures;
import com.iceymod.menu.Scenes;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * The scene main menu: the player stands in a place from a Minecraft
 * world with their friends around them, servers and worlds are one click
 * away in a panel, the account can be switched at the top, and a click on
 * a friend opens a conversation.
 *
 * It replaces the title screen only when the player chose it (see
 * SceneMenu). Everything it offers already exists elsewhere: the server
 * list is the game's own, friends and accounts come from the launcher
 * through MenuBridge, the other screens are the vanilla ones.
 */
public class SceneMenuScreen extends VirtualScreen {

    static boolean introPlayed;
    static int lastTab;
    static final int M = 14;

    static final class Entry {
        String name = "", sub = "";
        /** What to call it: a server still carrying the default name goes by its address. */
        String title() { return server != null && (name.isBlank() || name.equals("Minecraft Server")) ? server.ip : name; }
        MenuTextures.Tex icon;
        ServerData server;
        String worldId;
        boolean disabled;
    }
    static final class Pending { String text; long at; }
    static final class Standing {
        MenuBridge.Friend f; Scenes.Spot spot; float x, y, h, depth; boolean hover;
    }

    long openedAt;
    boolean intro;
    float viewYaw;
    long swapAt;
    boolean swapped;

    // Play panel
    int tab = lastTab;
    final List<Entry> servers = new ArrayList<>();
    final List<Entry> worlds = new ArrayList<>();
    boolean worldsLoaded;
    int selServer, selWorld;
    float scrollS, scrollW;
    long lastRowClick;
    int lastRowIndex = -1;

    // Friends panel: 0 = the small bar, 1 = the list, 2 = a conversation
    int left;
    String chatUuid = "", chatName = "";
    float chatScroll, listScroll;
    final List<Pending> pending = new ArrayList<>();
    // Text fields: 0 = friends panel (message, add a friend), 1 and 2 = the forms of the pages.
    final EditBox[] fields = new EditBox[3];
    final boolean[] fieldShown = new boolean[3];
    int focus = -1;
    boolean enterHit;
    EditBox input;
    boolean prevEnter = true;

    // Pages: 0 = home, 1 = servers, 2 = worlds, 3 = options
    int page;
    ServersPage serversPage;
    WorldsPage worldsPage;
    OptionsPage optionsPage;
    float pgX, pgY, pgW, pgH;
    private float pgWant = 1000, pgHNow;

    net.minecraft.client.Minecraft mc() { return minecraft; }

    // Account
    boolean accountOpen;
    long switchReq, switchAt;
    String switchName = "";

    // A line that slides in under the top bar for a few seconds
    String toastTitle = "", toastText = "";
    long toastAt, seenNoticeAt;

    // Leaving for another screen: fade out first
    Runnable leaveRun;
    long leaveAt;
    float leaveSeconds;


    // Rectangles of this frame, so clicks on a panel do not also hit a friend behind it
    float pX, pY, pW, pH, lX, lY, lW, lH;

    public SceneMenuScreen() { super(Component.literal(Branding.name())); }

    @Override
    protected void init() {
        swallowClick();
        prevEnter = true;
        setScene(Scenes.current());
        openedAt = System.currentTimeMillis();
        intro = !introPlayed;
        introPlayed = true;
        leaveRun = null;
        try { loadServers(); } catch (Throwable t) { System.out.println("[IceyMod] menu: server list " + t); }
        try { loadWorlds(); } catch (Throwable t) { System.out.println("[IceyMod] menu: worlds " + t); }
        for (int i = 0; i < fields.length; i++) {
            String keep = fields[i] != null ? fields[i].getValue() : "";
            EditBox b = new EditBox(this.font, 0, 0, 100, 10, Component.literal(""));
            b.setBordered(false);
            b.setMaxLength(i == 0 ? 200 : 120);
            b.setTextColor(0xFFFFFFFF);
            b.setValue(keep);
            try {
                if (UiFont.usable(this.font)) b.addFormatter((text, start) -> net.minecraft.util.FormattedCharSequence.forward(text, UiFont.style(false)));
            } catch (Throwable ignored) {}
            addWidget(b);
            fields[i] = b;
        }
        input = fields[0];
        if (serversPage == null) { serversPage = new ServersPage(this); worldsPage = new WorldsPage(this); optionsPage = new OptionsPage(this); }
    }

    /** A text field at this place of the canvas (a vanilla one, drawn through our transform). A click in it gives it the keyboard. */
    void field(Gfx g, int slot, float x, float y, float w, String hint, float delta) {
        EditBox b = fields[slot];
        fieldShown[slot] = true;
        b.setX(Math.round(x)); b.setY(Math.round(y)); b.setWidth(Math.round(w));
        b.setHint(UiFont.usable(this.font) ? Component.literal(hint).withStyle(UiFont.style(false).withColor(0x8E9AB0)) : Component.literal("§7" + hint));
        if (clicked && over(x - 8, y - 8, w + 16, 24)) focus = slot;
        boolean on = focus == slot;
        if (on && getFocused() != b) setFocused(b);
        b.setFocused(on);
        g.enableScissor(Math.round(x - 2), Math.round(y - 4), Math.round(x + w + 2), Math.round(y + 14));
        try { g.drawWidget(b, Math.round(mx), Math.round(my), delta); } finally { g.disableScissor(); }
    }

    /** Fields that were not drawn this frame give the keyboard back. */
    void endFields() {
        for (int i = 0; i < fields.length; i++) {
            if (!fieldShown[i]) {
                fields[i].setFocused(false);
                if (getFocused() == fields[i]) setFocused(null);
                if (focus == i) focus = -1;
            }
            fieldShown[i] = false;
        }
    }

    void loadServers() {
        servers.clear();
        ServerList list = new ServerList(minecraft);
        list.load();
        for (int i = 0; i < list.size(); i++) {
            ServerData d = list.get(i);
            Entry e = new Entry();
            e.name = d.name == null || d.name.isBlank() ? d.ip : d.name;
            e.sub = d.ip;
            e.server = d;
            try { e.icon = MenuTextures.fromBytes(d.ip, d.getIconBytes()); } catch (Throwable ignored) {}
            servers.add(e);
        }
        if (selServer >= servers.size()) selServer = 0;
    }

    void loadWorlds() throws Exception {
        var source = minecraft.getLevelSource();
        source.loadLevelSummaries(source.findLevelCandidates()).thenAcceptAsync(list -> {
            try {
                List<LevelSummary> sorted = new ArrayList<>(list);
                sorted.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
                worlds.clear();
                for (LevelSummary sm : sorted) {
                    Entry e = new Entry();
                    e.name = sm.getLevelName();
                    String mode = sm.getGameMode().getName();
                    e.sub = Character.toUpperCase(mode.charAt(0)) + mode.substring(1) + " · " + ago(sm.getLastPlayed());
                    e.worldId = sm.getLevelId();
                    e.disabled = sm.isDisabled() || sm.isLocked();
                    try { e.icon = MenuTextures.fromFile(sm.getIcon()); } catch (Throwable ignored) {}
                    worlds.add(e);
                }
                if (selWorld >= worlds.size()) selWorld = 0;
                worldsLoaded = true;
            } catch (Throwable t) { worldsLoaded = true; }
        }, minecraft);
    }

    static String ago(long time) {
        if (time <= 0) return "never played";
        long sec = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (sec < 90) return "just now";
        if (sec < 3600) return (sec / 60) + " min ago";
        if (sec < 86400 * 2) return (sec / 3600) + " h ago";
        return (sec / 86400) + " days ago";
    }

    @Override
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) {}

    @Override
    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) {
        try {
            beginFrame(mouseX, mouseY);
            g.pose().pushMatrix();
            try {
                g.pose().scale(s, s);
                draw(g, delta);
            } finally { g.pose().popMatrix(); }
        } catch (Throwable t) {
            // Never leave the player without a main menu: fall back to the normal one.
            System.out.println("[IceyMod] scene menu failed, showing the normal title screen: " + t);
            t.printStackTrace();
            SceneMenu.failed = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (SceneMenu.failed || scene == null || model == null) {
            SceneMenu.failed = true;
            MC.setScreen(minecraft, new net.minecraft.client.gui.screens.TitleScreen());
        }
    }

    // ───────────────────────── frame ─────────────────────────

    void draw(Gfx g, float delta) {
        long now = System.currentTimeMillis();
        float t = (now - openedAt) / 1000f;
        int accent = MenuPrefs.accent();
        MenuBridge.poll(left == 2 ? chatUuid : "");
        MenuBridge.State st = MenuBridge.state();
        boolean live = MenuBridge.live();
        accountAnswer(now);
        if (MenuBridge.noticeAt != seenNoticeAt && MenuBridge.notice != null) {
            seenNoticeAt = MenuBridge.noticeAt;
            toast(MenuBridge.notice[0], MenuBridge.notice[1]);
        }

        // Leaving: fade to black, then go.
        float leave = 0f;
        if (leaveRun != null) {
            leave = clamp01((now - leaveAt) / 1000f / leaveSeconds);
            if (leave >= 1f) { Runnable r = leaveRun; leaveRun = null; openedAt = now; intro = false; r.run(); return; }
        }
        boolean busy = leaveRun != null;
        if (busy) clicked = false;
        boolean enterNow = Input.keyDown(InputConstants.KEY_RETURN);
        enterHit = enterNow && !prevEnter && !busy;
        prevEnter = enterNow;

        // The place: at the very first start it swings into view; it turns a little with the mouse.
        float zoom = 1f - (intro ? 0.14f * (1f - outCubic(t / 1.9f)) : 0f) + 0.09f * leave * leave;
        viewYaw = intro ? 2.2f * (1f - outCubic(t / 1.9f)) : 0f;
        // "Another place": dip to black, swap, come back.
        float dip = 0f;
        if (swapAt != 0) {
            float p = (now - swapAt) / 520f;
            if (p >= 0.5f && !swapped) { swapped = true; setScene(Scenes.next()); if (model == null) setScene(Scenes.next()); }
            dip = 1f - Math.abs(2f * Math.min(1f, p) - 1f);
            if (p >= 1f) swapAt = 0;
        }
        if (scene == null || model == null) { SceneMenu.failed = true; return; }
        drawPlace(g, zoom, viewYaw, 1f, false);
        // Dark at the top for the bar, a little at the bottom for the panels.
        for (int i = 0; i < 70; i++) g.fill(0, i, VW, i + 1, a(0xFF060912, 0.62f * (1f - i / 70f) * (1f - i / 70f)));
        for (int i = 0; i < 60; i++) g.fill(0, VH - 1 - i, VW, VH - i, a(0xFF060912, 0.26f * (1f - i / 60f)));

        // Where the panels are this frame.
        float show = intro ? outCubic(span(t, 0.45f, 1.0f)) : outCubic(span(t, 0f, 0.28f));
        float pageK = ease("page", page != 0, 13f);
        pW = 232; pH = 204; pX = VW - M - pW + (1f - show) * 36f + pageK * (pW + 40); pY = VH - M - pH;
        pgW = Math.min(660, VW - 2 * M - 8);
        // The pane is as tall as what is on the page, up to the window.
        if (page != 0) pgWant = page == 1 ? serversPage.wantH() : page == 2 ? worldsPage.wantH() : optionsPage.wantH();
        float pgTarget = Math.min(VH - 50 - M, Math.max(232, pgWant));
        pgHNow = pgHNow <= 0 ? pgTarget : pgHNow + (pgTarget - pgHNow) * (1f - (float) Math.exp(-dt * 16f));
        pgH = Math.round(pgHNow);
        pgX = (VW - pgW) / 2f; pgY = 50 + (1f - pageK) * 14f;
        float openK = ease("left", left != 0, 14f);
        lW = 216; lH = 34 + (Math.min(256, VH - 40 - M * 2 - 8) - 34) * openK;
        lX = M - (1f - show) * 36f; lY = VH - M - lH;

        List<Standing> people = people(st, live);
        boolean overUi = my < 46 || over(pX, pY, pW, pH) || over(lX, lY, lW, lH) || accountOpen || (page != 0 && over(pgX, pgY, pgW, pgH));
        float tt = (now % 1000000L) / 1000f;

        // You, then the friends, the far ones first.
        Standing hovered = null;
        if (!overUi && !busy && page == 0) for (Standing p : people) if (over(p.x - p.h * 0.24f, p.y - p.h * 1.03f, p.h * 0.48f, p.h * 1.06f)) hovered = p;
        people.sort((a2, b2) -> Float.compare(b2.depth, a2.depth));
        float[] meAt = place(0, 0, 0);
        float meX = meAt[0], meY = meAt[1], meH = 1.875f * meAt[2];
        int n = 0;
        float meIn = (intro ? outCubic(span(t, 0.5f, 0.95f)) : show) * (1f - pageK);
        boolean meDrawn = false;
        for (Standing p : people) {
            float in = (intro ? outCubic(span(t, 0.75f + n * 0.09f, 1.15f + n * 0.09f)) : show) * (1f - pageK);
            n++;
            p.hover = p == hovered;
            float hv = ease("doll:" + p.f.uuid, p.hover, 12f);
            // A friend who stands nearer to the camera than you is drawn after you.
            if (!meDrawn && p.depth < meAt[3]) { drawMe(g, meX, meY, meH, meIn, tt); meDrawn = true; }
            drawFriend(g, p, in, hv, tt, meX);
        }
        if (!meDrawn) drawMe(g, meX, meY, meH, meIn, tt);
        weather(g, show);

        // Name tags over the figures.
        for (Standing p : people) {
            float hv = ease("doll:" + p.f.uuid, p.hover, 12f);
            tag(g, p.x, p.y - p.h - 15 - hv * 2, p.f.name, null, p.f.online ? 0xFF4ADE80 : 0xFF7C8798, p.f.unread, hv, show * (1f - pageK), accent);
            if (hv > 0.05f) textC(g, "Click to chat", p.x, p.y - p.h - 15 - 12 - hv * 2, 1f, a(0xFFFFFFFF, hv * show * (1f - pageK)));
        }
        tag(g, meX, meY - meH - 16, minecraft.getUser().getName(), "YOU", 0, 0, 0f, meIn, accent);
        if (hovered != null && clicked) { click(); openChat(hovered.f.uuid, hovered.f.name); swallowClick(); }

        topBar(g, st, live, show, accent, t, busy);
        if (pageK < 0.98f) playPanel(g, show * (1f - pageK), accent, busy || page != 0);
        if (pageK > 0.02f) {
            // A page: the world steps back, one large pane takes the middle.
            g.fill(0, 0, VW, VH, a(0x66060912, pageK * show));
            glass(g, pgX, pgY, pgW, pgH, 10, pageK * show);
            rect(g, pgX + 1, pgY + 1, pgW - 2, pgH - 2, 10, a(0x52080D18, pageK * show));
            if (page == 1) serversPage.draw(g, pgX, pgY, pgW, pgH, pageK * show, accent, delta);
            else if (page == 2) worldsPage.draw(g, pgX, pgY, pgW, pgH, pageK * show, accent, delta);
            else if (page == 3) optionsPage.draw(g, pgX, pgY, pgW, pgH, pageK * show, accent, delta);
            if (clicked && over(pgX, pgY, pgW, pgH)) swallowClick();
        }
        if (page == 0 || pgX > lX + lW + 6 || left != 0) friendsPanel(g, st, live, show, accent, openK, delta);
        footer(g, show, accent);
        if (accountOpen) accountMenu(g, st, live, accent, now);
        drawToast(g, now);

        // Fades: in from black at the very first start, out to black when leaving.
        float black = Math.max(Math.max(leave, dip), intro ? 1f - span(t, 0f, 0.7f) : 1f - span(t, 0f, 0.18f));
        if (black > 0.003f) g.fill(0, 0, VW, VH, a(0xFF05070D, black));

        // A click that landed on nothing closes what is open.
        if (clicked && accountOpen) { accountOpen = false; }
        endFields();
    }

    // ───────────────────────── people ─────────────────────────

    List<Standing> people(MenuBridge.State st, boolean live) {
        List<Standing> out = new ArrayList<>();
        if (scene == null || model == null || !live) return out;
        List<MenuBridge.Friend> list = new ArrayList<>(st.friends);
        list.sort((a2, b2) -> a2.online != b2.online ? (a2.online ? -1 : 1) : Long.compare(b2.lastSeen, a2.lastSeen));
        // Two friends stand with you: who is online first, then who was seen last.
        for (int i = 0; i < list.size() && i < scene.spots.size() && i < 2; i++) {
            Standing p = new Standing();
            p.f = list.get(i);
            p.spot = scene.spots.get(i);
            float[] at = place(p.spot.x, p.spot.y, p.spot.z);
            if (at[3] < 1.5f) continue;
            p.x = at[0]; p.y = at[1]; p.h = 1.875f * at[2];
            p.depth = at[3];
            out.add(p);
        }
        return out;
    }

    /** The dark patch on the ground under a player: a circle seen from the camera's height. */
    void shadow(Gfx g, float x, float y, float h, float alpha, float pitchSeen) {
        float block = h / 1.875f;
        float squash = (float) Math.sin(Math.max(0.08f, pitchSeen));
        float w = block * 0.86f, hh = Math.max(2f, block * 0.86f * squash);
        rect(g, x - w / 2, y - hh / 2, w, hh, 12, a(0x2A000000, alpha));
        rect(g, x - w * 0.37f, y - hh * 0.37f, w * 0.74f, hh * 0.74f, 12, a(0x34000000, alpha));
        rect(g, x - w * 0.22f, y - hh * 0.22f, w * 0.44f, hh * 0.44f, 12, a(0x34000000, alpha));
    }

    void drawMe(Gfx g, float x, float y, float h, float in, float tt) {
        if (in <= 0.01f) return;
        String uuid = minecraft.getUser().getProfileId().toString();
        MenuTextures.Tex skin = MenuTextures.skin(uuid);
        Doll.Pose p = new Doll.Pose();
        float lookX = Math.max(-1f, Math.min(1f, (mx - x) / (VW * 0.4f)));
        float lookY = Math.max(-1f, Math.min(1f, (my - (y - h * 0.85f)) / (VH * 0.5f)));
        p.pitch = model.pitchSeen(0, 0, 0);
        p.yaw = 0.30f * lookX + model.yawSeen(0, 0);
        p.headYaw = 0.55f * lookX;
        p.headPitch = 0.30f * lookY;
        float breathe = (float) Math.sin(tt * 1.7f);
        p.rArmZ = -(0.07f + 0.025f * breathe); p.lArmZ = 0.07f + 0.025f * breathe;
        p.rArmX = 0.06f * (float) Math.sin(tt * 1.1f); p.lArmX = -p.rArmX;
        shadow(g, x, y, h, in, p.pitch);
        Doll.draw(g, skin.id, skin.height, MenuBridge.slim(uuid), x, y, h * (0.9f + 0.1f * in), p, a(0xFF000000 | scene.light, in));
    }

    void drawFriend(Gfx g, Standing s2, float in, float hv, float tt, float meX) {
        if (in <= 0.01f) return;
        MenuTextures.Tex skin = MenuTextures.skin(s2.f.uuid);
        float ph = (s2.f.uuid.hashCode() & 0xFF) / 40f;
        Doll.Pose p = new Doll.Pose();
        float toward = Math.max(-0.55f, Math.min(0.55f, (meX - s2.x) / (VW * 0.5f)));
        float lookX = Math.max(-1f, Math.min(1f, (mx - s2.x) / (VW * 0.4f)));
        p.pitch = model.pitchSeen(s2.spot.x, s2.spot.y, s2.spot.z);
        p.yaw = toward * 0.8f * (1f - hv) + model.yawSeen(s2.spot.x, s2.spot.z);
        p.headYaw = (0.22f * (float) Math.sin(tt * 0.6f + ph)) * (1f - hv) + (0.5f * lookX - p.yaw * 0.5f) * hv;
        p.headPitch = 0.05f * (float) Math.sin(tt * 0.43f + ph * 2);
        float breathe = (float) Math.sin(tt * 1.6f + ph);
        p.rArmZ = -(0.07f + 0.025f * breathe); p.lArmZ = 0.07f + 0.025f * breathe;
        p.rArmX = 0.06f * (float) Math.sin(tt * 1.0f + ph); p.lArmX = -p.rArmX;
        // Under the mouse: turn to the viewer, lift the arm up beside the head and wave from the shoulder, with a little bounce.
        p.rArmZ += hv * (-2.62f + 0.28f * (float) Math.sin(tt * 7.5f));
        p.rArmX += hv * -0.10f;
        p.lArmZ += hv * 0.10f;
        p.headPitch -= hv * 0.06f;
        float hop = hv * Math.abs((float) Math.sin(tt * 3.75f)) * s2.h * 0.035f;
        int lit = 0xFF000000 | scene.light;
        int tint = s2.f.online ? lit : mix(lit, 0xFF4A5060, 0.45f);        // offline: dimmer
        tint = mix(tint, lit, hv * 0.6f);
        shadow(g, s2.x, s2.y, s2.h, in, p.pitch);
        Doll.draw(g, skin.id, skin.height, s2.f.slim || MenuBridge.slim(s2.f.uuid), s2.x, s2.y - hop, s2.h * (0.9f + 0.1f * in) * (1f + 0.03f * hv), p, a(tint, in));
    }

    void tag(Gfx g, float cx, float y, String name, String badge, int dot, int unread, float hv, float alpha, int accent) {
        if (alpha <= 0.02f) return;
        float nameW = textW(name, 1f);
        float w = 8 + (dot != 0 ? 8 : 0) + nameW + (badge != null ? 5 + textW(badge, 1f) : 0) + (unread > 0 ? 6 + Math.max(10, textW(String.valueOf(unread), 1f) + 6) : 0) + 8;
        float x = Math.round(cx - w / 2f), h = 15;
        rect(g, x, y, w, h, 5, a(0xC20A1020, alpha));
        ring(g, x, y, w, h, 5, a(mix(0x40FFFFFF, accent, hv), alpha));
        float tx = x + 8;
        if (dot != 0) { rect(g, tx, y + 5, 5, 5, 2, a(dot, alpha)); tx += 8; }
        text(g, name, tx, y + 4, 1f, a(0xFFFFFFFF, alpha));
        tx += nameW;
        if (badge != null) { text(g, badge, tx + 5, y + 4, 1f, a(accent, alpha)); tx += 5 + textW(badge, 1f); }
        if (unread > 0) {
            String c = String.valueOf(Math.min(99, unread));
            float bw = Math.max(10, textW(c, 1f) + 6);
            rect(g, tx + 6, y + 3, bw, 9, 4, a(accent, alpha));
            textC(g, c, tx + 6 + bw / 2f, y + 4, 1f, a(0xFF06101C, alpha));
        }
    }

    // ───────────────────────── top bar ─────────────────────────

    static final String[] NAV = { "Play", "Servers", "Worlds", "Friends", "Mods", "Options" };

    void topBar(Gfx g, MenuBridge.State st, boolean live, float show, int accent, float t, boolean busy) {
        float y0 = -18f * (1f - show);
        float al = show;
        // Brand
        try {
            if (!Branding.isSkiflame()) {
                g.pose().pushMatrix();
                g.pose().translate(M, y0 + 10);
                g.pose().scale(18 / 64f, 18 / 64f);
                g.blit(Branding.head(), 0, 0, 0f, 0f, 64, 64, 64, 64, a(0xFFFFFFFF, al));
                g.pose().popMatrix();
            }
        } catch (Throwable ignored) {}
        String brand = "§l" + Branding.name().toUpperCase();
        float brandX = M + (Branding.isSkiflame() ? 0 : 24);
        text(g, brand, brandX, y0 + 14, 1.25f, a(0xFFFFFFFF, al));
        float brandEnd = brandX + textW(brand, 1.25f);

        // Navigation, centred
        float gap = 22, total = -gap;
        for (String s2 : NAV) total += textW(s2, 1f) + gap;
        float x = (VW - total) / 2f;
        String ver = IceyMod.BUILD + "  ·  Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name();
        if (brandEnd + 8 + textW(ver, 1f) < x - 26) text(g, ver, brandEnd + 8, y0 + 16, 1f, a(0xFF8E9AB0, al));
        else if (brandEnd + 8 + textW(IceyMod.BUILD, 1f) < x - 26) text(g, IceyMod.BUILD, brandEnd + 8, y0 + 16, 1f, a(0xFF8E9AB0, al));
        glass(g, x - 14, y0 + 7, total + 28, 26, 10, al);
        for (int i = 0; i < NAV.length; i++) {
            String label = NAV[i];
            float w = textW(label, 1f);
            boolean active = (i == 0 && page == 0) || (i == 1 && page == 1) || (i == 2 && page == 2) || (i == 5 && page == 3) || (i == 3 && left != 0);
            boolean hov = !busy && !accountOpen && over(x - 9, y0 + 8, w + 18, 24);
            float hv = ease("nav" + i, hov, 14f), on = ease("navon" + i, active, 14f);
            if (on > 0.02f) rect(g, x - 9, y0 + 10, w + 18, 20, 8, a(accent, al * on));
            else if (hv > 0.02f) rect(g, x - 9, y0 + 10, w + 18, 20, 8, a(0x24FFFFFF, al * hv));
            text(g, (on > 0.5f ? "§l" : "") + label, x + (on > 0.5f ? (w - textW("§l" + label, 1f)) / 2f : 0), y0 + 16, 1f, a(mix(mix(0xFFC3CEDF, 0xFFFFFFFF, hv), 0xFF06101C, on), al));
            if (hov && clicked) { click(); nav(i); swallowClick(); }
            x += w + gap;
        }

        // Quit, and the account
        float qx = VW - M - 22, qy = y0 + 8;
        boolean qh = !busy && over(qx, qy, 22, 22);
        float qv = ease("quit", qh, 14f);
        glass(g, qx, qy, 22, 22, 6, al);
        if (qv > 0.02f) rect(g, qx, qy, 22, 22, 6, a(0x66E5484D, qv * al));
        textC(g, "×", qx + 11, qy + 7, 1f, a(0xFFFFFFFF, al));
        if (qh && clicked) { click(); minecraft.stop(); }

        String name = minecraft.getUser().getName();
        float aw = 8 + 14 + 6 + textW(name, 1f) + 6 + 6 + 8;
        float ax = qx - 8 - aw, ay = y0 + 8;
        boolean ah = !busy && over(ax, ay, aw, 22);
        float av = ease("acct", ah || accountOpen, 14f);
        glass(g, ax, ay, aw, 22, 6, al);
        if (av > 0.02f) ring(g, ax, ay, aw, 22, 6, a(accent, av * al * 0.9f));
        MenuTextures.Tex skin = MenuTextures.skin(minecraft.getUser().getProfileId().toString());
        Doll.face(g, skin.id, skin.height, ax + 8, ay + 4, 14, a(0xFFFFFFFF, al));
        text(g, name, ax + 28, ay + 7, 1f, a(0xFFFFFFFF, al));
        // a small arrow
        float cx = ax + aw - 13, cy = ay + 10;
        for (int i = 0; i < 3; i++) g.fill(Math.round(cx + i), Math.round(cy + i), Math.round(cx + 5 - i), Math.round(cy + i + 1), a(0xFFCBD5E6, al));
        if (ah && clicked) { click(); accountOpen = !accountOpen; swallowClick(); }
    }

    void nav(int i) {
        switch (i) {
            case 0 -> page = 0;
            case 1 -> { page = 1; left = 0; }
            case 2 -> { page = 2; left = 0; try { loadWorlds(); } catch (Throwable ignored) {} }
            case 3 -> { left = left == 0 ? 1 : 0; if (left != 0) page = 0; }
            case 4 -> leave(() -> MC.setScreen(minecraft, CardMenuScreen.current()), 0.14f);
            case 5 -> { page = 3; left = 0; }
            default -> {}
        }
    }

    /** The vanilla options screen (its constructor gained a third argument in one game version). */
    Screen options() {
        try {
            for (var c : net.minecraft.client.gui.screens.options.OptionsScreen.class.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2) return (Screen) c.newInstance(this, minecraft.options);
                if (p.length == 3 && p[2] == boolean.class) return (Screen) c.newInstance(this, minecraft.options, false);
            }
        } catch (Throwable ignored) {}
        return this;
    }

    void leave(Runnable r, float seconds) {
        if (leaveRun != null) return;
        leaveRun = r; leaveAt = System.currentTimeMillis(); leaveSeconds = seconds;
        accountOpen = false;
    }

    // ───────────────────────── account ─────────────────────────

    void accountMenu(Gfx g, MenuBridge.State st, boolean live, int accent, long now) {
        List<MenuBridge.Account> list = live ? st.accounts : new ArrayList<>();
        String me = minecraft.getUser().getProfileId().toString().replace("-", "");
        int rows = Math.max(1, list.size());
        float w = 176, rowH = 24, h = 8 + rows * rowH + (live ? 0 : 16) + 8;
        float x = VW - M - 22 - 8 - w, y = 34;
        float k = ease("acctmenu", true, 18f);
        glass(g, x, y - 4 * (1 - k), w, h, 6, k);
        float ry = y + 8 - 4 * (1 - k);
        if (list.isEmpty()) {
            MenuTextures.Tex skin = MenuTextures.skin(me);
            Doll.face(g, skin.id, skin.height, x + 10, ry + 5, 14, a(0xFFFFFFFF, k));
            text(g, minecraft.getUser().getName(), x + 30, ry + 4, 1f, a(0xFFFFFFFF, k));
            text(g, "Playing", x + 30, ry + 14, 1f, a(accent, k));
            ry += rowH;
        }
        for (MenuBridge.Account acc : list) {
            boolean current = acc.uuid.replace("-", "").equalsIgnoreCase(me);
            boolean hov = !current && over(x + 4, ry, w - 8, rowH);
            float hv = ease("acc:" + acc.uuid, hov, 16f);
            if (hv > 0.02f) rect(g, x + 4, ry, w - 8, rowH, 4, a(0x26FFFFFF, hv * k));
            MenuTextures.Tex skin = MenuTextures.skin(acc.uuid);
            Doll.face(g, skin.id, skin.height, x + 10, ry + 5, 14, a(0xFFFFFFFF, k));
            text(g, fit(acc.name, w - 44, 1f), x + 30, ry + 4, 1f, a(0xFFFFFFFF, k));
            boolean switching = switchReq != 0 && acc.name.equals(switchName);
            String sub = current ? "Playing" : switching ? "Switching…" : acc.expired ? "Sign in again in Icey Client" : "Switch to this account";
            text(g, sub, x + 30, ry + 14, 1f, a(current ? accent : acc.expired ? 0xFFE0A070 : 0xFF9FB0C8, k));
            if (hov && clicked && !acc.expired && switchReq == 0) {
                click();
                switchReq = MenuBridge.request("switch", "uuid", acc.uuid);
                switchAt = now; switchName = acc.name;
                swallowClick();
            }
            ry += rowH;
        }
        if (!live) text(g, "Open Icey Client to switch accounts", x + 10, ry + 4, 1f, a(0xFF9FB0C8, k));
        if (clicked && over(x, y, w, h)) swallowClick();
    }

    /** The launcher answered a "switch account" request (or took too long). */
    void accountAnswer(long now) {
        if (switchReq == 0) return;
        JsonObject o = MenuBridge.takeAccountAnswer();
        if (o == null) {
            if (now - switchAt > 20000) { switchReq = 0; toast("Could not switch", "Icey Client did not answer"); }
            return;
        }
        long id = 0;
        try { id = o.get("id").getAsLong(); } catch (Throwable ignored) {}
        if (id != switchReq) return;
        switchReq = 0;
        try {
            if (o.has("error")) { toast("Could not switch", "Sign in to that account again in Icey Client"); return; }
            String err = AccountSwitch.apply(o.get("name").getAsString(), o.get("uuid").getAsString(), o.get("token").getAsString());
            if (err != null) toast("Could not switch", err);
            else { toast("Now playing as " + o.get("name").getAsString(), ""); accountOpen = false; }
        } catch (Throwable t) {
            toast("Could not switch", "");
        }
    }

    // ───────────────────────── play panel ─────────────────────────

    void playPanel(Gfx g, float show, int accent, boolean busy) {
        float x = pX, y = pY, w = pW, h = pH;
        glass(g, x, y, w, h, 6, show);
        float al = show;
        // Servers | Worlds
        float tx = x + 10, ty = y + 10, tw = w - 20, th = 22;
        rect(g, tx, ty, tw, th, 5, a(0x52000000, al));
        float slide = ease("tab", tab == 1, 16f);
        rect(g, tx + 2 + slide * (tw / 2f - 2), ty + 2, tw / 2f - 2, th - 4, 4, a(accent, al));
        String[] tabs = { "Servers", "Worlds" };
        for (int i = 0; i < 2; i++) {
            float cx = tx + tw / 4f + i * tw / 2f;
            float sel = i == 0 ? 1f - slide : slide;
            textC(g, (sel > 0.5f ? "§l" : "") + tabs[i], cx, ty + 7, 1f, a(mix(0xFFC3CEDF, 0xFF06101C, sel), al));
            if (!busy && clicked && over(tx + i * tw / 2f, ty, tw / 2f, th) && tab != i) { click(); tab = i; lastTab = i; swallowClick(); }
        }

        List<Entry> list = tab == 0 ? servers : worlds;
        int sel = tab == 0 ? selServer : selWorld;
        float scroll = tab == 0 ? scrollS : scrollW;
        float ly = y + 40, lh = 104, rowH = 26;
        float maxScroll = Math.max(0, list.size() * rowH - lh);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (tab == 0) scrollS = scroll; else scrollW = scroll;

        if (list.isEmpty()) {
            String a1 = tab == 0 ? "No servers yet" : worldsLoaded ? "No worlds yet" : "Loading worlds…";
            String a2 = tab == 0 ? "Add one under All servers" : worldsLoaded ? "Create one under All worlds" : "";
            textC(g, "§l" + a1, x + w / 2f, ly + 34, 1.25f, a(0xFFFFFFFF, al));
            textC(g, a2, x + w / 2f, ly + 52, 1f, a(0xFF9FB0C8, al));
        } else {
            g.enableScissor(Math.round(x + 6), Math.round(ly), Math.round(x + w - 6), Math.round(ly + lh));
            try {
                for (int i = 0; i < list.size(); i++) {
                    float ry = ly + i * rowH - scroll;
                    if (ry + rowH < ly || ry > ly + lh) continue;
                    Entry e = list.get(i);
                    boolean hov = !busy && !accountOpen && over(x + 8, Math.max(ry, ly), w - 16, Math.min(rowH, ly + lh - ry)) && my >= ly && my < ly + lh;
                    float hv = ease("row" + tab + ":" + i, hov, 16f);
                    float sv = ease("sel" + tab + ":" + i, i == sel, 16f);
                    if (sv > 0.02f || hv > 0.02f) rect(g, x + 8, ry + 1, w - 16, rowH - 2, 4, a(0xFFFFFFFF, (0.08f * hv + 0.13f * sv) * al));
                    if (sv > 0.02f) rect(g, x + 8, ry + 6 + (1 - sv) * 6, 2, (rowH - 12) * sv, 1, a(accent, al));
                    icon(g, e, x + 15, ry + 3, 20, al);
                    String line2 = e.sub;
                    int line2Col = 0xFF9FB0C8;
                    if (e.server != null) {
                        com.iceymod.menu.ServerPing.Result ping = com.iceymod.menu.ServerPing.get(e.server.ip);
                        if (e.icon == null && ping.icon != null) e.icon = MenuTextures.fromBytes(e.server.ip, ping.icon);
                        if (ping.state == 1) { line2 = ping.online + " online  ·  " + ping.ms + " ms"; line2Col = 0xFF8FD8A8; }
                        else if (ping.state == 2) line2 = "No answer";
                        else line2 = e.title().equals(e.server.ip) ? "Checking…" : e.server.ip;
                    }
                    text(g, fit(e.title(), w - 60, 1f), x + 41, ry + 4, 1f, a(e.disabled ? 0xFF8C96A8 : 0xFFFFFFFF, al));
                    text(g, fit(line2, w - 60, 1f), x + 41, ry + 14, 1f, a(line2Col, al));
                    if (hov && clicked) {
                        long nowMs = System.currentTimeMillis();
                        boolean twice = lastRowIndex == i && nowMs - lastRowClick < 350;
                        lastRowIndex = i; lastRowClick = nowMs;
                        if (tab == 0) selServer = i; else selWorld = i;
                        click();
                        if (twice) play();
                        swallowClick();
                    }
                }
            } finally { g.disableScissor(); }
            if (maxScroll > 0) {
                float bar = Math.max(14, lh * lh / (list.size() * rowH));
                rect(g, x + w - 6, ly + (lh - bar) * (scroll / maxScroll), 2, bar, 1, a(0x66FFFFFF, al));
            }
        }

        // The big button
        Entry cur = list.isEmpty() ? null : list.get(Math.min(sel, list.size() - 1));
        float bx = x + 10, by = y + 152, bw = w - 20, bh = 30;
        boolean can = cur != null && !cur.disabled;
        boolean bh2 = can && !busy && !accountOpen && over(bx, by, bw, bh);
        float bv = ease("playbtn", bh2, 14f);
        float press = ease("playpress", bh2 && down, 30f);
        int fill = can ? mix(accent, 0xFFFFFFFF, 0.14f * bv) : 0xFF3A4458;
        rect(g, bx - bv + press, by - bv * 0.5f + press, bw + (bv - press) * 2, bh + (bv - press), 6, a(fill, al));
        rect(g, bx + 1, by + 1, bw - 2, 9, 5, a(0x30FFFFFF, al * (can ? 1f : 0.3f)));
        String label = cur == null ? (tab == 0 ? "Add a server" : "Create a world") : "Play " + cur.title();
        textC(g, fit("§l" + label, bw - 16, 1.25f), bx + bw / 2f, by + 10, 1.25f, a(can ? 0xFF06101C : 0xFF9AA5B8, al));
        if (bh2 && clicked) { click(); play(); swallowClick(); }
        if (cur == null && !busy && clicked && over(bx, by, bw, bh)) { click(); nav(tab == 0 ? 1 : 2); swallowClick(); }

        // Under it
        text(g, list.size() + (tab == 0 ? " saved" : list.size() == 1 ? " world" : " worlds"), x + 12, y + 189, 1f, a(0xFF9FB0C8, al));
        String all = tab == 0 ? "All servers ›" : "All worlds ›";
        float lw = textW(all, 1f);
        boolean lhv = !busy && !accountOpen && over(x + w - 12 - lw - 4, y + 184, lw + 8, 16);
        float lv = ease("all", lhv, 14f);
        text(g, all, x + w - 12 - lw, y + 189, 1f, a(mix(0xFFC3CEDF, accent, lv), al));
        if (lhv && clicked) { click(); nav(tab == 0 ? 1 : 2); swallowClick(); }
    }

    void icon(Gfx g, Entry e, float x, float y, float size, float al) {
        if (e.icon != null) {
            g.pose().pushMatrix();
            try {
                g.pose().translate(x, y);
                g.pose().scale(size / e.icon.width, size / e.icon.height);
                g.blit(e.icon.id, 0, 0, 0f, 0f, e.icon.width, e.icon.height, e.icon.width, e.icon.height, a(0xFFFFFFFF, al));
            } finally { g.pose().popMatrix(); }
        } else {
            rect(g, x, y, size, size, 4, a(0x40FFFFFF, al));
            String c = e.title().isEmpty() ? "?" : e.title().substring(0, 1).toUpperCase();
            textC(g, "§l" + c, x + size / 2f, y + size / 2f - 4, 1f, a(0xFFFFFFFF, al));
        }
    }

    void play() {
        List<Entry> list = tab == 0 ? servers : worlds;
        if (list.isEmpty()) return;
        Entry e = list.get(Math.min(tab == 0 ? selServer : selWorld, list.size() - 1));
        if (e.disabled) return;
        if (e.server != null) join(e.server); else playWorld(e);
    }

    void join(ServerData server) {
        leave(() -> {
            try { ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(server.ip), server, false, null); }
            catch (Throwable t) { toast("Could not join", String.valueOf(t.getMessage())); }
        }, 0.26f);
    }

    void playWorld(Entry e) {
        if (e.worldId == null || e.disabled) return;
        leave(() -> {
            try { minecraft.createWorldOpenFlows().openWorld(e.worldId, () -> MC.setScreen(minecraft, this)); }
            catch (Throwable t) { toast("Could not open the world", String.valueOf(t.getMessage())); }
        }, 0.26f);
    }

    // ───────────────────────── friends ─────────────────────────

    void openChat(String uuid, String name) {
        left = 2;
        if (!uuid.equals(chatUuid)) { chatScroll = 0; pending.clear(); input.setValue(""); }
        chatUuid = uuid; chatName = name;
    }

    void friendsPanel(Gfx g, MenuBridge.State st, boolean live, float show, int accent, float openK, float delta) {
        float x = lX, y = lY, w = lW, h = lH, al = show;
        glass(g, x, y, w, h, 6, al);
        boolean wantInput = false;
        String hint = "";
        float inX = 0, inY = 0, inW = 0;

        int online = 0, unread = 0;
        for (MenuBridge.Friend f : st.friends) { if (f.online) online++; unread += f.unread; }
        unread += st.incoming.size();

        if (left == 0 || openK < 0.5f) {
            // The small bar: who is on, how many unread.
            float a2 = al * (1f - Math.min(1f, openK * 2f));
            boolean hov = left == 0 && over(x, y, w, h) && !accountOpen;
            float hv = ease("chip", hov, 14f);
            if (hv > 0.02f) ring(g, x, y, w, h, 6, a(accent, hv * a2 * 0.8f));
            float fx = x + 10;
            int shown = 0;
            if (live) for (MenuBridge.Friend f : st.friends) {
                if (!f.online || shown >= 3) continue;
                MenuTextures.Tex skin = MenuTextures.skin(f.uuid);
                Doll.face(g, skin.id, skin.height, fx, y + 9, 16, a(0xFFFFFFFF, a2));
                fx += 12; shown++;
            }
            if (shown > 0) fx += 10;
            text(g, "§lFriends", fx, y + 8, 1f, a(0xFFFFFFFF, a2));
            String sub = !live ? "Open Icey Client to see them" : st.error.isEmpty() ? (online == 0 ? "Nobody online" : online + " online") : "Sign in with Microsoft in Icey Client";
            text(g, fit(sub, w - (fx - x) - 34, 1f), fx, y + 19, 1f, a(online > 0 && live ? 0xFF4ADE80 : 0xFF9FB0C8, a2));
            if (unread > 0 && live) {
                String c = String.valueOf(Math.min(99, unread));
                float bw = Math.max(12, textW(c, 1f) + 8);
                rect(g, x + w - 24 - bw, y + 11, bw, 12, 5, a(accent, a2));
                textC(g, "§l" + c, x + w - 24 - bw / 2f, y + 13, 1f, a(0xFF06101C, a2));
            }
            text(g, "›", x + w - 14, y + 13, 1f, a(0xFFC3CEDF, a2));
            if (hov && clicked) { click(); left = 1; swallowClick(); }
        }
        if (left != 0 && openK > 0.35f) {
            float a2 = al * Math.min(1f, (openK - 0.35f) / 0.5f);
            g.enableScissor(Math.round(x + 1), Math.round(y + 1), Math.round(x + w - 1), Math.round(y + h - 1));
            try {
                if (left == 1) {
                    text(g, "§lFriends", x + 12, y + 11, 1.25f, a(0xFFFFFFFF, a2));
                    if (closeButton(g, x + w - 26, y + 7, "×", a2)) { left = 0; }
                    float ry = y + 34;
                    float bottom = y + h - 34;
                    if (!live) {
                        textC(g, "Open Icey Client", x + w / 2f, y + h / 2f - 16, 1.25f, a(0xFFFFFFFF, a2));
                        textC(g, "Friends show up here while", x + w / 2f, y + h / 2f + 2, 1f, a(0xFF9FB0C8, a2));
                        textC(g, "the launcher is running.", x + w / 2f, y + h / 2f + 13, 1f, a(0xFF9FB0C8, a2));
                    } else if (!st.error.isEmpty()) {
                        textC(g, "Not signed in", x + w / 2f, y + h / 2f - 16, 1.25f, a(0xFFFFFFFF, a2));
                        textC(g, "Friends need a Microsoft account", x + w / 2f, y + h / 2f + 2, 1f, a(0xFF9FB0C8, a2));
                        textC(g, "signed in to Icey Client.", x + w / 2f, y + h / 2f + 13, 1f, a(0xFF9FB0C8, a2));
                    } else {
                        // Requests first
                        for (int i = 0; i < st.incoming.size() && i < 2; i++) {
                            MenuBridge.Friend f = st.incoming.get(i);
                            rect(g, x + 8, ry, w - 16, 24, 4, a(0x22FFFFFF, a2));
                            MenuTextures.Tex skin = MenuTextures.skin(f.uuid);
                            Doll.face(g, skin.id, skin.height, x + 13, ry + 5, 14, a(0xFFFFFFFF, a2));
                            text(g, fit(f.name, w - 110, 1f), x + 32, ry + 4, 1f, a(0xFFFFFFFF, a2));
                            text(g, "wants to be friends", x + 32, ry + 14, 1f, a(0xFF9FB0C8, a2));
                            if (pill(g, x + w - 64, ry + 5, 30, 14, "Add", accent, true, a2)) MenuBridge.request("accept", "uuid", f.uuid);
                            if (pill(g, x + w - 30, ry + 5, 18, 14, "×", 0xFF3A4458, false, a2)) MenuBridge.request("decline", "uuid", f.uuid);
                            ry += 27;
                        }
                        float lh2 = bottom - ry, rowH = 26;
                        List<MenuBridge.Friend> list = new ArrayList<>(st.friends);
                        list.sort((a3, b3) -> a3.online != b3.online ? (a3.online ? -1 : 1) : a3.name.compareToIgnoreCase(b3.name));
                        float maxScroll = Math.max(0, list.size() * rowH - lh2);
                        listScroll = Math.max(0, Math.min(maxScroll, listScroll));
                        if (list.isEmpty()) {
                            textC(g, "No friends yet", x + w / 2f, ry + lh2 / 2f - 12, 1.25f, a(0xFFFFFFFF, a2));
                            textC(g, "Add one by name below.", x + w / 2f, ry + lh2 / 2f + 6, 1f, a(0xFF9FB0C8, a2));
                        }
                        g.enableScissor(Math.round(x + 1), Math.round(ry), Math.round(x + w - 1), Math.round(bottom));
                        try {
                            for (int i = 0; i < list.size(); i++) {
                                MenuBridge.Friend f = list.get(i);
                                float fy = ry + i * rowH - listScroll;
                                if (fy + rowH < ry || fy > bottom) continue;
                                boolean hov = over(x + 8, fy, w - 16, rowH) && my >= ry && my < bottom && !accountOpen;
                                float hv = ease("fr:" + f.uuid, hov, 16f);
                                if (hv > 0.02f) rect(g, x + 8, fy + 1, w - 16, rowH - 2, 4, a(0x1FFFFFFF, hv * a2));
                                MenuTextures.Tex skin = MenuTextures.skin(f.uuid);
                                Doll.face(g, skin.id, skin.height, x + 13, fy + 5, 16, a(f.online ? 0xFFFFFFFF : 0xFF8E96A6, a2));
                                text(g, fit(f.name, w - 80, 1f), x + 35, fy + 5, 1f, a(0xFFFFFFFF, a2));
                                String status = f.online ? (f.inGame ? "In game" : "Online") : f.lastSeen > 0 ? "Seen " + ago(f.lastSeen) : "Offline";
                                text(g, status, x + 35, fy + 15, 1f, a(f.online ? 0xFF4ADE80 : 0xFF8C96A8, a2));
                                if (f.unread > 0) {
                                    String c = String.valueOf(Math.min(99, f.unread));
                                    float bw = Math.max(12, textW(c, 1f) + 8);
                                    rect(g, x + w - 14 - bw, fy + 7, bw, 12, 5, a(accent, a2));
                                    textC(g, "§l" + c, x + w - 14 - bw / 2f, fy + 9, 1f, a(0xFF06101C, a2));
                                }
                                if (hov && clicked) { click(); openChat(f.uuid, f.name); swallowClick(); }
                            }
                        } finally { g.disableScissor(); }
                        // Add by name
                        rect(g, x + 8, y + h - 30, w - 16 - 40, 22, 4, a(0x52000000, a2));
                        wantInput = true; hint = "Add a friend by name";
                        inX = x + 14; inY = y + h - 23; inW = w - 16 - 40 - 12;
                        if (pill(g, x + w - 44, y + h - 30, 36, 22, "Add", accent, true, a2)) addFriend();
                    }
                } else {
                    // A conversation
                    MenuBridge.Friend friend = null;
                    for (MenuBridge.Friend f : st.friends) if (f.uuid.equals(chatUuid)) friend = f;
                    if (closeButton(g, x + 6, y + 7, "‹", a2)) { left = 1; }
                    MenuTextures.Tex skin = MenuTextures.skin(chatUuid);
                    Doll.face(g, skin.id, skin.height, x + 30, y + 8, 18, a(0xFFFFFFFF, a2));
                    text(g, "§l" + chatName, x + 54, y + 8, 1f, a(0xFFFFFFFF, a2));
                    boolean on = friend != null && friend.online;
                    text(g, friend == null ? "" : on ? (friend.inGame ? "In game" : "Online") : "Offline", x + 54, y + 19, 1f, a(on ? 0xFF4ADE80 : 0xFF8C96A8, a2));
                    g.fill(Math.round(x + 8), Math.round(y + 33), Math.round(x + w - 8), Math.round(y + 34), a(0x26FFFFFF, a2));
                    float top = y + 38, bottom = y + h - 36;
                    chatBody(g, st, x, top, w, bottom, accent, a2);
                    rect(g, x + 8, y + h - 30, w - 16 - 44, 22, 4, a(0x52000000, a2));
                    wantInput = true; hint = "Message " + chatName;
                    inX = x + 14; inY = y + h - 23; inW = w - 16 - 44 - 12;
                    if (pill(g, x + w - 48, y + h - 30, 40, 22, "Send", accent, true, a2)) sendChat();
                }
            } finally { g.disableScissor(); }
        }

        if (wantInput && openK > 0.8f && leaveRun == null) {
            if (focus < 0) focus = 0;
            field(g, 0, inX, inY, inW, hint, delta);
            if (enterHit && focus == 0) { if (left == 2) sendChat(); else addFriend(); }
        }
        if (clicked && over(x, y, w, h)) swallowClick();
    }

    boolean closeButton(Gfx g, float x, float y, String glyph, float al) {
        boolean hov = over(x, y, 20, 20) && !accountOpen;
        float hv = ease("cb" + glyph, hov, 16f);
        rect(g, x, y, 20, 20, 5, a(0xFFFFFFFF, (0.06f + 0.12f * hv) * al));
        textC(g, glyph, x + 10, y + 6, 1f, a(0xFFFFFFFF, al));
        if (hov && clicked) { click(); swallowClick(); return true; }
        return false;
    }

    static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
    /** The accent colour, lightened: for small icons and labels on the dark glass. */
    static int lighter(int accent) { return mix(0xFF000000 | accent, 0xFFFFFFFF, 0.35f); }
    /** Ink that reads on the accent colour: dark on a light accent, white on a deep one. */
    static int ink(int accent) {
        float lum = (0.299f * ((accent >> 16) & 0xFF) + 0.587f * ((accent >> 8) & 0xFF) + 0.114f * (accent & 0xFF)) / 255f;
        return lum > 0.56f ? 0xFF06101C : 0xFFFFFFFF;
    }

    /** The top of a page: its name and a line under it. */
    void pageHead(Gfx g, float x, float y, String title, String sub, float al) {
        text(g, "§l" + title, x + 18, y + 14, 1.5f, a(0xFFFFFFFF, al));
        text(g, sub, x + 18, y + 32, 1f, a(0xFF9FB0C8, al));
    }

    float buttonW(char icon, String label) { return textW("§l" + label, 1f) + 22 + (icon != 0 && iconsUsable() ? 15 : 0); }

    /** A button with an icon and a label. {@code style}: 0 = quiet, 1 = the main action (accent colour), 2 = something that deletes. */
    boolean button(Gfx g, float x, float y, float w, float h, char icon, String label, int style, float al) {
        int accent = MenuPrefs.accent();
        boolean hov = over(x, y, w, h) && !accountOpen;
        String key = label + Math.round(x / 8f) + ":" + Math.round(y / 30f);
        float hv = ease("btn" + key, hov, 16f), sv = ease("btnS" + key, style == 1, 14f);
        int fill = style == 2 ? mix(0xFFD9434A, 0xFFFFFFFF, 0.14f * hv)
                : mix(mix(0x24FFFFFF, 0x40FFFFFF, hv), mix(0xFF000000 | accent, 0xFFFFFFFF, 0.16f * hv), sv);
        rect(g, x, y, w, h, 6, a(fill, al));
        if (style != 2 && sv < 0.98f) ring(g, x, y, w, h, 6, a(0x30FFFFFF, al * (1f - sv)));
        int inkC = style == 2 ? 0xFFFFFFFF : mix(0xFFFFFFFF, ink(accent), sv);
        boolean has = icon != 0 && iconsUsable();
        float cw = textW("§l" + label, 1f) + (has ? 15 : 0), cx = x + (w - cw) / 2f;
        if (has) { icon(g, icon, cx + 5.5f, y + h / 2f, 11, a(inkC, al)); cx += 15; }
        text(g, "§l" + label, cx, y + (h - 8) / 2f + 0.5f, 1f, a(inkC, al));
        if (hov && clicked) { click(); swallowClick(); return true; }
        return false;
    }

    /** A square button with only an icon; its name shows over it under the mouse. {@code strength}: how present it is (rows dim theirs until hovered). */
    boolean iconButton(Gfx g, float x, float y, float size, char icon, String tip, boolean danger, float strength, float al) {
        boolean hov = over(x, y, size, size) && !accountOpen;
        float hv = ease("ib" + tip + Math.round(x / 8f) + ":" + Math.round(y / 30f), hov, 16f);
        rect(g, x, y, size, size, 6, a(danger ? mix(0x1EFFFFFF, 0xFFD9434A, hv) : mix(0x1EFFFFFF, 0x48FFFFFF, hv), al * strength));
        if (iconsUsable()) icon(g, icon, x + size / 2f, y + size / 2f, 12, a(mix(0xFFC8D2E2, 0xFFFFFFFF, hv), al * strength));
        else textC(g, "§l" + tip.substring(0, 1), x + size / 2f, y + size / 2f - 4, 1f, a(0xFFFFFFFF, al * strength));
        if (hv > 0.35f) {
            float tw = textW(tip, 1f) + 12;
            rect(g, x + size / 2f - tw / 2f, y - 19, tw, 15, 4, a(0xF00A0E1A, hv * al));
            textC(g, tip, x + size / 2f, y - 15, 1f, a(0xFFFFFFFF, hv * al));
        }
        if (hov && clicked) { click(); swallowClick(); return true; }
        return false;
    }

    boolean pill(Gfx g, float x, float y, float w, float h, String label, int color, boolean dark, float al) {
        boolean hov = over(x, y, w, h) && !accountOpen;
        float hv = ease("pill" + label + Math.round(x) + ":" + Math.round(y / 40f), hov, 16f);
        rect(g, x, y, w, h, 4, a(mix(color, 0xFFFFFFFF, 0.16f * hv), al));
        textC(g, "§l" + label, x + w / 2f, y + (h - 8) / 2f + 0.5f, 1f, a(dark ? 0xFF06101C : 0xFFFFFFFF, al));
        if (hov && clicked) { click(); swallowClick(); return true; }
        return false;
    }

    void chatBody(Gfx g, MenuBridge.State st, float x, float top, float w, float bottom, int accent, float al) {
        // Lines of every message, oldest first; drawn from the bottom up.
        record Bubble(List<String> lines, boolean mine, boolean waiting) {}
        List<Bubble> bubbles = new ArrayList<>();
        float maxW = w - 62;
        long now = System.currentTimeMillis();
        if (st.chatWith.equals(chatUuid)) {
            for (MenuBridge.Message m : st.chat) bubbles.add(new Bubble(wrap(m.text, maxW), m.mine, false));
            // A sent line waits here until the service has it.
            pending.removeIf(p -> now - p.at > 20000 || st.chat.stream().anyMatch(m -> m.mine && m.text.equals(p.text) && m.at >= p.at - 5000));
        }
        for (Pending p : pending) bubbles.add(new Bubble(wrap(p.text, maxW), true, true));
        if (bubbles.isEmpty()) {
            textC(g, "Say hi to " + chatName, x + w / 2f, (top + bottom) / 2f - 4, 1f, a(0xFF9FB0C8, al));
            return;
        }
        float total = 0;
        for (Bubble b : bubbles) total += b.lines.size() * 10 + 8 + 4;
        float view = bottom - top;
        float maxScroll = Math.max(0, total - view);
        chatScroll = Math.max(0, Math.min(maxScroll, chatScroll));
        g.enableScissor(Math.round(x + 1), Math.round(top), Math.round(x + w - 1), Math.round(bottom));
        try {
            float y = bottom + chatScroll;
            for (int i = bubbles.size() - 1; i >= 0; i--) {
                Bubble b = bubbles.get(i);
                float bh = b.lines.size() * 10 + 8;
                y -= bh + 4;
                if (y > bottom || y + bh < top) continue;
                float bw = 0;
                for (String l : b.lines) bw = Math.max(bw, textW(l, 1f));
                bw += 14;
                float bx = b.mine ? x + w - 10 - bw : x + 10;
                rect(g, bx, y, bw, bh, 5, a(b.mine ? mix(0xFF0A1020, accent, 0.55f) : 0x38FFFFFF, al * (b.waiting ? 0.55f : 1f)));
                for (int l = 0; l < b.lines.size(); l++) text(g, b.lines.get(l), bx + 7, y + 5 + l * 10, 1f, a(0xFFFFFFFF, al * (b.waiting ? 0.7f : 1f)));
            }
        } finally { g.disableScissor(); }
    }

    List<String> wrap(String text, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (textW(word, 1f) > maxW && word.length() > 1) {             // one very long word
                int cut = word.length() - 1;
                while (cut > 1 && textW(word.substring(0, cut), 1f) > maxW) cut--;
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (textW(tryLine, 1f) > maxW && line.length() > 0) { out.add(line.toString()); line.setLength(0); line.append(word); }
            else { line.setLength(0); line.append(tryLine); }
            if (out.size() > 12) break;
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    void sendChat() {
        String text = input.getValue().trim();
        if (text.isEmpty() || chatUuid.isEmpty()) return;
        if (!MenuBridge.live()) { toast("Not sent", "Open Icey Client first"); return; }
        MenuBridge.sendMessage(chatUuid, chatName, text);
        Pending p = new Pending(); p.text = text; p.at = System.currentTimeMillis();
        pending.add(p);
        input.setValue("");
        chatScroll = 0;
    }

    void addFriend() {
        String name = input.getValue().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { if (!name.isEmpty()) toast("Not a Minecraft name", name); return; }
        MenuBridge.request("add", "name", name);
        input.setValue("");
        toast("Asking " + name, "They will see your request");
    }

    // ───────────────────────── bottom, toast, input ─────────────────────────

    /** Under the brand: where we are, and the two small links. */
    void footer(Gfx g, float show, int accent) {
        float x = M, y = 33 - 18f * (1f - show);
        String[] parts = { scene.name, "Another place", "Menu style" };
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            float w = textW(p, 1f);
            boolean link = i > 0;
            boolean hov = link && leaveRun == null && !accountOpen && over(x - 4, y - 4, w + 8, 16);
            float hv = ease("foot" + i, hov, 14f);
            text(g, p, x + 0.5f, y + 1f, 1f, a(0x8C050810, show));           // a soft dark edge: the sky behind can be bright
            text(g, p, x, y, 1f, a(link ? mix(0xFFE2E9F4, accent, hv) : 0xFFC2CCDC, show));
            if (hv > 0.02f) g.fill(Math.round(x), Math.round(y + 9), Math.round(x + w * hv), Math.round(y + 10), a(accent, show));
            if (hov && clicked) {
                click();
                if (i == 1) { if (swapAt == 0) { swapAt = System.currentTimeMillis(); swapped = false; } }
                else leave(() -> MC.setScreen(minecraft, new MenuChooserScreen()), 0.14f);
                swallowClick();
            }
            x += w;
            if (i < parts.length - 1) { text(g, "·", x + 5, y, 1f, a(0xFF6F7B90, show)); x += 14; }
        }
    }

    void toast(String title, String text) {
        toastTitle = title == null ? "" : title; toastText = text == null ? "" : text; toastAt = System.currentTimeMillis();
    }

    void drawToast(Gfx g, long now) {
        if (toastTitle.isEmpty()) return;
        float age = (now - toastAt) / 1000f;
        if (age > 4.2f) { toastTitle = ""; return; }
        float k = Math.min(outCubic(age / 0.3f), outCubic((4.2f - age) / 0.3f));
        String l2 = fit(toastText, 260, 1f);
        float w = Math.max(textW("§l" + toastTitle, 1f), textW(l2, 1f)) + 24, h = l2.isEmpty() ? 22 : 33;
        float x = (VW - w) / 2f, y = 42 - 10 * (1 - k);
        glass(g, x, y, w, h, 6, k);
        g.fill(Math.round(x + 6), Math.round(y + 6), Math.round(x + 8), Math.round(y + h - 6), a(MenuPrefs.accent(), k));
        text(g, "§l" + toastTitle, x + 14, y + 7, 1f, a(0xFFFFFFFF, k));
        if (!l2.isEmpty()) text(g, l2, x + 14, y + 19, 1f, a(0xFFB8C4D6, k));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float vx = (float) mouseX / s, vy = (float) mouseY / s;
        float step = (float) vertical * 22f;
        if (vx >= lX && vx < lX + lW && vy >= lY && vy < lY + lH && (left != 0 || page == 0)) {
            if (left == 2) chatScroll += step; else listScroll -= step;
            return true;
        }
        if (page != 0) {
            if (page == 1) serversPage.scroll(step); else if (page == 2) worldsPage.scroll(step); else optionsPage.scroll(step);
            return true;
        }
        if (vx >= pX && vx < pX + pW && vy >= pY && vy < pY + pH) {
            if (tab == 0) scrollS -= step; else scrollW -= step;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** Esc steps back out of what is open; the menu itself has nothing to close to. */
    @Override
    public void onClose() {
        if (accountOpen) accountOpen = false;
        else if (left == 2) left = 1;
        else if (left == 1) left = 0;
        else if (page == 1 && serversPage.back()) { }
        else if (page == 2 && worldsPage.back()) { }
        else if (page != 0) page = 0;
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
