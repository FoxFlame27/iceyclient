package com.iceymod.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mod-side client for the Icey network (Cloudflare Worker).
 *
 * Responsibilities:
 *   - Tell the worker we're online (one POST per session — the
 *     launcher does the rolling 60s heartbeat, this is a belt-and-
 *     suspenders init ping).
 *   - Batch-lookup which players in the current world are using
 *     Icey Client so the TAB / nameplate mixins know who to badge.
 *   - Fetch a player's cape PNG when the renderer asks for it.
 *
 * All HTTP calls run on virtual threads to keep the render thread
 * uncontested. Results land in concurrent caches; mixins read those
 * caches and never block.
 */
public final class IceyNetwork {

    private static final String BASE_URL = "https://icey-client-network.iceyclient.workers.dev";
    private static final long PRESENCE_TTL_MS = 60_000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    // uuid -> (fetchedAt, online)
    private static final ConcurrentHashMap<UUID, long[]> presenceCache = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingLookups = ConcurrentHashMap.newKeySet();

    private IceyNetwork() {}

    /**
     * Non-blocking check. Returns the last-known presence for {@code uuid}
     * (or false if we've never asked). If the cached entry is stale,
     * schedules a background refresh.
     */
    public static boolean isOnline(UUID uuid) {
        if (uuid == null) return false;
        long[] entry = presenceCache.get(uuid);
        long now = System.currentTimeMillis();
        boolean haveFresh = entry != null && (now - entry[0]) < PRESENCE_TTL_MS;
        if (!haveFresh) schedulePresenceLookup(uuid);
        return entry != null && entry[1] == 1L;
    }

    private static void schedulePresenceLookup(UUID uuid) {
        if (!pendingLookups.add(uuid)) return;
        Thread.ofVirtual().name("icey-presence-" + uuid).start(() -> {
            try {
                fetchPresenceBatch(Set.of(uuid));
            } catch (Throwable t) {
                // Swallow — best-effort.
            } finally {
                pendingLookups.remove(uuid);
            }
        });
    }

    /**
     * Manually warm the cache for a known set of player UUIDs (called
     * by the TAB mixin when it first sees the list).
     */
    public static void warmPresence(Set<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return;
        long now = System.currentTimeMillis();
        Set<UUID> stale = new HashSet<>();
        for (UUID u : uuids) {
            long[] entry = presenceCache.get(u);
            if (entry == null || (now - entry[0]) >= PRESENCE_TTL_MS) {
                if (pendingLookups.add(u)) stale.add(u);
            }
        }
        if (stale.isEmpty()) return;
        Thread.ofVirtual().name("icey-presence-batch").start(() -> {
            try { fetchPresenceBatch(stale); }
            finally { for (UUID u : stale) pendingLookups.remove(u); }
        });
    }

    private static void fetchPresenceBatch(Set<UUID> uuids) {
        if (uuids.isEmpty()) return;
        StringBuilder csv = new StringBuilder();
        for (UUID u : uuids) { if (csv.length() > 0) csv.append(','); csv.append(u); }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/presence?uuids=" + csv))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            parsePresence(res.body());
        } catch (Throwable ignored) {}
    }

    /**
     * Crude JSON parser tuned to the worker's exact response shape:
     * {@code { "presence": { "uuid": true, "uuid": false, ... } }}.
     * Avoids pulling in a heavy JSON dep.
     */
    private static void parsePresence(String body) {
        long now = System.currentTimeMillis();
        int presenceIdx = body.indexOf("\"presence\"");
        if (presenceIdx < 0) return;
        int start = body.indexOf('{', presenceIdx);
        int end = body.indexOf('}', start);
        if (start < 0 || end < 0) return;
        String inner = body.substring(start + 1, end);
        for (String pair : inner.split(",")) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim().replaceAll("\"", "");
            String val = pair.substring(colon + 1).trim();
            try {
                UUID u = UUID.fromString(key);
                presenceCache.put(u, new long[]{ now, val.startsWith("true") ? 1L : 0L });
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Synchronous cape PNG fetch. Use from a background thread only
     * — never the render thread.
     */
    public static byte[] fetchCape(UUID uuid) {
        if (uuid == null) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/capes/" + uuid))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) return null;
            return res.body();
        } catch (Throwable t) {
            return null;
        }
    }
}
