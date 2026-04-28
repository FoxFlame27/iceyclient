let _installationsInitialized = false;
let _installSelectedId = null;

async function InstallationsPageInit() {
  const page = document.getElementById('page-installations');
  let installations = [];
  try {
    installations = await window.icey.getInstallations();
    console.log('[Installations] Loaded:', installations.length, installations);
  } catch (e) {
    console.error('[Installations] Failed to load:', e);
  }

  // Keep selection if still valid
  if (_installSelectedId && !installations.find(i => i.id === _installSelectedId)) {
    _installSelectedId = null;
  }

  page.innerHTML = `
    <div class="installations-layout">
      <div class="installations-inner">
        <div class="installations-header">
          <h1 class="installations-title">Installations</h1>
          <button class="btn-create-install" onclick="_importWorldFromHeader()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            Import World
          </button>
          <button class="btn-create-install" onclick="showCreateInstallModal()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            New Installation
          </button>
        </div>
        <div class="installations-grid" id="installations-grid"></div>
      </div>
      <div class="installations-detail-panel ${_installSelectedId ? 'visible' : ''}" id="install-detail-panel">
        <div id="install-detail-inner"></div>
      </div>
    </div>
  `;

  _renderInstallationCards(installations);
  if (_installSelectedId) {
    _loadInstallDetail(_installSelectedId, installations);
  }

  _setupWorldDragDrop();
}

/**
 * Drag-and-drop a Minecraft world .zip onto the installations page.
 *
 *   - Drop on an install card → import into THAT installation.
 *   - Drop anywhere else on the page → use selected install if any,
 *     else the only install if there's one, else show the chooser.
 *
 * Visual feedback: full-page dashed overlay during drag-over.
 *
 * Cross-platform: this is pure DOM + Electron 28 File.path, no native
 * deps, works the same on Windows / macOS / Linux ARM64.
 */
let _dragCounter = 0;
function _setupWorldDragDrop() {
  const page = document.getElementById('page-installations');
  if (!page) return;

  // The overlay child gets removed every time InstallationsPageInit
  // reassigns innerHTML — re-append it on every call.
  let overlay = page.querySelector(':scope > .install-drop-overlay');
  if (!overlay) {
    overlay = document.createElement('div');
    overlay.className = 'install-drop-overlay';
    overlay.innerHTML = `
      <div class="install-drop-message">
        <svg viewBox="0 0 24 24" width="56" height="56" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        <div class="install-drop-text">Drop world .zip to import</div>
        <div class="install-drop-sub">Drop on a card to target that installation</div>
      </div>
    `;
    page.appendChild(overlay);
  }

  // Listeners on `page` survive innerHTML reassignment (the element
  // itself isn't replaced) — bind once via a flag on the element.
  if (page.dataset.dragBound === '1') return;
  page.dataset.dragBound = '1';

  // Tiny helper so handlers re-fetch the overlay each call (it gets
  // recreated on every InstallationsPageInit() innerHTML reassignment).
  const getOverlay = () => page.querySelector(':scope > .install-drop-overlay');

  page.addEventListener('dragenter', (e) => {
    if (!_dragHasFile(e)) return;
    e.preventDefault();
    _dragCounter++;
    const ov = getOverlay();
    if (ov) ov.classList.add('visible');
  });
  page.addEventListener('dragover', (e) => {
    if (!_dragHasFile(e)) return;
    e.preventDefault();
    if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
  });
  page.addEventListener('dragleave', () => {
    _dragCounter = Math.max(0, _dragCounter - 1);
    if (_dragCounter === 0) {
      const ov = getOverlay();
      if (ov) ov.classList.remove('visible');
    }
  });
  page.addEventListener('drop', async (e) => {
    e.preventDefault();
    _dragCounter = 0;
    const ov = getOverlay();
    if (ov) ov.classList.remove('visible');
    const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (!file) return;
    const filePath = file.path;
    if (!filePath) {
      Toast.error('Could not read dropped file');
      return;
    }
    const lower = filePath.toLowerCase();
    if (lower.endsWith('.rar') || lower.endsWith('.7z')) {
      Toast.error('.rar / .7z aren’t supported — extract to .zip first');
      return;
    }
    if (!lower.endsWith('.zip')) {
      Toast.error('Drop a .zip world file');
      return;
    }

    // If the drop landed on a specific install card, prefer that one.
    let targetId = null;
    let target = e.target;
    while (target && target !== page) {
      if (target.classList && target.classList.contains('install-card')) {
        targetId = target.dataset.id;
        break;
      }
      target = target.parentElement;
    }
    if (targetId) {
      _runImport(targetId, filePath);
    } else {
      _importWorldFromPath(filePath);
    }
  });
}

