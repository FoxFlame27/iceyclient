// Titlebar controls
document.getElementById('btn-minimize').addEventListener('click', () => window.icey.minimizeWindow());
document.getElementById('btn-maximize').addEventListener('click', () => window.icey.maximizeWindow());
document.getElementById('btn-close').addEventListener('click', () => window.icey.closeWindow());

// Tab navigation
const navTabs = document.querySelectorAll('.nav-tab');
const pages = document.querySelectorAll('.page');

function switchPage(pageName) {
  pages.forEach(p => p.classList.remove('active'));
  navTabs.forEach(t => t.classList.remove('active'));
  const page = document.getElementById('page-' + pageName);
  const tab = document.querySelector(`.nav-tab[data-page="${pageName}"]`);
  if (page) page.classList.add('active');
  if (tab) tab.classList.add('active');
  // Trigger page init
  if (pageName === 'home' && typeof HomePageInit === 'function') HomePageInit();
  if (pageName === 'installations' && typeof InstallationsPageInit === 'function') InstallationsPageInit();
  if (pageName === 'mods' && typeof ModsPageInit === 'function') ModsPageInit();
  if (pageName === 'skins' && typeof SkinsPageInit === 'function') SkinsPageInit();
  if (pageName === 'console' && typeof ConsolePageInit === 'function') ConsolePageInit();
  if (pageName === 'character' && typeof CharacterPageInit === 'function') CharacterPageInit();
  if (pageName === 'friends' && typeof FriendsPageInit === 'function') FriendsPageInit();
  if (pageName === 'options' && typeof OptionsPageInit === 'function') OptionsPageInit();
  if (pageName === 'setup' && typeof SetupPageInit === 'function') SetupPageInit();
}

navTabs.forEach(tab => {
  tab.addEventListener('click', () => switchPage(tab.dataset.page));
});

// Modal management
const modalOverlay = document.getElementById('modal-overlay');
const modalContent = document.getElementById('modal-content');

function showModal(html) {
  modalContent.innerHTML = html;
  modalOverlay.classList.remove('hidden');
  requestAnimationFrame(() => modalOverlay.classList.add('visible'));
}

function closeModal() {
  modalOverlay.classList.remove('visible');
  setTimeout(() => {
    modalOverlay.classList.add('hidden');
    modalContent.innerHTML = '';
  }, 200);
}

modalOverlay.addEventListener('click', (e) => {
  if (e.target === modalOverlay) closeModal();
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') closeModal();
});

// Profile avatar in sidebar + titlebar
// The account may just have changed: the friends list belongs to the account.
function _socialAccountChanged() { if (typeof Social !== 'undefined' && Social._timer) Social.sync(); }

// ── Own accounts show the face cut from their real skin ──
// Pictures all over the launcher ask an outside service for a face by name;
// for a saved account the main process has the actual skin, so any such
// picture of one of our own accounts is swapped for it. A watcher catches
// pictures that pages add later.
let _ownFaces = {};
function _swapOwnFaces(root) {
  (root || document).querySelectorAll('img[src*="nmsr.nickac.dev/face/"], img[src*="nmsr.nickac.dev/bust/"]').forEach((img) => {
    const m = /nmsr\.nickac\.dev\/(?:face|bust)\/([^?/]+)/.exec(img.getAttribute('src') || '');
    const face = m && _ownFaces[decodeURIComponent(m[1]).toLowerCase()];
    if (face) { img.style.imageRendering = 'pixelated'; img.src = face; }
  });
}
async function _loadOwnFaces() {
  try { _ownFaces = (await window.icey.getAccountFaces()) || {}; } catch (_) { _ownFaces = {}; }
  _swapOwnFaces();
}
(function _watchOwnFaces() {
  let pending = false;
  const run = () => { pending = false; if (Object.keys(_ownFaces).length) _swapOwnFaces(); };
  const start = () => new MutationObserver(() => { if (!pending) { pending = true; setTimeout(run, 60); } })
    .observe(document.body, { childList: true, subtree: true });
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start);
})();

