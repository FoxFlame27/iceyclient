let _homeTimerInterval = null;
let _homeStateCleanup = null;
let _serverRefreshInterval = null;

const FEATURED_SERVER = { name: 'Icey SMP', address: 'iceysmp.modrinth.gg' };
const SERVERS = [
  { name: 'Hypixel', address: 'mc.hypixel.net' },
  { name: 'Mineplex', address: 'us.mineplex.com' },
  { name: 'CubeCraft', address: 'play.cubecraft.net' },
  { name: 'ManaCube', address: 'play.manacube.com' },
  { name: 'MCCentral', address: 'mccentral.org' },
  { name: 'Lunar Network', address: 'lunar.gg' },
  { name: 'The Hive', address: 'geo.hivebedrock.network' },
  { name: 'PvP Legacy', address: 'pvplegacy.net' },
  { name: 'Badlion', address: 'play.badlion.net' },
];

async function HomePageInit() {
  const page = document.getElementById('page-home');
  try {
  const settings = SettingsManager.getAll();
  const showTimer = settings.showSessionTimer !== false;
  const isLiquid = (settings.layoutTheme || 'classic') === 'liquid';
  const isFrost = settings.layoutTheme === 'frost';
  _hqDisposeSkin();
  // Pull the active account for the Liquid Account button — same
  // mineskin avatar source the titlebar profile uses.
  let _liquidAuthUser = null;
  try {
    const _auth = await window.icey.getAuth();
    if (_auth && _auth.username) _liquidAuthUser = _auth.username;
  } catch (_) {}

  // Both layouts share the launch button + timer + installations + servers
  // logic that follows this innerHTML block. CSS hides the inactive
  // variant via [data-layout="liquid"] / :not([data-layout="liquid"]).
  const liquidHero = `
    <div class="home-liquid">
      <div class="home-liquid-menu">
        <!-- Play button — same exact markup + classes as the Classic
             launch button so it renders identically. Wrapped in a snow
             bar overlay just like Classic. -->
        <div class="home-launch-bar liquid-launch-wrap">
          <div class="launch-bar-snow" id="launch-bar-snow"></div>
          <button class="launch-btn launch-btn-idle" id="launch-btn" onclick="HomePlayClick()">
            <span class="launch-btn-title" id="launch-btn-title">LAUNCH</span>
            <span class="launch-btn-subtitle" id="launch-btn-subtitle"><span class="launch-btn-dot"></span> READY TO LAUNCH</span>
          </button>
        </div>

        <button class="liquid-menu-btn" onclick="switchPage('installations')">
          <div class="liquid-menu-icon"><img src="assets/installations-icon.png" alt="" style="width:36px;height:36px;object-fit:contain;filter:brightness(0) invert(1);"></div>
          <div class="liquid-menu-text">
            <div class="liquid-menu-title">INSTALLATIONS</div>
            <div class="liquid-menu-sub">Manage your Minecraft versions</div>
          </div>
          <div class="liquid-menu-arrow">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </button>

        <button class="liquid-menu-btn" onclick="switchPage('mods')">
          <div class="liquid-menu-icon"><img src="assets/fabric-icon.png" alt="" style="width:36px;height:36px;object-fit:contain;filter:brightness(0) invert(1);"></div>
          <div class="liquid-menu-text">
            <div class="liquid-menu-title">MODS</div>
            <div class="liquid-menu-sub">Browse and install mods</div>
          </div>
          <div class="liquid-menu-arrow">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </button>

        <button class="liquid-menu-btn" onclick="_liquidOpenAccount()">
          <div class="liquid-menu-icon liquid-menu-icon-skin">
            ${_liquidAuthUser
              ? `<img src="https://nmsr.nickac.dev/face/${encodeURIComponent(_liquidAuthUser)}?overlay=true" alt="${_liquidAuthUser}" onerror="this.style.display='none'">`
              : `<svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"/></svg>`}
          </div>
          <div class="liquid-menu-text">
            <div class="liquid-menu-title">ACCOUNT</div>
            <div class="liquid-menu-sub" id="liquid-account-sub">${_liquidAuthUser ? 'Signed in as ' + _liquidAuthUser : 'Sign in or switch account'}</div>
          </div>
          <div class="liquid-menu-arrow">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </button>
      </div>

      <div class="home-liquid-side">
        ${SettingsManager.isSkiflame()
          ? `<img class="home-liquid-logo-img home-skiflame-logo" src="assets/skiflame-logo.png" alt="Skiflame" onerror="this.style.display='none'">`
          : `<img class="home-liquid-logo-img" src="assets/icon.png" alt="Icey Client" onerror="this.style.display='none'">
        <div class="home-liquid-title">ICEY CLIENT</div>`}
        <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
          <span class="home-timer-label">Playtime</span>
          <span class="home-timer-value" id="home-timer-value">00:00:00</span>
        </div>
      </div>

      <!-- Installations cards across the bottom — same #home-inst-cards
           container the Classic layout uses, so _loadHomeInstallations()
           wires it up automatically. -->
      <div class="home-liquid-installs">
        <div class="home-inst-header">
          <span class="home-inst-title">Your Installations</span>
        </div>
        <div class="home-inst-cards" id="home-inst-cards"></div>
      </div>
    </div>
  `;

  // Classic home: the player's skin centre stage with the launch button
  // under it, servers in a glass panel on the right. The installation is
  // picked from the menu on the launch button's arrow.
  let _accountType = null;
  try {
    const _accts = await window.icey.getAccounts();
    const _active = (_accts.accounts || []).find(a => a.uuid === _accts.activeUuid);
    if (_active) _accountType = _active.type;
  } catch (_) {}
  const _accountLabel = !_liquidAuthUser ? 'Not signed in'
    : _accountType === 'offline' ? 'Offline account'
    : _accountType === 'launcher' ? 'Minecraft Launcher account'
    : 'Microsoft account';
  const _skinName = encodeURIComponent(_liquidAuthUser || 'MHF_Steve');

  const classicHero = `
    <div class="home-glass">
      <div class="hg-stage">
        <svg class="hg-waves" viewBox="0 0 1200 600" preserveAspectRatio="none" aria-hidden="true">
          <path d="M-20 330 C 220 210, 420 470, 640 330 S 1020 230, 1220 350"/>
          <path d="M-20 390 C 260 300, 440 520, 680 390 S 1000 300, 1220 420"/>
          <path d="M-20 270 C 200 380, 460 180, 660 290 S 1040 390, 1220 260"/>
        </svg>

        <button class="hg-identity" onclick="_liquidOpenAccount()" title="Accounts">
          <span class="hg-name">${_escapeHtml(_liquidAuthUser || 'Player')}</span>
          <span class="hg-tag ${_liquidAuthUser ? 'on' : ''}"><span class="hg-tag-dot"></span>${_accountLabel}</span>
        </button>

        <div class="hg-skin-wrap">
          <div class="hg-skin-glow"></div>
          <img class="hg-skin" alt="" draggable="false"
               src="https://nmsr.nickac.dev/fullbody/${_skinName}?overlay=true"
               onerror="_homeSkinFallback(this)">
        </div>

        <div class="hg-launch-row">
          <div class="home-launch-bar">
            <div class="launch-bar-snow" id="launch-bar-snow"></div>
            <button class="launch-btn launch-btn-idle" id="launch-btn" onclick="HomePlayClick()">
              <span class="launch-btn-title" id="launch-btn-title">LAUNCH</span>
              <span class="launch-btn-subtitle" id="launch-btn-subtitle"><span class="launch-btn-dot"></span> READY TO LAUNCH</span>
            </button>
          </div>
          <button class="hg-launch-caret" id="hg-launch-caret" onclick="_homeToggleInstMenu(event)" title="Choose installation">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
          <div class="hg-inst-menu hidden" id="hg-inst-menu"></div>
        </div>

        <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
          <span class="home-timer-label">Playtime</span>
          <span class="home-timer-value" id="home-timer-value">00:00:00</span>
        </div>
      </div>

      <!-- Right: installations (with their pictures), servers on a second tab -->
      <div class="home-servers-col hg-side" data-tab="${_homeSideTab}">
        <div class="hg-tabs">
          <button class="hg-tab" data-tab="installations" onclick="_homeSetSideTab('installations')">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>
            Installations
          </button>
          <button class="hg-tab" data-tab="servers" onclick="_homeSetSideTab('servers')">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><line x1="7" y1="7.5" x2="7.01" y2="7.5"/><line x1="7" y1="16.5" x2="7.01" y2="16.5"/></svg>
            Servers
          </button>
        </div>
        <div class="hg-pane hg-pane-installations">
          <div class="home-inst-cards" id="home-inst-cards"></div>
          <button class="home-add-server-btn" onclick="switchPage('installations')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Manage installations
          </button>
        </div>
        <div class="home-server-list hg-pane hg-pane-servers" id="server-list">
          <div class="home-server-bar featured" id="featured-server">
            <div class="server-loading">Loading Icey SMP...</div>
          </div>
          ${SERVERS.map((s, i) => `<div class="home-server-bar" id="server-bar-${i}"><div class="server-loading">${s.name}</div></div>`).join('')}
          <div class="home-custom-servers" id="custom-server-list"></div>
          <button class="home-add-server-btn" onclick="_showAddServerModal()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Add Server
          </button>
        </div>
      </div>
    </div>
  `;

  // Frost home: the Classic shell (side bar, top bar) with another home
  // page. The player's animated skin stands on the wallpaper, the launch
  // button under it, and below that a deck of cards to scroll through:
  // servers to join straight away, or the installations.
  if (!_deck.mode) _deck.mode = SettingsManager.get('homeDeckMode') === 'installations' ? 'installations' : 'servers';
  const frostHero = !isFrost ? '' : `
    <div class="home-glass hq">
      <div class="hq-scene" aria-hidden="true">
        <div class="hq-scene-img"></div>
        <canvas class="hq-snow" id="hq-snow"></canvas>
        <div class="hq-scene-shade"></div>
      </div>

      <button class="hg-identity" onclick="_liquidOpenAccount()" title="Accounts">
        <span class="hg-name" data-no-i18n>${_escapeHtml(_liquidAuthUser || 'Player')}</span>
      </button>

      <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
        <span class="home-timer-label">Playtime</span>
        <span class="home-timer-value" id="home-timer-value">00:00:00</span>
      </div>

      <div class="hq-skin" id="hq-skin" title="Right-click to customise" oncontextmenu="event.preventDefault(); event.stopPropagation(); switchPage('character');">
        <div class="hq-skin-glow"></div>
        <canvas class="hq-skin-canvas" id="hq-skin-canvas"></canvas>
        <img class="hq-skin-img" id="hq-skin-img" alt="" draggable="false"
             data-src="https://nmsr.nickac.dev/fullbody/${_skinName}?overlay=true"
             onerror="_homeSkinFallback(this)">
      </div>

      <div class="hq-launch-row">
        <div class="home-launch-bar">
          <div class="launch-bar-snow" id="launch-bar-snow"></div>
          <button class="launch-btn launch-btn-idle" id="launch-btn" onclick="HomePlayClick()">
            <span class="launch-btn-title" id="launch-btn-title">LAUNCH</span>
            <span class="launch-btn-subtitle" id="launch-btn-subtitle"><span class="launch-btn-dot"></span> READY TO LAUNCH</span>
          </button>
        </div>
      </div>

      <div class="hq-switch-row">
        <div class="hq-switch" id="hq-switch" data-mode="${_deck.mode}">
          <span class="hq-switch-thumb"></span>
          <button class="hq-switch-btn" data-mode="servers" onclick="_deckSetMode('servers')">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><line x1="7" y1="7.5" x2="7.01" y2="7.5"/><line x1="7" y1="16.5" x2="7.01" y2="16.5"/></svg>
            Servers
          </button>
          <button class="hq-switch-btn" data-mode="installations" onclick="_deckSetMode('installations')">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>
            Installations
          </button>
        </div>
        <button class="hq-add" id="hq-add" onclick="_deckAddClick()" title="Add Server">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        </button>
      </div>

      <div class="hq-deck" id="hq-deck">
        <button class="hq-arrow hq-arrow-left" onclick="_deckStep(-1)" aria-label="Previous">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <div class="hq-track" id="hq-track"></div>
        <button class="hq-arrow hq-arrow-right" onclick="_deckStep(1)" aria-label="Next">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </div>
    </div>
  `;

  page.innerHTML = isLiquid ? liquidHero : isFrost ? frostHero : classicHero;

  if (_homeStateCleanup) _homeStateCleanup();
  _homeStateCleanup = MinecraftLauncher.onChange((state) => _homeUpdateLaunchButton(state, showTimer));
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), showTimer);

  if (_homeTimerInterval) clearInterval(_homeTimerInterval);
  _homeTimerInterval = setInterval(() => {
    if (MinecraftLauncher.getState() === 'running') {
      const el = document.getElementById('home-timer-value');
      if (el) el.textContent = MinecraftLauncher.getSessionTime();
    }
  }, 1000);

  _initLaunchBarSnow();
  _loadHomeInstallations();
  if (_serverRefreshInterval) clearInterval(_serverRefreshInterval);
  if (isFrost) {
    _hqInitSnow();
    _hqInitSkin(_liquidAuthUser);
    _deckInit();
    _serverRefreshInterval = setInterval(() => _deckRefreshStatus(true), 120000);
  } else {
    _loadHomeInstMenu();
    _loadAllServers();
    _serverRefreshInterval = setInterval(_loadAllServers, 120000);
  }

  } catch (err) { console.error('[HomePageInit] ERROR:', err); }
}

async function _loadHomeInstallations() {
  const container = document.getElementById('home-inst-cards');
  if (!container) return;
  try {
    const installations = await window.icey.getInstallations();
    if (installations.length === 0) {
      container.innerHTML = `<div class="home-inst-empty" onclick="switchPage('installations')">No installations &mdash; click to create one</div>`;
      return;
    }
    container.innerHTML = installations.map(inst => {
      const isSelected = inst.selected;
      const imageUrl = inst.image
        ? `file://${inst.image.replace(/\\/g, '/')}`
        : 'assets/installbg-default.png';
      const platformLabel = inst.platform === 'fabric' ? 'Fabric' : 'Vanilla';
      const platformClass = inst.platform === 'fabric' ? 'fabric' : 'vanilla';
      return `
        <div class="home-inst-card ${isSelected ? 'selected' : ''}" onclick="_homeSelectInstallation('${inst.id}')">
          <div class="home-inst-card-img" style="background-image: url('${imageUrl}')">
            <div class="home-inst-card-version-overlay">${inst.version}</div>
          </div>
          <div class="home-inst-card-body">
            <div class="home-inst-card-name">${inst.name}</div>
            <span class="home-inst-card-platform ${platformClass}">${platformLabel}</span>
          </div>
          ${isSelected ? '<div class="home-inst-card-check"><svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><polyline points="20 6 9 17 4 12" fill="none" stroke="currentColor" stroke-width="2.5"/></svg></div>' : ''}
        </div>
      `;
    }).join('');
  } catch (e) {
    container.innerHTML = '<div class="home-inst-empty">Failed to load installations</div>';
  }
}

