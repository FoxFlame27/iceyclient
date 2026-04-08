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
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
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
      titlebarProfile.innerHTML = `
        <button class="titlebar-login-btn" onclick="_navLogin()">Login</button>
      `;
    }
  }
}

function _updateProfileDropdown(auth) {
  const dropdown = document.getElementById('titlebar-profile-dropdown');
  if (!dropdown || !auth) return;
  dropdown.innerHTML = `
    <div class="titlebar-dropdown-user">
      <img class="titlebar-dropdown-avatar" src="https://mineskin.eu/helm/${auth.username}/36.png" alt="">
      <div class="titlebar-dropdown-info">
        <div class="titlebar-dropdown-name">${auth.username}</div>
        <div class="titlebar-dropdown-label">Microsoft Account</div>
      </div>
    </div>
    <button class="titlebar-dropdown-btn" onclick="switchPage('skins'); _toggleProfileDropdown();">Manage Skin</button>
    <button class="titlebar-dropdown-btn logout" onclick="_navLogout()">Log Out</button>
  `;
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

// Init app
(async () => {
  await SettingsManager.load();
  loadNavProfile();
  switchPage('home');
})();