async function loadNavProfile() {
  _loadOwnFaces();
  _socialAccountChanged();
  const container = document.getElementById('nav-profile');
  const titlebarProfile = document.getElementById('titlebar-profile');
  const auth = await window.icey.getAuth();

  // The picture beside "ICEY CLIENT" is the signed-in player (head and
  // shoulders, outer skin layer included); the mascot when nobody is
  // signed in or the picture can't be fetched. Skiflame keeps its own logo.
  const brand = document.querySelector('.titlebar-logo');
  if (brand && document.documentElement.getAttribute('data-skin') !== 'skiflame') {
    brand.style.display = '';
    brand.onerror = () => { brand.onerror = null; brand.src = 'assets/icon.png'; };
    brand.src = auth && auth.username
      ? 'https://nmsr.nickac.dev/bust/' + encodeURIComponent(auth.username) + '?overlay=true'
      : 'assets/icon.png';
  }

  // Pull saved accounts so we can show the dropdown even when no account
  // is currently active — otherwise a user with 5 expired accounts can't
  // reach the remove buttons because getAuth returns null and the UI
  // collapses to a Login-only state.
  let accountsData = { accounts: [], maxAccounts: 5 };
  try { accountsData = await window.icey.getAccounts(); } catch (_) {}
  const hasSavedAccounts = (accountsData.accounts || []).length > 0;

  if (container) {
    if (auth && auth.username) {
      container.innerHTML = `
        <img class="nav-profile-avatar" src="https://nmsr.nickac.dev/face/${auth.username}?overlay=true" alt="${auth.username}" title="Click to switch account" onclick="_toggleProfileDropdown()">
        <div class="nav-profile-name" onclick="_toggleProfileDropdown()" style="cursor:pointer">${auth.username}</div>
      `;
    } else if (hasSavedAccounts) {
      container.innerHTML = `
        <div class="nav-profile-login" onclick="_toggleProfileDropdown()" title="Manage accounts">
          <img src="assets/skins-icon.png" alt="Accounts" style="width:22px;height:22px;filter:invert(1);opacity:0.6;">
        </div>
        <div class="nav-profile-name" style="color:var(--accent);cursor:pointer" onclick="_toggleProfileDropdown()">Accounts</div>
      `;
    } else {
      container.innerHTML = `
        <div class="nav-profile-login" onclick="_navLogin()" title="Login with Microsoft">
          <img src="assets/skins-icon.png" alt="Login" style="width:22px;height:22px;filter:invert(1);opacity:0.6;">
        </div>
        <div class="nav-profile-name" style="color:var(--accent);cursor:pointer" onclick="_navLogin()">Login</div>
      `;
    }
  }

  // Account chip in the top bar: head, name, arrow. One click target.
  const caret = '<svg class="titlebar-chip-caret" viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>';
  if (titlebarProfile) {
    if (auth && auth.username) {
      titlebarProfile.innerHTML = `
        <button class="titlebar-chip" onclick="_toggleProfileDropdown()" title="Accounts">
          <img class="titlebar-profile-head" src="https://nmsr.nickac.dev/face/${_escapeAttr(auth.username)}?overlay=true" alt="">
          <span class="titlebar-chip-name">${_escapeHtml(auth.username)}</span>
          ${caret}
        </button>
      `;
      _updateProfileDropdown(auth);
    } else if (hasSavedAccounts) {
      // Generic Steve head — clickable so user can reach the dropdown to
      // switch to / remove a saved account.
      titlebarProfile.innerHTML = `
        <button class="titlebar-chip" onclick="_toggleProfileDropdown()" title="Manage accounts">
          <img class="titlebar-profile-head" src="https://nmsr.nickac.dev/face/MHF_Steve?overlay=true" alt="">
          <span class="titlebar-chip-name">Accounts</span>
          ${caret}
        </button>
      `;
      _updateProfileDropdown(null);
    } else {
      titlebarProfile.innerHTML = `
        <button class="titlebar-chip titlebar-chip-signin" onclick="_toggleProfileDropdown()" title="Sign in">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"/></svg>
          <span class="titlebar-chip-name">Sign in</span>
          ${caret}
        </button>
      `;
      _updateProfileDropdown(null);
    }
  }
}