// Classic home: which tab the side panel shows. Kept while the app runs.
let _homeSideTab = 'installations';
function _homeSetSideTab(tab) {
  _homeSideTab = tab === 'servers' ? 'servers' : 'installations';
  const side = document.querySelector('.hg-side');
  if (side) side.dataset.tab = _homeSideTab;
}

// Classic home: the installation menu behind the launch button's arrow.
async function _loadHomeInstMenu() {
  const menu = document.getElementById('hg-inst-menu');
  if (!menu) return;
  let installations = [];
  try { installations = await window.icey.getInstallations(); } catch (_) {}
  const items = installations.map(inst => {
    const imageUrl = inst.image
      ? `file://${inst.image.replace(/\\/g, '/')}`
      : 'assets/installbg-default.png';
    const platform = inst.platform === 'fabric' ? 'Fabric' : 'Vanilla';
    return `
      <button class="hg-inst-item ${inst.selected ? 'selected' : ''}" onclick="_homeSelectInstallation('${inst.id}')">
        <span class="hg-inst-thumb" style="background-image:url('${imageUrl}')"></span>
        <span class="hg-inst-text">
          <span class="hg-inst-name">${_escapeHtml(inst.name)}</span>
          <span class="hg-inst-meta">${platform} ${_escapeHtml(inst.version)}</span>
        </span>
        ${inst.selected ? '<svg class="hg-inst-check" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>' : ''}
      </button>`;
  }).join('');
  menu.innerHTML = `
    <div class="hg-inst-menu-title">Installation</div>
    <div class="hg-inst-list">${items || '<div class="hg-inst-none">No installations yet</div>'}</div>
    <button class="hg-inst-manage" onclick="switchPage('installations')">
      ${installations.length ? 'Manage installations' : 'Create an installation'}
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
    </button>`;
}

function _homeToggleInstMenu(e) {
  if (e) e.stopPropagation();
  const menu = document.getElementById('hg-inst-menu');
  const caret = document.getElementById('hg-launch-caret');
  if (!menu) return;
  const open = menu.classList.toggle('hidden') === false;
  if (caret) caret.classList.toggle('open', open);
}

