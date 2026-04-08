async function SkinsPageInit() {
  const page = document.getElementById('page-skins');
  const auth = await window.icey.getAuth();

  if (!auth || !auth.username) {
    page.innerHTML = `
      <div class="skins-guard">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
          <circle cx="12" cy="7" r="4"/>
        </svg>
        <div class="skins-guard-title">Login Required</div>
        <div class="skins-guard-subtitle">Log in with Microsoft to manage your Minecraft skin.</div>
        <button class="btn-skins-login" onclick="_skinsLogin()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="8" height="8" rx="1"/><rect x="13" y="3" width="8" height="8" rx="1"/><rect x="3" y="13" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/></svg>
          Login with Microsoft
        </button>
      </div>
    `;
    return;
  }

  const username = auth.username;

  page.innerHTML = `
    <div class="skins-layout">
      <div class="skins-preview">
        <div class="skins-preview-header">Your Skin</div>
        <div class="skins-preview-card" id="skins-preview-card">
          <img class="skins-preview-body" id="skins-body-img" src="https://mineskin.eu/armor/body/${username}/250.png" alt="Skin" onerror="this.src='https://mc-heads.net/body/${username}/250'">
        </div>
        <div class="skins-preview-name">${username}</div>
        <div class="skins-preview-views">
          <button class="skins-view-btn active" onclick="_skinsSwitchView('body', this, '${username}')">Body</button>
          <button class="skins-view-btn" onclick="_skinsSwitchView('bust', this, '${username}')">Bust</button>
          <button class="skins-view-btn" onclick="_skinsSwitchView('head', this, '${username}')">Head</button>
        </div>
      </div>
      <div class="skins-upload">
        <div class="skins-upload-header">Change Skin</div>
        <div class="skins-upload-dropzone" id="skins-dropzone" onclick="_skinsBrowse()">
          <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <div class="skins-dropzone-text">Click to select a skin file</div>
          <div class="skins-dropzone-sub">PNG image, 64x64 pixels</div>
        </div>
        <div class="skins-variant-section">
          <div class="skins-variant-label">Arm Model</div>
          <div class="skins-variant-toggle">
            <button class="skins-variant-opt active" id="variant-classic" onclick="_setVariant('classic')">
              Classic (Steve)
            </button>
            <button class="skins-variant-opt" id="variant-slim" onclick="_setVariant('slim')">
              Slim (Alex)
            </button>
          </div>
        </div>
        <div class="skins-selected-file" id="skins-selected-file" style="display:none;">
          <img class="skins-selected-preview" id="skins-selected-preview" alt="">
          <span class="skins-selected-name" id="skins-selected-name"></span>
          <button class="skins-selected-clear" onclick="_skinsClearFile()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <button class="btn-skins-upload" id="btn-skins-upload" onclick="_skinsUpload()" disabled>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          Upload Skin
        </button>
      </div>
    </div>
  `;

  _skinsVariant = 'classic';
  _skinsFilePath = null;

  // Load profile to detect variant
  _loadSkinProfile(auth);

  // Setup dropzone
  const dropzone = document.getElementById('skins-dropzone');
  if (dropzone) {
    dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('dragover'); });
    dropzone.addEventListener('dragleave', () => { dropzone.classList.remove('dragover'); });
    dropzone.addEventListener('drop', async (e) => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
      const file = e.dataTransfer.files[0];
      if (file && file.name.endsWith('.png')) {
        _skinsSelectFile(file.path, file.name);
      } else {
        Toast.error('Please use a PNG file');
      }
    });
  }
}

let _skinsVariant = 'classic';
let _skinsFilePath = null;

function _skinsSwitchView(view, btn, username) {
  document.querySelectorAll('.skins-view-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  const img = document.getElementById('skins-body-img');
  if (!img) return;
  const urls = {
    body: `https://mineskin.eu/armor/body/${username}/250.png`,
    bust: `https://mineskin.eu/armor/bust/${username}/250.png`,
    head: `https://mineskin.eu/headhelm/${username}/250.png`
  };
  img.src = urls[view] || urls.body;
}

async function _loadSkinProfile(auth) {
  try {
    const profile = await window.icey.getMcProfile();
    if (profile) {
      const activeSkin = profile.skins?.find(s => s.state === 'ACTIVE');
      if (activeSkin?.variant === 'SLIM') {
        _setVariant('slim');
      }
    }
  } catch (_) {}
}

async function _skinsLogin() {
  const result = await window.icey.msLogin();
  if (result.error) {
    Toast.error(result.error);
  } else {
    Toast.success('Logged in as ' + result.username);
    await SettingsManager.set('username', result.username);
    loadNavProfile();
    SkinsPageInit();
  }
}

function _setVariant(v) {
  _skinsVariant = v;
  document.getElementById('variant-classic')?.classList.toggle('active', v === 'classic');
  document.getElementById('variant-slim')?.classList.toggle('active', v === 'slim');
}

async function _skinsBrowse() {
  const filePath = await window.icey.selectFile([
    { name: 'Skin Image', extensions: ['png'] }
  ]);
  if (!filePath) return;
  const filename = filePath.split(/[/\\]/).pop();
  _skinsSelectFile(filePath, filename);
}

function _skinsSelectFile(filePath, filename) {
  _skinsFilePath = filePath;
  const fileSection = document.getElementById('skins-selected-file');
  const nameEl = document.getElementById('skins-selected-name');
  const previewEl = document.getElementById('skins-selected-preview');
  const uploadBtn = document.getElementById('btn-skins-upload');
  if (fileSection) fileSection.style.display = 'flex';
  if (nameEl) nameEl.textContent = filename;
  if (previewEl) previewEl.src = 'file://' + filePath.replace(/\\/g, '/');
  if (uploadBtn) uploadBtn.disabled = false;
}

function _skinsClearFile() {
  _skinsFilePath = null;
  const fileSection = document.getElementById('skins-selected-file');
  const uploadBtn = document.getElementById('btn-skins-upload');
  if (fileSection) fileSection.style.display = 'none';
  if (uploadBtn) uploadBtn.disabled = true;
}

async function _skinsUpload() {
  if (!_skinsFilePath) return;
  const btn = document.getElementById('btn-skins-upload');
  if (btn) { btn.disabled = true; btn.textContent = 'Uploading...'; }

  const result = await window.icey.uploadSkin(_skinsFilePath, _skinsVariant);
  if (result.error) {
    Toast.error('Upload failed: ' + result.error);
  } else {
    Toast.success('Skin updated!');
    // Refresh preview
    const bodyImg = document.getElementById('skins-body-img');
    if (bodyImg) {
      const auth = await window.icey.getAuth();
      bodyImg.src = `https://mineskin.eu/armor/body/${auth.username}/250.png?t=${Date.now()}`;
    }
    _skinsClearFile();
    loadNavProfile();
  }
  if (btn) {
    btn.disabled = !_skinsFilePath;
    btn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg> Upload Skin`;
  }
}