async function _updateProfileDropdown(auth) {
  const dropdown = document.getElementById('titlebar-profile-dropdown');
  if (!dropdown) return;

  let accountsData = { activeUuid: null, accounts: [], maxAccounts: 5 };
  try { accountsData = await window.icey.getAccounts(); } catch (_) {}
  const allAccounts = accountsData.accounts || [];
  // When auth is null (every saved account is expired), show *all* saved
  // accounts in the list so the user can remove one to free up a slot.
  const others = auth
    ? allAccounts.filter(a => a.uuid !== accountsData.activeUuid)
    : allAccounts;
  const activeFull = auth
    ? (allAccounts.find(a => a.uuid === accountsData.activeUuid) || { type: 'microsoft' })
    : null;
  const atMax = allAccounts.length >= (accountsData.maxAccounts || 5);

  // One plain list: who is signed in, the other saved accounts, then the
  // few things you can do. No boxes around every line, no type badges
  // (every account is a Microsoft one since cracked accounts were removed).
  const otherAccountsHtml = others.map(a => `
    <div class="acctm-row">
      <button class="acctm-pick" onclick="_switchAccount('${_escapeAttr(a.uuid)}')">
        <img class="acctm-face" src="https://nmsr.nickac.dev/face/${_escapeAttr(a.username)}?overlay=true" alt="">
        <span class="acctm-name">${_escapeHtml(a.username)}</span>
        ${a.expired ? '<span class="acctm-expired">expired</span>' : ''}
      </button>
      <button class="acctm-x" title="Remove" onclick="event.stopPropagation(); _removeAccount('${_escapeAttr(a.uuid)}')">&times;</button>
    </div>
  `).join('');

  const addButtons = atMax
    ? `<div class="acctm-note">Max ${accountsData.maxAccounts} accounts. Remove one to add another.</div>`
    : `
        <button class="acctm-link" onclick="_addAccount()">Add Microsoft account</button>
        <button class="acctm-link" onclick="_importLauncherAccounts()">Use Minecraft Launcher account</button>
      `;

  const headerHtml = auth ? `
    <div class="acctm-head">
      <img class="acctm-face acctm-face-big" src="https://nmsr.nickac.dev/face/${_escapeAttr(auth.username)}?overlay=true" alt="">
      <div class="acctm-head-text">
        <div class="acctm-head-name">${_escapeHtml(auth.username)}</div>
        <div class="acctm-head-label">Active Account</div>
      </div>
    </div>
  ` : `
    <div class="acctm-head">
      <div class="acctm-head-text">
        <div class="acctm-head-name">${others.length ? 'No active account' : 'Not signed in'}</div>
        <div class="acctm-head-label">${others.length ? others.length + ' saved · pick one or remove to free a slot' : 'Add an account to play online'}</div>
      </div>
    </div>
  `;

  const footerHtml = auth ? `
    <div class="acctm-sep"></div>
    <button class="acctm-link acctm-out" onclick="_navLogout()">Log Out of ${_escapeHtml(auth.username)}</button>
  ` : '';

  dropdown.innerHTML = `
    ${headerHtml}
    ${others.length > 0 ? `<div class="acctm-sep"></div><div class="acctm-list">${otherAccountsHtml}</div>` : ''}
    <div class="acctm-sep"></div>
    ${addButtons}
    ${footerHtml}
  `;
}