document.addEventListener('click', (e) => {
  const menu = document.getElementById('hg-inst-menu');
  if (!menu || menu.classList.contains('hidden')) return;
  if (menu.contains(e.target)) return;
  menu.classList.add('hidden');
  const caret = document.getElementById('hg-launch-caret');
  if (caret) caret.classList.remove('open');
});

// Cracked names (and offline play) have no skin to render: show Steve.
function _homeSkinFallback(img) {
  const steve = 'https://nmsr.nickac.dev/fullbody/MHF_Steve?overlay=true';
  if (img.src !== steve) img.src = steve;
  else img.style.visibility = 'hidden';
}

async function _homeSelectInstallation(id) {
  const installations = await window.icey.getInstallations();
  installations.forEach(i => i.selected = (i.id === id));
  for (const inst of installations) {
    await window.icey.saveInstallation(inst);
  }
  _loadHomeInstallations();
  _loadHomeInstMenu();
  _deckSyncInstallations();
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), SettingsManager.getAll().showSessionTimer !== false);
  Toast.success('Installation selected');
}

async function _fetchServerStatus(address) {
  try { const r = await fetch(`https://api.mcsrvstat.us/3/${address}`, { headers: { 'User-Agent': 'IceyClient/1.0' } }); return await r.json(); }
  catch (_) { return { online: false }; }
}

function _copyServerIp(address) {
  navigator.clipboard.writeText(address);
  Toast.success('Copied ' + address);
}

