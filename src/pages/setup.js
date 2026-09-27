// Setup: a page of its own (tab "Setup"), built from the same cards as
// Settings. It opens on the first launch and again from Settings → Setup.
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

async function SetupPageInit() {
  const page = document.getElementById('page-setup');
  if (!page) return;
  const s = SettingsManager.getAll();
  let auth = null;
  try { auth = await window.icey.getAuth(); } catch (_) {}
  const signedIn = !!(auth && auth.username);

  const layout = s.layoutTheme === 'liquid' ? 'liquid' : 'classic';
  const accent = (s.accentColor || '#5bc8f5').toLowerCase();
  const menuStyle = s.hudMenuStyle === 'grid' ? 'grid' : 'panels';
  const menuColor = (s.hudMenuColor || accent).toLowerCase();
  const ram = s.allocatedRam || 4096;

  // Signing in is only asked of someone who isn't signed in yet.
  const account = signedIn ? '' : `
      <div class="options-section-heading">Account</div>
      <div id="setup-account">${_setupAccountHtml()}</div>`;

  page.innerHTML = `
    <div class="options-v2 setup-page">
      <div class="options-v2-header">
        <div class="options-v2-title">Setup</div>
        <div class="setup-intro">Choose how Icey Client looks and plays. Everything here can be changed later in Settings.</div>
      </div>

      <div class="options-section-heading">Theme</div>
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
      </div>
      ${account}

      <div class="options-section-heading">In-Game Menu</div>
      <div class="setup-picks">
        ${_setupPick('menu', 'panels', menuStyle === 'panels', 'menu-panels.jpg', 'Panels', 'Click to switch on, right-click for settings')}
        ${_setupPick('menu', 'grid', menuStyle === 'grid', 'menu-grid.jpg', 'Grid', 'The original buttons, with pages')}
      </div>
      <div class="options-toggle-row">
        <div class="options-toggle-card options-accent-card on setup-static">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Menu Colour</div>
            <div class="options-toggle-desc">Opens with Y in game, on Fabric installations</div>
          </div>
          <div class="options-accent-swatches" id="setup-menu-color">${_setupSwatches('menuColor', menuColor)}</div>
        </div>
      </div>

      <div class="options-section-heading">Game</div>
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
      </div>

      <div class="setup-actions">
        <button class="options-btn" onclick="_setupDone(false)">Skip</button>
        <button class="options-btn options-btn-primary" onclick="_setupDone(true)">Finish setup</button>
      </div>
    </div>`;
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
    await SettingsManager.set('username', username);
    Toast.success('Signed in as ' + username);
    if (typeof loadNavProfile === 'function') loadNavProfile();
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  }
  _setupRenderAccount(username);
}

// ── Leaving ────────────────────────────────────────────────────────────

async function _setupDone(finished) {
  await SettingsManager.set('setupCompleted', true);
  if (finished) Toast.success('All set. Have fun!');
  switchPage('home');
}
