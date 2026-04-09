let _skinsVariant = 'classic';
let _skinsFilePath = null;
let _skinsLookupName = '';

async function SkinsPageInit() {
  const page = document.getElementById('page-skins');
  const auth = await window.icey.getAuth();
  const loggedIn = auth && auth.username;
  const displayName = loggedIn ? auth.username : '';

  page.innerHTML = `
    <div class="skins-layout">
      <!-- Left: Search & Preview -->
      <div class="skins-preview">
        <div class="skins-preview-header">Skin Lookup</div>
        <div class="skins-search-bar">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input class="skins-search-input" type="text" id="skins-search" placeholder="Enter username..." value="${displayName}" spellcheck="false" maxlength="16" onkeydown="if(event.key==='Enter') _skinsLookup()">
          <button class="skins-search-btn" onclick="_skinsLookup()">Search</button>
        </div>
        <div class="skins-preview-card" id="skins-preview-card">
          ${displayName
            ? `<img class="skins-preview-body" id="skins-body-img" src="https://mineskin.eu/armor/body/${displayName}/250.png" alt="Skin">`
            : '<div class="skins-preview-empty">Search for a player to see their skin</div>'}
        </div>
        <div class="skins-preview-name" id="skins-preview-name">${displayName}</div>
        <div class="skins-preview-views" id="skins-preview-views" ${displayName ? '' : 'style="display:none"'}>
          <button class="skins-view-btn active" onclick="_skinsSwitchView('body', this)">Body</button>
          <button class="skins-view-btn" onclick="_skinsSwitchView('bust', this)">Bust</button>
          <button class="skins-view-btn" onclick="_skinsSwitchView('head', this)">Head</button>
        </div>
        ${loggedIn ? `<button class="btn-skins-use" id="btn-skins-use" onclick="_skinsUseSkin()" style="display:none">Use This Skin</button>` : ''}
      </div>

      <!-- Right: Upload your own -->
      <div class="skins-upload">
        <div class="skins-upload-header">Upload Skin</div>
        ${loggedIn ? `
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
            <button class="skins-variant-opt active" id="variant-classic" onclick="_setVariant('classic')">Classic</button>
            <button class="skins-variant-opt" id="variant-slim" onclick="_setVariant('slim')">Slim</button>
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
        ` : `
        <div class="skins-guard-mini">
          <div class="skins-guard-subtitle">Log in with Microsoft to upload and change your skin.</div>
          <button class="btn-skins-login" onclick="_skinsLogin()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="8" height="8" rx="1"/><rect x="13" y="3" width="8" height="8" rx="1"/><rect x="3" y="13" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/></svg>
            Login with Microsoft
          </button>
        </div>
        `}
      </div>
    </div>
  `;

  _skinsVariant = 'classic';
  _skinsFilePath = null;
  _skinsLookupName = displayName;

  if (loggedIn) {
    _loadSkinProfile(auth);
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
}

function _skinsLookup() {
  const input = document.getElementById('skins-search');
  const name = input?.value.trim();
  if (!name) { Toast.error('Enter a username'); return; }

  _skinsLookupName = name;
  const card = document.getElementById('skins-preview-card');
  const nameEl = document.getElementById('skins-preview-name');
  const views = document.getElementById('skins-preview-views');
  const useBtn = document.getElementById('btn-skins-use');

  if (card) card.innerHTML = `<img class="skins-preview-body" id="skins-body-img" src="https://mineskin.eu/armor/body/${name}/250.png" alt="Skin" onerror="_skinsLookupError()">`;
  if (nameEl) nameEl.textContent = name;
  if (views) views.style.display = 'flex';

  // Show "Use This Skin" button if looking up someone else's skin and logged in
  if (useBtn) {
    window.icey.getAuth().then(auth => {
      if (auth && auth.username && auth.username.toLowerCase() !== name.toLowerCase()) {
        useBtn.style.display = 'block';
      } else {
        useBtn.style.display = 'none';
      }
    });
  }

  // Reset view buttons
  document.querySelectorAll('.skins-view-btn').forEach((b, i) => b.classList.toggle('active', i === 0));
}

function _skinsLookupError() {
  Toast.error('Could not find skin for that player');
}

function _skinsSwitchView(view, btn) {
  document.querySelectorAll('.skins-view-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  const img = document.getElementById('skins-body-img');
  if (!img || !_skinsLookupName) return;
  const name = _skinsLookupName;
  const urls = {
    body: `https://mineskin.eu/armor/body/${name}/250.png`,
    bust: `https://mineskin.eu/armor/bust/${name}/250.png`,
    head: `https://mineskin.eu/headhelm/${name}/250.png`
  };
  img.src = urls[view] || urls.body;
}

async function _skinsUseSkin() {
  const name = _skinsLookupName;
  if (!name) return;

  showModal(`
    <div class="modal-header">
      <h2 class="modal-title">Change Skin</h2>
      <button class="modal-close" onclick="closeModal()">
        <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
    </div>
    <div class="modal-body">
      <p>This will change your Minecraft skin to <strong>${name}</strong>'s skin.</p>
      <p style="color:var(--text-muted);font-size:12px;margin-top:8px;">Warning: This will overwrite your current skin. Make sure you want to use this skin before continuing.</p>
    </div>
    <div class="modal-footer">
      <button class="modal-btn modal-btn-outline" onclick="closeModal()">Cancel</button>
      <button class="modal-btn modal-btn-primary" onclick="_skinsConfirmUse('${name}')">Use Skin</button>
    </div>
  `);
}

async function _skinsConfirmUse(name) {
  closeModal();
  Toast.info('Changing skin...');

  try {
    const result = await window.icey.uploadSkinFromUrl(name, _skinsVariant);
    if (result && result.error) {
      Toast.error('Failed to apply skin: ' + result.error);
    } else {
      Toast.success('Skin changed to ' + name + "'s skin!");
      loadNavProfile();
    }
  } catch (e) {
    Toast.error('Failed to apply skin');
  }
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
