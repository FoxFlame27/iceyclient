// Info page (DOM id is still "skins" internally to avoid having to
// rename every switchPage('skins') call site). Asymmetric layout:
// skin viewer + cape upload float on the LEFT against the page
// background; a huge playtime counter sits in the MIDDLE; the server
// list sits as a long vertical strip on the TOP-RIGHT. All box-less.
let _skinsLookupName = '';
let _infoPlaytimeTimer = null;
let _infoServerSearchTimer = null;
let _infoServerSearchResult = null; // last live-status result for the typed IP

const _infoDefaultServers = [
  { name: 'Hypixel',           address: 'mc.hypixel.net' },
  { name: 'Mineplex',          address: 'us.mineplex.com' },
  { name: 'CubeCraft',         address: 'play.cubecraft.net' },
  { name: 'ManaCube',          address: 'play.manacube.com' },
  { name: 'MCCentral',         address: 'mccentral.org' },
  { name: 'Lunar Network',     address: 'lunar.gg' },
  { name: 'The Hive',          address: 'geo.hivebedrock.network' },
  { name: 'PvP Legacy',        address: 'pvplegacy.net' },
  { name: 'Badlion',           address: 'play.badlion.net' },
  { name: 'mcpvp.club',        address: 'mcpvp.club' },
  { name: '2b2t',              address: '2b2t.org' },
  { name: 'WynnCraft',         address: 'play.wynncraft.com' },
  { name: 'Pixelmon Reforged', address: 'play.pixelmonreforged.com' },
  { name: 'Universocraft',     address: 'universocraft.com' },
  { name: 'Donut SMP',         address: 'donutsmp.net' },
  { name: 'Crystal PvP',       address: 'play.crystalpvp.cc' },
  { name: 'EarthSMP',          address: 'play.earthsmp.com' },
  { name: 'Loyisa',            address: 'play.loyisa.com' },
  { name: 'CivClassic',        address: 'civclassic.com' },
  { name: 'Constantiam',       address: 'constantiam.net' },
];

async function SkinsPageInit() {
  const page = document.getElementById('page-skins');
  const auth = await window.icey.getAuth();
  const displayName = (auth && auth.username) ? auth.username : '';
  _skinsLookupName = displayName;

  page.innerHTML = `
    <div class="info-page">
      <div class="info-header">
        <h1 class="info-title">Info</h1>
      </div>

      <div class="info-body">
        <!-- Middle column: huge live playtime counter. Sits in the
             gap between the left (skin/cape) and right (servers)
             columns so it's the visual anchor of the page. -->
        <div class="info-middle-col">
          <div class="info-playtime-label">You have played for</div>
          <div class="info-playtime-counter" id="info-playtime-counter">— —</div>
          <div class="info-playtime-foot">on Icey Client</div>
        </div>

        <div class="info-left-col">

          <!-- Skin viewer — top of the left column, NO bg box.
               Search input is slim and inline, viewer is small. -->
          <div class="info-skin-block">
            <div class="info-skin-search">
              <input class="info-skin-input" type="text" id="info-skin-search" placeholder="Username..." value="${_escAttr(displayName)}" spellcheck="false" maxlength="16" onkeydown="if(event.key==='Enter') _infoSkinLookup()">
              <button class="info-skin-go" onclick="_infoSkinLookup()" title="Search">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.4"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              </button>
            </div>
            <div class="info-skin-stage" id="info-skin-stage">
              ${displayName
                ? `<img class="info-skin-img" id="info-skin-img" src="https://mineskin.eu/armor/body/${encodeURIComponent(displayName)}/160.png" alt="Skin">`
                : '<div class="info-skin-empty">Search a player</div>'}
            </div>
            <div class="info-skin-meta">
              <div class="info-skin-name" id="info-skin-name">${_escHtml(displayName)}</div>
              <div class="info-skin-views" id="info-skin-views" ${displayName ? '' : 'style="display:none"'}>
                <button class="info-view-btn active" onclick="_infoSkinView('body', this)">Body</button>
                <button class="info-view-btn" onclick="_infoSkinView('bust', this)">Bust</button>
                <button class="info-view-btn" onclick="_infoSkinView('head', this)">Head</button>
              </div>
            </div>
            <button class="info-dl-btn" id="info-download-btn" onclick="_infoDownloadSkin()" ${displayName ? '' : 'disabled'}>
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              Download 64×64
            </button>
          </div>

          <!-- Cape upload — small drop zone, Icey logo as the visual
               anchor inside. Folder-open icon button to the right
               lets the user inspect the actual PNG file on disk. -->
          <div class="info-cape-block">
            <div class="info-cape-row">
              <div class="info-cape-drop" id="info-cape-drop"
                   ondragover="event.preventDefault(); this.classList.add('drag')"
                   ondragleave="this.classList.remove('drag')"
                   ondrop="_infoCapeDrop(event)"
                   onclick="document.getElementById('info-cape-file').click()">
                <img class="info-cape-logo" src="assets/icon.png" alt="" onerror="this.style.display='none'">
                <div class="info-cape-text">
                  <div class="info-cape-title">Drop a 64×32 cape PNG</div>
                  <div class="info-cape-sub">…or click to pick a file</div>
                </div>
                <input type="file" id="info-cape-file" accept="image/png" style="display:none" onchange="_infoCapeFile(event)">
              </div>
              <button class="info-cape-folder-btn" onclick="_infoOpenCapeFolder()" title="Open the cape folder">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"/>
                </svg>
              </button>
            </div>
            <div class="info-cape-status" id="info-cape-status"></div>
            <div class="info-cape-note">
              Copies your PNG to every installation's
              <code>game/config/iceyclient/cape.png</code>. iceymod
              picks it up at launch and injects it as your local
              cape — only you see it. Drop a new PNG anytime to
              swap mid-session.
            </div>
          </div>

        </div>

        <!-- Server list — top-right, no bg, long vertical strip. -->
        <div class="info-servers-col">
          <div class="info-servers-head">
            <div class="info-servers-title">Servers</div>
            <div class="info-servers-sub">${_infoDefaultServers.length} listed · click to copy IP</div>
          </div>
          <div class="info-server-search">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input class="info-server-input" type="text" id="info-server-search" placeholder="Search or paste an IP..." spellcheck="false" oninput="_infoFilterServers(this.value)" onkeydown="if(event.key==='Enter') _infoAddServer()">
          </div>
          <div class="info-server-list" id="info-server-list"></div>
        </div>

      </div>
    </div>
  `;

  _infoRenderServers();
  _infoStartPlaytimeTicker();
}

