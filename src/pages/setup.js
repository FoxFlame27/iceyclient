// Setup: a page of its own (tab "Setup"), built from the same cards as
// Settings, one step at a time: Theme, Account, In-Game Menu, Game. It
// opens on the first launch after every install, and from Settings →
// General.
// Every choice is saved and applied the moment it's made; the page is
// drawn once and only the touched card changes after that.

const SETUP_ACCENTS = [
  { name: 'Ice Blue', value: '#5bc8f5' },
  { name: 'Purple',   value: '#a78bfa' },
  { name: 'Green',    value: '#4ade80' },
  { name: 'Orange',   value: '#fb923c' },
  { name: 'Pink',     value: '#f472b6' },
  { name: 'Flame',    value: '#ff8a3d' }
];

let _setupBusy = false;

function _setupEsc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

const _SETUP_CHECK = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>';

function _setupPick(group, value, selected, image, name, desc) {
  return `
    <button class="setup-pick ${selected ? 'selected' : ''}" onclick="_setupChoose('${group}', '${value}', this)">
      <img src="assets/setup/${image}" alt="" draggable="false">
      <span class="setup-pick-bar">
        <span class="setup-pick-text">
          <span class="setup-pick-name">${name}</span>
          <span class="setup-pick-desc">${desc}</span>
        </span>
        <span class="setup-pick-check">${_SETUP_CHECK}</span>
      </span>
    </button>`;
}

function _setupSwatches(group, selected) {
  return SETUP_ACCENTS.map(c => `
    <button class="options-accent-swatch ${c.value === selected ? 'selected' : ''}" data-color="${c.value}"
            style="background:${c.value}" title="${c.name}"
            onclick="_setupChoose('${group}', '${c.value}', this)"></button>`).join('');
}

function _setupToggleCard(key, on, name, desc) {
  return `
    <label class="options-toggle-card ${on ? 'on' : 'off'}">
      <div class="options-toggle-body">
        <div class="options-toggle-name">${name}</div>
        <div class="options-toggle-desc">${desc}</div>
      </div>
      <span class="toggle">
        <input type="checkbox" ${on ? 'checked' : ''} onchange="_setupToggle('${key}', this)">
        <span class="toggle-slider"></span>
      </span>
    </label>`;
}

// The steps of this visit, and which one is showing.
let _setupSteps = [];
let _setupStep = 0;
let _setupSignedInAs = null;   // set when someone signs in on the Account step

async function SetupPageInit() {
  let auth = null;
  try { auth = await window.icey.getAuth(); } catch (_) {}
  // Signing in is only asked of someone who isn't signed in yet.
  _setupSteps = ['theme'].concat(auth && auth.username ? [] : ['account'], ['menu', 'game']);
  _setupStep = 0;
  _setupSignedInAs = null;
  _setupRender();
}

const _SETUP_TITLES = {
  theme: ['Theme', 'How the launcher looks.'],
  account: ['Account', 'Sign in to play online with your own skin. You can also do this later from the top bar.'],
  menu: ['In-Game Menu', 'The menu that opens with Y in game, on Fabric installations.'],
  game: ['Game', 'A few things about how the game runs.']
};

