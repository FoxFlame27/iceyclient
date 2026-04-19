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
  if (pageName === 'options' && typeof OptionsPageInit === 'function') OptionsPageInit();
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
async function loadNavProfile() {
  const container = document.getElementById('nav-profile');
  const titlebarProfile = document.getElementById('titlebar-profile');
  const auth = await window.icey.getAuth();

  if (container) {
    if (auth && auth.username) {
      container.innerHTML = `
        <img class="nav-profile-avatar" src="https://mineskin.eu/helm/${auth.username}/40.png" alt="${auth.username}" title="Click to switch account" onclick="_toggleProfileDropdown()">
        <div class="nav-profile-name" onclick="_toggleProfileDropdown()" style="cursor:pointer">${auth.username}</div>
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

  if (titlebarProfile) {
    if (auth && auth.username) {
      titlebarProfile.innerHTML = `
        <img class="titlebar-profile-head" src="https://mineskin.eu/helm/${auth.username}/24.png" alt="${auth.username}" onclick="_toggleProfileDropdown()">
      `;
      _updateProfileDropdown(auth);
    } else {
      titlebarProfile.innerHTML = '';
    }
  }
}

async function _updateProfileDropdown(auth) {
  const dropdown = document.getElementById('titlebar-profile-dropdown');
  if (!dropdown || !auth) return;

  let accountsData = { activeUuid: null, accounts: [], maxAccounts: 5 };
  try { accountsData = await window.icey.getAccounts(); } catch (_) {}
  const others = (accountsData.accounts || []).filter(a => a.uuid !== accountsData.activeUuid);
  const activeFull = (accountsData.accounts || []).find(a => a.uuid === accountsData.activeUuid) || { type: 'microsoft' };
  const atMax = (accountsData.accounts?.length || 0) >= (accountsData.maxAccounts || 5);

  const typeBadge = (type) => type === 'offline'
    ? '<span class="titlebar-account-type offline">Cracked</span>'
    : '<span class="titlebar-account-type ms">MS</span>';

  const otherAccountsHtml = others.map(a => `
    <div class="titlebar-account-row">
      <button class="titlebar-dropdown-btn titlebar-account-switch" onclick="_switchAccount('${_escapeAttr(a.uuid)}')">
        <img class="titlebar-account-avatar" src="https://mineskin.eu/helm/${_escapeAttr(a.username)}/22.png" alt="">
        <span class="titlebar-account-name">${_escapeHtml(a.username)}</span>
        ${typeBadge(a.type)}
        ${a.expired ? '<span class="titlebar-account-badge">expired</span>' : ''}
      </button>
      <button class="titlebar-account-remove" title="Remove" onclick="event.stopPropagation(); _removeAccount('${_escapeAttr(a.uuid)}')">&times;</button>
    </div>
  `).join('');

  const addButtons = atMax
    ? `<div class="titlebar-maxed">Max ${accountsData.maxAccounts} accounts. Remove one to add another.</div>`
    : `
        <button class="titlebar-dropdown-btn" onclick="_addAccount()">+ Add Microsoft Account</button>
        <button class="titlebar-dropdown-btn" onclick="_promptAddOffline()">+ Add Cracked Account</button>
      `;

  dropdown.innerHTML = `
    <div class="titlebar-dropdown-user">
      <img class="titlebar-dropdown-avatar" src="https://mineskin.eu/helm/${_escapeAttr(auth.username)}/36.png" alt="">
      <div class="titlebar-dropdown-info">
        <div class="titlebar-dropdown-name">${_escapeHtml(auth.username)} ${typeBadge(activeFull.type)}</div>
        <div class="titlebar-dropdown-label">Active Account</div>
      </div>
    </div>
    ${others.length > 0 ? `<div class="titlebar-dropdown-divider"></div><div class="titlebar-accounts-list">${otherAccountsHtml}</div>` : ''}
    <div class="titlebar-dropdown-divider"></div>
    ${addButtons}
    <button class="titlebar-dropdown-btn" onclick="switchPage('skins'); _toggleProfileDropdown();">Manage Skin</button>
    <button class="titlebar-dropdown-btn logout" onclick="_navLogout()">Log Out of ${_escapeHtml(auth.username)}</button>
  `;
}

function _escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]); }
function _escapeAttr(s) { return String(s).replace(/"/g, '&quot;'); }

async function _addAccount() {
  _toggleProfileDropdown();
  const result = await window.icey.msLogin();
  if (result.error) {
    Toast.error(result.error);
  } else {
    Toast.success('Added ' + result.username);
    await SettingsManager.set('username', result.username);
    loadNavProfile();
  }
}

function _promptAddOffline() {
  _toggleProfileDropdown();
  showModal(`
    <div class="create-install-modal">
      <div class="create-modal-header">
        <button class="modal-close" onclick="closeModal()">&times;</button>
        <div class="create-modal-title">Add Cracked Account</div>
        <div class="create-modal-subtitle">Offline-mode username. Only works on cracked servers.</div>
      </div>
      <div class="create-modal-form">
        <div class="form-group">
          <label class="form-label">Username</label>
          <input class="form-input" id="offline-username-input" placeholder="Steve" maxlength="16" autofocus>
        </div>
        <button class="btn-create-submit" onclick="_submitOfflineAccount()">Add Account</button>
      </div>
    </div>
  `);
  setTimeout(() => {
    const input = document.getElementById('offline-username-input');
    if (input) input.focus();
  }, 50);
}

async function _submitOfflineAccount() {
  const input = document.getElementById('offline-username-input');
  const name = input ? input.value.trim() : '';
  if (!name) { Toast.error('Enter a username'); return; }
  const result = await window.icey.addOfflineAccount(name);
  if (result.error) { Toast.error(result.error); return; }
  closeModal();
  Toast.success('Added ' + name + ' (cracked)');
  await SettingsManager.set('username', name);
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
  const titlebarHead = document.querySelector('.titlebar-profile-head');
  const navAvatar = document.querySelector('.nav-profile-avatar');
  const navName = document.querySelector('#nav-profile .nav-profile-name');
  const target = e.target;
  if (dropdown.contains(target)) return;
  if (target === titlebarHead || target === navAvatar || target === navName) return;
  dropdown.classList.add('hidden');
});

async function _navLogin() {
  const result = await window.icey.msLogin();
  if (result.error) {
    Toast.error(result.error);
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

// Check for updates on launch
async function _checkForUpdates() {
  try {
    const result = await window.icey.checkUpdate();
    if (result && result.updateAvailable) {
      Toast.info('Update available: v' + result.latest + ' (current: v' + result.current + ')', 8000);
    }
  } catch (_) { /* silent */ }
}

// Init app
(async () => {
  await SettingsManager.load();
  loadNavProfile();
  switchPage('home');
  setTimeout(_checkForUpdates, 2000);
})();