function _renderFeaturedServer(data, server) {
  const el = document.getElementById('featured-server');
  if (!el) return;
  el.style.cursor = 'pointer';
  el.onclick = () => _copyServerIp(server.address);
  if (!data.online) { el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-status offline">Offline</div>`; return; }
  el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-players"><span class="server-bar-online">${(data.players?.online ?? 0).toLocaleString()}</span><span class="server-bar-max">/ ${(data.players?.max ?? 0).toLocaleString()}</span></div>`;
}

function _renderServerBar(data, server, i) {
  const el = document.getElementById(`server-bar-${i}`);
  if (!el) return;
  el.style.cursor = 'pointer';
  el.onclick = () => _copyServerIp(server.address);
  if (!data.online) { el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-status offline">Offline</div>`; return; }
  el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-players"><span class="server-bar-online">${(data.players?.online ?? 0).toLocaleString()}</span><span class="server-bar-max">/ ${(data.players?.max ?? 0).toLocaleString()}</span></div>`;
}

async function _loadAllServers() {
  const [fd, ...sd] = await Promise.all([_fetchServerStatus(FEATURED_SERVER.address), ...SERVERS.map(s => _fetchServerStatus(s.address))]);
  _renderFeaturedServer(fd, FEATURED_SERVER);
  sd.forEach((d, i) => _renderServerBar(d, SERVERS[i], i));
  await _loadCustomServers();
}

async function _loadCustomServers() {
  const customServers = SettingsManager.get('customServers') || [];
  const container = document.getElementById('custom-server-list');
  if (!container) return;
  if (customServers.length === 0) { container.innerHTML = ''; return; }
  container.innerHTML = customServers.map((s, i) => `<div class="home-server-bar" id="custom-server-${i}"><div class="server-loading">${s.name || s.address}</div></div>`).join('');
  const results = await Promise.all(customServers.map(s => _fetchServerStatus(s.address)));
  results.forEach((data, i) => {
    const el = document.getElementById(`custom-server-${i}`);
    if (!el) return;
    const s = customServers[i];
    const name = s.name || s.address;
    el.style.cursor = 'pointer';
    el.onclick = () => _copyServerIp(s.address);
    if (!data.online) { el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${s.address}" alt=""></div><div class="server-bar-name">${name}</div><div class="server-bar-address">${s.address}</div><div class="server-bar-status offline">Offline</div><button class="server-remove-btn" onclick="event.stopPropagation(); _removeCustomServer(${i})" title="Remove">&times;</button>`; return; }
    el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${s.address}" alt=""></div><div class="server-bar-name">${name}</div><div class="server-bar-address">${s.address}</div><div class="server-bar-players"><span class="server-bar-online">${(data.players?.online ?? 0).toLocaleString()}</span><span class="server-bar-max">/ ${(data.players?.max ?? 0).toLocaleString()}</span></div><button class="server-remove-btn" onclick="event.stopPropagation(); _removeCustomServer(${i})" title="Remove">&times;</button>`;
  });
}

function _showAddServerModal() {
  showModal(`
    <div class="modal-header">
      <h2 class="modal-title">Add Server</h2>
      <button class="modal-close" onclick="closeModal()">
        <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
    </div>
    <div class="modal-body">
      <div class="form-group" style="margin-bottom:12px;">
        <label class="form-label">Server Name</label>
        <input class="form-input" type="text" id="add-server-name" placeholder="e.g. My Server" spellcheck="false">
      </div>
      <div class="form-group">
        <label class="form-label">Server IP</label>
        <input class="form-input" type="text" id="add-server-ip" placeholder="e.g. play.example.com" spellcheck="false">
      </div>
    </div>
    <div class="modal-footer">
      <button class="modal-btn modal-btn-outline" onclick="closeModal()">Cancel</button>
      <button class="modal-btn modal-btn-primary" onclick="_addCustomServer()">Add</button>
    </div>
  `);
  setTimeout(() => document.getElementById('add-server-ip')?.focus(), 100);
}

async function _addCustomServer() {
  const name = document.getElementById('add-server-name')?.value.trim();
  const address = document.getElementById('add-server-ip')?.value.trim();
  if (!address) { Toast.error('Enter a server IP'); return; }
  const customServers = SettingsManager.get('customServers') || [];
  customServers.push({ name: name || address, address });
  await SettingsManager.set('customServers', customServers);
  closeModal();
  Toast.success('Server added');
  _loadCustomServers();
  _deckReloadServers();
}

async function _removeCustomServer(index) {
  const customServers = SettingsManager.get('customServers') || [];
  customServers.splice(index, 1);
  await SettingsManager.set('customServers', customServers);
  Toast.info('Server removed');
  _loadCustomServers();
  _deckReloadServers();
}

function _initLaunchBarSnow() {
  const c = document.getElementById('launch-bar-snow');
  if (!c) return;
  const mk = () => { const f = document.createElement('div'); f.className = 'snowflake'; const s = Math.random()*3+1.5; f.style.cssText = `width:${s}px;height:${s}px;left:${Math.random()*100}%;animation-duration:${Math.random()*2+2}s;animation-delay:${Math.random()*2}s;opacity:${Math.random()*0.5+0.3}`; c.appendChild(f); setTimeout(() => f.remove(), 5000); };
  for (let i = 0; i < 6; i++) mk();
  setInterval(() => { if (document.getElementById('launch-bar-snow')) mk(); }, 500);
}

async function _homeGetSelected() {
  try {
    const installations = await window.icey.getInstallations();
    return installations.find(i => i.selected) || null;
  } catch (_) { return null; }
}

async function _homeUpdateLaunchButton(state, showTimer) {
  const btn = document.getElementById('launch-btn'), timer = document.getElementById('home-timer');
  if (!btn) return;
  const selected = await _homeGetSelected();
  const target = selected
    ? `${selected.platform === 'fabric' ? 'Fabric' : 'Vanilla'} ${_escapeHtml(selected.version)}`
    : 'No installation selected';
  // Frost home with the deck on Servers: the button joins the card in front.
  const joinServer = _deckJoinTarget();
  _hqSkinState(state);

  // Same button structure in both layouts — full innerHTML rewrite is
  // safe regardless of layoutTheme.
  btn.className = 'launch-btn';
  if (state === 'idle') {
    btn.classList.add('launch-btn-idle'); btn.disabled = false;
    btn.innerHTML = joinServer
      ? `<span class="launch-btn-title">JOIN</span><span class="launch-btn-subtitle"><span class="launch-btn-dot"></span> <span data-no-i18n>${_escapeHtml(joinServer.name)}</span><span class="launch-btn-sep">&middot;</span><span>${target}</span></span>`
      : `<span class="launch-btn-title">LAUNCH</span><span class="launch-btn-subtitle"><span class="launch-btn-dot"></span> ${target}</span>`;
    if (timer) timer.classList.remove('visible');
  } else if (state === 'starting') {
    btn.classList.add('launch-btn-starting'); btn.disabled = true;
    btn.innerHTML = `<span class="launch-btn-title starting-dots">Launching</span><span class="launch-btn-subtitle">PLEASE WAIT</span>`;
    if (timer) timer.classList.remove('visible');
  } else if (state === 'running') {
    btn.classList.add('launch-btn-running'); btn.disabled = false;
    btn.innerHTML = `<span class="launch-btn-title">STOP</span><span class="launch-btn-subtitle">GAME IS RUNNING</span>`;
    if (timer && showTimer) timer.classList.add('visible');
  }
}

// Liquid Account button — opens a dedicated centered modal listing all
// accounts. The titlebar dropdown lives at the top-right of the screen
// and is easy to miss when the user clicks Account on the home page,
// so this surface keeps the action local to where they clicked.
async function _liquidOpenAccount() {
  let accountsData = { activeUuid: null, accounts: [], maxAccounts: 5 };
  try { accountsData = await window.icey.getAccounts(); } catch (_) {}
  const accounts = accountsData.accounts || [];
  const activeUuid = accountsData.activeUuid;
  const active = accounts.find(a => a.uuid === activeUuid) || null;
  const others = accounts.filter(a => a.uuid !== activeUuid);
  const atMax = accounts.length >= (accountsData.maxAccounts || 5);

  const esc = (s) => String(s || '').replace(/[&<>"']/g,
    c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
  const attr = (s) => String(s || '').replace(/"/g, '&quot;');
  const typeBadge = (t) => t === 'offline'
    ? '<span class="acct-type offline">Cracked</span>'
    : t === 'launcher'
      ? '<span class="acct-type ms">Launcher</span>'
      : '<span class="acct-type ms">MS</span>';

  const headerHtml = active
    ? `<div class="acct-active">
         <img class="acct-active-avatar" src="https://nmsr.nickac.dev/face/${attr(active.username)}?overlay=true" alt="">
         <div class="acct-active-info">
           <div class="acct-active-name">${esc(active.username)} ${typeBadge(active.type)}</div>
           <div class="acct-active-label">Active Account</div>
         </div>
       </div>`
    : `<div class="acct-active">
         <div class="acct-active-info">
           <div class="acct-active-name">No active account</div>
           <div class="acct-active-label">${others.length} saved</div>
         </div>
       </div>`;

  const othersHtml = others.length === 0 ? '' : `
    <div class="acct-section-label">Switch to</div>
    <div class="acct-list">
      ${others.map(a => `
        <div class="acct-row">
          <button class="acct-switch" onclick="_liquidSwitchAccount('${attr(a.uuid)}')">
            <img class="acct-avatar" src="https://nmsr.nickac.dev/face/${attr(a.username)}?overlay=true" alt="">
            <span class="acct-name">${esc(a.username)}</span>
            ${typeBadge(a.type)}
            ${a.expired ? '<span class="acct-expired">expired</span>' : ''}
          </button>
          <button class="acct-remove" title="Remove" onclick="event.stopPropagation(); _liquidRemoveAccount('${attr(a.uuid)}')">&times;</button>
        </div>
      `).join('')}
    </div>`;

  const addHtml = atMax
    ? `<div class="acct-maxed">Max ${accountsData.maxAccounts} accounts. Remove one to add another.</div>`
    : `<div class="acct-add-row">
         <button class="acct-add-btn ms" onclick="_liquidImportLauncher()">Use Minecraft Launcher account</button>
         <button class="acct-add-btn ms" onclick="_liquidAddMicrosoft()">Add Microsoft account</button>
         <button class="acct-add-btn cracked" onclick="_liquidAddCracked()">Add cracked account</button>
       </div>`;

  const footerHtml = active
    ? `<div class="acct-footer">
         <button class="acct-secondary" onclick="closeModal(); switchPage('skins');">Open Info</button>
         <button class="acct-secondary danger" onclick="_liquidLogout()">Log out of ${esc(active.username)}</button>
       </div>`
    : '';

  showModal(`
    <div class="acct-picker">
      <div class="acct-picker-header">
        <div class="acct-picker-title">Account</div>
        <button class="modal-close" onclick="closeModal()">
          <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
        </button>
      </div>
      ${headerHtml}
      ${othersHtml}
      <div class="acct-divider"></div>
      ${addHtml}
      ${footerHtml}
    </div>
  `);
}

async function _liquidSwitchAccount(uuid) {
  const result = await window.icey.switchAccount(uuid);
  if (result.error) { Toast.error(result.error); return; }
  Toast.success('Switched to ' + result.active.username);
  await SettingsManager.set('username', result.active.username);
  if (typeof loadNavProfile === 'function') loadNavProfile();
  closeModal();
  HomePageInit().catch(() => {});
}

async function _liquidRemoveAccount(uuid) {
  const result = await window.icey.removeAccount(uuid);
  if (result.error) { Toast.error(result.error); return; }
  Toast.info('Account removed');
  if (typeof loadNavProfile === 'function') loadNavProfile();
  _liquidOpenAccount();
}

async function _liquidImportLauncher() {
  closeModal();
  const r = await window.icey.importLauncherAccounts();
  if (r.error) { Toast.error(r.error); return; }
  if (!r.found) { Toast.info('No account found in the Minecraft Launcher. Sign in there once, then try again.'); return; }
  Toast.success(r.imported || r.activated ? 'Using your Minecraft Launcher account' : 'Minecraft Launcher account already listed');
  if (typeof loadNavProfile === 'function') loadNavProfile();
  HomePageInit().catch(() => {});
}

async function _liquidAddMicrosoft() {
  closeModal();
  const result = await window.icey.msLogin();
  if (result.error) {
    if (result.removed && result.removed.length) {
      Toast.info('Login cancelled — removed expired account ' + result.removed.join(', '));
      if (typeof loadNavProfile === 'function') loadNavProfile();
      HomePageInit().catch(() => {});
    } else Toast.error(result.error);
    return;
  }
  Toast.success('Added ' + result.username);
  await SettingsManager.set('username', result.username);
  if (typeof loadNavProfile === 'function') loadNavProfile();
  HomePageInit().catch(() => {});
}

function _liquidAddCracked() {
  closeModal();
  // Re-use the existing offline-account prompt from app.js
  if (typeof _promptAddOffline === 'function') _promptAddOffline();
}

async function _liquidLogout() {
  const r = await window.icey.msLogout();
  if (r && r.error) { Toast.error(r.error); return; }
  Toast.info('Logged out');
  if (typeof loadNavProfile === 'function') loadNavProfile();
  closeModal();
  HomePageInit().catch(() => {});
}

async function HomePlayClick() {
  const state = MinecraftLauncher.getState();
  if (state === 'running') { MinecraftLauncher.stop(); return; }
  if (state === 'starting') return;
  const installations = await window.icey.getInstallations();
  const selected = installations.find(i => i.selected);
  if (!selected) { Toast.info('Select an installation first'); return; }
  const joinServer = _deckJoinTarget();
  if (joinServer) {
    _deck.focusKey = _deckKey(joinServer.address);
    _deckSetPref(joinServer.key, { installationId: selected.id });
  }
  _hqLaunchBurst();
  if (document.getElementById('hq-skin')) CharacterRig.playSound(CharacterRig.get().startSound);
  await MinecraftLauncher.launch(selected.id, joinServer ? { server: joinServer.address } : undefined);
}

// ───────────────────────────────────────────────────────────────
// Frost home: the card deck.
//
// One row of cards with the chosen one in front, in colour; the rest fan
// out to both sides in grey. Two decks share the row: servers (played
// ones first, then saved ones, then a few big networks so it is never
// empty) and installations. Scroll, arrow keys, the arrows or a click on
// a card move it. On Servers the launch button joins the front card with
// the selected installation; on Installations the front card is the
// selected installation.
// ───────────────────────────────────────────────────────────────
const _deck = {
  mode: null,
  items: { servers: [], installations: [] },
  index: { servers: 0, installations: 0 },
  status: new Map(),      // server key → { at, data }
  focusKey: null,         // server to keep in front across reloads
  wheelAcc: 0,
  wheelAt: 0,
  saveTimer: null,
};

// A server without an icon of its own gets a grass block, and its green.
const DECK_DEFAULT_ICON = 'assets/server-default.png';
const DECK_DEFAULT_TINT = '112,196,84';

// What the player decided about a server card, kept in settings under
// `serverPrefs`, keyed like the cards: { pinnedAt, hidden, name, installationId }.
function _deckPrefs() { return SettingsManager.get('serverPrefs') || {}; }
async function _deckSetPref(key, change) {
  const all = { ..._deckPrefs() };
  const cur = { ...(all[key] || {}), ...change };
  for (const k of Object.keys(cur)) if (cur[k] === null || cur[k] === false || cur[k] === '') delete cur[k];
  if (Object.keys(cur).length) all[key] = cur; else delete all[key];
  await SettingsManager.set('serverPrefs', all);
}

// Server icons outside the deck (the Info page's list): an <img> marked
// with data-server-icon="<address>" starts as the grass block and gets the
// server's own icon once the launcher has asked the server for it.
const _serverIconAsk = new Map();   // address → Promise<icon | null>
function _fillServerIcons(root) {
  if (!root || !window.icey || !window.icey.pingServer) return;
  root.querySelectorAll('img[data-server-icon]').forEach((img) => {
    const address = img.dataset.serverIcon;
    if (!_serverIconAsk.has(address)) {
      _serverIconAsk.set(address, window.icey.pingServer(address).then(p => (p && p.icon) || null).catch(() => null));
    }
    _serverIconAsk.get(address).then((icon) => { if (icon && img.isConnected) img.src = icon; });
  });
}

// "mcpvp" for mcpvp.com, eu.mcpvp.club and mcpvp.hub alike.
function _deckNetworkLabel(key) {
  return _deckNameFor(key).toLowerCase().replace(/[^a-z0-9]/g, '');
}

// A card whose server gave no icon (an address that only exists inside a
// network, like mcpvp.hub, or a regional or verification host) takes the
// icon of another card of the same network.
function _deckBorrowIcons() {
  const items = _deck.items.servers;
  let changed = false;
  for (const it of items) {
    if (it.icon) continue;
    if (_deckKey(FEATURED_SERVER.address) === it.key) continue;
    const a = _deckNetworkLabel(it.key);
    if (a.length < 4 || /^\d+$/.test(a)) continue;
    const from = items.find(o => o !== it && o.icon && !o.borrowed && (() => {
      const b = _deckNetworkLabel(o.key);
      return b === a || (Math.min(a.length, b.length) >= 5 && (a.includes(b) || b.includes(a)));
    })());
    if (from) { it.icon = from.icon; it.tint = from.tint; it.borrowed = true; changed = true; }
  }
  return changed;
}

function _deckKey(address) {
  return String(address || '').trim().toLowerCase().replace(/:25565$/, '');
}

// "mc.hypixel.net" → "Hypixel" when the list gave no name.
function _deckNameFor(address) {
  const key = _deckKey(address);
  const known = [FEATURED_SERVER, ...SERVERS].find(s => _deckKey(s.address) === key);
  if (known) return known.name;
  const host = key.replace(/:\d+$/, '');
  if (/^[\d.]+$/.test(host)) return host;
  const parts = host.split('.');
  let label = parts.length >= 2 ? parts[parts.length - 2] : parts[0];
  // On hosting domains (name.mc.gg, name.aternos.me) the server is the first part.
  if (parts.length >= 3 && (label.length <= 2 || /^(minehut|modrinth|aternos|exaroton|falix|falixsrv|falixserver|ploudos|feathermc|progamer|joinmc)$/.test(label))) label = parts[0];
  return label.charAt(0).toUpperCase() + label.slice(1);
}

function _deckJoinTarget() {
  if (!document.getElementById('hq-deck') || _deck.mode !== 'servers') return null;
  return _deck.items.servers[_deck.index.servers] || null;
}

async function _deckInit() {
  const deck = document.getElementById('hq-deck');
  if (!deck) return;
  deck.addEventListener('wheel', _deckOnWheel, { passive: false });
  // Right-click beside the cards: bring hidden servers back, add one.
  deck.oncontextmenu = (e) => { if (_deck.mode === 'servers') { e.preventDefault(); _deckOpenMenu(e, null); } };
  // What we had last time shows at once; fresh data replaces it.
  _deckRender();
  await Promise.all([_deckLoadServers(), _deckLoadInstallations()]);
  _deckRender();
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), SettingsManager.getAll().showSessionTimer !== false);
  _deckRefreshStatus(false);
  if (!_deck.restored) { _deck.restored = true; _deckApplyServerInstallation(_deckJoinTarget()); }
}

async function _deckLoadServers() {
  let known = [];
  try { known = (await window.icey.getKnownServers()) || []; } catch (_) {}
  const custom = SettingsManager.get('customServers') || [];
  const prefs = _deckPrefs();
  const seen = new Set();
  const out = [];
  const add = (address, name, icon, extra) => {
    const key = _deckKey(address);
    if (!key || seen.has(key)) return;
    seen.add(key);
    const pref = prefs[key] || {};
    if (pref.hidden) return;
    let clean = String(name || '').replace(/§./g, '').trim();
    if (/^(a )?minecraft server$/i.test(clean)) clean = '';      // the game's default name says nothing
    const old = _deck.items.servers.find(s => s.key === key);
    out.push({
      key, address: key, name: pref.name || clean || _deckNameFor(key),
      pinnedAt: pref.pinnedAt || 0,
      icon: icon || (old && old.icon) || null,
      tint: old ? old.tint : null,
      customIndex: -1, ...extra,
    });
  };
  known.filter(s => s.lastPlayed > 0).forEach(s => add(s.address, s.name, s.icon));
  add(FEATURED_SERVER.address, FEATURED_SERVER.name, null);
  known.filter(s => !(s.lastPlayed > 0)).forEach(s => add(s.address, s.name, s.icon));
  custom.forEach((s, i) => add(s.address, s.name, null, { customIndex: i }));
  // A user-added server that was also played keeps its remove button.
  custom.forEach((s, i) => { const it = out.find(o => o.key === _deckKey(s.address)); if (it) it.customIndex = i; });
  for (const s of SERVERS) {
    if (out.length >= 7) break;
    if (!/bedrock/i.test(s.address)) add(s.address, s.name, null);      // Bedrock servers cannot be joined from here
  }
  // Pinned cards first, in the order they were pinned; the rest as they came.
  const pinned = out.filter(s => s.pinnedAt).sort((a, b) => a.pinnedAt - b.pinnedAt);
  out.splice(0, out.length, ...pinned, ...out.filter(s => !s.pinnedAt));
  _deck.items.servers = out;
  const at = _deck.focusKey ? out.findIndex(s => s.key === _deck.focusKey) : -1;
  _deck.index.servers = at >= 0 ? at : Math.min(_deck.index.servers, Math.max(0, out.length - 1));
}

async function _deckLoadInstallations() {
  let installations = [];
  try { installations = (await window.icey.getInstallations()) || []; } catch (_) {}
  _deck.items.installations = installations.map(inst => ({
    key: inst.id, id: inst.id, name: inst.name,
    sub: `${inst.platform === 'fabric' ? 'Fabric' : 'Vanilla'} ${inst.version}`,
    image: inst.image ? 'file://' + encodeURI(inst.image.replace(/\\/g, '/')) : 'assets/installbg-default.png',
  }));
  const sel = installations.findIndex(i => i.selected);
  _deck.index.installations = sel >= 0 ? sel : 0;
}

async function _deckReloadServers() {
  if (!document.getElementById('hq-deck')) return;
  await _deckLoadServers();
  if (_deck.mode === 'servers') _deckRender();
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), SettingsManager.getAll().showSessionTimer !== false);
  _deckRefreshStatus(false);
}

async function _deckSyncInstallations() {
  if (!document.getElementById('hq-deck')) return;
  await _deckLoadInstallations();
  if (_deck.mode === 'installations') _deckRender();
}

function _deckSetMode(mode) {
  mode = mode === 'installations' ? 'installations' : 'servers';
  if (_deck.mode === mode) return;
  _deck.mode = mode;
  SettingsManager.set('homeDeckMode', mode);
  _deckRender();
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), SettingsManager.getAll().showSessionTimer !== false);
}