function _setupStepHtml(id) {
  const s = SettingsManager.getAll();
  const accent = (s.accentColor || '#5bc8f5').toLowerCase();

  if (id === 'theme') {
    const layout = s.layoutTheme === 'liquid' ? 'liquid' : 'classic';
    return `
      <div class="setup-picks">
        ${_setupPick('layout', 'classic', layout === 'classic', 'classic.jpg', 'Classic', 'Side bar, your skin in the middle')}
        ${_setupPick('layout', 'liquid', layout === 'liquid', 'liquid.jpg', 'Liquid', 'Bottom bar, big menu buttons')}
      </div>
      <div class="options-toggle-row">
        <div class="options-toggle-card options-accent-card on setup-static">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Accent Colour</div>
            <div class="options-toggle-desc">Buttons and highlights</div>
          </div>
          <div class="options-accent-swatches" id="setup-accent">${_setupSwatches('accent', accent)}</div>
        </div>
      </div>`;
  }

  if (id === 'account') return `<div id="setup-account">${_setupAccountHtml(_setupSignedInAs)}</div>`;

  if (id === 'menu') {
    const menuStyle = s.hudMenuStyle === 'grid' ? 'grid' : 'panels';
    const menuColor = (s.hudMenuColor || accent).toLowerCase();
    return `
      <div class="setup-picks">
        ${_setupPick('menu', 'panels', menuStyle === 'panels', 'menu-panels.jpg', 'Panels', 'Click to switch on, right-click for settings')}
        ${_setupPick('menu', 'grid', menuStyle === 'grid', 'menu-grid.jpg', 'Grid', 'The original buttons, with pages')}
      </div>
      <div class="options-toggle-row">
        <div class="options-toggle-card options-accent-card on setup-static">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Menu Colour</div>
            <div class="options-toggle-desc">Highlight in the game menu</div>
          </div>
          <div class="options-accent-swatches" id="setup-menu-color">${_setupSwatches('menuColor', menuColor)}</div>
        </div>
      </div>`;
  }

  const ram = s.allocatedRam || 4096;
  return `
      <div class="options-toggle-row">
        <div class="options-toggle-card on setup-static setup-ram">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Memory</div>
            <div class="options-toggle-desc">4 GB suits most players</div>
          </div>
          <div class="options-slider">
            <input type="range" min="1024" max="16384" step="512" value="${ram}" oninput="_setupRam(this.value)">
            <span class="options-slider-value" id="setup-ram-value">${(ram / 1024).toFixed(1)} GB</span>
          </div>
        </div>
        ${_setupToggleCard('performanceModsEnabled', s.performanceModsEnabled !== false, 'Performance Boost', 'Sodium, Lithium and other speed-up mods')}
        ${_setupToggleCard('closeLauncherOnStart', !!s.closeLauncherOnStart, 'Close on Launch', 'Close the launcher when the game starts')}
      </div>`;
}

// Draws the step that is showing. Called when the step changes, not on
// every choice: choices only touch their own card.
function _setupRender() {
  const page = document.getElementById('page-setup');
  if (!page) return;
  const id = _setupSteps[_setupStep];
  const [title, intro] = _SETUP_TITLES[id];
  const last = _setupStep === _setupSteps.length - 1;
  const done = Math.round(((_setupStep + 1) / _setupSteps.length) * 100);

  page.innerHTML = `
    <div class="options-v2 setup-page">
      <div class="options-v2-header">
        <div class="options-v2-title">Setup</div>
        <div class="setup-progress">
          <span class="setup-progress-text">Step ${_setupStep + 1} of ${_setupSteps.length}</span>
          <span class="setup-progress-bar"><span style="width:${done}%"></span></span>
        </div>
      </div>

      <div class="setup-step-head">
        <div class="options-section-heading">${title}</div>
        <div class="setup-intro">${intro}</div>
      </div>
      ${_setupStepHtml(id)}

      <div class="setup-actions">
        <button class="options-btn setup-skip" onclick="_setupDone(false)">Skip setup</button>
        ${_setupStep > 0 ? '<button class="options-btn" onclick="_setupGo(-1)">Back</button>' : ''}
        <button class="options-btn options-btn-primary" onclick="${last ? '_setupDone(true)' : '_setupGo(1)'}">${last ? 'Finish' : 'Next'}</button>
      </div>
    </div>`;
  page.scrollTop = 0;
}

function _setupGo(by) {
  const next = _setupStep + by;
  if (next < 0 || next >= _setupSteps.length) return;
  _setupStep = next;
  _setupRender();
}

// One handler for everything that is "pick one of these".
async function _setupChoose(group, value, el) {
  if (el && el.parentElement) {
    el.parentElement.querySelectorAll('.selected').forEach(n => n.classList.remove('selected'));
    el.classList.add('selected');
  }
  if (group === 'layout') {
    await SettingsManager.set('layoutTheme', value === 'liquid' ? 'liquid' : 'classic');
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  } else if (group === 'accent') {
    const followed = !SettingsManager.get('hudMenuColor');
    await SettingsManager.set('accentColor', value);
    // A menu colour that was following the accent keeps following it.
    if (followed) _setupMarkSwatch('setup-menu-color', value);
  } else if (group === 'menu') {
    await SettingsManager.set('hudMenuStyle', value === 'grid' ? 'grid' : 'panels');
  } else if (group === 'menuColor') {
    const accent = (SettingsManager.get('accentColor') || '#5bc8f5').toLowerCase();
    await SettingsManager.set('hudMenuColor', value.toLowerCase() === accent ? '' : value);
  }
}

function _setupMarkSwatch(containerId, color) {
  const box = document.getElementById(containerId);
  if (!box) return;
  box.querySelectorAll('.options-accent-swatch').forEach(n =>
    n.classList.toggle('selected', n.dataset.color === String(color).toLowerCase()));
}

