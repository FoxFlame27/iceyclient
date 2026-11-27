// Setup: a page of its own (tab "Setup"), built from the same cards as
// Settings, one step at a time: Language, Import, Theme, Background,
// Account, In-Game Menu, Game. It
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
  _setupSteps = ['language', 'import', 'theme', 'background'].concat(auth && auth.username ? [] : ['account'], ['menu', 'game']);
  _setupStep = 0;
  _setupSignedInAs = null;
  _setupImport = { scan: null, off: new Set(), accounts: true, busy: false, result: null };
  _setupRender();
}

const _SETUP_TITLES = {
  language: ['Language', 'The language of the launcher.'],
  import: ['Import', 'Bring your mods, servers and accounts over from another Minecraft client. Your worlds stay where they are.'],
  theme: ['Theme', 'How the launcher looks.'],
  background: ['Background', 'What is behind the launcher. You can change it later in Settings.'],
  account: ['Account', 'Sign in to play online with your own skin. You can also do this later from the top bar.'],
  menu: ['In-Game Menu', 'The menu that opens with Y in game, on Fabric installations.'],
  game: ['Game', 'A few things about how the game runs.']
};

function _setupStepHtml(id) {
  const s = SettingsManager.getAll();
  const accent = (s.accentColor || '#5bc8f5').toLowerCase();

  if (id === 'language') {
    // Each language under its own name, so it is never itself translated.
    const current = SettingsManager.language();
    return `
      <div class="setup-langs" data-no-i18n>
        ${I18N.languages.map(l => `
          <button class="setup-lang ${l.code === current ? 'selected' : ''}" lang="${l.code}" onclick="_setupLanguage('${l.code}', this)">
            <span class="setup-lang-name">${l.name}</span>
            <span class="setup-pick-check">${_SETUP_CHECK}</span>
          </button>`).join('')}
      </div>`;
  }

  if (id === 'theme') {
    const layout = ['liquid', 'frost'].includes(s.layoutTheme) ? s.layoutTheme : 'classic';
    return `
      <div class="setup-picks setup-picks-3">
        ${_setupPick('layout', 'classic', layout === 'classic', 'classic.jpg', 'Classic', 'Side bar, your skin in the middle')}
        ${_setupPick('layout', 'liquid', layout === 'liquid', 'liquid.jpg', 'Liquid', 'Bottom bar, big menu buttons')}
        ${_setupPick('layout', 'frost', layout === 'frost', 'frost.jpg', 'Frost', 'Your skin in the scene, cards to scroll')}
      </div>
      <div class="options-toggle-row">
        <div class="options-toggle-card options-accent-card on setup-static">
          <div class="options-toggle-body">
            <div class="options-toggle-name">Accent Colour</div>
            <div class="options-toggle-desc">Buttons and highlights</div>
          </div>
          <div class="options-accent-swatches" id="setup-accent">${_setupSwatches('accent', accent)}</div>
        </div>
        ${_setupToggleCard('panoramaShuffle', s.selectedPanorama === 'shuffle', 'Shuffle Panoramas', 'A different title screen panorama every launch')}
      </div>`;
  }

  if (id === 'background') {
    const bg = s.launcherBackground === 'panorama' ? 'panorama' : 'icey';
    return `
      <div class="setup-picks">
        ${_setupPick('background', 'icey', bg === 'icey', 'bg-snow.jpg', 'Snow', 'The Icey snow scene')}
        ${_setupPick('background', 'panorama', bg === 'panorama', 'bg-panorama.jpg', 'Panoramas', 'Minecraft panoramas that turn slowly, a different one every few minutes')}
      </div>`;
  }

  if (id === 'import') return `<div id="setup-import">${_setupImportHtml()}</div>`;

  if (id === 'account') return `<div id="setup-account">${_setupAccountHtml(_setupSignedInAs)}</div>`;

  if (id === 'menu') {
    const menuStyle = ((x) => ['cards', 'panels'].includes(x) ? x : 'grid')(s.hudMenuGridDefault ? s.hudMenuStyle : 'grid');
    const menuColor = (s.hudMenuColor || accent).toLowerCase();
    return `
      <div class="setup-picks setup-picks-3">
        ${_setupPick('menu', 'grid', menuStyle === 'grid', 'menu-grid.jpg', 'Grid', 'The original buttons, with pages')}
        ${_setupPick('menu', 'panels', menuStyle === 'panels', 'menu-panels.jpg', 'Panels', 'Click to switch on, right-click for settings')}
        ${_setupPick('menu', 'cards', menuStyle === 'cards', 'menu-cards.jpg', 'Cards', 'Glass tiles, with filters on top')}
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
        ${_setupToggleCard('iceyShadersEnabled', s.iceyShadersEnabled !== false, 'Icey Shaders', 'Blocky clouds, soft shadows, wind in the leaves and reflecting water. Needs a Fabric installation; adds Iris.')}
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
  if (_setupImport.busy) return;
  const next = _setupStep + by;
  if (next < 0 || next >= _setupSteps.length) return;
  _setupStep = next;
  _setupRender();
}

// The page is in the new language the moment it is picked: the text on
// screen is translated in place, nothing is drawn again.
async function _setupLanguage(code, el) {
  if (el && el.parentElement) {
    el.parentElement.querySelectorAll('.selected').forEach(n => n.classList.remove('selected'));
    el.classList.add('selected');
  }
  await SettingsManager.setLanguage(code);
}

// One handler for everything that is "pick one of these".
async function _setupChoose(group, value, el) {
  if (el && el.parentElement) {
    el.parentElement.querySelectorAll('.selected').forEach(n => n.classList.remove('selected'));
    el.classList.add('selected');
  }
  if (group === 'layout') {
    await SettingsManager.set('layoutTheme', ['liquid', 'frost'].includes(value) ? value : 'classic');
    if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  } else if (group === 'background') {
    await SettingsManager.set('launcherBackground', value === 'panorama' ? 'panorama' : 'icey');
  } else if (group === 'accent') {
    const followed = !SettingsManager.get('hudMenuColor');
    await SettingsManager.set('accentColor', value);
    // A menu colour that was following the accent keeps following it.
    if (followed) _setupMarkSwatch('setup-menu-color', value);
  } else if (group === 'menu') {
    await SettingsManager.set('hudMenuStyle', ['cards', 'panels'].includes(value) ? value : 'grid');
    await SettingsManager.set('hudMenuGridDefault', true);
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
  if (key === 'panoramaShuffle') {
    // Shuffle is a choice in the panorama picker, not a setting of its own.
    // Turning it off goes back to the panorama picked before.
    const s = SettingsManager.getAll();
    if (input.checked) {
      const was = s.selectedPanorama && s.selectedPanorama !== 'shuffle' ? s.selectedPanorama : (s.panoramaBeforeShuffle || 'Nether Panorama.zip');
      await SettingsManager.setMultiple({ selectedPanorama: 'shuffle', panoramaBeforeShuffle: was });
    } else {
      await SettingsManager.set('selectedPanorama', s.panoramaBeforeShuffle || 'Nether Panorama.zip');
    }
    return;
  }
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
    </div>
    <div class="setup-account-note">${note || (_setupBusy ? 'Waiting for the sign-in to finish…' : 'Or leave this for later: the account menu is in the top bar.')}</div>`;
}

function _setupRenderAccount(username, note) {
  const box = document.getElementById('setup-account');
  if (box) box.innerHTML = _setupAccountHtml(username, note);
}

async function _setupLogin(kind) {
  if (_setupBusy) return;
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

// ── Import from another client ─────────────────────────────────────────
// Mods, servers, settings and accounts of the other Minecraft clients on
// this computer. Each game folder with Fabric mods becomes an installation
// of its own; servers of the others go to the selected installation.

let _setupImport = { scan: null, off: new Set(), accounts: true, busy: false, result: null };

function _setupImportRow(s) {
  const on = !_setupImport.off.has(s.id);
  // One line under the name: where it is from and what it runs, or why
  // only its servers can come along.
  const from = [s.client !== s.name ? s.client : '', s.modsUsable && s.mcVersion ? s.mcVersion : ''].filter(Boolean).map(_setupEsc).join(' · ');
  const note = s.modsNote === 'loader' ? 'These mods are not for Fabric, so only the servers come over'
    : s.modsNote === 'version' ? 'The Minecraft version of these mods is unknown, so only the servers come over' : '';
  const sub = note ? `<span>${note}</span>` : `<span data-no-i18n>${from}</span>`;
  const counts = [];
  if (s.mods && s.modsUsable) counts.push(`<span>Mods: ${s.mods}</span>`);
  if (s.servers) counts.push(`<span>Servers: ${s.servers}</span>`);
  return `
    <label class="setup-imp-row ${on ? 'on' : 'off'}">
      <span class="setup-imp-main">
        <span class="setup-imp-name" data-no-i18n>${_setupEsc(s.name)}</span>
        <span class="setup-imp-sub">${sub}</span>
      </span>
      <span class="setup-imp-counts">${counts.join('')}</span>
      <span class="toggle">
        <input type="checkbox" ${on ? 'checked' : ''} onchange="_setupImportToggle('${s.id}', this)">
        <span class="toggle-slider"></span>
      </span>
    </label>`;
}

function _setupImportHtml() {
  const st = _setupImport;
  if (st.result) return _setupImportResultHtml(st.result);
  if (!st.scan) {
    _setupImportScan();
    return '<div class="setup-account-note">Looking for other Minecraft clients…</div>';
  }
  const { sources, accounts } = st.scan;
  const rows = sources.map(_setupImportRow);
  if (accounts.length) {
    rows.push(`
    <label class="setup-imp-row ${st.accounts ? 'on' : 'off'}">
      <span class="setup-imp-main">
        <span class="setup-imp-name">Accounts</span>
        <span class="setup-imp-sub"><span>From the Minecraft Launcher</span></span>
      </span>
      <span class="setup-imp-counts"><span data-no-i18n>${accounts.map(_setupEsc).join(', ')}</span></span>
      <span class="toggle">
        <input type="checkbox" ${st.accounts ? 'checked' : ''} onchange="_setupImportToggle('', this)">
        <span class="toggle-slider"></span>
      </span>
    </label>`);
  }
  const off = st.busy ? 'disabled' : '';
  return `
    ${rows.length ? `<div class="setup-imp">${rows.join('')}</div>`
      : '<div class="setup-account-note">No other Minecraft client was found on this computer.</div>'}
    <div class="setup-import-actions">
      ${rows.length ? `<button class="options-btn options-btn-primary" onclick="_setupImportRun()" ${off}>Import</button>` : ''}
      <button class="options-btn" onclick="_setupImportPick()" ${off}>Choose a folder…</button>
      <span class="setup-import-progress" id="setup-import-progress"></span>
    </div>`;
}

function _setupImportResultHtml(r) {
  const rows = [];
  const row = (name, sub, counts, bad) => rows.push(`
    <div class="setup-imp-row${bad ? ' bad' : ''}">
      <span class="setup-imp-main">
        <span class="setup-imp-name">${name}</span>
        ${sub ? `<span class="setup-imp-sub">${sub}</span>` : ''}
      </span>
      <span class="setup-imp-counts">${counts.join('')}</span>
    </div>`);
  for (const x of r.results || []) {
    const name = `<span data-no-i18n>${_setupEsc(x.name)}</span>`;
    if (x.error) { row(name, `<span data-no-i18n>${_setupEsc(x.error)}</span>`, [], true); continue; }
    const counts = [];
    if (x.mods) counts.push(`<span>Mods: ${x.mods.copied}</span>`);
    counts.push(`<span>New servers: ${x.servers}</span>`);
    const failed = (x.mods && x.mods.failed) || [];
    const sub = failed.length
      ? `<span>Left out, no version for this Minecraft:</span> <span data-no-i18n>${failed.map(f => _setupEsc(f.name)).join(', ')}</span>`
      : (x.installation ? '<span>New installation</span>' : '');
    row(name, sub, counts, false);
  }
  if (r.accounts && !r.accounts.error) {
    row('<span>Accounts</span>', '', [`<span>${r.accounts.imported ? 'Added: ' + r.accounts.imported : 'Already here'}</span>`], false);
  }
  return `
    <div class="setup-imp">${rows.join('')}</div>
    <div class="setup-account-note">Done. Imported installations are on the Installations page.</div>`;
}

function _setupImportDraw() {
  const box = document.getElementById('setup-import');
  if (box) box.innerHTML = _setupImportHtml();
}

async function _setupImportScan() {
  let scan = { sources: [], accounts: [] };
  try { scan = (await window.icey.importClientsScan()) || scan; } catch (_) {}
  _setupImport.scan = scan;
  _setupImportDraw();
}

function _setupImportToggle(id, input) {
  const card = input.closest('.setup-imp-row');
  if (card) { card.classList.toggle('on', input.checked); card.classList.toggle('off', !input.checked); }
  if (!id) { _setupImport.accounts = !!input.checked; return; }
  if (input.checked) _setupImport.off.delete(id); else _setupImport.off.add(id);
}

async function _setupImportPick() {
  if (_setupImport.busy) return;
  const r = await window.icey.importClientsPickFolder();
  if (!r || r.canceled) return;
  if (r.error) { Toast.info(r.error); return; }
  const list = _setupImport.scan.sources;
  if (!list.some(s => s.id === r.source.id)) list.push(r.source);
  _setupImportDraw();
}

async function _setupImportRun() {
  const st = _setupImport;
  if (st.busy || !st.scan) return;
  const ids = st.scan.sources.filter(s => !st.off.has(s.id)).map(s => s.id);
  const accounts = st.accounts && st.scan.accounts.length > 0;
  if (!ids.length && !accounts) { Toast.info('Nothing is switched on to import'); return; }
  st.busy = true;
  _setupImportDraw();
  const say = (html) => { const el = document.getElementById('setup-import-progress'); if (el) el.innerHTML = html; };
  say('<span>Importing…</span>');
  const stop = window.icey.onImportClientsProgress((p) => {
    if (!p || !p.name) return;
    const what = p.step === 'game' ? '<span>Setting up the game…</span>'
      : p.step === 'mods' ? `<span>Mods: ${p.done || 0} / ${p.of || 0}</span>`
      : p.step === 'settings' ? '<span>Settings and packs…</span>' : '';
    say(`<b data-no-i18n>${_setupEsc(p.name)}</b> ${what}`);
  });
  let r = null;
  try { r = await window.icey.importClientsRun(ids, { accounts }); } catch (e) { r = { error: (e && e.message) || 'Import failed' }; }
  try { stop(); } catch (_) {}
  st.busy = false;
  if (!r || r.error) {
    Toast.error((r && r.error) || 'Import failed');
    _setupImportDraw();
    return;
  }
  st.result = r;
  // Someone who just got an account is not asked to sign in again.
  try {
    const auth = await window.icey.getAuth();
    if (auth && auth.username) {
      await SettingsManager.set('username', auth.username);
      _setupSteps = _setupSteps.filter(x => x !== 'account');
    }
  } catch (_) {}
  Toast.success('Import finished');
  if (typeof loadNavProfile === 'function') loadNavProfile();
  if (typeof HomePageInit === 'function') HomePageInit().catch(() => {});
  _setupRender();
}

// ── Leaving ────────────────────────────────────────────────────────────

async function _setupDone(finished) {
  if (_setupImport.busy) return;
  await SettingsManager.markSetupDone();
  if (finished) Toast.success('All set. Have fun!');
  switchPage('home');
}