function _deckAddClick() {
  if (_deck.mode === 'servers') _showAddServerModal();
  else switchPage('installations');
}

function _deckRender() {
  const track = document.getElementById('hq-track');
  const sw = document.getElementById('hq-switch');
  const addBtn = document.getElementById('hq-add');
  if (!track) return;
  if (sw) sw.dataset.mode = _deck.mode;
  if (addBtn) addBtn.title = _deck.mode === 'servers' ? 'Add Server' : 'Manage installations';
  const items = _deck.items[_deck.mode] || [];
  track.textContent = '';
  track.dataset.mode = _deck.mode;

  if (!items.length) {
    if (_deck.mode === 'servers') {
      const empty = document.createElement('div');
      empty.className = 'home-inst-empty hq-empty';
      empty.textContent = 'Add Server';
      empty.onclick = () => _showAddServerModal();
      track.appendChild(empty);
    }
    if (_deck.mode === 'installations') {
      const empty = document.createElement('div');
      empty.className = 'home-inst-empty hq-empty';
      empty.innerHTML = 'No installations &mdash; click to create one';
      empty.onclick = () => switchPage('installations');
      track.appendChild(empty);
    }
    _deckLayout();
    return;
  }

  items.forEach((it, i) => {
    const card = document.createElement('button');
    card.className = 'hq-card ' + (_deck.mode === 'servers' ? 'hq-card-server' : 'hq-card-inst');
    card.dataset.i = String(i);
    card.dataset.key = it.key;
    card.setAttribute('data-no-i18n', '');
    card.onclick = () => _deckGo(i);
    card.ondblclick = () => { if (i === _deck.index[_deck.mode]) HomePlayClick(); };

    const art = document.createElement('span');
    art.className = 'hq-card-art';
    card.appendChild(art);

    if (_deck.mode === 'servers') {
      card.oncontextmenu = (e) => { e.preventDefault(); e.stopPropagation(); _deckGo(i); _deckOpenMenu(e, it); };
      if (it.pinnedAt) {
        const pin = document.createElement('span');
        pin.className = 'hq-card-pin';
        pin.innerHTML = '<svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M15 3l6 6-3 1-3.5 3.5.5 4.5-2 2-4-5-5 5v-1.5l4.5-4.5-5-4 2-2 4.5.5L14 6z"/></svg>';
        card.appendChild(pin);
      }
      const icon = document.createElement('img');
      icon.className = 'hq-card-icon';
      icon.alt = '';
      icon.draggable = false;
      icon.onerror = () => { if (!icon.src.endsWith(DECK_DEFAULT_ICON)) icon.src = DECK_DEFAULT_ICON; };
      card.appendChild(icon);
      const meta = document.createElement('span');
      meta.className = 'hq-card-meta';
      card.appendChild(meta);
      if (it.customIndex >= 0) {
        const rm = document.createElement('span');
        rm.className = 'hq-card-remove';
        rm.title = 'Remove';
        rm.textContent = '×';
        rm.onclick = (e) => { e.stopPropagation(); _removeCustomServer(it.customIndex); };
        card.appendChild(rm);
      }
    } else {
      // The installation's picture, the one set on the Installations page.
      art.style.backgroundImage = `url("${it.image}")`;
    }

    const ribbon = document.createElement('span');
    ribbon.className = 'hq-card-ribbon';
    const name = document.createElement('span');
    name.className = 'hq-card-name';
    name.textContent = it.name;
    const sub = document.createElement('span');
    sub.className = 'hq-card-sub';
    sub.textContent = _deck.mode === 'servers' ? it.address : it.sub;
    ribbon.append(name, sub);
    card.appendChild(ribbon);

    track.appendChild(card);
    if (_deck.mode === 'servers') _deckPaintServer(card, it);
  });
  _deckLayout();
}

