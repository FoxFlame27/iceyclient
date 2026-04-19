let _optionsView = 'main';         // 'main' or 'advanced'
let _optionsPanoramaCache = null;  // list of {filename, name}
const _optionsPanoramaPreviews = {}; // filename -> data URI

async function OptionsPageInit() {
  _optionsView = 'main';
  _optionsRender();
}

async function _optionsRender() {
  const page = document.getElementById('page-options');
  const settings = SettingsManager.getAll();
  if (_optionsView === 'advanced') {
    _renderAdvancedOptions(page, settings);
  } else {
    await _renderMainOptions(page, settings);
  }
}

async function _renderMainOptions(page, settings) {
  const auth = await window.icey.getAuth();
  const totalSecs = settings.totalPlaytime || 0;
  const ptHours = Math.floor(totalSecs / 3600);
  const ptMins = Math.floor((totalSecs % 3600) / 60);
  const playtimeStr = auth
    ? (ptHours > 0 ? `${ptHours}h ${ptMins}m` : `${ptMins}m`)
    : 'Log in to track';

  const iceyModsEnabled = settings.iceyModsEnabled !== false;
  const skinChangerEnabled = !!settings.skinChangerEnabled;

  // Load panorama catalog if not cached
  if (!_optionsPanoramaCache) {
    try { _optionsPanoramaCache = await window.icey.getPanoramas(); } catch (_) { _optionsPanoramaCache = []; }
  }
  const selectedFilename = settings.selectedPanorama || 'Nether Panorama.zip';
  const selectedEntry = _optionsPanoramaCache.find(p => p.filename === selectedFilename) || _optionsPanoramaCache[0];

  page.innerHTML = `
    <div class="options-v2">
      <div class="options-v2-header">
        <div class="options-v2-title">Settings</div>
      </div>

      <!-- Top row: Playtime + Advanced -->
      <div class="options-top-row">
        <div class="options-small-card playtime-card">
          <div class="options-small-body">
            <div class="options-small-label">Total Playtime</div>
            <div class="options-small-value">${playtimeStr}</div>
          </div>
          ${auth ? `<button class="options-small-reset" title="Reset" onclick="_optResetPlaytime()">&#x21bb;</button>` : ''}
        </div>

        <div class="options-small-card advanced-card" onclick="_optOpenAdvanced()">
          <div class="options-small-body">
            <div class="options-small-label">Advanced</div>
            <div class="options-small-value">All settings &rsaquo;</div>
          </div>
        </div>
      </div>

      <!-- Panorama card -->
      <div class="options-panorama-card" onclick="_optOpenPanoramaPicker()">
        <div class="options-panorama-preview" id="opt-panorama-preview">
          <div class="options-panorama-loading">Loading preview...</div>
        </div>
        <div class="options-panorama-overlay">
          <div class="options-panorama-name">${selectedEntry ? _optEscape(selectedEntry.name) : 'No panorama'}</div>
          <button class="options-panorama-btn" onclick="event.stopPropagation(); _optOpenPanoramaPicker()">
            Show All Panoramas
          </button>
        </div>
      </div>

      <!-- Feature toggle row -->
      <div class="options-toggle-row">
        <div class="options-toggle-card ${iceyModsEnabled ? 'on' : 'off'}" onclick="_optToggleFeature('iceyModsEnabled', ${!iceyModsEnabled})">
          <img class="options-toggle-icon" src="assets/icon.png" alt="Icey">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Icey Mods</div>
            <div class="options-toggle-desc">Icey mod + panorama pack</div>
          </div>
          <label class="toggle" onclick="event.stopPropagation();">
            <input type="checkbox" ${iceyModsEnabled ? 'checked' : ''} onchange="_optToggleFeature('iceyModsEnabled', this.checked)">
            <span class="toggle-slider"></span>
          </label>
        </div>

        <div class="options-toggle-card ${skinChangerEnabled ? 'on' : 'off'}" onclick="_optToggleFeature('skinChangerEnabled', ${!skinChangerEnabled})">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Skin Changer</div>
            <div class="options-toggle-desc">In-game skin swap mod</div>
          </div>
          <label class="toggle" onclick="event.stopPropagation();">
            <input type="checkbox" ${skinChangerEnabled ? 'checked' : ''} onchange="_optToggleFeature('skinChangerEnabled', this.checked)">
            <span class="toggle-slider"></span>
          </label>
        </div>
      </div>
    </div>
  `;

  // Fetch the preview for the selected panorama
  _optLoadPanoramaPreview(selectedEntry?.filename);
}

async function _optLoadPanoramaPreview(filename) {
  const el = document.getElementById('opt-panorama-preview');
  if (!el || !filename) return;
  if (_optionsPanoramaPreviews[filename]) {
    el.style.backgroundImage = `url('${_optionsPanoramaPreviews[filename]}')`;
    el.innerHTML = '';
    return;
  }
  try {
    const data = await window.icey.getPanoramaPreview(filename);
    if (data) {
      _optionsPanoramaPreviews[filename] = data;
      el.style.backgroundImage = `url('${data}')`;
      el.innerHTML = '';
    } else {
      el.innerHTML = '<div class="options-panorama-loading">No preview</div>';
    }
  } catch (_) {
    el.innerHTML = '<div class="options-panorama-loading">No preview</div>';
  }
}

async function _optOpenPanoramaPicker() {
  if (!_optionsPanoramaCache) {
    _optionsPanoramaCache = await window.icey.getPanoramas();
  }
  const settings = SettingsManager.getAll();
  const selected = settings.selectedPanorama || 'Nether Panorama.zip';
  showModal(`
    <div class="panorama-picker-modal">
      <div class="panorama-picker-header">
        <div class="panorama-picker-title">Choose Panorama</div>
        <button class="modal-close" onclick="closeModal()">&times;</button>
      </div>
      <div class="panorama-picker-subtitle">Pick one — it replaces the current panorama on next launch.</div>
      <div class="panorama-picker-grid" id="panorama-picker-grid">
        ${_optionsPanoramaCache.map(p => `
          <div class="panorama-option ${p.filename === selected ? 'selected' : ''}"
               data-filename="${_optEscape(p.filename)}"
               onclick="_optSelectPanorama('${_optEscape(p.filename)}')">
            <div class="panorama-option-preview" id="pano-preview-${_optSlug(p.filename)}">
              <div class="options-panorama-loading">...</div>
            </div>
            <div class="panorama-option-name">${_optEscape(p.name)}</div>
            ${p.filename === selected ? '<div class="panorama-option-check">&#10003;</div>' : ''}
          </div>
        `).join('')}
      </div>
    </div>
  `);
  // Lazy-load previews for all options
  for (const p of _optionsPanoramaCache) {
    const slug = _optSlug(p.filename);
    const target = document.getElementById('pano-preview-' + slug);
    if (!target) continue;
    if (_optionsPanoramaPreviews[p.filename]) {
      target.style.backgroundImage = `url('${_optionsPanoramaPreviews[p.filename]}')`;
      target.innerHTML = '';
    } else {
      window.icey.getPanoramaPreview(p.filename).then(data => {
        if (data) {
          _optionsPanoramaPreviews[p.filename] = data;
          const t2 = document.getElementById('pano-preview-' + slug);
          if (t2) { t2.style.backgroundImage = `url('${data}')`; t2.innerHTML = ''; }
        }
      }).catch(() => {});
    }
  }
}

async function _optSelectPanorama(filename) {
  await SettingsManager.set('selectedPanorama', filename);
  closeModal();
  Toast.success('Panorama set — takes effect on next launch');
  _optionsRender();
}

async function _optToggleFeature(key, value) {
  await SettingsManager.set(key, value);
  _optionsRender();
}

async function _optResetPlaytime() {
  await SettingsManager.set('totalPlaytime', 0);
  Toast.info('Playtime reset');
  _optionsRender();
}

function _optOpenAdvanced() {
  _optionsView = 'advanced';
  _optionsRender();
}

function _optBackToMain() {
  _optionsView = 'main';
  _optionsRender();
}

function _optSlug(s) { return String(s).replace(/[^a-z0-9]/gi, '_'); }

function _optEscape(str) {
  return String(str).replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// ── Advanced view (everything that was in the old Settings page) ────────
function _renderAdvancedOptions(page, settings) {
  const accentColors = [
    { name: 'Ice Blue', value: '#5bc8f5' },
    { name: 'Purple', value: '#a78bfa' },
    { name: 'Green', value: '#4ade80' },
    { name: 'Orange', value: '#fb923c' },
    { name: 'Pink', value: '#f472b6' }
  ];
  const currentAccent = settings.accentColor || '#5bc8f5';
  const currentTheme = settings.theme || 'dark';
  const ram = settings.allocatedRam || 4096;
  const ramGB = (ram / 1024).toFixed(1);

  page.innerHTML = `
    <div class="options-wrapper">
      <div class="options-advanced-header">
        <button class="options-back-btn" onclick="_optBackToMain()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
          </svg>
          Back
        </button>
        <div class="options-section-title" style="margin:0">Advanced Settings</div>
      </div>

      <div class="options-section">
        <div class="options-section-title">Appearance</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Theme</span></div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" id="opt-theme" ${currentTheme === 'light' ? 'checked' : ''} onchange="_optSetTheme(this.checked)">
                <span class="toggle-slider"></span>
              </label>
              <span style="font-size:12px;color:var(--text-muted);min-width:36px;">${currentTheme === 'light' ? 'Light' : 'Dark'}</span>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Accent Color</span></div>
            <div class="options-row-control">
              <div class="color-swatches">
                ${accentColors.map(c => `
                  <div class="color-swatch ${c.value === currentAccent ? 'selected' : ''}"
                       style="background:${c.value};color:${c.value}"
                       title="${c.name}"
                       onclick="_optSetAccent('${c.value}', this)"></div>
                `).join('')}
              </div>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Home Background Opacity</span></div>
            <div class="options-row-control">
              <div class="options-slider">
                <input type="range" min="0" max="100" value="${settings.homeBackgroundOpacity ?? 80}" id="opt-bg-opacity" oninput="_optSetBgOpacity(this.value)">
                <span class="options-slider-value" id="opt-bg-opacity-val">${settings.homeBackgroundOpacity ?? 80}%</span>
              </div>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Show Session Timer</span></div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.showSessionTimer !== false ? 'checked' : ''} onchange="_optSet('showSessionTimer', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <div class="options-section">
        <div class="options-section-title">Java &amp; Performance</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Java Executable Path</span>
              <span class="options-row-desc" id="opt-java-desc">${_optEscape(settings.javaPath || 'Not detected')}</span>
            </div>
            <div class="options-row-control">
              <button class="options-btn" onclick="_optAutoDetectJava()">Auto-detect</button>
              <button class="options-btn" onclick="_optBrowseJava()">Browse</button>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Allocated RAM</span></div>
            <div class="options-row-control">
              <div class="options-slider">
                <input type="range" min="512" max="16384" step="512" value="${ram}" id="opt-ram" oninput="_optSetRam(this.value)">
                <span class="options-slider-value" id="opt-ram-val">${ramGB} GB</span>
              </div>
            </div>
          </div>
          <div class="options-row options-row-expandable">
            <div class="options-row-header">
              <div class="jvm-args-toggle" id="jvm-args-toggle" onclick="_optToggleJvmArgs()">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
                <span class="options-row-name">JVM Arguments (Advanced)</span>
              </div>
            </div>
            <div class="jvm-args-content" id="jvm-args-content">
              <textarea class="form-input" id="opt-jvm-args" placeholder="-XX:+UseG1GC" onchange="_optSet('jvmArgs', this.value)">${_optEscape(settings.jvmArgs || '')}</textarea>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Close Launcher on Game Start</span></div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.closeLauncherOnStart ? 'checked' : ''} onchange="_optSet('closeLauncherOnStart', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <div class="options-section">
        <div class="options-section-title">Account</div>
        <div class="options-card" id="opt-account-card"></div>
      </div>

      <div class="options-section">
        <div class="options-section-title">Sound</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">UI Sounds</span></div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.uiSounds !== false ? 'checked' : ''} onchange="_optSet('uiSounds', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">Volume</span></div>
            <div class="options-row-control">
              <div class="options-slider">
                <input type="range" min="0" max="100" value="${settings.volume ?? 60}" oninput="_optSetVolume(this.value)">
                <span class="options-slider-value" id="opt-volume-val">${settings.volume ?? 60}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="options-section">
        <div class="options-section-title">About</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">App Version</span></div>
            <div class="options-row-control"><span id="opt-version" style="font-size:13px;color:var(--text-secondary);">loading...</span></div>
          </div>
          <div class="options-row">
            <div class="options-row-label"><span class="options-row-name">GitHub</span></div>
            <div class="options-row-control">
              <button class="options-btn" onclick="window.icey.openExternal('https://github.com/FoxFlame27/iceyclient')">Open</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
  _optLoadAccount();
  window.icey.getAppVersion().then(v => {
    const el = document.getElementById('opt-version');
    if (el) el.textContent = 'v' + v;
  });
}

