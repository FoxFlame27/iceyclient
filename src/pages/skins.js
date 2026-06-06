// The page id is still "skins" internally to avoid renaming every
// switchPage('skins') call across the codebase, but the visible label
// is "Info" — a 3-column layout: skin search + download (left), cape
// upload (middle), server browser (right).
let _skinsLookupName = '';

const _infoDefaultServers = [
  { name: 'Hypixel', address: 'mc.hypixel.net' },
  { name: 'Mineplex', address: 'us.mineplex.com' },
  { name: 'CubeCraft', address: 'play.cubecraft.net' },
  { name: 'ManaCube', address: 'play.manacube.com' },
  { name: 'MCCentral', address: 'mccentral.org' },
  { name: 'Lunar Network', address: 'lunar.gg' },
  { name: 'The Hive', address: 'geo.hivebedrock.network' },
  { name: 'PvP Legacy', address: 'pvplegacy.net' },
  { name: 'Badlion', address: 'play.badlion.net' },
  { name: 'mcpvp.club', address: 'mcpvp.club' },
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
        <div class="info-sub">Skin browser · custom cape · server picker</div>
      </div>

      <div class="info-grid">

        <!-- LEFT: skin viewer + download -->
        <div class="info-panel info-skin-panel">
          <div class="info-panel-title">Skin Browser</div>
          <div class="info-search-row">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input class="info-search-input" type="text" id="info-skin-search" placeholder="Username..." value="${_escAttr(displayName)}" spellcheck="false" maxlength="16" onkeydown="if(event.key==='Enter') _infoSkinLookup()">
            <button class="info-search-btn" onclick="_infoSkinLookup()">Go</button>
          </div>
          <div class="info-skin-card" id="info-skin-card">
            ${displayName
              ? `<img class="info-skin-img" id="info-skin-img" src="https://mineskin.eu/armor/body/${encodeURIComponent(displayName)}/200.png" alt="Skin">`
              : '<div class="info-skin-empty">Search for a player to see their skin</div>'}
          </div>
          <div class="info-skin-name" id="info-skin-name">${_escHtml(displayName)}</div>
          <div class="info-skin-views" id="info-skin-views" ${displayName ? '' : 'style="display:none"'}>
            <button class="info-view-btn active" onclick="_infoSkinView('body', this)">Body</button>
            <button class="info-view-btn" onclick="_infoSkinView('bust', this)">Bust</button>
            <button class="info-view-btn" onclick="_infoSkinView('head', this)">Head</button>
          </div>
          <button class="info-primary-btn" id="info-download-btn" onclick="_infoDownloadSkin()" ${displayName ? '' : 'disabled'}>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            Download 64×64 PNG
          </button>
        </div>

        <!-- MIDDLE: cape upload -->
        <div class="info-panel info-cape-panel">
          <div class="info-panel-title">Custom Cape (Local)</div>
          <div class="info-cape-drop" id="info-cape-drop" ondragover="event.preventDefault(); this.classList.add('drag')" ondragleave="this.classList.remove('drag')" ondrop="_infoCapeDrop(event)">
            <svg viewBox="0 0 24 24" width="44" height="44" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            <div class="info-cape-drop-title">Drop a 64×32 PNG here</div>
            <div class="info-cape-drop-sub">…or click to choose a file</div>
            <input type="file" id="info-cape-file" accept="image/png" style="display:none" onchange="_infoCapeFile(event)">
            <button class="info-secondary-btn" onclick="document.getElementById('info-cape-file').click()">Choose file</button>
          </div>
          <div class="info-cape-note">
            Drops the PNG into <code>.minecraft/assets/skins/</code>. To override a specific vanilla cape, rename the file to match one of the existing cape files in that folder. Only you can see it — it's a local texture override, not a Mojang upload.
          </div>
          <div class="info-cape-status" id="info-cape-status"></div>
        </div>

        <!-- RIGHT: servers -->
        <div class="info-panel info-servers-panel">
          <div class="info-panel-title">Servers</div>
          <div class="info-search-row info-search-row-lg">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input class="info-search-input" type="text" id="info-server-search" placeholder="Type a server IP..." spellcheck="false" onkeydown="if(event.key==='Enter') _infoAddServer()">
            <button class="info-search-btn" onclick="_infoAddServer()">Copy</button>
          </div>
          <div class="info-server-list" id="info-server-list"></div>
        </div>

      </div>
    </div>
  `;

  _infoRenderServers();
}

function _escHtml(s) { return String(s || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]); }
function _escAttr(s) { return String(s || '').replace(/"/g, '&quot;'); }

// ── Skin browser ───────────────────────────────────────────────
function _infoSkinLookup() {
  const input = document.getElementById('info-skin-search');
  const name = input?.value.trim();
  if (!name) { Toast.error('Enter a username'); return; }
  _skinsLookupName = name;
  const card = document.getElementById('info-skin-card');
  const nameEl = document.getElementById('info-skin-name');
  const views = document.getElementById('info-skin-views');
  const dl = document.getElementById('info-download-btn');
  if (card) card.innerHTML = `<img class="info-skin-img" id="info-skin-img" src="https://mineskin.eu/armor/body/${encodeURIComponent(name)}/200.png" alt="Skin" onerror="_infoSkinLookupError()">`;
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
    body: `https://mineskin.eu/armor/body/${name}/200.png`,
    bust: `https://mineskin.eu/armor/bust/${name}/200.png`,
    head: `https://mineskin.eu/headhelm/${name}/200.png`
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
      if (status) status.innerHTML = '<span class="info-cape-ok">Saved to ' + _escHtml(res.savedTo) + '</span>';
      Toast.success('Cape installed');
    }
  } catch (e) {
    if (status) status.innerHTML = '<span class="info-cape-err">Failed: ' + _escHtml(e.message) + '</span>';
    Toast.error('Cape install failed: ' + e.message);
  }
}

// ── Server browser ─────────────────────────────────────────────
function _infoRenderServers() {
  const container = document.getElementById('info-server-list');
  if (!container) return;
  container.innerHTML = _infoDefaultServers.map(s => `
    <div class="info-server-row" onclick="_infoCopyServerIp('${_escAttr(s.address)}')">
      <img class="info-server-icon" src="https://api.mcsrvstat.us/icon/${encodeURIComponent(s.address)}" alt="">
      <div class="info-server-text">
        <div class="info-server-name">${_escHtml(s.name)}</div>
        <div class="info-server-ip">${_escHtml(s.address)}</div>
      </div>
      <svg class="info-server-copy" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
    </div>
  `).join('');
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