// Icon, colour and player count of one server card, from what is known so
// far. The card takes its colour from the server's icon.
function _deckPaintServer(card, it) {
  // Its own icon (from the multiplayer list or the status lookup), else a grass block.
  // Icey's own server wears the Icey mark while it has none to give.
  const own = !it.icon && it.key === _deckKey(FEATURED_SERVER.address);
  const src = it.icon || (own ? 'assets/icon.png' : DECK_DEFAULT_ICON);
  const icon = card.querySelector('.hq-card-icon');
  if (icon && icon.getAttribute('src') !== src) {
    icon.style.visibility = '';
    icon.style.imageRendering = own ? 'auto' : '';      // a big picture scaled down, not pixel art
    icon.src = src;
  }
  const art = card.querySelector('.hq-card-art');
  if (art) art.style.backgroundImage = `url("${src}")`;
  if (it.tint) card.style.setProperty('--tint', it.tint);
  else if (it.icon || own) card.style.removeProperty('--tint');
  else card.style.setProperty('--tint', DECK_DEFAULT_TINT);
  const meta = card.querySelector('.hq-card-meta');
  const st = _deck.status.get(it.key);
  if (meta) {
    if (!st) meta.innerHTML = '';
    else if (st.data && st.data.online) {
      meta.innerHTML = `<i class="hq-dot on"></i><span>${Number(st.data.players?.online ?? 0).toLocaleString()}</span>`;
    } else {
      meta.innerHTML = '<i class="hq-dot"></i><span>Offline</span>';
    }
    meta.classList.toggle('shown', !!st);
  }
}

function _deckLayout() {
  const track = document.getElementById('hq-track');
  if (!track) return;
  const cards = track.querySelectorAll('.hq-card');
  const active = _deck.index[_deck.mode] || 0;
  const cw = cards.length ? cards[0].offsetWidth : 150;
  const n = cards.length;
  // With enough cards the row is a ring: there is always something on
  // both sides, and the cards that cross over do it out of sight.
  const ring = _deckIsRing(n);
  const reach = ring ? Math.min(4, Math.ceil(n / 2) - 2) : 4;
  cards.forEach((card) => {
    let o = Number(card.dataset.i) - active;
    if (ring) { o = ((o % n) + n) % n; if (o > n / 2) o -= n; }
    const a = Math.abs(o);
    // Each step out is a little shorter than the last, so the row fans.
    const x = Math.sign(o) * cw * (0.80 * a - 0.062 * a * a);
    const scale = Math.max(0.5, 1 - 0.14 * a + 0.012 * a * a);
    const hidden = a > reach;
    card.style.transform = `translateX(${x.toFixed(1)}px) scale(${scale.toFixed(3)})`;
    card.style.zIndex = String(50 - a);
    card.style.opacity = hidden ? '0' : '1';
    card.style.setProperty('--dim', String(Math.max(0.42, 0.78 - 0.1 * Math.max(0, a - 1))));
    card.style.pointerEvents = hidden ? 'none' : '';
    card.classList.toggle('active', o === 0);
    card.classList.toggle('right', o > 0);       // its right half shows, so the label sits there
    card.tabIndex = o === 0 ? 0 : -1;
  });
  const deck = document.getElementById('hq-deck');
  if (deck) {
    deck.classList.toggle('at-start', !ring && active <= 0);
    deck.classList.toggle('at-end', !ring && active >= n - 1);
  }
}

function _deckIsRing(n) { return n >= 7; }

function _deckStep(d) { _deckGo((_deck.index[_deck.mode] || 0) + d); }

function _deckGo(i) {
  const items = _deck.items[_deck.mode] || [];
  if (!items.length) return;
  const n = items.length;
  i = _deckIsRing(n) ? ((i % n) + n) % n : Math.max(0, Math.min(n - 1, i));
  if (i === _deck.index[_deck.mode]) return;
  _deck.index[_deck.mode] = i;
  _deckLayout();
  if (_deck.mode === 'servers') {
    _deck.focusKey = items[i].key;
    _homeUpdateLaunchButton(MinecraftLauncher.getState(), SettingsManager.getAll().showSessionTimer !== false);
    // The installation this server was last joined with comes along,
    // once the deck rests on the card.
    clearTimeout(_deck.saveTimer);
    const item = items[i];
    _deck.saveTimer = setTimeout(() => _deckApplyServerInstallation(item), 320);
  } else {
    // The front card is the selected installation; save once the deck rests.
    clearTimeout(_deck.saveTimer);
    const id = items[i].id;
    _deck.saveTimer = setTimeout(() => _deckSelectInstallation(id), 260);
  }
}

// Each server remembers the installation it was last joined with (set in
// HomePlayClick). Bringing its card to the front selects that installation
// again, so a 1.21 network and a snapshot server each start the right game.
// Only a change of card does this: picking another installation by hand and
// pressing Join is how the memory is changed.
async function _deckApplyServerInstallation(item) {
  if (!item || _deck.mode !== 'servers') return;
  const want = (_deckPrefs()[item.key] || {}).installationId;
  if (!want) return;
  let installations = [];
  try { installations = (await window.icey.getInstallations()) || []; } catch (_) {}
  const target = installations.find(i => i.id === want);
  if (!target || target.selected) return;
  await _deckSelectInstallation(want);
  await _deckLoadInstallations();
}

async function _deckSelectInstallation(id) {
  try {
    const installations = await window.icey.getInstallations();
    for (const inst of installations) {
      const want = inst.id === id;
      if (!!inst.selected !== want) { inst.selected = want; await window.icey.saveInstallation(inst); }
    }
  } catch (_) {}
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), SettingsManager.getAll().showSessionTimer !== false);
}

function _deckOnWheel(e) {
  e.preventDefault();
  const d = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
  const now = Date.now();
  if (now - _deck.wheelAt > 260) _deck.wheelAcc = 0;
  _deck.wheelAt = now;
  _deck.wheelAcc += d;
  if (Math.abs(_deck.wheelAcc) >= 48) {
    _deckStep(_deck.wheelAcc > 0 ? 1 : -1);
    _deck.wheelAcc = 0;
  }
}

// ── Right-click on a server card: pin, move, rename, hide ──
function _deckCloseMenu() {
  const m = document.getElementById('hq-menu');
  if (m) m.remove();
}