async function _setupToggle(key, input) {
  const card = input.closest('.options-toggle-card');
  if (card) { card.classList.toggle('on', input.checked); card.classList.toggle('off', !input.checked); }
  await SettingsManager.set(key, !!input.checked);
}

async function _setupRam(value) {
  const v = parseInt(value, 10);
  const label = document.getElementById('setup-ram-value');
  if (label) label.textContent = (v / 1024).toFixed(1) + ' GB';
  await SettingsManager.set('allocatedRam', v);
}

// ── Account ────────────────────────────────────────────────────────────

function _setupAccountHtml(username, note) {
  if (username) {
    return `
      <div class="options-toggle-row">
        <div class="options-toggle-card on setup-static">
          <img class="options-toggle-icon" src="https://nmsr.nickac.dev/face/${encodeURIComponent(username)}?overlay=true" alt="">
          <div class="options-toggle-body">
            <div class="options-toggle-name">${_setupEsc(username)}</div>
            <div class="options-toggle-desc">Signed in</div>
          </div>
        </div>
      </div>`;
  }
  const off = _setupBusy ? 'disabled' : '';
  return `
    <div class="options-toggle-row">
      <button class="options-toggle-card off setup-account-btn" onclick="_setupLogin('microsoft')" ${off}>
        <div class="options-toggle-body">
          <div class="options-toggle-name">Microsoft</div>
          <div class="options-toggle-desc">Sign in with your Microsoft account</div>
        </div>
      </button>
      <button class="options-toggle-card off setup-account-btn" onclick="_setupLogin('launcher')" ${off}>
        <div class="options-toggle-body">
          <div class="options-toggle-name">Minecraft Launcher</div>
          <div class="options-toggle-desc">Use the account from the official launcher</div>
        </div>
      </button>
      <div class="options-toggle-card off setup-static setup-offline">
        <div class="options-toggle-body">
          <div class="options-toggle-name">Offline</div>
          <div class="options-toggle-desc">Single player and cracked servers</div>
        </div>
        <input id="setup-offline-name" type="text" maxlength="16" placeholder="Username" spellcheck="false" ${off}
               onkeydown="if(event.key==='Enter'){event.preventDefault();_setupLogin('offline');}">
        <button class="options-btn" onclick="_setupLogin('offline')" ${off}>Add</button>
      </div>
    </div>
    <div class="setup-account-note">${note || (_setupBusy ? 'Waiting for the sign-in to finish…' : 'Or leave this for later: the account menu is in the top bar.')}</div>`;
}

function _setupRenderAccount(username, note) {
  const box = document.getElementById('setup-account');
  if (box) box.innerHTML = _setupAccountHtml(username, note);
}

async function _setupLogin(kind) {
  if (_setupBusy) return;
  let offlineName = '';
  if (kind === 'offline') {
    const input = document.getElementById('setup-offline-name');
    offlineName = input ? input.value.trim() : '';
    if (!offlineName) { Toast.error('Enter a username'); return; }
  }
  _setupBusy = true;
  _setupRenderAccount();
  let username = null;
  try {
    if (kind === 'microsoft') {
      const r = await window.icey.msLogin();
      if (r && r.error) Toast.error(r.error);
      else if (r) username = r.username;
    } else if (kind === 'launcher') {
      const r = await window.icey.importLauncherAccounts();
      if (r && r.error) Toast.error(r.error);
      else if (!r || !r.found) Toast.info('No account found in the Minecraft Launcher. Sign in there once, then try again.');
      else { const a = await window.icey.getAuth(); username = a && a.username; }
    } else {
      const r = await window.icey.addOfflineAccount(offlineName);
      if (r && r.error) Toast.error(r.error);
      else username = offlineName;
    }
  } catch (e) {
    Toast.error((e && e.message) || 'Sign-in failed');
  }
  _setupBusy = false;
  if (username) {
    _setupSignedInAs = username;
    await SettingsManager.set('username', username);
    Toast.success('Signed in as ' + username);
    if (typeof loadNavProfile === 'function') loadNavProfile();
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  }
  _setupRenderAccount(username);
}

// ── Leaving ────────────────────────────────────────────────────────────

async function _setupDone(finished) {
  await SettingsManager.markSetupDone();
  if (finished) Toast.success('All set. Have fun!');
  switchPage('home');
}
