let _homeTimerInterval = null;
let _homeStateCleanup = null;

async function HomePageInit() {
  const page = document.getElementById('page-home');
  try {
  console.log('[HomePageInit] Starting...');
  const installations = await window.icey.getInstallations();
  console.log('[HomePageInit] Installations:', installations);
  const settings = SettingsManager.getAll();
  console.log('[HomePageInit] Settings:', settings);
  const selected = installations.find(i => i.selected);
  const opacity = settings.homeBackgroundOpacity ?? 80;
  const showTimer = settings.showSessionTimer !== false;

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

  // Get installation image
  const installImage = selected && selected.image
    ? `file://${selected.image.replace(/\\/g, '/')}`
    : 'assets/installbg.png';

  page.innerHTML = `
    <div class="home-bg" style="background-image: url('assets/background.png');"></div>
    <div class="home-overlay" style="opacity: ${opacity / 100};"></div>
    <div class="home-content">
      <!-- Left box with launch button -->
      <div class="home-left-box" style="background-image: url('assets/image-for-in-black-box.png');">
        <div class="home-left-box-overlay"></div>
        <div class="home-left-content">
          <img class="home-logo-text" src="assets/text-above-playbutton.png" alt="Icey Client" onerror="this.style.display='none'">
          <button class="launch-btn launch-btn-idle" id="launch-btn" onclick="HomePlayClick()">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
            <span id="launch-btn-text">LAUNCH</span>
          </button>
          <div class="home-timer ${showTimer ? '' : 'hidden'}" id="home-timer">
            <span class="home-timer-label">Playtime</span>
            <span class="home-timer-value" id="home-timer-value">00:00:00</span>
          </div>
        </div>
      </div>

      <!-- Right box with installation info -->
      <div class="home-right-box">
        ${selected ? `
          <div class="home-right-header">Selected Installation</div>
          <div class="home-install-preview">
            <div class="home-install-image">
              <img src="${installImage}" alt="${selected.name}" onerror="this.parentElement.innerHTML='<div class=\\'home-install-image-placeholder\\'><svg viewBox=\\'0 0 24 24\\' width=\\'32\\' height=\\'32\\' fill=\\'none\\' stroke=\\'currentColor\\' stroke-width=\\'1.5\\'><rect x=\\'3\\' y=\\'3\\' width=\\'18\\' height=\\'18\\' rx=\\'2\\'/><circle cx=\\'8.5\\' cy=\\'8.5\\' r=\\'1.5\\'/><path d=\\'M21 15l-5-5L5 21\\'/></svg></div>'">
            </div>
            <div class="home-install-details">
              <div class="home-install-name">${selected.name}</div>
              <div class="home-install-row">
                <span class="home-install-label">Version</span>
                <span class="home-install-value">${selected.version}</span>
              </div>
              <div class="home-install-row">
                <span class="home-install-label">Platform</span>
                <span class="home-install-value ${selected.platform === 'fabric' ? 'fabric' : ''}">
                  ${selected.platform === 'fabric' ? `<img src="assets/fabric.png" alt=""> Fabric` : 'Vanilla'}
                </span>
              </div>
            </div>
          </div>
          <div class="home-right-separator"></div>
          <div class="home-mods-section">
            <div class="home-mods-header">Installed Mods</div>
            ${modCount > 0 ? `
              ${modsToShow.map(m => `
                <div class="home-mod-item">
                  <svg class="home-mod-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
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
        ` : `
          <div class="home-no-install">
            <svg class="home-no-install-icon" viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
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

  // Init with current state
  _homeUpdateLaunchButton(MinecraftLauncher.getState(), showTimer);

  // Start timer update loop
  if (_homeTimerInterval) clearInterval(_homeTimerInterval);
  _homeTimerInterval = setInterval(() => {
    if (MinecraftLauncher.getState() === 'running') {
      const el = document.getElementById('home-timer-value');
      if (el) el.textContent = MinecraftLauncher.getSessionTime();
    }
  }, 1000);

  console.log('[HomePageInit] Done rendering');
  } catch (err) {
    console.error('[HomePageInit] ERROR:', err);
    page.innerHTML = `<div style="color:red;padding:40px;font-size:16px;">Error loading home page: ${err.message}<br><pre>${err.stack}</pre></div>`;
  }
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
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
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
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
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
