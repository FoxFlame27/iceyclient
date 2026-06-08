/**
 * Icey Client network — self-hosted Node.js server.
 *
 * Drop-in alternative to the Cloudflare Worker in ../backend. Same
 * endpoint surface, different runtime:
 *   PUT  /capes/:uuid           upload PNG (raw body, content-type image/png, <=64KB)
 *   GET  /capes/:uuid           fetch PNG, 404 if none
 *   DELETE /capes/:uuid         delete
 *   POST /presence/:uuid        heartbeat — recorded in-memory with 90s TTL
 *   GET  /presence?uuids=a,b,c  batch lookup -> { presence: { uuid: bool, ... } }
 *
 * Storage:
 *   - Capes: filesystem under DATA_DIR/capes/<uuid>.png. Survives restarts.
 *   - Presence: in-memory Map. Resets on restart. Acceptable — the
 *     launcher heartbeats every 60s so the map repopulates within ~1min.
 *
 * Env:
 *   PORT      default 8787
 *   DATA_DIR  default ./data (relative to this file)
 *
 * Auth posture: open. Worst case from an open upload is a stranger's
 * cape getting overwritten, which is annoying not catastrophic.
 * Signed-token flow against Mojang sessions is a follow-up.
 */
const express = require('express');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT, 10) || 8787;
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const CAPES_DIR = path.join(DATA_DIR, 'capes');
const PRESENCE_TTL_MS = 90_000;
const CAPE_MAX_BYTES = 64 * 1024;
const PNG_SIG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

fs.mkdirSync(CAPES_DIR, { recursive: true });

function normalizeUuid(s) {
  if (!s || typeof s !== 'string') return null;
  const stripped = s.replace(/-/g, '').toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(stripped)) return null;
  return `${stripped.slice(0, 8)}-${stripped.slice(8, 12)}-${stripped.slice(12, 16)}-${stripped.slice(16, 20)}-${stripped.slice(20)}`;
}

const presence = new Map(); // uuid -> lastPing ms

const app = express();

// CORS for the launcher (Electron renderer issues fetch from a
// file:// origin — without the wildcard the browser blocks the
// response even though Electron doesn't actually need it for IPC,
// but the mod uses Java HttpClient which is unaffected anyway).
app.use((req, res, next) => {
  res.setHeader('access-control-allow-origin', '*');
  res.setHeader('access-control-allow-methods', 'GET, PUT, POST, DELETE, OPTIONS');
  res.setHeader('access-control-allow-headers', 'content-type');
  if (req.method === 'OPTIONS') return res.status(204).end();
  next();
});

app.put('/capes/:uuid',
  express.raw({ type: 'image/png', limit: CAPE_MAX_BYTES + 1024 }),
  (req, res) => {
    const uuid = normalizeUuid(req.params.uuid);
    if (!uuid) return res.status(400).json({ error: 'bad uuid' });
    const buf = req.body;
    if (!buf || buf.length === 0) return res.status(400).json({ error: 'empty body' });
    if (buf.length > CAPE_MAX_BYTES) return res.status(413).json({ error: 'too large' });
    for (let i = 0; i < 8; i++) {
      if (buf[i] !== PNG_SIG[i]) return res.status(400).json({ error: 'not a PNG' });
    }
    fs.writeFileSync(path.join(CAPES_DIR, `${uuid}.png`), buf);
    res.json({ ok: true, uuid, size: buf.length });
  }
);

app.get('/capes/:uuid', (req, res) => {
  const uuid = normalizeUuid(req.params.uuid);
  if (!uuid) return res.status(400).json({ error: 'bad uuid' });
  const p = path.join(CAPES_DIR, `${uuid}.png`);
  if (!fs.existsSync(p)) return res.status(404).send('not found');
  res.setHeader('content-type', 'image/png');
  res.setHeader('cache-control', 'public, max-age=60');
  res.sendFile(p);
});

app.delete('/capes/:uuid', (req, res) => {
  const uuid = normalizeUuid(req.params.uuid);
  if (!uuid) return res.status(400).json({ error: 'bad uuid' });
  try { fs.unlinkSync(path.join(CAPES_DIR, `${uuid}.png`)); } catch (_) {}
  res.json({ ok: true, uuid });
});

app.post('/presence/:uuid', (req, res) => {
  const uuid = normalizeUuid(req.params.uuid);
  if (!uuid) return res.status(400).json({ error: 'bad uuid' });
  presence.set(uuid, Date.now());
  res.json({ ok: true, uuid, ttl: PRESENCE_TTL_MS / 1000 });
});

app.get('/presence', (req, res) => {
  const raw = String(req.query.uuids || '').split(',').slice(0, 200);
  const now = Date.now();
  const out = {};
  for (const r of raw) {
    const u = normalizeUuid(r);
    if (!u) continue;
    const ts = presence.get(u);
    out[u] = ts != null && (now - ts) < PRESENCE_TTL_MS;
  }
  res.json({ presence: out });
});

// Periodic cleanup so the presence Map can't grow unbounded.
setInterval(() => {
  const now = Date.now();
  for (const [uuid, ts] of presence) {
    if ((now - ts) > PRESENCE_TTL_MS) presence.delete(uuid);
  }
}, 60_000).unref();

app.get('/', (_req, res) => res.json({ ok: true, service: 'icey-client-network' }));
app.get('/health', (_req, res) => res.json({ ok: true, service: 'icey-client-network' }));

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Icey network listening on :${PORT}`);
  console.log(`Capes stored under ${CAPES_DIR}`);
});
