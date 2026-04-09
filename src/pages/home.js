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
              <span class="launch-btn-title" id="launch-btn-title">LAUNCH</span>
              <span class="launch-btn-subtitle" id="launch-btn-subtitle"><span class="launch-btn-dot"></span> READY TO LAUNCH</span>
            </button>
          </div>
          <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
            <span class="home-timer-label">Playtime</span>
            <span class="home-timer-value" id="home-timer-value">00:00:00</span>
          </div>
        </div>

        <!-- Installations cards at bottom -->
        <div class="home-installations-section">
          <div class="home-inst-header">
            <span class="home-inst-title">Your Installations</span>
          </div>
          <div class="home-inst-cards" id="home-inst-cards"></div>
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
          <div class="home-custom-servers" id="custom-server-list"></div>
          <button class="home-add-server-btn" onclick="_showAddServerModal()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Add Server
          </button>
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
        ? `file://${inst.image.replace(/\\\\/g, '/')}`
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
}

async function _removeCustomServer(index) {
  const customServers = SettingsManager.get('customServers') || [];
  customServers.splice(index, 1);
  await SettingsManager.set('customServers', customServers);
  Toast.info('Server removed');
  _loadCustomServers();
}

function _initLaunchBarSnow() {
  const c = document.getElementById('launch-bar-snow');
  if (!c) return;
  const mk = () => { const f = document.createElement('div'); f.className = 'snowflake'; const s = Math.random()*3+1.5; f.style.cssText = `width:${s}px;height:${s}px;left:${Math.random()*100}%;animation-duration:${Math.random()*2+2}s;animation-delay:${Math.random()*2}s;opacity:${Math.random()*0.5+0.3}`; c.appendChild(f); setTimeout(() => f.remove(), 5000); };
  for (let i = 0; i < 6; i++) mk();
  setInterval(() => { if (document.getElementById('launch-bar-snow')) mk(); }, 500);
}

async function _homeGetSelectedVersion() {
  try {
    const installations = await window.icey.getInstallations();
    const selected = installations.find(i => i.selected);
    return selected ? selected.version : '';
  } catch (_) { return ''; }
}

async function _homeUpdateLaunchButton(state, showTimer) {
  const btn = document.getElementById('launch-btn'), timer = document.getElementById('home-timer');
  if (!btn) return;
  btn.className = 'launch-btn';
  const version = await _homeGetSelectedVersion();
  const versionStr = version ? ` ${version}` : '';
  if (state === 'idle') {
    btn.classList.add('launch-btn-idle'); btn.disabled = false;
    btn.innerHTML = `<span class="launch-btn-title">LAUNCH${versionStr}</span><span class="launch-btn-subtitle"><span class="launch-btn-dot"></span> READY TO LAUNCH</span>`;
    if (timer) timer.classList.remove('visible');
  } else if (state === 'starting') {
    btn.classList.add('launch-btn-starting'); btn.disabled = true;
    btn.innerHTML = `<span class="launch-btn-title starting-dots">Starting</span><span class="launch-btn-subtitle">PLEASE WAIT</span>`;
    if (timer) timer.classList.remove('visible');
  } else if (state === 'running') {
    btn.classList.add('launch-btn-running'); btn.disabled = false;
    btn.innerHTML = `<span class="launch-btn-title">STOP</span><span class="launch-btn-subtitle">GAME IS RUNNING</span>`;
    if (timer && showTimer) timer.classList.add('visible');
  }
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
