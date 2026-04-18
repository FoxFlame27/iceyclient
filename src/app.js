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
        <img class="nav-profile-avatar" src="https://mineskin.eu/helm/${auth.username}/40.png" alt="${auth.username}" title="${auth.username}" onclick="switchPage('skins')">
        <div class="nav-profile-name">${auth.username}</div>
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

  let accountsData = { activeUuid: null, accounts: [] };
  try { accountsData = await window.icey.getAccounts(); } catch (_) {}
  const others = (accountsData.accounts || []).filter(a => a.uuid !== accountsData.activeUuid);

  const otherAccountsHtml = others.map(a => `
    <div class="titlebar-account-row">
      <button class="titlebar-dropdown-btn titlebar-account-switch" onclick="_switchAccount('${_escapeAttr(a.uuid)}')">
        <img class="titlebar-account-avatar" src="https://mineskin.eu/helm/${_escapeAttr(a.username)}/22.png" alt="">
        <span class="titlebar-account-name">${_escapeHtml(a.username)}</span>
        ${a.expired ? '<span class="titlebar-account-badge">expired</span>' : ''}
      </button>
      <button class="titlebar-account-remove" title="Remove" onclick="event.stopPropagation(); _removeAccount('${_escapeAttr(a.uuid)}')">&times;</button>
    </div>
  `).join('');

  dropdown.innerHTML = `
    <div class="titlebar-dropdown-user">
      <img class="titlebar-dropdown-avatar" src="https://mineskin.eu/helm/${_escapeAttr(auth.username)}/36.png" alt="">
      <div class="titlebar-dropdown-info">
        <div class="titlebar-dropdown-name">${_escapeHtml(auth.username)}</div>
        <div class="titlebar-dropdown-label">Active Account</div>
      </div>
    </div>
    ${others.length > 0 ? `<div class="titlebar-dropdown-divider"></div><div class="titlebar-accounts-list">${otherAccountsHtml}</div>` : ''}
    <div class="titlebar-dropdown-divider"></div>
    <button class="titlebar-dropdown-btn" onclick="_addAccount()">+ Add Account</button>
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
  const head = document.querySelector('.titlebar-profile-head');
  if (dropdown && !dropdown.contains(e.target) && e.target !== head) {
    dropdown.classList.add('hidden');
  }
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