function _deckOpenMenu(e, it) {
  _deckCloseMenu();
  const prefs = _deckPrefs();
  const hiddenCount = Object.values(prefs).filter(p => p && p.hidden).length;
  const pinned = _deck.items.servers.filter(s => s.pinnedAt);
  const at = it ? pinned.findIndex(s => s.key === it.key) : -1;
  const menu = document.createElement('div');
  menu.id = 'hq-menu';
  menu.className = 'hq-menu';
  const item = (label, icon, fn, cls) => {
    const b = document.createElement('button');
    b.className = 'hq-menu-item' + (cls ? ' ' + cls : '');
    b.innerHTML = `<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${icon}</svg><span></span>`;
    b.lastChild.textContent = label;
    b.onclick = () => { _deckCloseMenu(); fn(); };
    menu.appendChild(b);
  };
  const line = () => { if (menu.lastChild && !menu.lastChild.classList.contains('hq-menu-line')) menu.appendChild(Object.assign(document.createElement('div'), { className: 'hq-menu-line' })); };
  if (it) {
    const head = document.createElement('div');
    head.className = 'hq-menu-head';
    head.setAttribute('data-no-i18n', '');
    head.textContent = it.name;
    menu.appendChild(head);
    if (at < 0) item('Pin to front', '<path d="M12 17v5"/><path d="M8 3h8l-1 6 3 4H6l3-4z"/>', () => _deckPin(it, true));
    else {
      item('Unpin', '<path d="M12 17v5"/><path d="M8 3h8l-1 6 3 4H6l3-4z"/><line x1="3" y1="3" x2="21" y2="21"/>', () => _deckPin(it, false));
      if (at > 0) item('Move left', '<polyline points="15 18 9 12 15 6"/>', () => _deckMovePin(it, -1));
      if (at < pinned.length - 1) item('Move right', '<polyline points="9 18 15 12 9 6"/>', () => _deckMovePin(it, 1));
    }
    item('Rename', '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>', () => _deckRename(it));
    item('Hide', '<path d="M17.9 17.9A10.4 10.4 0 0 1 12 20C5 20 1 12 1 12a18.6 18.6 0 0 1 5.1-5.9"/><path d="M9.9 4.2A9.6 9.6 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.2 3.2"/><line x1="1" y1="1" x2="23" y2="23"/>', () => _deckHide(it), 'danger');
  }
  if (hiddenCount) {
    line();
    item(`Show hidden servers (${hiddenCount})`, '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>', () => _deckUnhideAll());
  }
  if (!it) item('Add Server', '<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>', () => _showAddServerModal());
  document.body.appendChild(menu);
  // Cards sit at the bottom of the window, so the menu opens upwards when it has to.
  const w = menu.offsetWidth, h = menu.offsetHeight;
  menu.style.left = Math.max(8, Math.min(e.clientX, window.innerWidth - w - 8)) + 'px';
  menu.style.top = Math.max(8, Math.min(e.clientY, window.innerHeight - h - 8)) + 'px';
}

document.addEventListener('mousedown', (e) => {
  const m = document.getElementById('hq-menu');
  if (m && !m.contains(e.target)) _deckCloseMenu();
});
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') _deckCloseMenu(); });
window.addEventListener('blur', _deckCloseMenu);

async function _deckPin(it, on) {
  await _deckSetPref(it.key, { pinnedAt: on ? Date.now() : null });
  _deck.focusKey = it.key;
  _deckReloadServers();
}

// Pinned cards keep their order by pin time; moving one swaps times with its neighbour.
async function _deckMovePin(it, dir) {
  const pinned = _deck.items.servers.filter(s => s.pinnedAt);
  const at = pinned.findIndex(s => s.key === it.key);
  const other = pinned[at + dir];
  if (at < 0 || !other) return;
  const a = it.pinnedAt, b = other.pinnedAt;
  await _deckSetPref(it.key, { pinnedAt: b });
  await _deckSetPref(other.key, { pinnedAt: a });
  _deck.focusKey = it.key;
  _deckReloadServers();
}

async function _deckHide(it) {
  await _deckSetPref(it.key, { hidden: true, pinnedAt: null });
  _deck.focusKey = null;
  _deckReloadServers();
}

async function _deckUnhideAll() {
  const prefs = _deckPrefs();
  for (const key of Object.keys(prefs)) if (prefs[key] && prefs[key].hidden) await _deckSetPref(key, { hidden: null });
  _deckReloadServers();
}

function _deckRename(it) {
  showModal(`
    <div class="modal-header">
      <h2 class="modal-title">Rename Server</h2>
      <button class="modal-close" onclick="closeModal()">
        <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label class="form-label">Server Name</label>
        <input class="form-input" type="text" id="deck-rename-input" maxlength="40" spellcheck="false">
      </div>
    </div>
    <div class="modal-footer">
      <button class="modal-btn modal-btn-outline" onclick="closeModal()">Cancel</button>
      <button class="modal-btn modal-btn-primary" id="deck-rename-save">Save</button>
    </div>
  `);
  const input = document.getElementById('deck-rename-input');
  const save = async () => {
    // An empty name gives the card back its own name.
    await _deckSetPref(it.key, { name: input.value.trim() || null });
    closeModal();
    _deck.focusKey = it.key;
    _deckReloadServers();
  };
  input.value = it.name;
  input.onkeydown = (e) => { if (e.key === 'Enter') save(); };
  document.getElementById('deck-rename-save').onclick = save;
  setTimeout(() => { input.focus(); input.select(); }, 100);
}

document.addEventListener('keydown', (e) => {
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
  const home = document.getElementById('page-home');
  if (!home || !home.classList.contains('active') || !document.getElementById('hq-deck')) return;
  const overlay = document.getElementById('modal-overlay');
  if (overlay && !overlay.classList.contains('hidden')) return;
  const t = e.target;
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return;
  e.preventDefault();
  _deckStep(e.key === 'ArrowRight' ? 1 : -1);
});

window.addEventListener('resize', () => { if (document.getElementById('hq-track')) _deckLayout(); });

// Player counts and icons. force = also refresh what is already known.
async function _deckRefreshStatus(force) {
  const home = document.getElementById('page-home');
  if (!document.getElementById('hq-deck') || (force && home && !home.classList.contains('active'))) return;
  const items = _deck.items.servers.slice();
  const paint = (it) => {
    const card = document.querySelector(`#hq-track[data-mode="servers"] .hq-card[data-key="${CSS.escape(it.key)}"]`);
    if (card) _deckPaintServer(card, it);
  };
  // The colour needs no network when the icon came from the multiplayer list.
  items.forEach(async (it) => {
    if (it.icon && !it.tint) { it.tint = await _deckTint(it.icon); if (it.tint) paint(it); }
  });
  await Promise.all(items.map(async (it) => {
    const have = _deck.status.get(it.key);
    if (have && !force && Date.now() - have.at < 120000) return;
    // A failed lookup (no internet, rate limit) says nothing about the
    // server, so the card keeps what it showed instead of "Offline".
    // First the launcher asks the server itself, the way the game's server
    // list does; the status website is only the fallback.
    let data = null;
    try {
      const p = await window.icey.pingServer(it.address);
      if (p) {
        if (p.icon && !it.icon) it.icon = p.icon;
        if (p.online) data = { online: true, players: { online: p.players, max: p.max } };
      }
    } catch (_) {}
    if (!data) {
      try {
        const r = await fetch(`https://api.mcsrvstat.us/3/${encodeURIComponent(it.address)}`);
        if (r.ok) data = await r.json();
      } catch (_) {}
    }
    if (it.icon && !it.tint) it.tint = await _deckTint(it.icon);
    if (!data || typeof data.online !== 'boolean') { paint(it); return; }
    _deck.status.set(it.key, { at: Date.now(), data });
    if (!it.icon && data && typeof data.icon === 'string' && data.icon.startsWith('data:image/')) it.icon = data.icon;
    if (it.icon && !it.tint) it.tint = await _deckTint(it.icon);
    paint(it);
  }));
  if (_deckBorrowIcons()) items.forEach(paint);
}

// The card's colour comes from its server icon: the average of the
// icon's colourful pixels, lifted so it reads on dark glass.
function _deckTint(dataUrl) {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      try {
        const c = document.createElement('canvas');
        c.width = c.height = 16;
        const g = c.getContext('2d');
        g.drawImage(img, 0, 0, 16, 16);
        const px = g.getImageData(0, 0, 16, 16).data;
        let r = 0, gg = 0, b = 0, w = 0;
        for (let i = 0; i < px.length; i += 4) {
          if (px[i + 3] < 128) continue;
          const mx = Math.max(px[i], px[i + 1], px[i + 2]), mn = Math.min(px[i], px[i + 1], px[i + 2]);
          const sat = mx ? (mx - mn) / mx : 0;
          const k = sat * sat * (mx / 255) + 0.002;
          r += px[i] * k; gg += px[i + 1] * k; b += px[i + 2] * k; w += k;
        }
        if (!w) return resolve(null);
        r /= w; gg /= w; b /= w;
        const mx = Math.max(r, gg, b), mn = Math.min(r, gg, b);
        if (mx - mn < 28) return resolve(null);          // a grey icon keeps the accent
        const lift = 235 / mx;
        resolve([r, gg, b].map(v => Math.round(Math.min(255, v * lift))).join(','));
      } catch (_) { resolve(null); }
    };
    img.onerror = () => resolve(null);
    img.src = dataUrl;
  });
}