function _dragHasFile(e) {
  const items = e.dataTransfer && e.dataTransfer.items;
  if (!items) return false;
  for (let i = 0; i < items.length; i++) {
    if (items[i].kind === 'file') return true;
  }
  return false;
}

async function _runImport(installationId, filePath) {
  // Final defensive Fabric check — every code path goes through this
  // function so a stale UI / drag-drop short circuit can't slip a
  // non-Fabric target past us.
  try {
    const all = await window.icey.getInstallations();
    const target = all.find(i => i.id === installationId);
    if (!target) {
      Toast.error('Installation not found');
      return;
    }
    if (target.platform !== 'fabric') {
      Toast.error('Maps can only be imported into Fabric installations');
      return;
    }
  } catch (_) {
    // If we can't even read installations, fall through and let the
    // IPC handler itself complain — but don't refuse silently.
  }
  Toast.info('Importing world…');
  const result = await window.icey.importWorld(installationId, filePath);
  if (result && result.error) {
    Toast.error('Import failed: ' + result.error);
    return;
  }
  Toast.success('World loaded: ' + result.worldName);
  // Offer to install E4MC for instant remote multiplayer — only if
  // the install doesn't already have it AND the user hasn't dismissed
  // the prompt before.
  _maybePromptE4mc(installationId);
}

async function _maybePromptE4mc(installationId) {
  try {
    const data = await window.icey.getInstalledMods(installationId);
    const mods = (data && data.mods) || [];
    const hasE4mc = mods.some(m => /e4mc/i.test(m.filename || m.name || ''));
    if (hasE4mc) return;

    const installations = await window.icey.getInstallations();
    const inst = installations.find(i => i.id === installationId);
    if (!inst || inst.platform !== 'fabric') return;

    showModal(`
      <div class="import-pick-modal" style="width:380px;">
        <div class="import-pick-header">
          <h2 class="import-pick-title">Play with friends remotely?</h2>
          <button class="modal-close" onclick="closeModal()">
            <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
          </button>
        </div>
        <p style="color:var(--text-secondary,#94a3b8);font-size:13px;line-height:1.5;margin:0 0 12px;">
          Install <b>E4MC</b> and you can share any world over the internet — no port forwarding, no Realms. Open the world, press the E4MC keybind, send your friend the link.
        </p>
        <div style="display:flex;gap:8px;flex-direction:column;">
          <button class="import-pick-item" id="e4mc-install-btn">
            <div class="import-pick-name">Install E4MC</div>
            <div class="import-pick-meta">From Modrinth · ~150 KB · Fabric ${inst.version}</div>
          </button>
          <button class="import-pick-item" id="e4mc-skip-btn">
            <div class="import-pick-name">Skip</div>
            <div class="import-pick-meta">Maybe later</div>
          </button>
        </div>
      </div>
    `);
    document.getElementById('e4mc-install-btn').addEventListener('click', () => {
      closeModal();
      _installE4mc(installationId, inst.version);
    });
    document.getElementById('e4mc-skip-btn').addEventListener('click', () => {
      closeModal();
    });
  } catch (_) {
    // Modal is a nice-to-have; never let it break the import success path.
  }
}