function _escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]); }
function _escapeAttr(s) { return String(s).replace(/"/g, '&quot;'); }

async function _addAccount() {
  _toggleProfileDropdown();
  const result = await window.icey.msLogin();
  if (result.error) {
    if (result.removed && result.removed.length) {
      Toast.info('Login cancelled — removed expired account ' + result.removed.join(', '));
      loadNavProfile();
    } else Toast.error(result.error);
  } else {
    Toast.success('Added ' + result.username);
    await SettingsManager.set('username', result.username);
    loadNavProfile();
  }
}

async function _importLauncherAccounts() {
  _toggleProfileDropdown();
  const r = await window.icey.importLauncherAccounts();
  if (r.error) { Toast.error(r.error); return; }
  if (!r.found) { Toast.info('No account found in the Minecraft Launcher. Sign in there once, then try again.'); return; }
  if (r.imported || r.activated) Toast.success('Using your Minecraft Launcher account');
  else Toast.info('Minecraft Launcher account is already in the list');
  loadNavProfile();
}

async function _switchAccount(uuid) {
  const result = await window.icey.switchAccount(uuid);
  if (result.error) { Toast.error(result.error); return; }
  Toast.success('Switched to ' + result.active.username);
  await SettingsManager.set('username', result.active.username);
  _toggleProfileDropdown();
  loadNavProfile();
}

async function _removeAccount(uuid) {
  const result = await window.icey.removeAccount(uuid);
  if (result.error) { Toast.error(result.error); return; }
  Toast.info('Account removed');
  loadNavProfile();
}

function _toggleProfileDropdown() {
  const dropdown = document.getElementById('titlebar-profile-dropdown');
  if (dropdown) dropdown.classList.toggle('hidden');
}

// Close dropdown when clicking outside
document.addEventListener('click', (e) => {
  const dropdown = document.getElementById('titlebar-profile-dropdown');
  if (!dropdown) return;
  const navAvatar = document.querySelector('.nav-profile-avatar');
  const navName = document.querySelector('#nav-profile .nav-profile-name');
  const target = e.target;
  if (dropdown.contains(target)) return;
  if (target.closest && target.closest('#titlebar-profile')) return;
  if (target === navAvatar || target === navName) return;
  dropdown.classList.add('hidden');
});

async function _navLogin() {
  const result = await window.icey.msLogin();
  if (result.error) {
    if (result.removed && result.removed.length) {
      Toast.info('Login cancelled — removed expired account ' + result.removed.join(', '));
      loadNavProfile();
    } else Toast.error(result.error);
  } else {
    Toast.success('Logged in as ' + result.username);
    // Update settings username
    await SettingsManager.set('username', result.username);
    loadNavProfile();
  }
}

async function _navLogout() {
  await window.icey.msLogout();
  Toast.info('Logged out');
  loadNavProfile();
}

// Updates install themselves: the chip next to the version says what is
// going on ("Updating Icey Client 43%"), turns into "Restart to update"
// when the new version is downloaded, and quitting the launcher installs
// it as well. Only where the app cannot replace itself (Linux) is it still
// a note with the version number.
let _updateNoted = null;
function _updateDraw(st) {
  const chip = document.getElementById('titlebar-update');
  if (!chip || !st) return;
  const set = (label, extra, clickable) => {
    chip.hidden = false;
    chip.disabled = !clickable;
    chip.classList.toggle('ready', !!clickable);
    chip.innerHTML = `<span>${label}</span>` + (extra ? `<span data-no-i18n>${extra}</span>` : '');
  };
  if (st.state === 'downloading') set('Updating Icey Client', (st.percent || 0) + '%', false);
  else if (st.state === 'ready') set('Restart to update', '', true);
  else if (st.state === 'installing') set('Updating Icey Client…', '', false);
  else {
    chip.hidden = true;
    if (st.state === 'manual' && st.latest && _updateNoted !== st.latest) {
      _updateNoted = st.latest;
      Toast.info('Update available: v' + st.latest + ' (current: v' + st.current + ')', 8000);
    }
  }
}
async function _updateClick() {
  const r = await window.icey.updateInstall();
  if (r && r.error) Toast.info(r.error);
}
function _checkForUpdates() {
  const chip = document.getElementById('titlebar-update');
  if (chip) chip.onclick = _updateClick;
  try { window.icey.onUpdateState(_updateDraw); } catch (_) {}
  window.icey.updateState().then(_updateDraw).catch(() => {});
}

// While a game is running and the launcher is not the window in use, the
// launcher stops drawing: its 3D skin, the falling snow and the drifting
// background all cost the graphics card frames the game could have, most
// of all on a laptop's built-in one. Click the launcher and it moves again.
let _gamesUp = 0;
function _applyDrawPause() {
  const on = _gamesUp > 0 && !document.hasFocus();
  if (document.documentElement.classList.contains('game-running') === on) return;
  document.documentElement.classList.toggle('game-running', on);
  const sized = (v) => !!(v && v.canvas && v.canvas.clientWidth > 0 && v.canvas.clientHeight > 0);
  for (const v of [typeof _hqViewer !== 'undefined' ? _hqViewer : null, typeof _hgViewer !== 'undefined' ? _hgViewer : null,
    typeof _chrViewer !== 'undefined' ? _chrViewer : null]) {
    try { if (v) v.renderPaused = on || !sized(v); } catch (_) {}
  }
}
async function _refreshGamesUp() {
  try { _gamesUp = ((await window.icey.getRunningMc()) || []).length; } catch (_) { _gamesUp = 0; }
  _applyDrawPause();
}
window.addEventListener('blur', _applyDrawPause);
window.addEventListener('focus', _applyDrawPause);
try {
  window.icey.onMcEvent((e) => {
    if (e && /^mc-(started|stopped|crashed|error)$/.test(e.type || '')) setTimeout(_refreshGamesUp, 300);
  });
} catch (_) {}

// Init app
(async () => {
  await SettingsManager.load();
  loadNavProfile();
  // The first launch after every install opens on the Setup tab.
  switchPage(SettingsManager.isSetupDue() ? 'setup' : 'home');
  window.icey.getAppVersion().then(v => {
    const el = document.getElementById('titlebar-version');
    if (el && v) el.textContent = 'v' + v;
  }).catch(() => {});
  setTimeout(_checkForUpdates, 2000);
  _refreshGamesUp();
  // Friends: start asking who is online (notices show on every page).
  setTimeout(() => { if (typeof Social !== 'undefined') Social.start(); }, 1500);
})();
