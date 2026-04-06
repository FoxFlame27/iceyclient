let _homeInitialized = false;
let _homeTimerInterval = null;
let _homeStateCleanup = null;

async function HomePageInit() {
  const page = document.getElementById('page-home');

  const installations = await window.icey.getInstallations();
  const settings = SettingsManager.getAll();
  const selected = installations.find(i => i.selected);
  const opacity = settings.homeBackgroundOpacity ?? 80;
  const showTimer = settings.showSessionTimer !== false;

  const hasFabric = selected && selected.platform === 'fabric' && selected.fabricActive;

  page.innerHTML = `
    <div class="home-bg ${!selected ? 'home-bg-fallback' : ''}" id="home-bg" style="background-image: url('assets/homebg.png');"></div>
    <div class="home-overlay" id="home-overlay" style="background: linear-gradient(to bottom, transparent 40%, rgba(8,12,24,${opacity / 100}) 100%);"></div>
    <div class="home-content">
      <img class="home-logo" src="assets/icon.png" alt="Icey Client" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
      <div class="home-logo-fallback" style="display:none;">
        <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="var(--accent)" stroke-width="1.5">
          <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
        </svg>
      </div>

      <div class="home-badge" id="home-badge">
        ${selected ? `
          <span class="home-badge-text">${selected.name} &middot; ${selected.version}</span>
          ${hasFabric ? `
            <span class="home-badge-separator">&middot;</span>
            <span class="home-badge-fabric">
              <img src="assets/fabric.png" alt="Fabric">
              Fabric Active
            </span>
          ` : ''}
        ` : `
          <span class="home-badge-muted">No installation selected</span>
        `}
      </div>

      <div class="home-play-area">
        <button class="play-btn play-btn-idle" id="play-btn" onclick="HomePlayClick()">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
          <span id="play-btn-text">PLAY</span>
        </button>
        <div class="session-timer-container ${showTimer ? '' : 'hidden'}" id="session-timer">
          <span class="session-timer-label">SESSION</span>
          <span class="session-timer-value" id="session-timer-value">00:00:00</span>
        </div>
      </div>
    </div>
  `;

  // Set up MC state listener
  if (_homeStateCleanup) _homeStateCleanup();
  _homeStateCleanup = MinecraftLauncher.onChange((state) => {
    _homeUpdatePlayButton(state, showTimer);
  });

  // Init with current state
  _homeUpdatePlayButton(MinecraftLauncher.getState(), showTimer);

  // Start timer update loop
  if (_homeTimerInterval) clearInterval(_homeTimerInterval);
  _homeTimerInterval = setInterval(() => {
    if (MinecraftLauncher.getState() === 'running') {
      const el = document.getElementById('session-timer-value');
      if (el) el.textContent = MinecraftLauncher.getSessionTime();
    }
  }, 1000);
}

function _homeUpdatePlayButton(state, showTimer) {
  const btn = document.getElementById('play-btn');
  const text = document.getElementById('play-btn-text');
  const timer = document.getElementById('session-timer');
  if (!btn || !text) return;

  btn.className = 'play-btn';

  switch (state) {
    case 'idle':
      btn.classList.add('play-btn-idle');
      btn.disabled = false;
      btn.innerHTML = `
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
        <span id="play-btn-text">PLAY</span>
      `;
      if (timer) timer.classList.remove('visible');
      break;
    case 'starting':
      btn.classList.add('play-btn-starting');
      btn.disabled = true;
      btn.innerHTML = `
        <div class="loading-dots"><span></span><span></span><span></span></div>
        <span id="play-btn-text">STARTING...</span>
      `;
      if (timer) timer.classList.remove('visible');
      break;
    case 'running':
      btn.classList.add('play-btn-running');
      btn.disabled = false;
      btn.innerHTML = `
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
        <span id="play-btn-text">STOP</span>
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
