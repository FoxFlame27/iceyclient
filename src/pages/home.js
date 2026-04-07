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
];

async function HomePageInit() {
  const page = document.getElementById('page-home');
  try {
  const installations = await window.icey.getInstallations();
  const settings = SettingsManager.getAll();
  const selected = installations.find(i => i.selected);
  const showTimer = settings.showSessionTimer !== false;
  const opacity = settings.homeBackgroundOpacity ?? 80;

  // Get mods for selected installation
  let mods = [];
  let modCount = 0;
  if (selected) {
    try {
      const modData = await window.icey.getInstalledMods(selected.id);
      mods = modData.mods || [];
      modCount = mods.length;
    } catch (_) {}
  }

  const maxModsShown = 4;
  const modsToShow = mods.slice(0, maxModsShown);
  const modsRemaining = modCount - maxModsShown;

  page.innerHTML = `
    <div class="home-bg" style="background-image: url('assets/background.png');"></div>
    <div class="home-overlay" style="opacity: ${opacity / 100};"></div>
    <div class="home-content-new">
      <!-- Left column -->
      <div class="home-left-col">
        <!-- Big Icey Client logo -->
        <img class="home-hero-logo" src="assets/text-above-playbutton.png" alt="Icey Client" onerror="this.style.display='none'">

        <!-- Thin launch bar with snow -->
        <div class="home-launch-bar">
          <div class="launch-bar-snow" id="launch-bar-snow"></div>
          <button class="launch-btn launch-btn-idle" id="launch-btn" onclick="HomePlayClick()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
            <span id="launch-btn-text">LAUNCH</span>
          </button>
          <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
            <span class="home-timer-label">Playtime</span>
            <span class="home-timer-value" id="home-timer-value">00:00:00</span>
          </div>
        </div>

        <!-- Server status section -->
        <div class="home-servers-section">
          <div class="home-servers-title">Popular Servers</div>
          <div class="home-featured-server" id="featured-server">
            <div class="server-loading">Loading...</div>
          </div>
          <div class="home-server-list" id="server-list">
            ${SERVERS.map((s, i) => `
              <div class="home-server-bar" id="server-bar-${i}">
                <div class="server-loading">Loading...</div>
              </div>
            `).join('')}
          </div>
        </div>
      </div>

      <!-- Right sidebar -->
      <div class="home-right-box">
        ${selected ? `
          <div class="home-right-inner">
            <div class="home-right-header">Selected Installation</div>
            <div class="home-install-details">
              <div class="home-install-name">${selected.name}</div>
              <div class="home-install-row">
                <span class="home-install-label">Version</span>
                <span class="home-install-value">${selected.version}</span>
              </div>
              <div class="home-install-row">
                <span class="home-install-label">Platform</span>
                <span class="home-install-value ${selected.platform === 'fabric' ? 'fabric' : ''}">
                  ${selected.platform === 'fabric' ? '<img src="assets/fabric.png" alt=""> Fabric' : 'Vanilla'}
                </span>
              </div>
            </div>
            <div class="home-right-separator"></div>
            <div class="home-mods-section">
              <div class="home-mods-header">Installed Mods</div>
              ${modCount > 0 ? `
                ${modsToShow.map(m => `
                  <div class="home-mod-item">
                    <svg class="home-mod-icon" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                    </svg>
                    <span class="home-mod-name">${m.name}</span>
                  </div>
                `).join('')}
                ${modsRemaining > 0 ? `
                  <div class="home-mods-more" onclick="switchPage('mods')">+${modsRemaining} more</div>
                ` : ''}
              ` : `
                <div class="home-no-mods">${selected.platform === 'fabric' ? 'No mods installed yet' : 'Vanilla installation'}</div>
              `}
            </div>
          </div>
        ` : `
          <div class="home-no-install">
            <svg class="home-no-install-icon" viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="3" width="18" height="5" rx="1.5"/><rect x="3" y="10" width="18" height="5" rx="1.5"/><rect x="3" y="17" width="18" height="5" rx="1.5"/>
            </svg>
            <div class="home-no-install-text">No installation selected</div>
            <button class="home-no-install-btn" onclick="switchPage('installations')">Go to Installations</button>
          </div>
        `}
      </div>
    </div>
  `;

  // Set up MC state listener
  if (_homeStateCleanup) _homeStateCleanup();
  _homeStateCleanup = MinecraftLauncher.onChange((state) => {
    _homeUpdateLaunchButton(state, showTimer);
  });
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), showTimer);

  // Start timer update loop
  if (_homeTimerInterval) clearInterval(_homeTimerInterval);
  _homeTimerInterval = setInterval(() => {
    if (MinecraftLauncher.getState() === 'running') {
      const el = document.getElementById('home-timer-value');
      if (el) el.textContent = MinecraftLauncher.getSessionTime();
    }
  }, 1000);

  // Start snow effect in launch bar
  _initLaunchBarSnow();

  // Load server statuses
  _loadAllServers();

  // Refresh servers every 2 minutes
  if (_serverRefreshInterval) clearInterval(_serverRefreshInterval);
  _serverRefreshInterval = setInterval(_loadAllServers, 120000);

  } catch (err) {
    console.error('[HomePageInit] ERROR:', err);
  }
}