// ── Live playtime counter ─────────────────────────────────────
function _infoStartPlaytimeTicker() {
  if (_infoPlaytimeTimer) { clearInterval(_infoPlaytimeTimer); _infoPlaytimeTimer = null; }
  const tick = () => {
    const el = document.getElementById('info-playtime-counter');
    if (!el) {
      if (_infoPlaytimeTimer) clearInterval(_infoPlaytimeTimer);
      _infoPlaytimeTimer = null;
      return;
    }
    let total = SettingsManager.get('totalPlaytime') || 0;
    // Add the current MC session seconds if a session is in flight
    // so the counter ticks live while MC is open.
    try {
      if (typeof MinecraftLauncher !== 'undefined'
          && MinecraftLauncher.getState
          && MinecraftLauncher.getState() === 'running'
          && MinecraftLauncher._sessionStart) {
        total += Math.floor((Date.now() - MinecraftLauncher._sessionStart) / 1000);
      }
    } catch (_) {}
    el.textContent = _infoFmtPlaytime(total);
  };
  tick();
  _infoPlaytimeTimer = setInterval(tick, 1000);
}

function _infoFmtPlaytime(totalSeconds) {
  totalSeconds = Math.max(0, Math.floor(totalSeconds));
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  // Show only the units that have ever rolled over so the counter
  // doesn't start "0d 0h 0m 7s" on first launch — drop leading
  // zeros except for the smallest non-zero unit.
  const parts = [];
  if (days > 0) parts.push(days + 'd');
  if (days > 0 || hours > 0) parts.push(hours + 'h');
  if (days > 0 || hours > 0 || minutes > 0) parts.push(minutes + 'm');
  parts.push(seconds + 's');
  return parts.join(' ');
}

