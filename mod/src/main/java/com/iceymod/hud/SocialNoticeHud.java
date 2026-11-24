package com.iceymod.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.HudHook;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * A notice in the top right corner: a friend came online, or wrote.
 *
 * The launcher knows about friends (it is signed in to the friends
 * service); the game does not. So the launcher writes each notice into
 * config/iceymod-notice.json of the running game, and this class looks at
 * that file twice a second and shows what is new for a few seconds.
 * Drawn with the mod's own HUD layer, so it is the same code on every
 * game version.
 */
public final class SocialNoticeHud {

    private static final long SHOW_MS = 6000;
    private static final long SLIDE_MS = 250;

    private static long lastCheck;
    private static long lastModified;
    private static long shownId;
    private static String title = "";
    private static String text = "";
    private static long shownAt;
    // Who wrote last: the reply key answers them.
    private static String replyUuid = "";
    private static String replyName = "";
    private static long replyAt;
    private static boolean shownIsMessage;
    /** Set by the mod: the name of the reply key ("O"), for the hint under a message. */
    public static volatile String replyKeyName = "";

    /** { uuid, name } of the friend who wrote in the last ten minutes, or null. */
    public static String[] replyTarget() {
        if (replyUuid.isEmpty() || System.currentTimeMillis() - replyAt > 600000) return null;
        return new String[] { replyUuid, replyName };
    }

    private SocialNoticeHud() {}

    public static void register() {
        HudHook.register("social-notice", (g, tickDelta) -> {
            try { render(g); }
            catch (Throwable t) { /* never crash the HUD */ }
        });
    }

    private static void poll() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500) return;
        lastCheck = now;
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("iceymod-notice.json");
            if (!Files.exists(p)) return;
            long modified = Files.getLastModifiedTime(p).toMillis();
            if (modified == lastModified) return;
            lastModified = modified;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            long id = o.has("id") ? o.get("id").getAsLong() : 0;
            // Only a notice written just now: not one left over from an earlier session.
            if (id == shownId || now - id > 20000) return;
            shownId = id;
            title = o.has("title") ? o.get("title").getAsString() : "";
            text = o.has("text") ? o.get("text").getAsString() : "";
            shownAt = now;
            shownIsMessage = false;
            if (o.has("reply") && o.get("reply").isJsonObject()) {
                JsonObject r = o.getAsJsonObject("reply");
                String u = r.has("uuid") ? r.get("uuid").getAsString() : "";
                String n = r.has("name") ? r.get("name").getAsString() : "";
                if (u.matches("[0-9a-fA-F-]{32,36}") && n.matches("[A-Za-z0-9_]{1,16}")) {
                    replyUuid = u; replyName = n; replyAt = now; shownIsMessage = true;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void render(Gfx g) {
        poll();
        if (title.isEmpty()) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MS) { title = ""; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        String line2 = text;
        int maxText = 190;
        if (mc.font.width(line2) > maxText) line2 = mc.font.plainSubstrByWidth(line2, maxText - mc.font.width("...")) + "...";
        String line1 = title;
        if (mc.font.width(line1) > maxText) line1 = mc.font.plainSubstrByWidth(line1, maxText - mc.font.width("...")) + "...";

        boolean two = !line2.isEmpty();
        String hint = shownIsMessage && !replyKeyName.isEmpty() ? "Press " + replyKeyName + " to reply" : "";
        int w = Math.max(Math.max(mc.font.width(line1), mc.font.width(line2)), mc.font.width(hint)) + 16;
        int h = (two ? 30 : 19) + (hint.isEmpty() ? 0 : 11);
        // Slides in from the right edge, and out again at the end.
        float in = Math.min(1f, age / (float) SLIDE_MS);
        float out = Math.min(1f, (SHOW_MS - age) / (float) SLIDE_MS);
        float k = Math.min(in, out);
        int x = g.guiWidth() - 6 - Math.round(w * k) + Math.round((1f - k) * 6);
        int y = 6;

        g.fill(x, y, x + w, y + h, 0xE00E1424);
        g.fill(x, y, x + 2, y + h, 0xFF5BC8F5);
        g.drawString(mc.font, line1, x + 8, y + 5, 0xFFFFFFFF);
        if (two) g.drawString(mc.font, line2, x + 8, y + 17, 0xFFB8C4D6);
        if (!hint.isEmpty()) g.drawString(mc.font, hint, x + 8, y + h - 12, 0xFF5BC8F5);
    }
}