async function _fetchServerStatus(address) {
  try {
    const resp = await fetch(`https://api.mcsrvstat.us/3/${address}`, {
      headers: { 'User-Agent': 'IceyClient/1.0' }
    });
    return await resp.json();
  } catch (e) {
    console.error(`[ServerStatus] Failed to fetch ${address}:`, e);
    return { online: false };
  }
}

function _renderFeaturedServer(data, server) {
  const el = document.getElementById('featured-server');
  if (!el) return;

  if (!data.online) {
    el.innerHTML = `
      <div class="server-icon-large"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt="${server.name}"></div>
      <div class="server-info-featured">
        <div class="server-name-large">${server.name}</div>
        <div class="server-address">${server.address}</div>
        <div class="server-offline">Offline</div>
      </div>
    `;
    return;
  }

  const online = data.players?.online ?? 0;
  const max = data.players?.max ?? 0;
  const motd = data.motd?.clean?.[0] ?? '';

  el.innerHTML = `
    <div class="server-icon-large"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt="${server.name}"></div>
    <div class="server-info-featured">
      <div class="server-name-large">${server.name}</div>
      <div class="server-motd">${motd}</div>
      <div class="server-address">${server.address}</div>
    </div>
    <div class="server-players-featured">
      <div class="server-players-count">${online.toLocaleString()}</div>
      <div class="server-players-label">/ ${max.toLocaleString()} players</div>
    </div>
  `;
}

function _renderServerBar(data, server, index) {
  const el = document.getElementById(`server-bar-${index}`);
  if (!el) return;

  if (!data.online) {
    el.innerHTML = `
      <div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt="${server.name}"></div>
      <div class="server-bar-name">${server.name}</div>
      <div class="server-bar-address">${server.address}</div>
      <div class="server-bar-status offline">Offline</div>
    `;
    return;
  }

  const online = data.players?.online ?? 0;
  const max = data.players?.max ?? 0;

  el.innerHTML = `
    <div class="server-bar-icon"><img src="https://api.mcsrvstat.us/icon/${server.address}" alt="${server.name}"></div>
    <div class="server-bar-name">${server.name}</div>
    <div class="server-bar-address">${server.address}</div>
    <div class="server-bar-players">
      <span class="server-bar-online">${online.toLocaleString()}</span>
      <span class="server-bar-max">/ ${max.toLocaleString()}</span>
    </div>
  `;
}

async function _loadAllServers() {
  // Load featured server
  const featuredData = await _fetchServerStatus(FEATURED_SERVER.address);
  _renderFeaturedServer(featuredData, FEATURED_SERVER);

  // Load all smaller servers in parallel
  const results = await Promise.all(SERVERS.map(s => _fetchServerStatus(s.address)));
  results.forEach((data, i) => _renderServerBar(data, SERVERS[i], i));
}

function _initLaunchBarSnow() {
  const container = document.getElementById('launch-bar-snow');
  if (!container) return;

  function createSnowflake() {
    const flake = document.createElement('div');
    flake.className = 'snowflake';
    const size = Math.random() * 3 + 1.5;
    const left = Math.random() * 100;
    const duration = Math.random() * 2 + 2;
    const delay = Math.random() * 3;
    flake.style.cssText = `width:${size}px;height:${size}px;left:${left}%;animation-duration:${duration}s;animation-delay:${delay}s;opacity:${Math.random() * 0.5 + 0.3}`;
    container.appendChild(flake);
    setTimeout(() => flake.remove(), (duration + delay) * 1000);
  }

  // Initial batch
  for (let i = 0; i < 8; i++) createSnowflake();
  // Continuous snow
  setInterval(() => {
    if (document.getElementById('launch-bar-snow')) createSnowflake();
  }, 400);
}

function _homeUpdateLaunchButton(state, showTimer) {
  const btn = document.getElementById('launch-btn');
  const timer = document.getElementById('home-timer');
  if (!btn) return;

  btn.className = 'launch-btn';

  switch (state) {
    case 'idle':
      btn.classList.add('launch-btn-idle');
      btn.disabled = false;
      btn.innerHTML = `
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
        <span id="launch-btn-text">LAUNCH</span>
      `;
      if (timer) timer.classList.remove('visible');
      break;
    case 'starting':
      btn.classList.add('launch-btn-starting');
      btn.disabled = true;
      btn.innerHTML = `
        <div class="loading-dots"><span></span><span></span><span></span></div>
        <span id="launch-btn-text">STARTING...</span>
      `;
      if (timer) timer.classList.remove('visible');
      break;
    case 'running':
      btn.classList.add('launch-btn-running');
      btn.disabled = false;
      btn.innerHTML = `
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
        <span id="launch-btn-text">STOP</span>
      `;
      if (timer && showTimer) timer.classList.add('visible');
      break;
  }
}

async function HomePlayClick() {
  const state = MinecraftLauncher.getState();
  if (state === 'running') {
    MinecraftLauncher.stop();
    return;
  }
  if (state === 'starting') return;

  const installations = await window.icey.getInstallations();
  const selected = installations.find(i => i.selected);
  if (!selected) {
    Toast.info('Select an installation first');
    return;
  }

  await MinecraftLauncher.launch(selected.id);
}
