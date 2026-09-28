let _optionsView = 'main';         // 'main' or 'advanced'
let _optionsTab = 'general';       // which tab of the main view is open
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
  const healthIndicatorsEnabled = settings.healthIndicatorsEnabled !== false;
  const javaStuffEnabled = !!settings.javaStuffEnabled;
  const perfEnabled = settings.performanceModsEnabled !== false;
  const scrollBindsEnabled = !!settings.scrollBindsEnabled;
  const closeOnStart = !!settings.closeLauncherOnStart;
  // Icey network — community features. Default-on, can be turned off
  // by privacy-conscious users.
  const networkCapeShare = settings.iceyNetworkCapeShare !== false;
  const networkPresence = settings.iceyNetworkPresence !== false;
  const networkShowBadges = settings.iceyNetworkShowBadges !== false;
  // Layout theme: 'classic' (sidebar left) or 'liquid' (bottom nav +
  // 4-button home). The default is 'classic' so existing users don't
  // get a surprise UI rearrangement on first launch after upgrading.
  const layoutTheme = settings.layoutTheme === 'liquid' ? 'liquid' : 'classic';
  // Accent color — promoted out of Advanced Settings → Appearance so
  // it lives alongside Health Indicators / Close on Launch in the
  // main toggle row grid.
  const accentColor = settings.accentColor || '#5bc8f5';
  // In-game Y menu: 'panels' (glass columns, the default) or 'grid' (the
  // older button grid). An empty colour means "same as the accent".
  const hudMenuStyle = settings.hudMenuStyle === 'grid' ? 'grid' : 'panels';
  const hudMenuColor = settings.hudMenuColor || '';
  const accentChoices = [
    { name: 'Ice Blue', value: '#5bc8f5' },
    { name: 'Purple',   value: '#a78bfa' },
    { name: 'Green',    value: '#4ade80' },
    { name: 'Orange',   value: '#fb923c' },
    { name: 'Pink',     value: '#f472b6' },
    { name: 'Flame',    value: '#ff8a3d' }
  ];

  // Load panorama catalog if not cached
  if (!_optionsPanoramaCache) {
    try { _optionsPanoramaCache = await window.icey.getPanoramas(); } catch (_) { _optionsPanoramaCache = []; }
  }
  const selectedFilename = settings.selectedPanorama || 'Nether Panorama.zip';
  const selectedEntry = _optionsPanoramaCache.find(p => p.filename === selectedFilename) || _optionsPanoramaCache[0];

  const toggleCard = (key, on, name, desc, icon) => `
        <div class="options-toggle-card ${on ? 'on' : 'off'}" onclick="_optToggleFeature('${key}', ${!on})">
          ${icon || ''}
          <div class="options-toggle-body">
            <div class="options-toggle-name">${name}</div>
            <div class="options-toggle-desc">${desc}</div>
          </div>
          <label class="toggle" onclick="event.stopPropagation();">
            <input type="checkbox" ${on ? 'checked' : ''} onchange="_optToggleFeature('${key}', this.checked)">
            <span class="toggle-slider"></span>
          </label>
        </div>`;
  const swatches = (selected, handler) => accentChoices.map(c => `
              <button class="options-accent-swatch ${c.value === selected ? 'selected' : ''}"
                      style="background:${c.value}"
                      title="${c.name}"
                      onclick="${handler}('${c.value}', this)"></button>`).join('');
  const chevron = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--text-muted);flex-shrink:0"><polyline points="9 18 15 12 9 6"/></svg>';
  const img = (src) => `<img class="options-toggle-icon" src="${src}" alt="">`;

  // One tab at a time, so no page is a wall of switches.
  const panes = {
    general: `
      <div class="options-top-row">
        <div class="options-small-card playtime-card">
          <div class="options-small-body">
            <div class="options-small-label">Total Playtime</div>
            <div class="options-small-value">${playtimeStr}</div>
          </div>
          ${auth ? `<button class="options-small-reset" title="Reset" onclick="_optResetPlaytime()">&#x21bb;</button>` : ''}
        </div>
        <div class="options-small-card advanced-card" onclick="_optRunSetup()">
          <div class="options-small-body">
            <div class="options-small-label">Setup</div>
            <div class="options-small-value">Open setup &rsaquo;</div>
          </div>
        </div>
      </div>
      <div class="options-toggle-row">
        ${toggleCard('closeLauncherOnStart', closeOnStart, 'Close on Launch', 'Close the launcher when the game starts')}
      </div>`,

    appearance: `
      <div class="options-toggle-row">
        <div class="options-toggle-card ${layoutTheme === 'liquid' ? 'on' : 'off'}" onclick="_optSetLayoutTheme('${layoutTheme === 'liquid' ? 'classic' : 'liquid'}')">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Liquid Theme</div>
            <div class="options-toggle-desc">${layoutTheme === 'liquid' ? 'On: bottom bar' : 'Off: Classic, side bar'}</div>
          </div>
          <label class="toggle" onclick="event.stopPropagation();">
            <input type="checkbox" ${layoutTheme === 'liquid' ? 'checked' : ''} onchange="_optSetLayoutTheme(this.checked ? 'liquid' : 'classic')">
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div class="options-toggle-card options-accent-card on">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Accent Colour</div>
            <div class="options-toggle-desc">Buttons and highlights</div>
          </div>
          <div class="options-accent-swatches" onclick="event.stopPropagation();">${swatches(accentColor, '_optSetAccent')}</div>
        </div>
      </div>
      <div class="options-section-heading">Title Screen Panorama</div>
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
      </div>`,

    mods: `
      <div class="options-toggle-row">
        ${toggleCard('iceyModsEnabled', iceyModsEnabled, 'Icey Mods', 'Icey mod and panorama pack', img('assets/icon.png'))}
        ${toggleCard('skinChangerEnabled', skinChangerEnabled, 'Skin Changer', 'Swap skins in game (SkinShuffle)', img('assets/mods/skinshuffle.png'))}
        ${toggleCard('healthIndicatorsEnabled', healthIndicatorsEnabled, 'Health Indicators', 'HP bars above players and mobs', img('assets/mods/healthindicators.png'))}
      </div>
      <div class="options-toggle-row">
        ${toggleCard('performanceModsEnabled', perfEnabled, 'Performance Boost', 'Sodium, Lithium, FerriteCore, ImmediatelyFast, Entity Culling, Krypton (ping) &amp; Dynamic FPS, matched to your version. Fabric only.',
          '<div class="options-toggle-icon-svg"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg></div>')}
        ${toggleCard('javaStuffEnabled', javaStuffEnabled, 'Java &amp; Stuff', 'Actions &amp; Stuff-style animations, 3D items, shaders &amp; sounds. Fabric only. Armor packs stay off — enable in-game.', img('assets/mods/javastuff.png'))}
      </div>
      <div class="options-toggle-row">
        ${toggleCard('scrollBindsEnabled', scrollBindsEnabled, 'Scroll Keybinds', 'Use scroll up and scroll down as keys, for example to jump or attack. Pick the actions in the in-game menu (Y). Needs Icey Mods. Check that your server allows it.', '<img class="options-toggle-icon options-toggle-icon-smooth" src="assets/mods/scrollbinds.svg" alt="">')}
      </div>`,

    game: `
      <div class="options-toggle-row">
        <div class="options-toggle-card ${hudMenuStyle === 'panels' ? 'on' : 'off'}" onclick="_optSetHudMenuStyle('${hudMenuStyle === 'panels' ? 'grid' : 'panels'}')">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Panel Menu</div>
            <div class="options-toggle-desc">${hudMenuStyle === 'panels' ? 'Glass columns by category' : 'Off: the older button grid'}</div>
          </div>
          <label class="toggle" onclick="event.stopPropagation();">
            <input type="checkbox" ${hudMenuStyle === 'panels' ? 'checked' : ''} onchange="_optSetHudMenuStyle(this.checked ? 'panels' : 'grid')">
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div class="options-toggle-card options-accent-card on">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Menu Colour</div>
            <div class="options-toggle-desc">${hudMenuColor ? 'Highlight in the game menu' : 'Matches the accent colour'}</div>
          </div>
          <div class="options-accent-swatches" onclick="event.stopPropagation();">${swatches(hudMenuColor || accentColor, '_optSetHudMenuColor')}</div>
        </div>
      </div>
      <div class="options-tab-note">The menu opens with Y in game, on Fabric installations with Icey Mods on.</div>`,

    network: `
      <div class="options-toggle-row">
        ${toggleCard('iceyNetworkCapeShare', networkCapeShare, 'Share Cape', 'Other Icey Client users see your cape')}
        ${toggleCard('iceyNetworkPresence', networkPresence, 'Show Online', "Show others that you're using Icey Client")}
        ${toggleCard('iceyNetworkShowBadges', networkShowBadges, 'Show Badges', 'See Icey logos on other players in game')}
      </div>`
  };
  const tabs = [
    ['general', 'General'], ['appearance', 'Appearance'], ['mods', 'Mods'],
    ['game', 'In-Game Menu'], ['network', 'Network']
  ];
  if (!panes[_optionsTab]) _optionsTab = 'general';

  page.innerHTML = `
    <div class="options-v2">
      <div class="options-v2-header">
        <div class="options-v2-title">Settings</div>
      </div>
      <div class="mods-tab-bar options-tabs">
        ${tabs.map(([id, label]) => `<button class="mods-tab ${id === _optionsTab ? 'active' : ''}" onclick="_optSetTab('${id}')">${label}</button>`).join('')}
        <button class="mods-tab" onclick="_optOpenAdvanced()">Advanced</button>
      </div>
      ${panes[_optionsTab]}
    </div>
  `;

  // Fetch the preview for the selected panorama
  if (_optionsTab === 'appearance') _optLoadPanoramaPreview(selectedEntry?.filename);
}

function _optSetTab(tab) {
  _optionsTab = tab;
  _optionsRender();
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
  const updates = { [key]: value };
  // Health Indicators depends on Architectury. Keep the two in lockstep:
  // turning HI off also turns its dependency off (no orphaned jar), and
  // turning HI on brings the dependency back so it can actually load.
  if (key === 'healthIndicatorsEnabled') updates.architecturyEnabled = value;
  await SettingsManager.setMultiple(updates);
  if (key === 'healthIndicatorsEnabled') {
    Toast.info(value ? 'Health Indicators + Architectury on' : 'Health Indicators + Architectury off');
  } else if (key === 'scrollBindsEnabled') {
    Toast.info(value ? 'Scroll Keybinds on. Applies the next time you launch the game.' : 'Scroll Keybinds off. Applies the next time you launch the game.');
  } else if (key === 'javaStuffEnabled') {
    Toast.info(value ? 'Java & Stuff will install on next launch (first time takes a few minutes)' : 'Java & Stuff will be removed on next launch');
  }
  _optionsRender();
}

// Swap layout between classic (left sidebar) and liquid (bottom nav +
// 4-button home). SettingsManager pushes the `data-layout` attribute on
// <html> so the CSS swap is instant — we just re-render Home and the
// settings page so any home-specific markup picks up the change.
async function _optSetLayoutTheme(value) {
  const next = value === 'liquid' ? 'liquid' : 'classic';
  await SettingsManager.set('layoutTheme', next);
  Toast.info(next === 'liquid' ? 'Liquid theme on' : 'Classic theme on');
  if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  _optionsRender();
}

async function _optSetHudMenuStyle(value) {
  const next = value === 'panels' ? 'panels' : 'grid';
  await SettingsManager.set('hudMenuStyle', next);
  Toast.info((next === 'panels' ? 'Panel menu' : 'Grid menu') + ' on. Applies the next time you launch the game.');
  _optionsRender();
}

async function _optSetHudMenuColor(color) {
  // Picking the accent again goes back to following it.
  const accent = (SettingsManager.get('accentColor') || '#5bc8f5').toLowerCase();
  await SettingsManager.set('hudMenuColor', color.toLowerCase() === accent ? '' : color);
  _optionsRender();
}

function _optRunSetup() {
  switchPage('setup');
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
        <div class="options-section-title">Bundled Mods</div>
        <div class="options-card">
          <div class="options-row">
            <img class="options-row-icon" src="assets/mods/architectury.png" alt="Architectury">
            <div class="options-row-label">
              <span class="options-row-name">Architectury</span>
              <span class="options-row-desc">Dependency of Health Indicators — follows that toggle automatically. Turn off only if you manage your own architectury jar.</span>
            </div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.architecturyEnabled !== false ? 'checked' : ''} onchange="_optSet('architecturyEnabled', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Garbage collector</span>
              <span class="options-row-desc">Auto picks generational ZGC (smoothest frame times) on machines with 16 GB+ RAM and Java 21+, otherwise tuned G1. Force one here if you want to compare.</span>
            </div>
            <div class="options-row-control">
              <select class="options-select" onchange="_optSet('gcMode', this.value)">
                <option value="auto" ${(settings.gcMode || 'auto') === 'auto' ? 'selected' : ''}>Auto</option>
                <option value="g1" ${settings.gcMode === 'g1' ? 'selected' : ''}>G1 (default)</option>
                <option value="zgc" ${settings.gcMode === 'zgc' ? 'selected' : ''}>ZGC generational</option>
              </select>
            </div>
          </div>
          <div class="options-row options-secret-row">
            <div class="options-row-label">
              <span class="options-row-name">SECRET</span>
              <span class="options-row-desc">${settings.skiflameMode ? 'Skiflame mode is on. Flame theme, Skiflame logo and background, in the launcher and in-game.' : 'Don\'t press this.'}</span>
            </div>
            <div class="options-row-control">
              <button class="options-btn options-secret-btn ${settings.skiflameMode ? 'on' : ''}" onclick="_optToggleSkiflame()">${settings.skiflameMode ? 'Skiflame ON' : 'SECRET'}</button>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Use Minecraft Launcher login</span>
              <span class="options-row-desc">Sign in automatically with the account from the official Minecraft Launcher. Turn off if you only want accounts you add here.</span>
            </div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.useLauncherLogin !== false ? 'checked' : ''} onchange="_optSet('useLauncherLogin', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Version matching</span>
              <span class="options-row-desc">All bundled mods and their dependencies are fetched from Modrinth for the exact Minecraft version of the installation you launch, so new Minecraft releases work without a launcher update.</span>
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
          <span class="options-row-desc">Logged in as <strong>${_optEscape(auth.username)}</strong>${auth.type === 'launcher' ? ' via the Minecraft Launcher' : auth.type === 'offline' ? ' (cracked)' : ''}</span>
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
          <span class="options-row-name">Account</span>
          <span class="options-row-desc">Icey uses the account you're signed into in the official Minecraft Launcher. No account found there yet — open the Minecraft Launcher once, or sign in with Microsoft here.</span>
        </div>
        <div class="options-row-control">
          <button class="options-btn" onclick="_optImportLauncher()">Use Launcher Login</button>
          <button class="options-btn" onclick="_optLogin()">Microsoft Log In</button>
        </div>
      </div>
    `;
  }
}