async function _installE4mc(installationId, mcVersion) {
  Toast.info('Installing E4MC…');
  try {
    const dl = await ModrinthAPI.getDownloadUrl('e4mc', mcVersion, 'fabric');
    if (!dl || !dl.url) {
      Toast.error('No E4MC version for MC ' + mcVersion);
      return;
    }
    const gameDir = await window.icey.getInstallGameDir(installationId);
    const dest = gameDir + '/mods/' + dl.filename;
    const result = await window.icey.downloadFile(dl.url, dest);
    if (result && result.error) {
      Toast.error('E4MC install failed: ' + result.error);
      return;
    }
    Toast.success('E4MC installed — restart MC to use it');
  } catch (e) {
    Toast.error('E4MC install failed: ' + (e && e.message ? e.message : e));
  }
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
    const isDetailSelected = inst.id === _installSelectedId;
    const imageStyle = inst.image
      ? `background-image: url('file://${inst.image.replace(/\\/g, '/')}')`
      : `background-image: url('assets/installbg-default.png')`;
    const isFabricActive = inst.platform === 'fabric' && inst.fabricActive;

    return `
      <div class="install-card ${isSelected ? 'selected' : ''} ${isDetailSelected ? 'detail-active' : ''}" data-id="${inst.id}" onclick="_clickInstallation('${inst.id}')">
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
              <span class="install-card-platform-text"><img src="assets/vanilla-icon.png" alt="" width="14" height="14" style="filter:invert(1);vertical-align:middle;margin-right:3px;">Vanilla</span>
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

async function _clickInstallation(id) {
  _installSelectedId = id;
  const installations = await window.icey.getInstallations();
  _renderInstallationCards(installations);

  const panel = document.getElementById('install-detail-panel');
  if (panel) panel.classList.add('visible');

  _loadInstallDetail(id, installations);
}

async function _loadInstallDetail(id, installations) {
  if (!installations) installations = await window.icey.getInstallations();
  const inst = installations.find(i => i.id === id);
  const container = document.getElementById('install-detail-inner');
  if (!container || !inst) return;

  let mods = [];
  let rps = [];
  try {
    const data = await window.icey.getInstalledMods(inst.id);
    mods = data.mods || [];
    rps = data.resourcePacks || [];
  } catch (_) {}

  const allItems = [...mods, ...rps];
  const isFabricActive = inst.platform === 'fabric' && inst.fabricActive;

  container.innerHTML = `
    <div class="detail-header">Quick Info</div>
    <div class="detail-name">${inst.name}</div>
    <div class="detail-row"><span class="detail-label">Version</span><span class="detail-value">${inst.version}</span></div>
    <div class="detail-row"><span class="detail-label">Platform</span><span class="detail-value ${inst.platform === 'fabric' ? 'fabric' : ''}">${inst.platform === 'fabric' ? '<img src="assets/fabric.png" alt=""> Fabric' : '<img src="assets/vanilla-icon.png" alt="" width="14" height="14" style="filter:invert(1);vertical-align:middle;margin-right:3px;">Vanilla'}</span></div>
    ${inst.platform === 'fabric' ? `<div class="detail-row"><span class="detail-label">Fabric</span><span class="detail-value">${isFabricActive ? '<span style="color:#4ade80">Active</span>' : '<span style="color:var(--text-muted)">Inactive</span>'}</span></div>` : ''}
    ${inst.fromMcLauncher ? '<div class="detail-row"><span class="detail-label">Source</span><span class="detail-value">MC Launcher</span></div>' : ''}
    <div class="detail-separator"></div>
    <div class="detail-mods-header">Mods & Resource Packs <span class="detail-mods-count">${allItems.length}</span></div>
    <div class="detail-mods-list">
      ${allItems.length > 0 ? allItems.slice(0, 8).map(m => `
        <div class="detail-mod-item">
          ${m.icon ? `<img class="detail-mod-icon" src="${m.icon}">` : `<div class="detail-mod-icon-fallback"><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg></div>`}
          <span class="detail-mod-name">${m.name}</span>
        </div>
      `).join('') + (allItems.length > 8 ? `<div class="detail-mods-more">+${allItems.length - 8} more</div>` : '') : `<div class="detail-no-mods">${inst.platform === 'fabric' ? 'No mods installed' : 'Vanilla'}</div>`}
    </div>
    <div class="detail-separator"></div>
    <button class="detail-select-btn" onclick="_selectAndLaunch('${inst.id}')">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><polygon points="8,5 19,12 8,19"/></svg>
      Launch
    </button>
    <button class="detail-select-secondary" onclick="_selectInstallation('${inst.id}')">
      ${inst.selected ? '<span style="color:#4ade80">Selected</span>' : 'Select as Active'}
    </button>
    ${inst.platform === 'fabric' ? `
      <button class="detail-select-secondary" onclick="_importWorld('${inst.id}')" title="Import a Minecraft world ZIP into this installation's saves">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" style="vertical-align:middle;margin-right:6px;">
          <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/>
        </svg>
        Import World (.zip)
      </button>
    ` : `
      <button class="detail-select-secondary" disabled
              title="Map import is only available on Fabric installations (so you can use mods like E4MC to share with friends)"
              style="opacity:.5;cursor:not-allowed;">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" style="vertical-align:middle;margin-right:6px;">
          <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20"/>
        </svg>
        Import World — Fabric only
      </button>
    `}
  `;
}

async function _importWorld(id) {
  const filePath = await window.icey.selectFile([
    { name: 'Minecraft Worlds', extensions: ['zip'] }
  ]);
  if (!filePath) return;
  _runImport(id, filePath);
}

/**
 * Header "+ Import World" entry point. Picks the zip first; if exactly
 * one installation exists OR one is currently selected, imports
 * straight into it. Otherwise pops a chooser modal listing every
 * installation.
 */
async function _importWorldFromHeader() {
  const filePath = await window.icey.selectFile([
    { name: 'Minecraft Worlds', extensions: ['zip'] }
  ]);
  if (!filePath) return;
  _importWorldFromPath(filePath);
}

async function _importWorldFromPath(filePath) {
  const installations = await window.icey.getInstallations();
  if (!installations || installations.length === 0) {
    Toast.error('Create an installation first');
    return;
  }
  // Map import is Fabric-only — filter the candidate list before
  // any auto-pick or chooser logic.
  const fabricOnly = installations.filter(i => i.platform === 'fabric');
  if (fabricOnly.length === 0) {
    Toast.error('No Fabric installations found — create one to import maps');
    return;
  }
  let targetId;
  // Prefer the currently-selected install if it's Fabric.
  if (_installSelectedId) {
    const selected = installations.find(i => i.id === _installSelectedId);
    if (selected && selected.platform === 'fabric') targetId = selected.id;
  }
  if (!targetId && fabricOnly.length === 1) {
    targetId = fabricOnly[0].id;
  }
  if (!targetId) {
    targetId = await _pickInstallationForImport(fabricOnly);
    if (!targetId) return;
  }
  _runImport(targetId, filePath);
}

function _pickInstallationForImport(installations) {
  return new Promise(resolve => {
    const items = installations.map(i =>
      `<button class="import-pick-item" data-id="${i.id}">
         <div class="import-pick-name">${i.name}</div>
         <div class="import-pick-meta">${i.version}${i.platform === 'fabric' ? ' · Fabric' : ''}</div>
       </button>`
    ).join('');
    showModal(`
      <div class="import-pick-modal">
        <div class="import-pick-header">
          <h2 class="import-pick-title">Import into…</h2>
          <button class="modal-close" onclick="closeModal()">
            <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
          </button>
        </div>
        <div class="import-pick-body" id="import-pick-body">${items}</div>
      </div>
    `);
    const body = document.getElementById('import-pick-body');
    body.querySelectorAll('.import-pick-item').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.dataset.id;
        closeModal();
        resolve(id);
      });
    });
    // Cancel via close → resolve(null) once modal closes. Hooked via
    // a one-shot mutation observer so we don't leak the promise.
    const root = document.querySelector('.import-pick-modal');
    if (root) {
      const observer = new MutationObserver(() => {
        if (!document.body.contains(root)) {
          observer.disconnect();
          resolve(null);
        }
      });
      observer.observe(document.body, { childList: true, subtree: true });
    } else {
      resolve(null);
    }
  });
}

async function _selectAndLaunch(id) {
  // Select it first
  const installations = await window.icey.getInstallations();
  installations.forEach(i => i.selected = (i.id === id));
  for (const inst of installations) {
    await window.icey.saveInstallation(inst);
  }
  // Switch to home page and launch
  switchPage('home');
  await MinecraftLauncher.launch(id);
}

async function _selectInstallation(id) {
  const installations = await window.icey.getInstallations();
  installations.forEach(i => i.selected = (i.id === id));
  for (const inst of installations) {
    await window.icey.saveInstallation(inst);
  }
  _renderInstallationCards(installations);
  _loadInstallDetail(id, installations);
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
  if (_installSelectedId) _loadInstallDetail(_installSelectedId, installations);
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
  if (_installSelectedId === id) _installSelectedId = null;
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
                <img src="assets/vanilla-icon.png" alt="" width="14" height="14" style="filter:invert(1);">
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
        <label class="shaders-tick" id="create-shaders-option" style="display:none;">
          <input type="checkbox" id="create-install-shaders">
          <span>Shaders</span>
        </label>
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
  const shadersOpt = document.getElementById('create-shaders-option');

  if (vanilla) vanilla.classList.toggle('active', platform === 'vanilla');
  if (fabric) fabric.classList.toggle('active', platform === 'fabric');
  if (info) info.style.display = platform === 'fabric' ? 'flex' : 'none';
  if (shadersOpt) shadersOpt.style.display = platform === 'fabric' ? 'flex' : 'none';
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

  const mcDir = await window.icey.getMcDir();

  try {
    const versionDetail = await VersionManager.getVersionDetail(versionUrl);
    progressBar.style.width = '10%';

    const clientDownload = versionDetail.downloads?.client;
    if (!clientDownload) throw new Error('No client download found for this version');

    progressText.textContent = 'Downloading Minecraft ' + version + '...';

    const versionDir = mcDir + '/versions/' + version;
    const jarPath = versionDir + '/' + version + '.jar';
    const jsonPath = versionDir + '/' + version + '.json';

    if (!(await window.icey.downloadFile(clientDownload.url, jarPath)).error) {
      progressBar.style.width = '35%';
    }

    await window.icey.downloadFile(versionUrl, jsonPath);
    progressBar.style.width = '40%';

    progressText.textContent = 'Downloading libraries...';
    const libEventCleanup = window.icey.onMcEvent((data) => {
      if (data.type === 'lib-progress' && progressText) {
        const libProgress = 40 + ((data.completed / data.total) * 30);
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

    const instId = name.toLowerCase().replace(/[^a-z0-9]/g, '-') + '-' + Date.now();
    const installation = {
      id: instId,
      name: name,
      version: version,
      platform: _createPlatform,
      fabricActive: false,
      selected: false,
      image: null,
      createdAt: Date.now()
    };
    await window.icey.saveInstallation(installation);

    if (_createPlatform === 'fabric') {
      progressText.textContent = 'Installing Fabric...';
      progressBar.style.width = '75%';

      const fabricEventCleanup = window.icey.onMcEvent((data) => {
        if (data.type === 'fabric-progress' && progressText) {
          progressText.textContent = data.message;
        }
      });

      const fabricResult = await window.icey.installFabric(instId, version);
      fabricEventCleanup();

      if (fabricResult.error) {
        Toast.error(fabricResult.error);
        installation.platform = 'vanilla';
        await window.icey.saveInstallation(installation);
      } else {
        // Fabric installed successfully - activate it
        installation.fabricActive = true;
        await window.icey.saveInstallation(installation);

        // Install Iris if checkbox was ticked
        const installShaders = document.getElementById('create-install-shaders')?.checked;
        if (installShaders) {
          progressText.textContent = 'Installing Iris Shaders...';
          progressBar.style.width = '85%';
          const irisResult = await window.icey.installIris(version, instId);
          if (irisResult.error) {
            Toast.error('Iris install failed: ' + irisResult.error);
          } else {
            Toast.success('Iris Shaders installed');
          }
          progressBar.style.width = '95%';
        }
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
