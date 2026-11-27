package com.iceymod.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;

/**
 * Asks a server how it is doing, for the server lists of the scene menu:
 * players online, the line of text it shows, its icon, how long it took
 * to answer. This is the same small "status" exchange the game's own
 * server list does, spoken directly so it is one piece of code for every
 * game version. It runs on a few background threads and never blocks the
 * menu; the menu just looks at the latest answer.
 */
public final class ServerPing {
    private ServerPing() {}

    public static final class Result {
        /** 0 = still asking, 1 = answered, 2 = no answer */
        public volatile int state;
        public volatile int online, max;
        public volatile long ms;
        public volatile String motd = "";
        public volatile byte[] icon;
        volatile long at;
    }

    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Icey server status");
        t.setDaemon(true);
        return t;
    });

    /** The latest answer of this server; asks (again) when there is none or it is over a minute old. */
    public static Result get(String address) {
        String key = address == null ? "" : address.trim().toLowerCase();
        Result r = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (r == null || (r.state != 0 && now - r.at > 60000)) {
            Result fresh = new Result();
            fresh.at = now;
            if (r != null) { fresh.online = r.online; fresh.max = r.max; fresh.motd = r.motd; fresh.icon = r.icon; fresh.ms = r.ms; }
            CACHE.put(key, fresh);
            POOL.execute(() -> ask(key, fresh));
            return fresh;
        }
        return r;
    }

    /** Ask everything again at the next look (the refresh button). */
    public static void forget() { CACHE.clear(); }

    private static void ask(String address, Result out) {
        try {
            if (!ServerAddress.isValidAddress(address)) { out.state = 2; return; }
            ServerAddress parsed = ServerAddress.parseString(address);
            var resolved = ServerNameResolver.DEFAULT.resolveAddress(parsed);
            if (resolved.isEmpty()) { out.state = 2; return; }
            InetSocketAddress target = resolved.get().asInetSocketAddress();
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(5000);
                socket.setTcpNoDelay(true);
                socket.connect(target, 5000);
                DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                // "I would like your status": protocol version, the name we asked for, the port, 1 = status.
                ByteArrayOutputStream hello = new ByteArrayOutputStream();
                varInt(hello, 0);
                varInt(hello, net.minecraft.SharedConstants.getProtocolVersion());
                byte[] host = parsed.getHost().getBytes(StandardCharsets.UTF_8);
                varInt(hello, host.length); hello.write(host);
                hello.write((parsed.getPort() >> 8) & 0xFF); hello.write(parsed.getPort() & 0xFF);
                varInt(hello, 1);
                packet(outStream, hello.toByteArray());
                packet(outStream, new byte[] { 0 });
                outStream.flush();
                long sent = System.nanoTime();
                readVarInt(in);                              // length
                if (readVarInt(in) != 0) throw new IOException("unexpected answer");
                int len = readVarInt(in);
                if (len <= 0 || len > 1 << 21) throw new IOException("bad length");
                byte[] json = new byte[len];
                in.readFully(json);
                out.ms = Math.max(1, (System.nanoTime() - sent) / 1_000_000);
                JsonObject o = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("players") && o.get("players").isJsonObject()) {
                    JsonObject p = o.getAsJsonObject("players");
                    out.online = p.has("online") ? p.get("online").getAsInt() : 0;
                    out.max = p.has("max") ? p.get("max").getAsInt() : 0;
                }
                if (o.has("description")) {
                    StringBuilder sb = new StringBuilder();
                    text(o.get("description"), sb);
                    out.motd = sb.toString().replaceAll("§.", "").replaceAll("\\s+", " ").trim();
                }
                if (o.has("favicon") && o.get("favicon").isJsonPrimitive()) {
                    String f = o.get("favicon").getAsString();
                    int comma = f.indexOf(',');
                    if (f.startsWith("data:image/png;base64,") && comma > 0) {
                        try { out.icon = Base64.getDecoder().decode(f.substring(comma + 1).replace("\n", "")); } catch (Throwable ignored) {}
                    }
                }
                out.state = 1;
            }
        } catch (Throwable t) {
            out.state = 2;
        } finally {
            out.at = System.currentTimeMillis();
        }
    }

    /** The plain words of a server's description (it may be nested pieces of text). */
    private static void text(JsonElement e, StringBuilder sb) {
        if (e == null || e.isJsonNull() || sb.length() > 300) return;
        if (e.isJsonPrimitive()) { sb.append(e.getAsString()); return; }
        if (e.isJsonArray()) { for (JsonElement x : e.getAsJsonArray()) text(x, sb); return; }
        JsonObject o = e.getAsJsonObject();
        if (o.has("text")) text(o.get("text"), sb);
        if (o.has("extra")) text(o.get("extra"), sb);
    }

    private static void packet(DataOutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream len = new ByteArrayOutputStream();
        varInt(len, body.length);
        out.write(len.toByteArray());
        out.write(body);
    }

    private static void varInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) { out.write((value & 0x7F) | 0x80); value >>>= 7; }
        out.write(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
            if (shift > 35) throw new IOException("number too long");
        }
    }
}