async function _optToggleSkiflame() {
  const next = !SettingsManager.get('skiflameMode');
  await SettingsManager.set('skiflameMode', next);
  Toast.show(next ? '🔥 Skiflame mode ON' : 'Back to Icey Client', next ? 'success' : 'info');
  if (typeof loadNavProfile === 'function') loadNavProfile();
  _optionsRender();
}

async function _optImportLauncher() {
  const r = await window.icey.importLauncherAccounts();
  if (r.error) { Toast.error(r.error); return; }
  if (!r.found) { Toast.info('No account found in the Minecraft Launcher. Sign in there once, then try again.'); return; }
  Toast.success('Using your Minecraft Launcher account');
  loadNavProfile();
  _optLoadAccount();
}

async function _optLogin() {
  const result = await window.icey.msLogin();
  if (result.error) {
    if (result.removed && result.removed.length) {
      Toast.info('Login cancelled — removed expired account ' + result.removed.join(', '));
      loadNavProfile(); _optLoadAccount();
    } else Toast.error(result.error);
  } else {
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
  // Clear previous selection on both the old Advanced swatches (if
  // the Advanced screen is open) AND the new main-Settings swatches.
  document.querySelectorAll('.color-swatch, .options-accent-swatch').forEach(s => s.classList.remove('selected'));
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