// ── Frost home: the animated skin ──
let _hqViewer = null;
let _hqSkinObserver = null;
let _hqSkinAnim = '';
let _hqWaveTimer = null;

function _hqDisposeSkin() {
  clearTimeout(_hqWaveTimer);
  if (_hqSkinObserver) { _hqSkinObserver.disconnect(); _hqSkinObserver = null; }
  if (_hqViewer) { try { CharacterRig.clear(_hqViewer); _hqViewer.dispose(); } catch (_) {} _hqViewer = null; }
  _hqSkinAnim = '';
}

function _hqSkinShowImage() {
  const img = document.getElementById('hq-skin-img');
  if (img && !img.getAttribute('src')) { img.src = img.dataset.src; img.classList.add('shown'); }
}

async function _hqInitSkin(username) {
  _hqDisposeSkin();
  const canvas = document.getElementById('hq-skin-canvas');
  const wrap = document.getElementById('hq-skin');
  if (!canvas || !wrap) return;
  if (!window.skinview3d) { _hqSkinShowImage(); return; }
  let tex = null;
  try { tex = await window.icey.getSkinTexture(username || 'MHF_Steve'); } catch (_) {}
  if (!canvas.isConnected) return;                      // the page was rebuilt meanwhile
  if (!tex || tex.error || !tex.dataUrl) { _hqSkinShowImage(); return; }
  try {
    const viewer = new skinview3d.SkinViewer({
      canvas,
      width: Math.max(1, wrap.clientWidth),
      height: Math.max(1, wrap.clientHeight),
    });
    await viewer.loadSkin(tex.dataUrl, { model: tex.slim ? 'slim' : 'default' });
    if (!canvas.isConnected) { viewer.dispose(); return; }
    viewer.fov = 38;
    viewer.zoom = 0.9;                 // head to feet inside the space between the name and the button
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    viewer.playerWrapper.rotation.y = -0.42;
    viewer.globalLight.intensity = 2.6;
    viewer.cameraLight.intensity = 1.1;
    _hqViewer = viewer;
    // Armor and whatever he holds, as chosen on the Character page.
    await CharacterRig.apply(viewer, CharacterRig.get());
    if (!canvas.isConnected) { _hqDisposeSkin(); return; }
    canvas.classList.add('shown');
    // Wave hello, then settle into the state the launcher is in.
    viewer.animation = new skinview3d.WaveAnimation();
    _hqSkinAnim = 'wave';
    _hqWaveTimer = setTimeout(() => { _hqSkinAnim = ''; _hqSkinState(MinecraftLauncher.getState()); }, 2600);
    _hqSkinObserver = new ResizeObserver(() => {
      if (!_hqViewer) return;
      const w = wrap.clientWidth, h = wrap.clientHeight;
      _hqViewer.renderPaused = !(w > 0 && h > 0);       // another page is showing
      if (w > 0 && h > 0) _hqViewer.setSize(w, h);
    });
    _hqSkinObserver.observe(wrap);
  } catch (e) {
    console.warn('[home] 3D skin failed:', e);
    _hqDisposeSkin();
    _hqSkinShowImage();
  }
}

// Pressing play: a ring of light and a spray of snow break out of the
// button. The rest of the take-off (the scene rushing closer, the cards
// dropping away, the skin breaking into a run) follows the launcher's
// state through the .launching class, see _hqSkinState.
function _hqLaunchBurst() {
  const row = document.querySelector('.hq-launch-row');
  if (!row) return;
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const burst = document.createElement('div');
  burst.className = 'hq-burst';
  let html = '<i class="hq-burst-ring"></i><i class="hq-burst-ring late"></i><i class="hq-burst-sweep"></i>';
  for (let i = 0; i < 26; i++) {
    const ang = (i / 26) * Math.PI * 2 + (Math.random() - 0.5) * 0.4;
    const dist = 150 + Math.random() * 210;
    const size = 4 + Math.floor(Math.random() * 3) * 2;
    html += `<i class="hq-burst-flake" style="--dx:${(Math.cos(ang) * dist * 1.5).toFixed(0)}px;--dy:${(Math.sin(ang) * dist * 0.75 - 50).toFixed(0)}px;--s:${size}px;--d:${(Math.random() * 0.12).toFixed(2)}s"></i>`;
  }
  burst.innerHTML = html;
  row.appendChild(burst);
  setTimeout(() => burst.remove(), 1500);
}

// Idle while waiting, running while the game starts, a slow walk while it runs.
function _hqSkinState(state) {
  const root = document.querySelector('.home-glass.hq');
  if (root) root.classList.toggle('launching', state === 'starting');
  if (!_hqViewer || _hqSkinAnim === 'wave' || _hqSkinAnim === state) return;
  _hqSkinAnim = state;
  if (state === 'starting') {
    _hqViewer.animation = CharacterRig.animation(CharacterRig.get().startAnim);     // the player's pick; running by default
  } else if (state === 'running') {
    const a = new skinview3d.WalkingAnimation(); a.speed = 0.55; _hqViewer.animation = a;
  } else {
    _hqViewer.animation = new skinview3d.IdleAnimation();
  }
}

// ── Frost home: snow drifting across the wallpaper ──
let _hqSnowRun = 0;
function _hqInitSnow() {
  const canvas = document.getElementById('hq-snow');
  if (!canvas) return;
  const run = ++_hqSnowRun;
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  // Skiflame: embers in the logo's colours rise instead of snow falling.
  const ember = !!(SettingsManager.isSkiflame && SettingsManager.isSkiflame());
  const EMBERS = ['#5ec8ff', '#7d7bff', '#b56bff', '#ffb066'];
  const g = canvas.getContext('2d');
  const flakes = [];
  const spawn = (anyY) => ({
    x: Math.random(), y: anyY ? Math.random() : (ember ? 1.03 : -0.03),
    s: 2 + Math.floor(Math.random() * 3) * 1.5,
    v: (0.018 + Math.random() * 0.03) * (ember ? -1.6 : 1),
    d: (Math.random() - 0.5) * 0.012,
    p: Math.random() * Math.PI * 2,
    o: 0.25 + Math.random() * 0.5,
    c: ember ? EMBERS[Math.floor(Math.random() * EMBERS.length)] : '#fff',
  });
  for (let i = 0; i < 46; i++) flakes.push(spawn(true));
  let last = performance.now();
  const frame = (now) => {
    if (run !== _hqSnowRun || !canvas.isConnected) return;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) { last = now; setTimeout(() => requestAnimationFrame(frame), 400); return; }
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    g.clearRect(0, 0, w, h);
    g.fillStyle = '#fff';
    for (let i = 0; i < flakes.length; i++) {
      const f = flakes[i];
      f.y += f.v * dt; f.p += dt * 0.8;
      f.x += (f.d + Math.sin(f.p) * 0.006) * dt;
      if (f.y > 1.04 || f.y < -0.04) flakes[i] = spawn(false);
      g.globalAlpha = ember ? f.o * Math.min(1, Math.max(0, f.y * 1.4)) : f.o;   // embers burn out on the way up
      g.fillStyle = f.c;
      g.fillRect(Math.round(f.x * w), Math.round(f.y * h), f.s, f.s);   // square flakes, like the game's
    }
    g.globalAlpha = 1;
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
}

// The cards follow what changes outside the page: a server added or
// deleted in the game's multiplayer list, an installation made or removed.
// Looked at again whenever the launcher window gets the focus back and
// when the game closes.
let _deckSyncAt = 0;
function _deckSyncOutside() {
  const home = document.getElementById('page-home');
  if (!document.getElementById('hq-deck') || !home || !home.classList.contains('active')) return;
  if (Date.now() - _deckSyncAt < 1500) return;
  _deckSyncAt = Date.now();
  _deckReloadServers();
  _deckSyncInstallations();
}
window.addEventListener('focus', _deckSyncOutside);
if (typeof MinecraftLauncher !== 'undefined') MinecraftLauncher.onChange((state) => { if (state === 'idle') setTimeout(_deckSyncOutside, 800); });