async function _optLoadAccount() {
  const card = document.getElementById('opt-account-card');
  if (!card) return;
  const auth = await window.icey.getAuth();
  if (auth && auth.username) {
    card.innerHTML = `
      <div class="options-row">
        <div class="options-row-label">
          <span class="options-row-name">Active Account</span>
          <span class="options-row-desc">Logged in as <strong>${_optEscape(auth.username)}</strong></span>
        </div>
        <div class="options-row-control">
          <button class="options-btn" onclick="_optLogout()">Log Out</button>
        </div>
      </div>
    `;
  } else {
    card.innerHTML = `
      <div class="options-row">
        <div class="options-row-label">
          <span class="options-row-name">Microsoft Account</span>
          <span class="options-row-desc">Sign in to play on online servers and access your skins.</span>
        </div>
        <div class="options-row-control">
          <button class="options-btn" onclick="_optLogin()">Log In</button>
        </div>
      </div>
    `;
  }
}

async function _optLogin() {
  const result = await window.icey.msLogin();
  if (result.error) Toast.error(result.error);
  else {
    Toast.success('Logged in as ' + result.username);
    await SettingsManager.set('username', result.username);
    loadNavProfile();
    _optLoadAccount();
  }
}

async function _optLogout() {
  await window.icey.msLogout();
  Toast.info('Logged out');
  loadNavProfile();
  _optLoadAccount();
}

