// Info page (DOM id is still "skins" internally to avoid having to
// rename every switchPage('skins') call site). Asymmetric layout:
// skin viewer + cape upload float on the LEFT against the page
// background with no card-boxes around them; the server list sits as
// a long vertical strip on the TOP-RIGHT, also box-less.
let _skinsLookupName = '';

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
               anchor inside. Still no bg box around the whole block. -->
          <div class="info-cape-block">
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
            <div class="info-cape-status" id="info-cape-status"></div>
            <div class="info-cape-note">
              Copies your PNG to every installation's
              <code>game/config/iceyclient/cape.png</code> and to
              <code>.minecraft/assets/skins/</code>. The in-game
              render needs the iceymod mixin landing in v1.86.34 to
              actually inject it as your local cape texture.
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
function _infoRenderServers(filter) {
  const container = document.getElementById('info-server-list');
  if (!container) return;
  const f = (filter || '').toLowerCase().trim();
  const items = f
    ? _infoDefaultServers.filter(s => s.name.toLowerCase().includes(f) || s.address.toLowerCase().includes(f))
    : _infoDefaultServers;
  if (items.length === 0) {
    container.innerHTML = `<div class="info-server-empty">No match — press Enter to copy "${_escHtml(filter)}"</div>`;
    return;
  }
  container.innerHTML = items.map(s => `
    <div class="info-server-row" onclick="_infoCopyServerIp('${_escAttr(s.address)}')">
      <img class="info-server-icon" src="https://api.mcsrvstat.us/icon/${encodeURIComponent(s.address)}" alt="" loading="lazy" onerror="this.style.visibility='hidden'">
      <div class="info-server-text">
        <div class="info-server-name">${_escHtml(s.name)}</div>
        <div class="info-server-ip">${_escHtml(s.address)}</div>
      </div>
    </div>
  `).join('');
}
function _infoFilterServers(q) { _infoRenderServers(q); }
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
