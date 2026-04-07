let _installationsInitialized = false;

async function InstallationsPageInit() {
  const page = document.getElementById('page-installations');
  let installations = [];
  try {
    installations = await window.icey.getInstallations();
  } catch (e) {
    console.error('[Installations] Failed to load:', e);
  }

  page.innerHTML = `
    <div class="installations-inner">
      <div class="installations-header">
        <h1 class="installations-title">Installations</h1>
        <button class="btn-create-install" onclick="showCreateInstallModal()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          New Installation
        </button>
      </div>
      <div class="installations-grid" id="installations-grid"></div>
    </div>
  `;

  _renderInstallationCards(installations);
}

function _renderInstallationCards(installations) {
  const grid = document.getElementById('installations-grid');
  if (!grid) return;

  if (installations.length === 0) {
    grid.innerHTML = `
      <div class="installations-empty" style="grid-column: 1 / -1;">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="3" y="3" width="18" height="5" rx="1.5"/><rect x="3" y="10" width="18" height="5" rx="1.5"/><rect x="3" y="17" width="18" height="5" rx="1.5"/>
        </svg>
        <div class="installations-empty-title">No Installations</div>
        <div class="installations-empty-subtitle">Create your first installation to get started.</div>
      </div>
    `;
    return;
  }

  grid.innerHTML = installations.map(inst => {
    const isSelected = inst.selected;
    const imageStyle = inst.image
      ? `background-image: url('file://${inst.image.replace(/\\/g, '/')}')`
      : `background-image: url('assets/installbg.png')`;
    const isFabricActive = inst.platform === 'fabric' && inst.fabricActive;

    return `
      <div class="install-card ${isSelected ? 'selected' : ''}" data-id="${inst.id}" onclick="_selectInstallation('${inst.id}')">
        <div class="install-card-image" style="${imageStyle}">
          <button class="install-card-image-btn" onclick="event.stopPropagation(); _changeInstallImage('${inst.id}')" title="Change image">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/>
            </svg>
          </button>
        </div>
        <div class="install-card-info">
          <div class="install-card-name" title="${inst.name}">${inst.name}</div>
          <span class="install-card-version">${inst.version}</span>
          ${inst.fromMcLauncher ? '<span class="install-card-badge">MC Launcher</span>' : ''}
          <div class="install-card-platform">
            ${inst.platform === 'fabric' ? `
              <div class="fabric-toggle ${isFabricActive ? 'active' : ''}" onclick="event.stopPropagation(); _toggleFabric('${inst.id}')" title="Toggle Fabric">
                <img src="assets/fabric.png" alt="Fabric">
              </div>
            ` : `
              <span class="install-card-platform-text">Vanilla</span>
            `}
          </div>
          <div class="install-card-actions">
            <button class="install-card-action-btn" onclick="event.stopPropagation(); _openInstallFolder('${inst.id}')" title="Open folder">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </button>
            <button class="install-card-action-btn delete" onclick="event.stopPropagation(); _confirmDeleteInstallation('${inst.id}', '${inst.name.replace(/'/g, "\\'")}')" title="Delete">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

async function _selectInstallation(id) {
  const installations = await window.icey.getInstallations();
  installations.forEach(i => i.selected = (i.id === id));
  for (const inst of installations) {
    await window.icey.saveInstallation(inst);
  }
  _renderInstallationCards(installations);
  Toast.success('Installation selected');
}

async function _toggleFabric(id) {
  const installations = await window.icey.getInstallations();
  installations.forEach(i => i.fabricActive = false);
  const target = installations.find(i => i.id === id);
  if (target && target.platform === 'fabric') {
    target.fabricActive = true;
  }
  for (const inst of installations) {
    await window.icey.saveInstallation(inst);
  }
  _renderInstallationCards(installations);
  Toast.success('Fabric activated');
}

async function _changeInstallImage(id) {
  const filePath = await window.icey.selectFile([
    { name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp', 'gif'] }
  ]);
  if (!filePath) return;

  const result = await window.icey.updateInstallationImage(id, filePath);
  if (result.error) {
    Toast.error(result.error);
    return;
  }
  Toast.success('Image updated');
  InstallationsPageInit();
}

async function _openInstallFolder(id) {
  const mcDir = await window.icey.getMcDir();
  window.icey.openFolder(mcDir);
}

function _confirmDeleteInstallation(id, name) {
  showModal(`
    <div class="modal-header">
      <h2 class="modal-title">Delete Installation</h2>
      <button class="modal-close" onclick="closeModal()">
        <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
    </div>
    <div class="modal-body">Are you sure you want to delete '${name}'? This cannot be undone.</div>
    <div class="modal-footer">
      <button class="modal-btn modal-btn-outline" onclick="closeModal()">Cancel</button>
      <button class="modal-btn modal-btn-danger" onclick="_deleteInstallation('${id}')">Delete</button>
    </div>
  `);
}

async function _deleteInstallation(id) {
  await window.icey.deleteInstallation(id);
  closeModal();
  Toast.success('Installation deleted');
  InstallationsPageInit();
}

async function showCreateInstallModal() {
  showModal(`
    <div class="create-install-modal">
      <div class="create-modal-header">
        <div class="create-modal-icon">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="3" width="18" height="18" rx="3"/>
            <line x1="12" y1="8" x2="12" y2="16"/>
            <line x1="8" y1="12" x2="16" y2="12"/>
          </svg>
        </div>
        <h2 class="create-modal-title">New Installation</h2>
        <p class="create-modal-subtitle">Set up a new Minecraft installation</p>
        <button class="modal-close" onclick="closeModal()">
          <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
        </button>
      </div>
      <div class="create-modal-form">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-input" type="text" id="create-name" placeholder="e.g. Survival World" maxlength="40" spellcheck="false">
        </div>
        <div class="form-row">
          <div class="form-group" style="flex:1">
            <label class="form-label">Version</label>
            <select class="form-input form-select" id="create-version" disabled>
              <option>Loading...</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Platform</label>
            <div class="platform-toggle">
              <button class="platform-opt active" id="pill-vanilla" onclick="_setPlatform('vanilla')">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/></svg>
                Vanilla
              </button>
              <button class="platform-opt" id="pill-fabric" onclick="_setPlatform('fabric')">
                <img src="assets/fabric.png" alt="" width="14" height="14">
                Fabric
              </button>
            </div>
          </div>
        </div>
        <div class="fabric-notice" id="fabric-info" style="display:none;">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          Fabric loader will be installed automatically. Java is required.
        </div>
        <div id="create-progress" style="display:none;">
          <div class="create-progress-track"><div class="create-progress-fill" id="create-progress-bar"></div></div>
          <div class="create-progress-text" id="create-progress-text">Preparing...</div>
        </div>
        <button class="btn-create-submit" id="btn-create-submit" onclick="_submitCreateInstallation()">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
          Create Installation
        </button>
      </div>
    </div>
  `);

  _createPlatform = 'vanilla';
  try {
    const versions = await VersionManager.fetchVersions();
    const select = document.getElementById('create-version');
    if (select) {
      select.disabled = false;
      select.innerHTML = versions.map(v => `<option value="${v.id}" data-url="${v.url}">${v.id}</option>`).join('');
    }
  } catch (e) {
    const select = document.getElementById('create-version');
    if (select) {
      select.innerHTML = '<option>Failed to load versions</option>';
    }
  }
}

let _createPlatform = 'vanilla';

function _setPlatform(platform) {
  _createPlatform = platform;
  const vanilla = document.getElementById('pill-vanilla');
  const fabric = document.getElementById('pill-fabric');
  const info = document.getElementById('fabric-info');

  if (vanilla) vanilla.classList.toggle('active', platform === 'vanilla');
  if (fabric) fabric.classList.toggle('active', platform === 'fabric');
  if (info) info.style.display = platform === 'fabric' ? 'flex' : 'none';
}

async function _submitCreateInstallation() {
  const nameInput = document.getElementById('create-name');
  const versionSelect = document.getElementById('create-version');
  const submitBtn = document.getElementById('btn-create-submit');
  const progressDiv = document.getElementById('create-progress');
  const progressBar = document.getElementById('create-progress-bar');
  const progressText = document.getElementById('create-progress-text');

  const name = nameInput?.value.trim() || 'My Installation';
  const version = versionSelect?.value;
  const versionUrl = versionSelect?.selectedOptions[0]?.dataset?.url;

  if (!version || !versionUrl) {
    Toast.error('Please select a version');
    return;
  }

  submitBtn.disabled = true;
  submitBtn.textContent = 'Creating...';
  progressDiv.style.display = 'block';
  progressText.textContent = 'Fetching version info...';
  progressBar.style.width = '5%';

  // Use the real .minecraft directory
  const mcDir = await window.icey.getMcDir();

  try {
    // Fetch version detail
    const versionDetail = await VersionManager.getVersionDetail(versionUrl);
    progressBar.style.width = '10%';

    // Download version jar into .minecraft/versions/<version>/
    const clientDownload = versionDetail.downloads?.client;
    if (!clientDownload) throw new Error('No client download found for this version');

    progressText.textContent = 'Downloading Minecraft ' + version + '...';

    const versionDir = mcDir + '/versions/' + version;
    const jarPath = versionDir + '/' + version + '.jar';
    const jsonPath = versionDir + '/' + version + '.json';

    // Skip download if already exists (from MC launcher)
    if (!(await window.icey.downloadFile(clientDownload.url, jarPath)).error) {
      progressBar.style.width = '35%';
    }

    // Save version JSON
    await window.icey.downloadFile(versionUrl, jsonPath);
    progressBar.style.width = '40%';

    // Download libraries into .minecraft/libraries/
    progressText.textContent = 'Downloading libraries...';
    const libEventCleanup = window.icey.onMcEvent((data) => {
      if (data.type === 'lib-progress' && progressText) {
        const libProgress = 40 + ((data.completed / data.total) * 30); // 40-70%
        progressBar.style.width = libProgress + '%';
        progressText.textContent = `Downloading libraries (${data.completed}/${data.total})...`;
      }
    });

    const libResult = await window.icey.downloadMcLibraries(versionUrl);
    libEventCleanup();

    if (libResult.error) {
      console.warn('Library download had issues:', libResult.error);
    }
    progressBar.style.width = '70%';

    // Save as Icey installation (using version as ID so it matches .minecraft/versions/)
    const installation = {
      id: version,
      name: name,
      version: version,
      platform: _createPlatform,
      fabricActive: false,
      selected: false,
      image: null,
      createdAt: Date.now()
    };
    await window.icey.saveInstallation(installation);

    // Fabric installation
    if (_createPlatform === 'fabric') {
      progressText.textContent = 'Installing Fabric...';
      progressBar.style.width = '75%';

      const fabricEventCleanup = window.icey.onMcEvent((data) => {
        if (data.type === 'fabric-progress' && progressText) {
          progressText.textContent = data.message;
        }
      });

      const fabricResult = await window.icey.installFabric(version, version);
      fabricEventCleanup();

      if (fabricResult.error) {
        Toast.error(fabricResult.error);
        installation.platform = 'vanilla';
        await window.icey.saveInstallation(installation);
      }
    }

    progressBar.style.width = '100%';
    progressText.textContent = 'Done!';
    Toast.success(`Installation "${name}" created`);
    setTimeout(() => {
      closeModal();
      InstallationsPageInit();
    }, 500);

  } catch (e) {
    Toast.error('Failed: ' + e.message);
    submitBtn.disabled = false;
    submitBtn.innerHTML = `
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
      Create Installation
    `;
    progressDiv.style.display = 'none';
  }
}
