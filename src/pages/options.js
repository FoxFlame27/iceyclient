let _optionsInitialized = false;

async function OptionsPageInit() {
  const page = document.getElementById('page-options');
  const settings = SettingsManager.getAll();
  const version = await window.icey.getAppVersion();

  const accentColors = [
    { name: 'Ice Blue', value: '#5bc8f5' },
    { name: 'Purple', value: '#a78bfa' },
    { name: 'Green', value: '#4ade80' },
    { name: 'Orange', value: '#fb923c' },
    { name: 'Pink', value: '#f472b6' }
  ];

  const languages = [
    { code: 'en', name: 'English' },
    { code: 'nl', name: 'Dutch' },
    { code: 'de', name: 'German' },
    { code: 'fr', name: 'French' },
    { code: 'es', name: 'Spanish' }
  ];

  const currentAccent = settings.accentColor || '#5bc8f5';
  const currentTheme = settings.theme || 'dark';
  const ram = settings.allocatedRam || 2048;
  const ramGB = (ram / 1024).toFixed(1);

  page.innerHTML = `
    <div class="options-wrapper">

      <!-- APPEARANCE -->
      <div class="options-section">
        <div class="options-section-title">Appearance</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Theme</span>
            </div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" id="opt-theme" ${currentTheme === 'light' ? 'checked' : ''} onchange="_optSetTheme(this.checked)">
                <span class="toggle-slider"></span>
              </label>
              <span style="font-size:12px;color:var(--text-muted);min-width:36px;">${currentTheme === 'light' ? 'Light' : 'Dark'}</span>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Accent Color</span>
            </div>
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
            <div class="options-row-label">
              <span class="options-row-name">Home Background Opacity</span>
            </div>
            <div class="options-row-control">
              <div class="options-slider">
                <input type="range" min="0" max="100" value="${settings.homeBackgroundOpacity ?? 80}" id="opt-bg-opacity" oninput="_optSetBgOpacity(this.value)">
                <span class="options-slider-value" id="opt-bg-opacity-val">${settings.homeBackgroundOpacity ?? 80}%</span>
              </div>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Show Session Timer</span>
            </div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.showSessionTimer !== false ? 'checked' : ''} onchange="_optSet('showSessionTimer', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- JAVA & PERFORMANCE -->
      <div class="options-section">
        <div class="options-section-title">Java & Performance</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Java Executable Path</span>
              <span class="options-row-desc" id="opt-java-desc">${settings.javaPath || 'Not detected'}</span>
            </div>
            <div class="options-row-control">
              <button class="options-btn" onclick="_optAutoDetectJava()">Auto-detect</button>
              <button class="options-btn" onclick="_optBrowseJava()">Browse</button>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Allocated RAM</span>
            </div>
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
              <textarea class="form-input" id="opt-jvm-args" placeholder="-XX:+UseG1GC -XX:+ParallelRefProcEnabled" onchange="_optSet('jvmArgs', this.value)">${settings.jvmArgs || ''}</textarea>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Close Launcher on Game Start</span>
            </div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.closeLauncherOnStart ? 'checked' : ''} onchange="_optSet('closeLauncherOnStart', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- ACCOUNT -->
      <div class="options-section">
        <div class="options-section-title">Account</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Username</span>
              <span class="options-row-desc">Icey Client uses offline mode. Online servers may not be accessible.</span>
            </div>
            <div class="options-row-control">
              <input class="options-input" type="text" value="${_optEscape(settings.username || 'Player')}" placeholder="Player" onchange="_optSet('username', this.value)" maxlength="16">
            </div>
          </div>
        </div>
      </div>

      <!-- LANGUAGE -->
      <div class="options-section">
        <div class="options-section-title">Language</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Language</span>
            </div>
            <div class="options-row-control">
              <select class="options-select" onchange="_optSet('language', this.value)">
                ${languages.map(l => `<option value="${l.code}" ${settings.language === l.code ? 'selected' : ''}>${l.name}</option>`).join('')}
              </select>
            </div>
          </div>
        </div>
      </div>

      <!-- SOUND -->
      <div class="options-section">
        <div class="options-section-title">Sound</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">UI Sounds</span>
            </div>
            <div class="options-row-control">
              <label class="toggle">
                <input type="checkbox" ${settings.uiSounds !== false ? 'checked' : ''} onchange="_optSet('uiSounds', this.checked)">
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Volume</span>
            </div>
            <div class="options-row-control">
              <div class="options-slider">
                <input type="range" min="0" max="100" value="${settings.volume ?? 60}" oninput="_optSetVolume(this.value)">
                <span class="options-slider-value" id="opt-volume-val">${settings.volume ?? 60}%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ABOUT -->
      <div class="options-section">
        <div class="options-section-title">About</div>
        <div class="options-card">
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">App Version</span>
            </div>
            <div class="options-row-control">
              <span style="font-size:13px;color:var(--text-secondary);">v${version}</span>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">Check for Updates</span>
            </div>
            <div class="options-row-control">
              <button class="options-btn" onclick="Toast.info('You\\'re on the latest version')">Check</button>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-row-name">GitHub</span>
            </div>
            <div class="options-row-control">
              <button class="options-btn" onclick="window.icey.openExternal('https://github.com/FoxFlame27/iceyclient')">Open</button>
            </div>
          </div>
          <div class="options-row">
            <div class="options-row-label">
              <span class="options-credits">Built with Electron &middot; Powered by Modrinth & CurseForge</span>
            </div>
          </div>
        </div>
      </div>

    </div>
  `;
}

async function _optSet(key, value) {
  await SettingsManager.set(key, value);
}

async function _optSetTheme(isLight) {
  const theme = isLight ? 'light' : 'dark';
  await SettingsManager.set('theme', theme);
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
  if (toggle && content) {
    toggle.classList.toggle('expanded');
    content.classList.toggle('visible');
  }
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
    Toast.error('Java not found. Please install Java 17+.');
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

function _optEscape(str) {
  return str.replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
