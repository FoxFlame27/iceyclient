package com.iceymod.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * What the main menu knows about friends and accounts.
 *
 * The game is not signed in to the friends service and holds no other
 * account's sign-in; the launcher does. So the two talk through small
 * files in the config folder, as the notices and replies already do:
 *
 *   iceymod-menu-open.json   written here every two seconds while the menu
 *                            is on screen, so the launcher only asks the
 *                            service when someone is looking
 *   iceymod-social.json      the launcher's answer: friends, requests, the
 *                            open conversation, the accounts
 *   iceymod-menu-req-N.json  something the player asked for here (switch
 *                            account, answer a request, add a friend)
 *   iceymod-account.json     the launcher's answer to "switch account"
 */
public final class MenuBridge {
    private MenuBridge() {}

    public static final class Friend {
        public String uuid = "", name = "";
        public boolean online, inGame, slim;
        public int unread;
        public long lastSeen;
    }
    public static final class Account {
        public String uuid = "", name = "";
        public boolean active, slim, expired;
    }
    public static final class Message {
        public long id, at;
        public boolean mine;
        public String text = "";
    }
    public static final class State {
        public long at;
        public String error = "";
        public String meUuid = "", meName = "";
        public final List<Friend> friends = new ArrayList<>();
        public final List<Friend> incoming = new ArrayList<>();
        public final List<Account> accounts = new ArrayList<>();
        public String chatWith = "";
        public final List<Message> chat = new ArrayList<>();
        public final Map<String, Boolean> slim = new HashMap<>();
    }

    private static State state = new State();
    private static long lastPoll, lastModified, lastBeat, lastNoticeModified, shownNotice;
    private static String beatChat = null;

    /** A line for the menu to show for a few seconds (a friend came online, a message could not be sent). */
    public static volatile String[] notice;
    public static volatile long noticeAt;

    private static Path dir() { return FabricLoader.getInstance().getConfigDir(); }

    public static State state() { return state; }

    /** The launcher is running and answered within the last quarter minute. */
    public static boolean live() { return System.currentTimeMillis() - state.at < 15000; }