function _escHtml(s) { return String(s || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]); }
function _escAttr(s) { return String(s || '').replace(/"/g, '&quot;'); }

// ── Skin browser ───────────────────────────────────────────────
function _infoSkinLookup() {
  const input = document.getElementById('info-skin-search');
  const name = input?.value.trim();
  if (!name) { Toast.error('Enter a username'); return; }
  _skinsLookupName = name;
  const stage = document.getElementById('info-skin-stage');
  const nameEl = document.getElementById('info-skin-name');
  const views = document.getElementById('info-skin-views');
  const dl = document.getElementById('info-download-btn');
  if (stage) stage.innerHTML = `<img class="info-skin-img" id="info-skin-img" src="https://mineskin.eu/armor/body/${encodeURIComponent(name)}/160.png" alt="Skin" onerror="_infoSkinLookupError()">`;
  if (nameEl) nameEl.textContent = name;
  if (views) views.style.display = 'flex';
  if (dl) dl.disabled = false;
  document.querySelectorAll('.info-view-btn').forEach((b, i) => b.classList.toggle('active', i === 0));
}
function _infoSkinLookupError() { Toast.error('Could not find skin for that player'); }
function _infoSkinView(view, btn) {
  document.querySelectorAll('.info-view-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  const img = document.getElementById('info-skin-img');
  if (!img || !_skinsLookupName) return;
  const name = encodeURIComponent(_skinsLookupName);
  const urls = {
    body: `https://mineskin.eu/armor/body/${name}/160.png`,
    bust: `https://mineskin.eu/armor/bust/${name}/160.png`,
    head: `https://mineskin.eu/headhelm/${name}/160.png`
  };
  img.src = urls[view] || urls.body;
}
async function _infoDownloadSkin() {
  if (!_skinsLookupName) { Toast.error('Search for a player first'); return; }
  Toast.info('Fetching skin…');
  const res = await window.icey.downloadSkinPng(_skinsLookupName);
  if (res?.canceled) return;
  if (res?.error) { Toast.error('Download failed: ' + res.error); return; }
  Toast.success('Saved to ' + res.savedTo);
}

// ── Cape upload ────────────────────────────────────────────────
function _infoCapeDrop(e) {
  e.preventDefault();
  const drop = document.getElementById('info-cape-drop');
  if (drop) drop.classList.remove('drag');
  const file = e.dataTransfer?.files?.[0];
  if (file) _infoCapeInstall(file);
}
function _infoCapeFile(e) {
  const file = e.target.files?.[0];
  if (file) _infoCapeInstall(file);
}
async function _infoOpenCapeFolder() {
  const res = await window.icey.openCapeFolder();
  if (res?.error) { Toast.error(res.error); return; }
  Toast.info('Opened ' + res.path);
}

async function _infoCapeInstall(file) {
  if (!file) return;
  if (!/\.png$/i.test(file.name)) { Toast.error('Cape must be a PNG'); return; }
  const status = document.getElementById('info-cape-status');
  if (status) status.textContent = 'Installing ' + file.name + '…';
  try {
    const buf = new Uint8Array(await file.arrayBuffer());
    const res = await window.icey.installCustomCape(Array.from(buf), file.name);
    if (res?.error) {
      if (status) status.innerHTML = '<span class="info-cape-err">Failed: ' + _escHtml(res.error) + '</span>';
      Toast.error('Cape install failed: ' + res.error);
    } else {
      const count = res?.copies || 1;
      const installs = res?.installCount || 0;
      const summary = `Copied to ${installs} installation${installs === 1 ? '' : 's'} + global .minecraft`;
      if (status) status.innerHTML = '<span class="info-cape-ok">' + _escHtml(summary) + '</span>';
      Toast.success('Cape installed (' + count + ' cop' + (count === 1 ? 'y' : 'ies') + ')');
    }
  } catch (e) {
    if (status) status.innerHTML = '<span class="info-cape-err">Failed: ' + _escHtml(e.message) + '</span>';
    Toast.error('Cape install failed: ' + e.message);
  }
}

// ── Server list ────────────────────────────────────────────────
// Featured list is persisted in settings.featuredServers; on first
// run it's seeded from _infoDefaultServers. Each entry: {name, address}.
function _infoGetFeatured() {
  const v = SettingsManager.get('featuredServers');
  if (!Array.isArray(v) || v.length === 0) return _infoDefaultServers.slice();
  return v;
}
async function _infoSetFeatured(list) {
  await SettingsManager.set('featuredServers', list);
}

function _infoRenderServers(searchQuery) {
  const container = document.getElementById('info-server-list');
  if (!container) return;
  const q = (searchQuery || '').trim();
  const featured = _infoGetFeatured();

  let blocks = '';

  // Live-status search result block (shown above the list whenever
  // the user has typed something AND we've fetched live status).
  if (q && _infoServerSearchResult) {
    blocks += _infoSearchResultHtml(q, _infoServerSearchResult, featured);
  } else if (q && _infoServerSearchResult === 'loading') {
    blocks += `<div class="info-server-loading">Looking up ${_escHtml(q)}…</div>`;
  }

  // Featured list (filtered by q if typed, full list otherwise).
  const filtered = q
    ? featured.filter(s => s.name.toLowerCase().includes(q.toLowerCase())
                        || s.address.toLowerCase().includes(q.toLowerCase()))
    : featured;
  if (filtered.length === 0 && !q) {
    blocks += `<div class="info-server-empty">No featured servers yet</div>`;
  } else {
    blocks += filtered.map(s => `
      <div class="info-server-row">
        <img class="info-server-icon" src="https://api.mcsrvstat.us/icon/${encodeURIComponent(s.address)}" alt="" loading="lazy" onerror="this.style.visibility='hidden'">
        <div class="info-server-text" onclick="_infoCopyServerIp('${_escAttr(s.address)}')">
          <div class="info-server-name">${_escHtml(s.name)}</div>
          <div class="info-server-ip">${_escHtml(s.address)}</div>
        </div>
        <button class="info-server-action remove" title="Remove from featured" onclick="event.stopPropagation(); _infoRemoveFeatured('${_escAttr(s.address)}')">×</button>
      </div>
    `).join('');
  }

  container.innerHTML = blocks;
}

function _infoSearchResultHtml(q, data, featured) {
  if (data && data.error) {
    return `<div class="info-server-empty">Lookup failed: ${_escHtml(data.error)}</div>`;
  }
  const online = data?.online;
  const players = data?.players;
  const motd = data?.motd?.clean?.[0] || '';
  const isFeatured = featured.some(s => s.address.toLowerCase() === q.toLowerCase());
  return `
    <div class="info-server-row info-server-row-result">
      <img class="info-server-icon info-server-icon-lg" src="https://api.mcsrvstat.us/icon/${encodeURIComponent(q)}" alt="" loading="lazy" onerror="this.style.visibility='hidden'">
      <div class="info-server-text" onclick="_infoCopyServerIp('${_escAttr(q)}')">
        <div class="info-server-name">${_escHtml(q)}</div>
        <div class="info-server-ip">
          ${online
            ? `<span class="info-server-live">● ${players?.online ?? 0} / ${players?.max ?? 0} online</span>${motd ? ' · ' + _escHtml(motd.slice(0, 36)) : ''}`
            : '<span class="info-server-offline">Offline</span>'}
        </div>
      </div>
      ${isFeatured
        ? `<button class="info-server-action remove" title="Remove from featured" onclick="event.stopPropagation(); _infoRemoveFeatured('${_escAttr(q)}')">×</button>`
        : `<button class="info-server-action add" title="Add to featured" onclick="event.stopPropagation(); _infoAddToFeatured('${_escAttr(q)}')">+</button>`}
    </div>
  `;
}

function _infoFilterServers(q) {
  // Debounce server-status API call so typing fast doesn't fire 30
  // requests. After 400ms idle, lookup the typed IP.
  if (_infoServerSearchTimer) { clearTimeout(_infoServerSearchTimer); _infoServerSearchTimer = null; }
  const query = (q || '').trim();
  if (!query) {
    _infoServerSearchResult = null;
    _infoRenderServers('');
    return;
  }
  // Only fire a live lookup if the query looks like an IP/hostname
  // (has a dot or colon). Otherwise just filter the featured list.
  const looksLikeIp = /\.|:/.test(query);
  if (!looksLikeIp) {
    _infoServerSearchResult = null;
    _infoRenderServers(query);
    return;
  }
  _infoServerSearchResult = 'loading';
  _infoRenderServers(query);
  _infoServerSearchTimer = setTimeout(async () => {
    try {
      const data = await window.icey.queryServerStatus(query);
      _infoServerSearchResult = data;
    } catch (e) {
      _infoServerSearchResult = { error: e.message };
    }
    _infoRenderServers(query);
  }, 400);
}
function _infoCopyServerIp(addr) {
  navigator.clipboard.writeText(addr);
  Toast.success('Copied ' + addr);
}
function _infoAddServer() {
  const inp = document.getElementById('info-server-search');
  const ip = inp?.value?.trim();
  if (!ip) { Toast.error('Type an IP first'); return; }
  navigator.clipboard.writeText(ip);
  Toast.success('Copied ' + ip);
}
async function _infoAddToFeatured(addr) {
  const featured = _infoGetFeatured();
  if (featured.some(s => s.address.toLowerCase() === addr.toLowerCase())) {
    Toast.info('Already featured');
    return;
  }
  featured.push({ name: addr, address: addr });
  await _infoSetFeatured(featured);
  Toast.success('Added to featured');
  _infoRenderServers(document.getElementById('info-server-search')?.value || '');
}
async function _infoRemoveFeatured(addr) {
  const featured = _infoGetFeatured().filter(s => s.address.toLowerCase() !== addr.toLowerCase());
  await _infoSetFeatured(featured);
  Toast.info('Removed');
  _infoRenderServers(document.getElementById('info-server-search')?.value || '');
}
