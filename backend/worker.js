/**
 * Icey Client network — Cloudflare Worker.
 *
 * Responsibilities:
 *   - Host custom cape PNGs (one per player UUID) in R2.
 *   - Track which players have an Icey Client session active (KV
 *     with short TTL, treated as a "presence" / online indicator).
 *
 * Deployment (one-time):
 *   1. wrangler login
 *   2. wrangler r2 bucket create icey-capes
 *   3. wrangler kv:namespace create ICEY_PRESENCE
 *   4. Fill the IDs into wrangler.toml (alongside this file).
 *   5. wrangler deploy
 *
 * Auth posture (MVP):
 *   - Cape uploads are open. The launcher will only ever upload to
 *     the UUID of the currently signed-in account, but the backend
 *     does NOT verify this. Acceptable for v1 — worst case someone
 *     overwrites a stranger's cape, which is mildly annoying, not
 *     catastrophic. A signed-token flow (Mojang session validation
 *     -> short-lived bearer) is a follow-up.
 *   - Presence is open too — anyone can claim anyone is online.
 *     The badge means "presence record exists", which is a soft
 *     signal not a security claim.
 *
 * Endpoints:
 *   PUT  /capes/:uuid           upload PNG (body = raw PNG, content-type image/png)
 *   GET  /capes/:uuid           fetch PNG, 404 if none
 *   DELETE /capes/:uuid         remove cape
 *   POST /presence/:uuid        heartbeat — sets presence with 90s TTL
 *   GET  /presence?uuids=a,b,c  batch lookup — returns { uuid: bool, ... }
 */

const CAPE_MAX_BYTES = 64 * 1024;        // 64KB cap, plenty for 64x32 PNG
const PRESENCE_TTL_SECONDS = 90;          // launcher heartbeats every 60s
const UUID_RE = /^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$/;

function normalizeUuid(s) {
  if (!s || typeof s !== 'string') return null;
  const stripped = s.replace(/-/g, '').toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(stripped)) return null;
  return `${stripped.slice(0, 8)}-${stripped.slice(8, 12)}-${stripped.slice(12, 16)}-${stripped.slice(16, 20)}-${stripped.slice(20)}`;
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, PUT, POST, DELETE, OPTIONS',
    'access-control-allow-headers': 'content-type',
    'access-control-max-age': '86400',
  };
}

function json(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...corsHeaders(), ...extra },
  });
}

async function handleCapePut(request, uuid, env) {
  const contentType = request.headers.get('content-type') || '';
  if (!contentType.includes('image/png')) {
    return json({ error: 'content-type must be image/png' }, 415);
  }
  const buf = await request.arrayBuffer();
  if (buf.byteLength === 0) return json({ error: 'empty body' }, 400);
  if (buf.byteLength > CAPE_MAX_BYTES) {
    return json({ error: `cape exceeds ${CAPE_MAX_BYTES} bytes` }, 413);
  }
  // Light PNG signature check — first 8 bytes.
  const sig = new Uint8Array(buf, 0, 8);
  const PNG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  for (let i = 0; i < 8; i++) {
    if (sig[i] !== PNG[i]) return json({ error: 'not a PNG' }, 400);
  }
  await env.CAPES.put(`${uuid}.png`, buf, {
    httpMetadata: { contentType: 'image/png', cacheControl: 'public, max-age=300' },
  });
  return json({ ok: true, uuid, size: buf.byteLength }, 200);
}

async function handleCapeGet(uuid, env) {
  const obj = await env.CAPES.get(`${uuid}.png`);
  if (!obj) return new Response('not found', { status: 404, headers: corsHeaders() });
  return new Response(obj.body, {
    status: 200,
    headers: {
      'content-type': 'image/png',
      'cache-control': 'public, max-age=60',
      'etag': obj.httpEtag,
      ...corsHeaders(),
    },
  });
}

async function handleCapeDelete(uuid, env) {
  await env.CAPES.delete(`${uuid}.png`);
  return json({ ok: true, uuid });
}

async function handlePresencePost(uuid, env) {
  await env.PRESENCE.put(uuid, '1', { expirationTtl: PRESENCE_TTL_SECONDS });
  return json({ ok: true, uuid, ttl: PRESENCE_TTL_SECONDS });
}

async function handlePresenceGet(uuidsParam, env) {
  if (!uuidsParam) return json({ error: 'missing uuids' }, 400);
  const raw = uuidsParam.split(',').slice(0, 200);  // cap batch size
  const result = {};
  await Promise.all(raw.map(async (r) => {
    const u = normalizeUuid(r);
    if (!u) return;
    const v = await env.PRESENCE.get(u);
    result[u] = v === '1';
  }));
  return json({ presence: result });
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    const url = new URL(request.url);
    const path = url.pathname;

    // /capes/:uuid
    const capeMatch = path.match(/^\/capes\/([^/]+)$/);
    if (capeMatch) {
      const uuid = normalizeUuid(capeMatch[1]);
      if (!uuid) return json({ error: 'bad uuid' }, 400);
      if (request.method === 'PUT') return handleCapePut(request, uuid, env);
      if (request.method === 'GET') return handleCapeGet(uuid, env);
      if (request.method === 'DELETE') return handleCapeDelete(uuid, env);
      return json({ error: 'method not allowed' }, 405);
    }

    // /presence/:uuid (POST heartbeat)
    const presencePostMatch = path.match(/^\/presence\/([^/]+)$/);
    if (presencePostMatch && request.method === 'POST') {
      const uuid = normalizeUuid(presencePostMatch[1]);
      if (!uuid) return json({ error: 'bad uuid' }, 400);
      return handlePresencePost(uuid, env);
    }

    // /presence?uuids=a,b,c (GET batch)
    if (path === '/presence' && request.method === 'GET') {
      return handlePresenceGet(url.searchParams.get('uuids'), env);
    }

    if (path === '/' || path === '/health') {
      return json({ ok: true, service: 'icey-client-network' });
    }
    return json({ error: 'not found' }, 404);
  },
};