    /** Call every frame while the menu is on screen. */
    public static void poll(String openChat) {
        long now = System.currentTimeMillis();
        String chat = openChat == null ? "" : openChat;
        if (now - lastBeat > 2000 || !chat.equals(beatChat)) {
            lastBeat = now; beatChat = chat;
            try {
                JsonObject o = new JsonObject();
                o.addProperty("at", now);
                o.addProperty("chat", chat);
                write("iceymod-menu-open", o);
            } catch (Throwable ignored) {}
        }
        if (now - lastPoll < 400) return;
        lastPoll = now;
        try {
            Path p = dir().resolve("iceymod-social.json");
            if (Files.exists(p)) {
                long m = Files.getLastModifiedTime(p).toMillis();
                if (m != lastModified) { lastModified = m; state = parse(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject()); }
            }
        } catch (Throwable ignored) {}
        try {
            Path p = dir().resolve("iceymod-notice.json");
            if (Files.exists(p)) {
                long m = Files.getLastModifiedTime(p).toMillis();
                if (m != lastNoticeModified) {
                    lastNoticeModified = m;
                    JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                    long id = o.has("id") ? o.get("id").getAsLong() : 0;
                    String title = str(o, "title"), text = str(o, "text");
                    if (id != shownNotice && now - id < 20000 && !title.startsWith("Sent to") && !title.equals("Message sent")) {
                        shownNotice = id;
                        notice = new String[] { title, text };
                        noticeAt = now;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (Throwable t) { return ""; }
    }
    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (Throwable t) { return false; }
    }
    private static long num(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsLong() : 0; } catch (Throwable t) { return 0; }
    }

    private static Friend friend(JsonObject o) {
        Friend f = new Friend();
        f.uuid = str(o, "uuid"); f.name = str(o, "name");
        f.online = bool(o, "online"); f.inGame = bool(o, "inGame"); f.slim = bool(o, "slim");
        f.unread = (int) num(o, "unread"); f.lastSeen = num(o, "lastSeen");
        return f;
    }

    private static State parse(JsonObject o) {
        State s = new State();
        s.at = num(o, "at");
        s.error = str(o, "error");
        if (o.has("me") && o.get("me").isJsonObject()) {
            s.meUuid = str(o.getAsJsonObject("me"), "uuid");
            s.meName = str(o.getAsJsonObject("me"), "name");
        }
        if (o.has("friends") && o.get("friends").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("friends")) if (e.isJsonObject()) {
                Friend f = friend(e.getAsJsonObject());
                if (f.uuid.matches("[0-9a-fA-F-]{32,36}") && f.name.matches("[A-Za-z0-9_]{1,16}")) s.friends.add(f);
            }
        if (o.has("incoming") && o.get("incoming").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("incoming")) if (e.isJsonObject()) {
                Friend f = friend(e.getAsJsonObject());
                if (f.uuid.matches("[0-9a-fA-F-]{32,36}") && f.name.matches("[A-Za-z0-9_]{1,16}")) s.incoming.add(f);
            }
        if (o.has("accounts") && o.get("accounts").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("accounts")) if (e.isJsonObject()) {
                JsonObject a = e.getAsJsonObject();
                Account acc = new Account();
                acc.uuid = str(a, "uuid"); acc.name = str(a, "name");
                acc.active = bool(a, "active"); acc.slim = bool(a, "slim"); acc.expired = bool(a, "expired");
                if (acc.uuid.matches("[0-9a-fA-F-]{32,36}") && acc.name.matches("[A-Za-z0-9_]{1,16}")) s.accounts.add(acc);
            }
        if (o.has("chat") && o.get("chat").isJsonObject()) {
            JsonObject c = o.getAsJsonObject("chat");
            s.chatWith = str(c, "with");
            if (c.has("messages") && c.get("messages").isJsonArray())
                for (JsonElement e : c.getAsJsonArray("messages")) if (e.isJsonObject()) {
                    JsonObject mo = e.getAsJsonObject();
                    Message m = new Message();
                    m.id = num(mo, "id"); m.at = num(mo, "at"); m.mine = bool(mo, "mine");
                    String t = str(mo, "text");
                    m.text = t.length() > 300 ? t.substring(0, 300) : t;
                    s.chat.add(m);
                }
        }
        if (o.has("slim") && o.get("slim").isJsonObject())
            for (var en : o.getAsJsonObject("slim").entrySet())
                try { s.slim.put(en.getKey().replace("-", "").toLowerCase(), en.getValue().getAsBoolean()); } catch (Throwable ignored) {}
        return s;
    }

    public static boolean slim(String uuid) {
        Boolean b = state.slim.get(uuid == null ? "" : uuid.replace("-", "").toLowerCase());
        return b != null && b;
    }

    private static void write(String name, JsonObject o) throws java.io.IOException {
        Path tmp = dir().resolve(name + ".tmp");
        Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, dir().resolve(name + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static long lastReq;
    /** Something for the launcher to do. Returns the request's number. */
    public static long request(String type, String key, String value) {
        long id = Math.max(System.currentTimeMillis(), lastReq + 1);
        lastReq = id;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("type", type);
            if (key != null) o.addProperty(key, value);
            write("iceymod-menu-req-" + id, o);
        } catch (Throwable t) {
            System.out.println("[IceyMod] menu request not written: " + t);
        }
        return id;
    }

    /** A message to a friend, through the same file the reply key uses. */
    public static void sendMessage(String uuid, String name, String text) {
        try {
            long id = Math.max(System.currentTimeMillis(), lastReq + 1);
            lastReq = id;
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("to", uuid);
            o.addProperty("name", name);
            o.addProperty("text", text);
            write("iceymod-reply-" + id, o);
        } catch (Throwable t) {
            System.out.println("[IceyMod] message not written: " + t);
        }
    }

    /**
     * The launcher's answer to a "switch" request, or null while there is
     * none. The file holds the other account's sign-in for a moment, so it
     * is deleted the instant it has been read.
     */
    public static JsonObject takeAccountAnswer() {
        try {
            Path p = dir().resolve("iceymod-account.json");
            if (!Files.exists(p)) return null;
            String text = Files.readString(p, StandardCharsets.UTF_8);
            try { Files.delete(p); } catch (Throwable ignored) {}
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Throwable t) {
            return null;
        }
    }
}
