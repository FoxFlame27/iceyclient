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

  page.innerHTML = `
    <div class="home-layout">
      <!-- Main area: logo + button -->
      <div class="home-main">
        <div class="home-hero">
          <img class="home-hero-logo" src="assets/text-above-playbutton.png" alt="Icey Client" onerror="this.style.display='none'">
          <div class="home-launch-bar">
            <div class="launch-bar-snow" id="launch-bar-snow"></div>
            <button class="launch-btn launch-btn-idle" id="launch-btn" onclick="HomePlayClick()">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
              <span id="launch-btn-text">LAUNCH</span>
            </button>
          </div>
          <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
            <span class="home-timer-label">Playtime</span>
            <span class="home-timer-value" id="home-timer-value">00:00:00</span>
          </div>
          <div class="home-selected-badge" id="home-selected-badge"></div>
        </div>

        <!-- Installations switcher at bottom -->
        <div class="home-installations-bar" id="home-installations-bar">
          <div class="home-instbar-title">Installations</div>
          <div class="home-instbar-list" id="home-instbar-list"></div>
        </div>
      </div>

      <!-- Right: Servers list -->
      <div class="home-servers-col">
        <div class="home-servers-title">Servers</div>
        <div class="home-server-list" id="server-list">
          <div class="home-server-bar featured" id="featured-server">
            <div class="server-loading">Loading Icey SMP...</div>
          </div>
          ${SERVERS.map((s, i) => `<div class="home-server-bar" id="server-bar-${i}"><div class="server-loading">${s.name}</div></div>`).join('')}
        </div>
      </div>
    </div>
  `;

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
  _loadAllServers();
  if (_serverRefreshInterval) clearInterval(_serverRefreshInterval);
  _serverRefreshInterval = setInterval(_loadAllServers, 120000);

  } catch (err) { console.error('[HomePageInit] ERROR:', err); }
}

async function _loadHomeInstallations() {
  const list = document.getElementById('home-instbar-list');
  const badge = document.getElementById('home-selected-badge');
  if (!list) return;
  try {
    const installations = await window.icey.getInstallations();
    const selected = installations.find(i => i.selected);
    if (badge && selected) {
      badge.innerHTML = `<span class="badge-dot"></span> ${selected.name} <span class="badge-ver">${selected.version}</span>`;
      badge.style.display = 'flex';
    }
    if (installations.length === 0) {
      list.innerHTML = `<div class="home-instbar-empty" onclick="switchPage('installations')">No installations &mdash; click to create one</div>`;
      return;
    }
    list.innerHTML = installations.map(inst => {
      const isSelected = inst.selected;
      const platformIcon = inst.platform === 'fabric'
        ? '<img class="home-instbar-fabric" src="assets/fabric.png" alt="F">'
        : '';
      return `
        <div class="home-instbar-item ${isSelected ? 'selected' : ''}" onclick="_homeSelectInstallation('${inst.id}')">
          ${platformIcon}
          <span class="home-instbar-name">${inst.name}</span>
          <span class="home-instbar-ver">${inst.version}</span>
        </div>
      `;
    }).join('');
  } catch (e) {
    list.innerHTML = '<div class="home-instbar-empty">Failed to load</div>';
  }
}

async function _homeSelectInstallation(id) {
  const installations = await window.icey.getInstallations();
  installations.forEach(i => i.selected = (i.id === id));
  for (const inst of installations) {
    await window.icey.saveInstallation(inst);
  }
  _loadHomeInstallations();
  Toast.success('Installation selected');
}

async function _fetchServerStatus(address) {
  try { const r = await fetch(`https://api.mcsrvstat.us/3/${address}`, { headers: { 'User-Agent': 'IceyClient/1.0' } }); return await r.json(); }
  catch (_) { return { online: false }; }
}

function _renderFeaturedServer(data, server) {
  const el = document.getElementById('featured-server');
  if (!el) return;
  if (!data.online) { el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-status offline">Offline</div>`; return; }
  el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-players"><span class="server-bar-online">${(data.players?.online ?? 0).toLocaleString()}</span><span class="server-bar-max">/ ${(data.players?.max ?? 0).toLocaleString()}</span></div>`;
}

function _renderServerBar(data, server, i) {
  const el = document.getElementById(`server-bar-${i}`);
  if (!el) return;
  if (!data.online) { el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-status offline">Offline</div>`; return; }
  el.innerHTML = `<div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt=""></div><div class="server-bar-name">${server.name}</div><div class="server-bar-address">${server.address}</div><div class="server-bar-players"><span class="server-bar-online">${(data.players?.online ?? 0).toLocaleString()}</span><span class="server-bar-max">/ ${(data.players?.max ?? 0).toLocaleString()}</span></div>`;
}

async function _loadAllServers() {
  const [fd, ...sd] = await Promise.all([_fetchServerStatus(FEATURED_SERVER.address), ...SERVERS.map(s => _fetchServerStatus(s.address))]);
  _renderFeaturedServer(fd, FEATURED_SERVER);
  sd.forEach((d, i) => _renderServerBar(d, SERVERS[i], i));
}

function _initLaunchBarSnow() {
  const c = document.getElementById('launch-bar-snow');
  if (!c) return;
  const mk = () => { const f = document.createElement('div'); f.className = 'snowflake'; const s = Math.random()*3+1.5; f.style.cssText = `width:${s}px;height:${s}px;left:${Math.random()*100}%;animation-duration:${Math.random()*2+2}s;animation-delay:${Math.random()*2}s;opacity:${Math.random()*0.5+0.3}`; c.appendChild(f); setTimeout(() => f.remove(), 5000); };
  for (let i = 0; i < 6; i++) mk();
  setInterval(() => { if (document.getElementById('launch-bar-snow')) mk(); }, 500);
}

function _homeUpdateLaunchButton(state, showTimer) {
  const btn = document.getElementById('launch-btn'), timer = document.getElementById('home-timer');
  if (!btn) return;
  btn.className = 'launch-btn';
  if (state === 'idle') { btn.classList.add('launch-btn-idle'); btn.disabled = false; btn.innerHTML = `<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg><span id="launch-btn-text">LAUNCH</span>`; if (timer) timer.classList.remove('visible'); }
  else if (state === 'starting') { btn.classList.add('launch-btn-starting'); btn.disabled = true; btn.innerHTML = `<div class="loading-dots"><span></span><span></span><span></span></div><span id="launch-btn-text">STARTING...</span>`; if (timer) timer.classList.remove('visible'); }
  else if (state === 'running') { btn.classList.add('launch-btn-running'); btn.disabled = false; btn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg><span id="launch-btn-text">STOP</span>`; if (timer && showTimer) timer.classList.add('visible'); }
}

async function HomePlayClick() {
  const state = MinecraftLauncher.getState();
  if (state === 'running') { MinecraftLauncher.stop(); return; }
  if (state === 'starting') return;
  const installations = await window.icey.getInstallations();
  const selected = installations.find(i => i.selected);
  if (!selected) { Toast.info('Select an installation first'); return; }
  await MinecraftLauncher.launch(selected.id);
}
