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

// One-line request log so journalctl shows live traffic — every
// PUT /capes, POST /presence, GET /presence shows up as a tagged
// line we can grep / tail.
app.use((req, _res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.originalUrl}`);
  next();
});

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

// ── Downloads, counted per device ─────────────────────────────────
// The download buttons on iceymc.com point at /dl/<system>. Each request
// is passed straight on to the newest release file on GitHub; before that
// the device is noted, once. GitHub's own numbers count every download,
// so one person fetching fifty versions shows as fifty: here that person
// is one.
//
// A device is the visitor's address plus their browser, kept only as a
// salted hash (the address itself is never stored, and the salt never
// leaves this machine). Crawlers are passed on without being counted.
const crypto = require('crypto');
const DL_FILE = path.join(DATA_DIR, 'downloads.json');
const DL_SALT_FILE = path.join(DATA_DIR, 'downloads.salt');
const DL_TARGETS = {
  'win':       'Icey.Client.Setup.exe',
  'mac-arm':   'Icey.Client-arm64.dmg',
  'mac-intel': 'Icey.Client-x64.dmg',
  'linux':     'Icey.Client-aarch64.rpm',
};
const DL_RELEASE = 'https://github.com/FoxFlame27/iceyclient/releases/latest/download/';
const DL_BOTS = /bot|crawl|spider|slurp|preview|monitor|curl|wget|python|httpclient|headless|facebookexternalhit|whatsapp|discord|telegram|scan/i;

let dlSalt;
try { dlSalt = fs.readFileSync(DL_SALT_FILE, 'utf8'); }
catch (_) { dlSalt = crypto.randomBytes(32).toString('hex'); fs.writeFileSync(DL_SALT_FILE, dlSalt, { mode: 0o600 }); }

// { since, devices: { <hash>: { first, last, n, systems: [..] } } }
let dl = { since: new Date().toISOString(), devices: {} };
let dlDirty = false;
try {
  const saved = JSON.parse(fs.readFileSync(DL_FILE, 'utf8'));
  if (saved && saved.devices) dl = saved;
} catch (_) { dlDirty = true; }      // first run: write the file, so "since" stays the same across restarts
function dlSave() {
  if (!dlDirty) return;
  dlDirty = false;
  try {
    fs.writeFileSync(DL_FILE + '.tmp', JSON.stringify(dl));
    fs.renameSync(DL_FILE + '.tmp', DL_FILE);
  } catch (e) { console.error('downloads.json not saved: ' + e.message); }
}
setInterval(dlSave, 15_000).unref();
process.on('SIGTERM', () => { dlSave(); if (typeof socialSave === 'function') socialSave(); process.exit(0); });
process.on('SIGINT', () => { dlSave(); if (typeof socialSave === 'function') socialSave(); process.exit(0); });

app.get('/dl/:system', (req, res) => {
  const file = DL_TARGETS[req.params.system];
  if (!file) return res.status(404).json({ error: 'unknown system' });
  const ua = String(req.headers['user-agent'] || '');
  // nginx hands the visitor's address over in X-Real-IP. That header is
  // only believed when the request really comes from nginx on this
  // machine: this port is also open to the outside (the launcher uses it),
  // and anyone could send a made-up address there to inflate the count.
  const peer = String(req.socket.remoteAddress || '');
  const fromNginx = /^(::ffff:)?127\.|^::1$/.test(peer);
  const ip = fromNginx ? String(req.headers['x-real-ip'] || peer) : peer;
  // Only a click on the site's own download button counts. Crawlers that
  // disguise themselves as a phone fetch these links directly, without the
  // page as referrer; and no phone installs a desktop launcher.
  const fromSite = /^https:\/\/(www\.)?iceymc\.com\//.test(String(req.headers['referer'] || ''));
  const phone = /iphone|ipad|ipod|android|mobile/i.test(ua);
  if (ua && ip && fromSite && !phone && !DL_BOTS.test(ua)) {
    const id = crypto.createHash('sha256').update(dlSalt + '|' + ip + '|' + ua).digest('hex').slice(0, 32);
    const now = new Date().toISOString();
    const d = dl.devices[id] || (dl.devices[id] = { first: now, last: now, n: 0, systems: [] });
    d.last = now;
    d.n += 1;
    if (!d.systems.includes(req.params.system)) d.systems.push(req.params.system);
    dlDirty = true;
  }
  res.setHeader('cache-control', 'no-store');
  res.redirect(302, DL_RELEASE + file);
});

// Numbers only: how many different devices, in total and per system.
app.get('/dl-stats', (_req, res) => {
  const out = { since: dl.since, devices: 0, downloads: 0, systems: { 'win': 0, 'mac-arm': 0, 'mac-intel': 0, 'linux': 0 } };
  for (const d of Object.values(dl.devices)) {
    out.devices += 1;
    out.downloads += d.n;
    for (const s of d.systems) if (s in out.systems) out.systems[s] += 1;
  }
  res.setHeader('cache-control', 'no-store');
  res.json(out);
});

// ── Friends and messages ──────────────────────────────────────────
// Who is who: a player proves their Minecraft account the way a game
// server does. The launcher asks for a challenge, tells Mojang's session
// server "I am joining <challenge>" with its own access token (which
// never comes here), and this server asks Mojang whether that player
// really did. Then it hands out a token of its own for the calls below.
//
// Friends are mutual: one asks, the other accepts. Only friends see each
// other online and can write to each other. Everything is kept in one
// JSON file; messages are capped per pair.
const SOCIAL_FILE = path.join(DATA_DIR, 'social.json');
const SOCIAL_ONLINE_MS = 60_000;
const SOCIAL_MAX_TEXT = 500;
const SOCIAL_KEEP_PER_PAIR = 300;

let social = { nextId: 1, users: {}, friends: {}, requests: {}, messages: {}, unread: {}, tokens: {} };
try {
  const saved = JSON.parse(fs.readFileSync(SOCIAL_FILE, 'utf8'));
  if (saved && saved.users) social = Object.assign(social, saved);
} catch (_) {}
let socialDirty = false;
function socialSave() {
  if (!socialDirty) return;
  socialDirty = false;
  try {
    fs.writeFileSync(SOCIAL_FILE + '.tmp', JSON.stringify(social), { mode: 0o600 });
    fs.renameSync(SOCIAL_FILE + '.tmp', SOCIAL_FILE);
  } catch (e) { console.error('social.json not saved: ' + e.message); }
}
setInterval(socialSave, 10_000).unref();

const socialOnline = new Map();      // uuid -> { at, inGame }
const socialChallenges = new Map();  // serverId -> created ms
const socialSendTimes = new Map();   // uuid -> [ms, ...] for the rate limit

const sha = (v) => crypto.createHash('sha256').update(String(v)).digest('hex');
const pairKey = (a, b) => (a < b ? a + ':' + b : b + ':' + a);
const listOf = (obj, key) => obj[key] || (obj[key] = []);
const areFriends = (a, b) => (social.friends[a] || []).includes(b);
const nameOf = (uuid) => (social.users[uuid] && social.users[uuid].name) || 'Player';
const isOnline = (uuid) => { const o = socialOnline.get(uuid); return !!o && Date.now() - o.at < SOCIAL_ONLINE_MS; };
function socialIssueToken(uuid, name) {
  const token = crypto.randomBytes(32).toString('hex');
  social.tokens[sha(token)] = { uuid, at: Date.now() };
  social.users[uuid] = Object.assign(social.users[uuid] || {}, { name, lastSeen: Date.now() });
  // One player, a handful of devices: drop that player's oldest tokens.
  const mine = Object.entries(social.tokens).filter(([, t]) => t.uuid === uuid).sort((x, y) => y[1].at - x[1].at);
  for (const [h] of mine.slice(5)) delete social.tokens[h];
  socialDirty = true;
  return token;
}

const socialJson = express.json({ limit: '8kb' });

app.post('/social/auth/challenge', (_req, res) => {
  const now = Date.now();
  for (const [id, at] of socialChallenges) if (now - at > 120_000) socialChallenges.delete(id);
  if (socialChallenges.size > 5000) return res.status(503).json({ error: 'busy' });
  const serverId = crypto.randomBytes(15).toString('hex');
  socialChallenges.set(serverId, now);
  res.json({ serverId });
});

app.post('/social/auth/verify', socialJson, async (req, res) => {
  try {
    const username = String((req.body && req.body.username) || '');
    const serverId = String((req.body && req.body.serverId) || '');
    if (!/^[A-Za-z0-9_]{1,16}$/.test(username) || !socialChallenges.has(serverId)) return res.status(400).json({ error: 'bad request' });
    socialChallenges.delete(serverId);
    const r = await fetch('https://sessionserver.mojang.com/session/minecraft/hasJoined?username=' + encodeURIComponent(username) + '&serverId=' + serverId);
    if (r.status !== 200) return res.status(403).json({ error: 'not verified' });
    const profile = await r.json();
    const uuid = normalizeUuid(profile && profile.id);
    if (!uuid) return res.status(403).json({ error: 'not verified' });
    res.json({ token: socialIssueToken(uuid, profile.name || username), uuid, name: profile.name || username });
  } catch (e) {
    res.status(502).json({ error: 'verification unavailable' });
  }
});

// For testing on a spare port only: never set SOCIAL_TEST on the real service.
if (process.env.SOCIAL_TEST === '1') {
  app.post('/social/auth/test', socialJson, (req, res) => {
    const uuid = normalizeUuid(req.body && req.body.uuid);
    if (!uuid) return res.status(400).json({ error: 'bad uuid' });
    res.json({ token: socialIssueToken(uuid, String(req.body.name || 'Tester')), uuid });
  });
}

function socialAuth(req, res, next) {
  const m = /^Bearer ([0-9a-f]{64})$/.exec(String(req.headers.authorization || ''));
  const t = m && social.tokens[sha(m[1])];
  if (!t) return res.status(401).json({ error: 'sign in again' });
  req.me = t.uuid;
  next();
}

function socialState(me, since) {
  const person = (u) => ({ uuid: u, name: nameOf(u) });
  const mineUnread = social.unread[me] || {};
  const friends = (social.friends[me] || []).map((u) => {
    const o = socialOnline.get(u);
    const online = isOnline(u);
    return { uuid: u, name: nameOf(u), online, inGame: online && !!o.inGame, lastSeen: (social.users[u] && social.users[u].lastSeen) || 0, unread: mineUnread[u] || 0 };
  });
  // Messages to or from me that the caller has not seen yet.
  const fresh = [];
  if (since >= 0) {
    for (const u of social.friends[me] || []) {
      for (const msg of social.messages[pairKey(me, u)] || []) if (msg.id > since) fresh.push(msg);
    }
    fresh.sort((x, y) => x.id - y.id);
  }
  return {
    me: person(me),
    friends,
    incoming: (social.requests[me] || []).map(person),
    outgoing: Object.keys(social.requests).filter(to => social.requests[to].includes(me)).map(person),
    messages: fresh.slice(-100),
    lastId: social.nextId - 1,
  };
}

// The launcher calls this every few seconds: "I am here", and what is new?
app.post('/social/sync', socialJson, socialAuth, (req, res) => {
  const me = req.me;
  socialOnline.set(me, { at: Date.now(), inGame: !!(req.body && req.body.inGame) });
  if (social.users[me]) social.users[me].lastSeen = Date.now();
  const since = Number.isFinite(Number(req.body && req.body.since)) ? Number(req.body.since) : -1;
  res.json(socialState(me, since));
});

// Leaving (the launcher closes): offline at once instead of a minute later.
app.post('/social/leave', socialAuth, (req, res) => {
  socialOnline.delete(req.me);
  res.json({ ok: true });
});

app.post('/social/friends/add', socialJson, socialAuth, async (req, res) => {
  const me = req.me;
  const name = String((req.body && req.body.name) || '').trim();
  if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) return res.status(400).json({ status: 'unknown' });
  // Someone already seen here, else ask Mojang who carries that name.
  let other = Object.keys(social.users).find(u => social.users[u].name.toLowerCase() === name.toLowerCase());
  if (!other) {
    try {
      const r = await fetch('https://api.mojang.com/users/profiles/minecraft/' + encodeURIComponent(name));
      if (r.status === 200) {
        const p = await r.json();
        other = normalizeUuid(p && p.id);
        if (other && !social.users[other]) social.users[other] = { name: p.name || name, lastSeen: 0 };
      }
    } catch (_) { return res.status(502).json({ status: 'unavailable' }); }
  }
  if (!other) return res.json({ status: 'unknown' });
  if (other === me) return res.json({ status: 'self' });
  if (areFriends(me, other)) return res.json({ status: 'friends', name: nameOf(other) });
  if ((social.friends[me] || []).length >= 200) return res.json({ status: 'full' });
  if ((social.requests[me] || []).includes(other)) {
    // They had asked already: that makes it mutual.
    social.requests[me] = social.requests[me].filter(u => u !== other);
    listOf(social.friends, me).push(other);
    listOf(social.friends, other).push(me);
    socialDirty = true;
    return res.json({ status: 'friends', name: nameOf(other) });
  }
  const theirs = listOf(social.requests, other);
  if (!theirs.includes(me)) {
    if (theirs.length >= 100) return res.json({ status: 'full' });
    theirs.push(me);
    socialDirty = true;
  }
  res.json({ status: 'requested', name: nameOf(other) });
});

app.post('/social/friends/respond', socialJson, socialAuth, (req, res) => {
  const me = req.me;
  const other = normalizeUuid(req.body && req.body.uuid);
  if (!other || !(social.requests[me] || []).includes(other)) return res.status(404).json({ error: 'no such request' });
  social.requests[me] = social.requests[me].filter(u => u !== other);
  if (req.body.accept && !areFriends(me, other)) {
    listOf(social.friends, me).push(other);
    listOf(social.friends, other).push(me);
  }
  socialDirty = true;
  res.json({ ok: true });
});

// Unfriend, or take back a request that was not answered yet.
app.post('/social/friends/remove', socialJson, socialAuth, (req, res) => {
  const me = req.me;
  const other = normalizeUuid(req.body && req.body.uuid);
  if (!other) return res.status(400).json({ error: 'bad uuid' });
  social.friends[me] = (social.friends[me] || []).filter(u => u !== other);
  social.friends[other] = (social.friends[other] || []).filter(u => u !== me);
  if (social.requests[other]) social.requests[other] = social.requests[other].filter(u => u !== me);
  if (social.unread[me]) delete social.unread[me][other];
  socialDirty = true;
  res.json({ ok: true });
});

// A conversation: the last messages with one friend. Opening it reads it.
app.get('/social/messages', socialAuth, (req, res) => {
  const me = req.me;
  const other = normalizeUuid(req.query.with);
  if (!other || !areFriends(me, other)) return res.status(403).json({ error: 'not friends' });
  if (social.unread[me] && social.unread[me][other]) { delete social.unread[me][other]; socialDirty = true; }
  res.json({ messages: (social.messages[pairKey(me, other)] || []).slice(-100) });
});

app.post('/social/messages', socialJson, socialAuth, (req, res) => {
  const me = req.me;
  const other = normalizeUuid(req.body && req.body.to);
  const text = String((req.body && req.body.text) || '').replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, SOCIAL_MAX_TEXT);
  if (!other || !areFriends(me, other)) return res.status(403).json({ error: 'not friends' });
  if (!text) return res.status(400).json({ error: 'empty' });
  const now = Date.now();
  const times = (socialSendTimes.get(me) || []).filter(t => now - t < 60_000);
  if (times.length >= 40) return res.status(429).json({ error: 'slow down' });
  times.push(now);
  socialSendTimes.set(me, times);
  const msg = { id: social.nextId++, from: me, to: other, text, at: now };
  const list = listOf(social.messages, pairKey(me, other));
  list.push(msg);
  if (list.length > SOCIAL_KEEP_PER_PAIR) list.splice(0, list.length - SOCIAL_KEEP_PER_PAIR);
  const u = social.unread[other] || (social.unread[other] = {});
  u[me] = (u[me] || 0) + 1;
  socialDirty = true;
  res.json({ message: msg });
});

app.post('/social/read', socialJson, socialAuth, (req, res) => {
  const other = normalizeUuid(req.body && req.body.with);
  if (other && social.unread[req.me] && social.unread[req.me][other]) { delete social.unread[req.me][other]; socialDirty = true; }
  res.json({ ok: true });
});

app.get('/', (_req, res) => res.json({ ok: true, service: 'icey-client-network' }));
app.get('/health', (_req, res) => res.json({ ok: true, service: 'icey-client-network' }));

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Icey network listening on :${PORT}`);
  console.log(`Capes stored under ${CAPES_DIR}`);
});