async function _optSet(key, value) { await SettingsManager.set(key, value); }
async function _optSetTheme(isLight) {
  await SettingsManager.set('theme', isLight ? 'light' : 'dark');
  const label = document.querySelector('#opt-theme')?.parentElement?.nextElementSibling;
  if (label) label.textContent = isLight ? 'Light' : 'Dark';
}
async function _optSetAccent(color, el) {
  await SettingsManager.set('accentColor', color);
  document.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('selected'));
  if (el) el.classList.add('selected');
}
async function _optSetBgOpacity(val) {
  const label = document.getElementById('opt-bg-opacity-val');
  if (label) label.textContent = val + '%';
  await SettingsManager.set('homeBackgroundOpacity', parseInt(val));
}
async function _optSetRam(val) {
  const gb = (parseInt(val) / 1024).toFixed(1);
  const label = document.getElementById('opt-ram-val');
  if (label) label.textContent = gb + ' GB';
  await SettingsManager.set('allocatedRam', parseInt(val));
}
function _optToggleJvmArgs() {
  const toggle = document.getElementById('jvm-args-toggle');
  const content = document.getElementById('jvm-args-content');
  if (toggle && content) { toggle.classList.toggle('expanded'); content.classList.toggle('visible'); }
}
async function _optAutoDetectJava() {
  const javaPath = await window.icey.autoDetectJava();
  const desc = document.getElementById('opt-java-desc');
  if (javaPath) {
    if (desc) desc.textContent = javaPath;
    await SettingsManager.set('javaPath', javaPath);
    Toast.success('Java found: ' + javaPath);
  } else {
    if (desc) desc.textContent = 'Not found';
    Toast.error('Java not found. Please install Java 21.');
  }
}
async function _optBrowseJava() {
  const filePath = await window.icey.selectFile([
    { name: 'Java Executable', extensions: process.platform === 'win32' ? ['exe'] : ['*'] }
  ]);
  if (!filePath) return;
  const desc = document.getElementById('opt-java-desc');
  if (desc) desc.textContent = filePath;
  await SettingsManager.set('javaPath', filePath);
  Toast.success('Java path set');
}
async function _optSetVolume(val) {
  const label = document.getElementById('opt-volume-val');
  if (label) label.textContent = val + '%';
  await SettingsManager.set('volume', parseInt(val));
}
