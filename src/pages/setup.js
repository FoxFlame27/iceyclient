// First-run setup: appearance, account, in-game menu, preferences.
// Opens once (settings.setupCompleted), and again from Settings → Setup.
// Every choice is saved the moment it's made, so leaving half-way keeps
// what was picked.
const SetupWizard = {
  _step: 0,
  _root: null,
  _menuStyle: 'panels',
  _offlineOpen: false,
  _busy: false,

  ALL_STEPS: [
    { id: 'welcome', label: 'Welcome' },
    { id: 'look',    label: 'Appearance' },
    { id: 'account', label: 'Account' },
    { id: 'menu',    label: 'Game menu' },
    { id: 'prefs',   label: 'Preferences' }
  ],
  // The steps of this run: signing in is left out for someone who already is.
  STEPS: [],

  ACCENTS: [
    { name: 'Ice Blue', value: '#5bc8f5' },
    { name: 'Purple',   value: '#a78bfa' },
    { name: 'Green',    value: '#4ade80' },
    { name: 'Orange',   value: '#fb923c' },
    { name: 'Pink',     value: '#f472b6' },
    { name: 'Flame',    value: '#ff8a3d' }
  ],

  async open() {
    if (this._root) return;
    this._step = 0;
    this._offlineOpen = false;
    this._switching = false;
    this._busy = false;
    // New players start on the glass panels; anyone who already chose keeps it.
    this._menuStyle = SettingsManager.get('hudMenuStyle') === 'grid' ? 'grid' : 'panels';
    let signedIn = false;
    try { const auth = await window.icey.getAuth(); signedIn = !!(auth && auth.username); } catch (_) {}
    this.STEPS = this.ALL_STEPS.filter(s => s.id !== 'account' || !signedIn);

    const root = document.createElement('div');
    root.id = 'setup-overlay';
    root.className = 'setup-overlay';
    document.body.appendChild(root);
    document.body.classList.add('setup-active');
    this._root = root;
    this._onKey = (e) => {
      if (e.key !== 'Enter' || this._busy) return;
      const tag = (e.target && e.target.tagName) || '';
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'BUTTON') return;
      this.next();
    };
    document.addEventListener('keydown', this._onKey);
    await this._render();
    requestAnimationFrame(() => root.classList.add('visible'));
  },

  async _close() {
    const root = this._root;
    if (!root) return;
    this._root = null;
    document.removeEventListener('keydown', this._onKey);
    document.body.classList.remove('setup-active');
    root.classList.remove('visible');
    setTimeout(() => root.remove(), 260);
    if (typeof loadNavProfile === 'function') loadNavProfile();
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  },

  async skip() {
    await SettingsManager.set('setupCompleted', true);
    this._close();
  },

  async finish() {
    await SettingsManager.setMultiple({ setupCompleted: true, hudMenuStyle: this._menuStyle });
    Toast.success('Setup complete. Have fun!');
    this._close();
  },

  async next() {
    if (this._step >= this.STEPS.length) return this.finish();
    // Leaving the game-menu step confirms what was showing as selected.
    if (this.STEPS[this._step].id === 'menu') await SettingsManager.set('hudMenuStyle', this._menuStyle);
    this._step++;
    this._render();
  },

  back() {
    if (this._step === 0) return;
    this._step--;
    this._render();
  },

  goTo(i) {
    if (i < 0 || i > this.STEPS.length || i === this._step) return;
    this._step = i;
    this._render();
  },

  _esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g,
      c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  },

  _check: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',

  async _render() {
    if (!this._root) return;
    const done = this._step >= this.STEPS.length;
    const step = done ? { id: 'done' } : this.STEPS[this._step];
    const body = await this['_step_' + step.id]();
    if (!this._root) return;

    const rail = this.STEPS.map((s, i) => {
      const state = i < this._step ? 'done' : i === this._step ? 'current' : '';
      return `
        <button class="setup-rail-step ${state}" onclick="SetupWizard.goTo(${i})">
          <span class="setup-rail-num">${i < this._step ? this._check : i + 1}</span>
          <span class="setup-rail-label">${s.label}</span>
        </button>`;
    }).join('');

    const nextLabel = done ? 'Start playing' : this._step === 0 ? 'Get started' : 'Continue';
    const skiflame = SettingsManager.isSkiflame();

    this._root.innerHTML = `
      <div class="setup-shell">
        <aside class="setup-rail">
          <div class="setup-brand">
            <img src="assets/icon.png" alt="">
            <span>${skiflame ? 'SKIFLAME' : 'ICEY CLIENT'}</span>
          </div>
          <div class="setup-rail-steps">${rail}</div>
          ${done ? '' : '<button class="setup-skip" onclick="SetupWizard.skip()">Skip setup</button>'}
        </aside>
        <section class="setup-main">
          <div class="setup-body" data-step="${step.id}">${body}</div>
          <footer class="setup-footer">
            <button class="setup-btn setup-btn-ghost ${this._step === 0 ? 'invisible' : ''}" onclick="SetupWizard.back()">Back</button>
            <div class="setup-dots">${this.STEPS.map((_, i) => `<span class="${i === this._step ? 'on' : i < this._step ? 'past' : ''}"></span>`).join('')}</div>
            <button class="setup-btn setup-btn-primary" onclick="SetupWizard.next()">
              ${nextLabel}
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
            </button>
          </footer>
        </section>
      </div>`;
  },

  _head(eyebrow, title, sub) {
    return `
      <div class="setup-head">
        <div class="setup-eyebrow">${eyebrow}</div>
        <h1 class="setup-title">${title}</h1>
        <p class="setup-sub">${sub}</p>
      </div>`;
  },

  _eyebrow() { return `Step ${this._step + 1} of ${this.STEPS.length}`; },

  // ── Steps ────────────────────────────────────────────────────────────

  async _step_welcome() {
    const skiflame = SettingsManager.isSkiflame();
    return `
      <div class="setup-welcome">
        ${skiflame ? '' : '<img class="setup-welcome-logo" src="assets/text-above-playbutton.png" alt="Icey Client">'}
        <h1 class="setup-title">Welcome${skiflame ? '' : ' to Icey Client'}</h1>
        <p class="setup-sub">Let's set things up the way you like them. It takes about a minute, and everything can be changed later in Settings.</p>
        <div class="setup-welcome-points">
          ${['Pick your look']
            .concat(this.STEPS.some(s => s.id === 'account') ? ['Sign in'] : [])
            .concat(['Choose the game menu', 'Tune the game'])
            .map((t, i) => `<div class="setup-point"><span>${i + 1}</span>${t}</div>`).join('')}
        </div>
      </div>`;
  },

  async _step_look() {
    const layout = SettingsManager.get('layoutTheme') === 'liquid' ? 'liquid' : 'classic';
    const accent = (SettingsManager.get('accentColor') || '#5bc8f5').toLowerCase();
    return `
      ${this._head(this._eyebrow(), 'Choose your look', 'The layout of the launcher and the colour that highlights it.')}
      <div class="setup-label">Layout</div>
      <div class="setup-choices">
        <button class="setup-choice ${layout === 'classic' ? 'selected' : ''}" onclick="SetupWizard.setLayout('classic')">
          <div class="setup-mock mock-classic">
            <i class="m-side"></i><i class="m-top"></i><i class="m-skin"></i><i class="m-launch"></i><i class="m-panel"></i>
          </div>
          <div class="setup-choice-text">
            <div class="setup-choice-name">Classic <span class="setup-choice-check">${this._check}</span></div>
            <div class="setup-choice-desc">Side bar, with your skin front and centre.</div>
          </div>
        </button>
        <button class="setup-choice ${layout === 'liquid' ? 'selected' : ''}" onclick="SetupWizard.setLayout('liquid')">
          <div class="setup-mock mock-liquid">
            <i class="m-row m-row-1"></i><i class="m-row m-row-2"></i><i class="m-row m-row-3"></i><i class="m-row m-row-4"></i><i class="m-logo"></i><i class="m-dock"></i>
          </div>
          <div class="setup-choice-text">
            <div class="setup-choice-name">Liquid <span class="setup-choice-check">${this._check}</span></div>
            <div class="setup-choice-desc">Bottom bar, with big menu buttons.</div>
          </div>
        </button>
      </div>
      <div class="setup-label">Accent colour</div>
      <div class="setup-swatches">
        ${this.ACCENTS.map(c => `
          <button class="setup-swatch ${c.value === accent ? 'selected' : ''}" style="--swatch:${c.value}"
                  title="${c.name}" onclick="SetupWizard.setAccent('${c.value}')"><span></span>${c.name}</button>`).join('')}
      </div>`;
  },

  async setLayout(value) {
    await SettingsManager.set('layoutTheme', value === 'liquid' ? 'liquid' : 'classic');
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
    this._render();
  },

  async setAccent(value) {
    await SettingsManager.set('accentColor', value);
    this._render();
  },

  async _step_account() {
    let auth = null;
    try { auth = await window.icey.getAuth(); } catch (_) {}
    const head = this._head(this._eyebrow(), 'Sign in', 'Use your Minecraft account to play online, with your own skin and cape.');

    if (auth && auth.username && !this._switching) {
      let type = 'microsoft';
      try {
        const a = await window.icey.getAccounts();
        const active = (a.accounts || []).find(x => x.uuid === a.activeUuid);
        if (active) type = active.type;
      } catch (_) {}
      const typeLabel = type === 'offline' ? 'Offline account' : type === 'launcher' ? 'Minecraft Launcher account' : 'Microsoft account';
      const name = this._esc(auth.username);
      return `
        ${head}
        <div class="setup-account-card">
          <img src="https://nmsr.nickac.dev/face/${encodeURIComponent(auth.username)}?overlay=true" alt="">
          <div class="setup-account-info">
            <div class="setup-account-name">${name}</div>
            <div class="setup-account-type"><span class="setup-online-dot"></span>${typeLabel}</div>
          </div>
          <span class="setup-account-ok">${this._check} Signed in</span>
        </div>
        <button class="setup-link" onclick="SetupWizard.switchAccount()">Use a different account</button>`;
    }

    return `
      ${head}
      <div class="setup-options">
        <button class="setup-option" onclick="SetupWizard.loginMicrosoft()" ${this._busy ? 'disabled' : ''}>
          <span class="setup-option-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M2 2h9.5v9.5H2zM12.5 2H22v9.5h-9.5zM2 12.5h9.5V22H2zM12.5 12.5H22V22h-9.5z"/></svg>
          </span>
          <span class="setup-option-text">
            <span class="setup-option-name">Sign in with Microsoft</span>
            <span class="setup-option-desc">Opens the Microsoft sign-in window.</span>
          </span>
          <span class="setup-option-tag">Recommended</span>
        </button>
        <button class="setup-option" onclick="SetupWizard.loginLauncher()" ${this._busy ? 'disabled' : ''}>
          <span class="setup-option-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
          </span>
          <span class="setup-option-text">
            <span class="setup-option-name">Use the Minecraft Launcher account</span>
            <span class="setup-option-desc">Picks up the account you're signed into in the official launcher.</span>
          </span>
        </button>
        <button class="setup-option ${this._offlineOpen ? 'open' : ''}" onclick="SetupWizard.toggleOffline()" ${this._busy ? 'disabled' : ''}>
          <span class="setup-option-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"/></svg>
          </span>
          <span class="setup-option-text">
            <span class="setup-option-name">Play offline</span>
            <span class="setup-option-desc">Just a username. Works in single player and on cracked servers only.</span>
          </span>
        </button>
        ${this._offlineOpen ? `
          <div class="setup-offline">
            <input id="setup-offline-name" type="text" maxlength="16" placeholder="Username" spellcheck="false"
                   onkeydown="if(event.key==='Enter'){event.preventDefault();SetupWizard.addOffline();}">
            <button class="setup-btn setup-btn-primary" onclick="SetupWizard.addOffline()">Add</button>
          </div>` : ''}
      </div>
      <p class="setup-note">${this._busy ? 'Waiting for sign-in to finish…' : 'No account at hand? Continue, and sign in later from the top bar.'}</p>`;
  },

  switchAccount() { this._switching = true; this._render(); },

  async _afterLogin(username) {
    this._switching = false;
    this._offlineOpen = false;
    if (username) await SettingsManager.set('username', username);
    if (typeof loadNavProfile === 'function') loadNavProfile();
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  },

  async _guard(fn) {
    if (this._busy) return;
    this._busy = true;
    this._render();
    try { await fn(); } catch (e) { Toast.error((e && e.message) || 'Something went wrong'); }
    this._busy = false;
    this._render();
  },

  loginMicrosoft() {
    return this._guard(async () => {
      const r = await window.icey.msLogin();
      if (!r || r.error) { if (r && r.error) Toast.error(r.error); return; }
      Toast.success('Signed in as ' + r.username);
      await this._afterLogin(r.username);
    });
  },

  loginLauncher() {
    return this._guard(async () => {
      const r = await window.icey.importLauncherAccounts();
      if (!r || r.error) { if (r && r.error) Toast.error(r.error); return; }
      if (!r.found) { Toast.info('No account found in the Minecraft Launcher. Sign in there once, then try again.'); return; }
      Toast.success('Using your Minecraft Launcher account');
      await this._afterLogin(null);
    });
  },

  toggleOffline() {
    this._offlineOpen = !this._offlineOpen;
    this._render().then(() => {
      const input = document.getElementById('setup-offline-name');
      if (input) input.focus();
    });
  },

  addOffline() {
    const input = document.getElementById('setup-offline-name');
    const name = input ? input.value.trim() : '';
    if (!name) { Toast.error('Enter a username'); return; }
    return this._guard(async () => {
      const r = await window.icey.addOfflineAccount(name);
      if (!r || r.error) { if (r && r.error) Toast.error(r.error); return; }
      Toast.success('Added ' + name);
      await this._afterLogin(name);
    });
  },

  async _step_menu() {
    const accent = (SettingsManager.get('accentColor') || '#5bc8f5').toLowerCase();
    const color = (SettingsManager.get('hudMenuColor') || '').toLowerCase();
    const shown = color || accent;
    return `
      ${this._head(this._eyebrow(), 'In-game menu', 'The menu that opens with <kbd>Y</kbd> in game, where you switch the HUD and its modules on and off.')}
      <div class="setup-label">Style</div>
      <div class="setup-choices" style="--menu:${shown}">
        <button class="setup-choice ${this._menuStyle === 'panels' ? 'selected' : ''}" onclick="SetupWizard.setMenuStyle('panels')">
          <div class="setup-mock mock-panels">
            ${[0, 1, 2, 3, 4].map(i => `<i class="m-col m-col-${i}"><b></b><u class="on"></u><u></u><u class="${i % 2 ? 'on' : ''}"></u><u></u><u class="${i === 2 ? 'on' : ''}"></u></i>`).join('')}
          </div>
          <div class="setup-choice-text">
            <div class="setup-choice-name">Panels <span class="setup-choice-check">${this._check}</span></div>
            <div class="setup-choice-desc">Glass columns by category. Click to toggle, right-click for settings.</div>
          </div>
        </button>
        <button class="setup-choice ${this._menuStyle === 'grid' ? 'selected' : ''}" onclick="SetupWizard.setMenuStyle('grid')">
          <div class="setup-mock mock-grid">
            <i class="m-tabs"></i>
            ${Array.from({ length: 12 }, (_, i) => `<i class="m-cell ${i % 3 === 0 ? 'on' : ''}"></i>`).join('')}
          </div>
          <div class="setup-choice-text">
            <div class="setup-choice-name">Grid <span class="setup-choice-check">${this._check}</span></div>
            <div class="setup-choice-desc">The original button grid, with pages and search.</div>
          </div>
        </button>
      </div>
      <div class="setup-label">Menu colour</div>
      <div class="setup-swatches">
        <button class="setup-swatch ${color ? '' : 'selected'}" style="--swatch:${accent}"
                title="Follows the launcher's accent colour" onclick="SetupWizard.setMenuColor('')"><span></span>Match launcher</button>
        ${this.ACCENTS.map(c => `
          <button class="setup-swatch ${c.value === color ? 'selected' : ''}" style="--swatch:${c.value}"
                  title="${c.name}" onclick="SetupWizard.setMenuColor('${c.value}')"><span></span>${c.name}</button>`).join('')}
      </div>
      <p class="setup-note">The menu comes with the Icey mod, on Fabric installations. You can switch style in game as well.</p>`;
  },

  async setMenuStyle(value) {
    this._menuStyle = value === 'grid' ? 'grid' : 'panels';
    await SettingsManager.set('hudMenuStyle', this._menuStyle);
    this._render();
  },

  async setMenuColor(value) {
    await SettingsManager.set('hudMenuColor', value);
    this._render();
  },

  _toggleRow(key, on, name, desc) {
    return `
      <label class="setup-pref">
        <span class="setup-pref-text">
          <span class="setup-pref-name">${name}</span>
          <span class="setup-pref-desc">${desc}</span>
        </span>
        <span class="toggle">
          <input type="checkbox" ${on ? 'checked' : ''} onchange="SetupWizard.setPref('${key}', this.checked)">
          <span class="toggle-slider"></span>
        </span>
      </label>`;
  },

  async _step_prefs() {
    const s = SettingsManager.getAll();
    const ram = s.allocatedRam || 4096;
    return `
      ${this._head(this._eyebrow(), 'Preferences', 'Sensible defaults are already set. Adjust what you want.')}
      <div class="setup-prefs">
        <div class="setup-pref setup-pref-slider">
          <span class="setup-pref-text">
            <span class="setup-pref-name">Memory for Minecraft</span>
            <span class="setup-pref-desc">4 GB suits most players. Go higher for big modpacks or shaders.</span>
          </span>
          <span class="setup-ram">
            <input type="range" min="1024" max="16384" step="512" value="${ram}" oninput="SetupWizard.setRam(this.value)">
            <span class="setup-ram-value" id="setup-ram-value">${(ram / 1024).toFixed(1)} GB</span>
          </span>
        </div>
        ${this._toggleRow('performanceModsEnabled', s.performanceModsEnabled !== false, 'Performance boost', 'Adds Sodium, Lithium and other speed-up mods, matched to your version.')}
        ${this._toggleRow('iceyModsEnabled', s.iceyModsEnabled !== false, 'Icey mod', 'The in-game HUD, menu and the Icey title screen.')}
        ${this._toggleRow('closeLauncherOnStart', !!s.closeLauncherOnStart, 'Close launcher when the game starts', 'Frees a little memory while you play.')}
        ${this._toggleRow('iceyNetworkPresence', s.iceyNetworkPresence !== false, 'Show me as online', 'Other Icey Client players can see that you use it too.')}
      </div>`;
  },

  async setPref(key, value) { await SettingsManager.set(key, !!value); },

  async setRam(value) {
    const v = parseInt(value, 10);
    const label = document.getElementById('setup-ram-value');
    if (label) label.textContent = (v / 1024).toFixed(1) + ' GB';
    await SettingsManager.set('allocatedRam', v);
  },

  async _step_done() {
    const s = SettingsManager.getAll();
    let auth = null;
    try { auth = await window.icey.getAuth(); } catch (_) {}
    const accent = (s.accentColor || '#5bc8f5').toLowerCase();
    const accentName = (this.ACCENTS.find(c => c.value === accent) || { name: 'Custom' }).name;
    const rows = [
      ['Layout', s.layoutTheme === 'liquid' ? 'Liquid' : 'Classic'],
      ['Accent colour', accentName],
      ['Account', auth && auth.username ? this._esc(auth.username) : 'Not signed in'],
      ['Game menu', this._menuStyle === 'grid' ? 'Grid' : 'Panels'],
      ['Memory', ((s.allocatedRam || 4096) / 1024).toFixed(1) + ' GB']
    ];
    return `
      <div class="setup-done">
        <div class="setup-done-badge">
          <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
        </div>
        <h1 class="setup-title">You're all set</h1>
        <p class="setup-sub">Here's what you picked. All of it lives in Settings if you change your mind.</p>
        <div class="setup-summary">
          ${rows.map(r => `<div class="setup-summary-row"><span>${r[0]}</span><strong>${r[1]}</strong></div>`).join('')}
        </div>
      </div>`;
  }
};
