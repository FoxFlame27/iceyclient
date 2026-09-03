let _modsSearchTimeout = null;
let _modsActiveInstallation = null;
let _modsFilter = 'all';
let _modsInstalledFiles = [];
let _modsBrowseMode = false;
let _modsOffset = 0;
let _modsLoading = false;
let _modsHasMore = true;
let _modsCurrentQuery = '';
let _modsActiveTab = 'mods'; // 'mods' or 'shaders'
let _modsLastResults = [];   // last rendered browse results (for re-rendering badges)

async function ModsPageInit() {
  const page = document.getElementById('page-mods');
  const installations = await window.icey.getInstallations();

  if (installations.length === 0) {
    page.innerHTML = `
      <div class="mods-guard">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
        <div class="mods-guard-title">No Installations</div>
        <div class="mods-guard-subtitle">Create an installation first to manage mods.</div>
        <button class="btn-goto-installations" onclick="switchPage('installations')">Go to Installations</button>
      </div>
    `;
    return;
  }

  // Use selected installation, or first fabric, or just first
  const selected = installations.find(i => i.selected);
  const fabricInst = installations.find(i => i.platform === 'fabric' && i.fabricActive);
  _modsActiveInstallation = selected || fabricInst || installations[0];
  _modsBrowseMode = false;
  if (_modsActiveTab === 'shaders') {
    _renderShadersView(page);
  } else {
    _renderModsMainView(page, installations);
  }
}

async function _renderModsMainView(page, installations) {
  if (!page) page = document.getElementById('page-mods');
  if (!installations) installations = await window.icey.getInstallations();

  const instOptions = installations.map(inst => {
    const sel = inst.id === _modsActiveInstallation?.id ? 'selected' : '';
    const label = inst.name + ' (' + inst.version + ')';
    return `<option value="${inst.id}" ${sel}>${label}</option>`;
  }).join('');

  // v2 layout: unified 70/30 split. Installed list always on the
  // left (70%), upload + browse search always on the right (30%).
  // When the user types in the browse search the right column
  // expands to 50% via a data-search attribute that the CSS reads.
  page.innerHTML = `
    <div class="mods-main-view-v2">
      <div class="mods-top-bar">
        <div class="mods-tab-bar">
          <button class="mods-tab active" data-tab="mods" onclick="_switchModsTab('mods', this)">Mods</button>
          <button class="mods-tab" data-tab="shaders" onclick="_switchModsTab('shaders', this)">Shaders</button>
        </div>
      </div>

      <!-- Centered install picker button — opens a popover with all installations. -->
      <div class="mods-install-row">
        <button class="mods-install-btn" id="mods-install-btn" type="button" onclick="_modsToggleInstallMenu(event)" aria-haspopup="listbox" aria-expanded="false">
          <span class="mods-install-btn-label">Installing to</span>
          <span class="mods-install-btn-value" id="mods-install-btn-value">—</span>
          <svg class="mods-install-btn-caret" viewBox="0 0 12 12" width="11" height="11" aria-hidden="true">
            <polyline points="2,4 6,8 10,4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <div class="mods-install-menu" id="mods-install-menu" role="listbox" hidden>
          ${instOptions.replace(/<option value="([^"]*)"[^>]*>([^<]*)<\/option>/g, (_m, v, t) =>
            `<button type="button" class="mods-install-menu-item" data-install-id="${v}" onclick="_modsPickInstallation('${v.replace(/'/g, "\\'")}')">${t}</button>`)}
        </div>
        <select class="mods-install-select-hidden" id="mods-install-select" onchange="_modsChangeInstallation(this.value)" hidden>
          ${instOptions}
        </select>
      </div>

      <div class="mods-split" id="mods-split" data-search="false">
        <!-- LEFT 70% (50% in search mode): installed list. -->
        <div class="mods-left-col">
          <div class="mods-section-header">
            <div class="mods-section-title">Installed</div>
            <span class="mods-section-count" id="mods-installed-count">0</span>
          </div>
          <div id="mods-installed-list" class="mods-installed-list"></div>
        </div>

        <!-- RIGHT 30% (50% in search mode): upload + browse. -->
        <div class="mods-right-col">
          <div class="mods-dropzone-mini" id="mods-dropzone" onclick="_modsBrowseFiles()">
            <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.6">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            <div class="mods-dropzone-text">Upload</div>
            <div class="mods-dropzone-subtext">click or drag .jar / .zip</div>
          </div>

          <div class="mods-browse-block">
            <div class="mods-section-title">Browse</div>
            <div class="mods-browse-search">
              <svg class="mods-search-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input type="text" id="mods-search-input" placeholder="Search Modrinth & CurseForge..." oninput="_modsSearchDebounced()">
            </div>
            <div class="mods-filter-pills">
              <button class="mods-filter-pill active" data-filter="all" onclick="_setModsFilter('all', this)">All</button>
              <button class="mods-filter-pill" data-filter="mod" onclick="_setModsFilter('mod', this)">Mods</button>
              <button class="mods-filter-pill" data-filter="resourcepack" onclick="_setModsFilter('resourcepack', this)">RP</button>
              <button class="mods-filter-pill" data-filter="shader" onclick="_setModsFilter('shader', this)">Shaders</button>
            </div>
            <div id="mods-browse-results" class="mods-browse-list"></div>
            <div id="mods-load-more" class="mods-load-more" style="display:none;">Loading more...</div>
          </div>
        </div>
      </div>
    </div>
  `;

  _setupModsDropzone();
  _refreshInstalledMods();
  _syncModsInstallBtn();

  // Right-column browse auto-loads trending so the panel isn't
  // empty when you arrive on the page. Defer to avoid blocking
  // the initial render.
  setTimeout(() => { try { _loadTrendingMods(); } catch (_) {} }, 50);

  // Infinite scroll inside the right-column browse list.
  const browseList = document.getElementById('mods-browse-results');
  if (browseList && !browseList.dataset.scrollWired) {
    browseList.dataset.scrollWired = '1';
    browseList.addEventListener('scroll', () => {
      if (_modsLoading || !_modsHasMore) return;
      const { scrollTop, scrollHeight, clientHeight } = browseList;
      if (scrollTop + clientHeight >= scrollHeight - 200) _loadMoreMods();
    });
  }

  // Delegated Install click handler — was missing in the v2 layout
  // ("press Install does nothing"). The legacy _renderModsBrowse
  // wired this on its own container; v2 has its own #mods-browse-results
  // so we need to attach it here too.
  if (browseList && !browseList.dataset.installListenerAttached) {
    browseList.dataset.installListenerAttached = '1';
    browseList.addEventListener('click', (e) => {
      const btn = e.target.closest('.btn-install-mod');
      if (!btn) return;
      _installModFromSearch(
        btn,
        btn.dataset.source,
        btn.dataset.modId,
        btn.dataset.modName,
        btn.dataset.projectType
      );
    });
  }
}

function _enterModsBrowse() {
  _modsBrowseMode = true;
  _modsOffset = 0;
  _modsHasMore = true;
  _modsCurrentQuery = '';
  const page = document.getElementById('page-mods');
  page.innerHTML = `
    <div class="mods-browse-view" id="mods-browse-view">
      <div class="mods-browse-header">
        <div class="mods-browse-title">Browse Mods & Resource Packs</div>
        <button class="btn-mods-back" onclick="_exitModsBrowse()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
          </svg>
          Back
        </button>
      </div>
      <div class="mods-browse-search">
        <svg class="mods-search-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input type="text" id="mods-search-input" placeholder="Search Modrinth & CurseForge..." oninput="_modsSearchDebounced()">
      </div>
      <div class="mods-filter-pills">
        <button class="mods-filter-pill active" data-filter="all" onclick="_setModsFilter('all', this)">All</button>
        <button class="mods-filter-pill" data-filter="mod" onclick="_setModsFilter('mod', this)">Mods</button>
        <button class="mods-filter-pill" data-filter="resourcepack" onclick="_setModsFilter('resourcepack', this)">Resource Packs</button>
        <button class="mods-filter-pill" data-filter="shader" onclick="_setModsFilter('shader', this)">Shaders</button>
      </div>
      <div id="mods-browse-results" class="mods-browse-list"></div>
      <div id="mods-load-more" class="mods-load-more" style="display:none;">Loading more...</div>
    </div>
  `;

  // Infinite scroll
  const scrollContainer = document.getElementById('mods-browse-view');
  scrollContainer.addEventListener('scroll', () => {
    if (_modsLoading || !_modsHasMore) return;
    const { scrollTop, scrollHeight, clientHeight } = scrollContainer;
    if (scrollTop + clientHeight >= scrollHeight - 200) {
      _loadMoreMods();
    }
  });

  // Delegated install-button handler — replaces the old inline onclick
  // which silently broke whenever a mod name had unusual characters.
  const resultsContainer = document.getElementById('mods-browse-results');
  if (resultsContainer && !resultsContainer.dataset.installListenerAttached) {
    resultsContainer.dataset.installListenerAttached = '1';
    resultsContainer.addEventListener('click', (e) => {
      const btn = e.target.closest('.btn-install-mod');
      if (!btn) return;
      _installModFromSearch(
        btn,
        btn.dataset.source,
        btn.dataset.modId,
        btn.dataset.modName,
        btn.dataset.projectType
      );
    });
  }

  _loadTrendingMods();
}

function _exitModsBrowse() {
  _modsBrowseMode = false;
  _renderModsMainView();
}

async function _modsChangeInstallation(id) {
  const installations = await window.icey.getInstallations();
  _modsActiveInstallation = installations.find(i => i.id === id) || installations[0];
  // Persist the choice as THE selected installation so Home's LAUNCH
  // button and the next visit to this page follow it (previously the
  // pick lived only in this module and silently reverted).
  if (_modsActiveInstallation) {
    let changed = false;
    for (const inst of installations) {
      const shouldSelect = inst.id === _modsActiveInstallation.id;
      if (!!inst.selected !== shouldSelect) { inst.selected = shouldSelect; changed = true; await window.icey.saveInstallation(inst); }
    }
    if (changed && typeof _loadHomeInstallations === 'function') { try { _loadHomeInstallations(); } catch (_) {} }
  }
  _refreshInstalledMods();
  _syncModsInstallBtn();
}

// Sync the centered button's label to the active install name.
function _syncModsInstallBtn() {
  const valEl = document.getElementById('mods-install-btn-value');
  const sel = document.getElementById('mods-install-select');
  if (valEl && _modsActiveInstallation) {
    valEl.textContent = _modsActiveInstallation.name || '—';
  }
  if (sel && _modsActiveInstallation) sel.value = _modsActiveInstallation.id;
  const menu = document.getElementById('mods-install-menu');
  if (menu) {
    menu.querySelectorAll('.mods-install-menu-item').forEach(b => {
      b.classList.toggle('active', _modsActiveInstallation && b.dataset.installId === _modsActiveInstallation.id);
    });
  }
}

function _modsToggleInstallMenu(ev) {
  ev?.stopPropagation();
  const menu = document.getElementById('mods-install-menu');
  const btn = document.getElementById('mods-install-btn');
  if (!menu || !btn) return;
  const open = !menu.hasAttribute('hidden');
  if (open) {
    menu.setAttribute('hidden', '');
    btn.setAttribute('aria-expanded', 'false');
    document.removeEventListener('click', _modsInstallMenuOutsideClick, true);
  } else {
    _syncModsInstallBtn();
    menu.removeAttribute('hidden');
    btn.setAttribute('aria-expanded', 'true');
    setTimeout(() => document.addEventListener('click', _modsInstallMenuOutsideClick, true), 0);
  }
}

function _modsInstallMenuOutsideClick(ev) {
  const menu = document.getElementById('mods-install-menu');
  const btn = document.getElementById('mods-install-btn');
  if (!menu || !btn) return;
  if (menu.contains(ev.target) || btn.contains(ev.target)) return;
  menu.setAttribute('hidden', '');
  btn.setAttribute('aria-expanded', 'false');
  document.removeEventListener('click', _modsInstallMenuOutsideClick, true);
}

function _modsPickInstallation(id) {
  _modsChangeInstallation(id);
  const menu = document.getElementById('mods-install-menu');
  const btn = document.getElementById('mods-install-btn');
  if (menu) menu.setAttribute('hidden', '');
  if (btn) btn.setAttribute('aria-expanded', 'false');
  document.removeEventListener('click', _modsInstallMenuOutsideClick, true);
}

function _setupModsDropzone() {
  const dropzone = document.getElementById('mods-dropzone');
  if (!dropzone) return;

  dropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropzone.classList.add('dragover');
  });

  dropzone.addEventListener('dragleave', () => {
    dropzone.classList.remove('dragover');
  });

  dropzone.addEventListener('drop', async (e) => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    if (!_modsActiveInstallation) return;

    const files = Array.from(e.dataTransfer.files);
    for (const file of files) {
      await _installLocalFile(file.path, file.name);
    }
    await _refreshInstalledMods();
  });
}

async function _modsBrowseFiles() {
  const filePath = await window.icey.selectFile([
    { name: 'Mods & Resource Packs', extensions: ['jar', 'zip'] }
  ]);
  if (!filePath) return;
  const filename = filePath.split(/[/\\]/).pop();
  await _installLocalFile(filePath, filename);
  await _refreshInstalledMods();
}

async function _installLocalFile(filePath, filename) {
  if (!_modsActiveInstallation) { Toast.error('Select an installation first'); return; }
  if (!filePath) { Toast.error('Invalid file path'); return; }

  // Per-installation game dir — this is where the launcher actually looks for mods.
  const gameDir = await window.icey.getInstallGameDir(_modsActiveInstallation.id);

  const lower = filename.toLowerCase();
  let destFolder;
  let isResourcePack = false;
  if (lower.endsWith('.jar')) {
    destFolder = gameDir + '/mods';
  } else if (lower.endsWith('.zip')) {
    destFolder = gameDir + '/resourcepacks';
    isResourcePack = true;
  } else {
    Toast.error('Unsupported file type. Use .jar or .zip');
    return;
  }

  const dest = destFolder + '/' + filename;
  const result = await window.icey.copyFile(filePath, dest);
  if (result && result.error) {
    Toast.error('Failed to install: ' + result.error);
    return;
  }
  if (isResourcePack) {
    try { await window.icey.registerResourcepack(_modsActiveInstallation.id, filename); } catch (_) {}
  }
  Toast.success('Installed ' + filename);
}

function _setModsFilter(filter, btn) {
  _modsFilter = filter;
  document.querySelectorAll('.mods-filter-pill').forEach(p => p.classList.remove('active'));
  if (btn) btn.classList.add('active');

  const input = document.getElementById('mods-search-input');
  if (input && input.value.trim()) {
    _modsSearch(input.value.trim());
  } else {
    _loadTrendingMods();
  }
}

function _modsSearchDebounced() {
  clearTimeout(_modsSearchTimeout);
  const input = document.getElementById('mods-search-input');
  if (!input) return;
  const query = input.value.trim();
  // Flip the split to 50/50 when the user starts typing; back to 70/30 when empty.
  const split = document.getElementById('mods-split');
  if (split) split.dataset.search = query ? 'true' : 'false';
  if (!query) {
    _loadTrendingMods();
    return;
  }
  _modsSearchTimeout = setTimeout(() => _modsSearch(query), 400);
}

async function _modsSearch(query) {
  _modsOffset = 0;
  _modsHasMore = true;
  _modsCurrentQuery = query;
  const resultsDiv = document.getElementById('mods-browse-results');
  if (!resultsDiv) return;

  resultsDiv.innerHTML = `
    <div class="mod-skeleton skeleton"></div>
    <div class="mod-skeleton skeleton"></div>
    <div class="mod-skeleton skeleton"></div>
  `;

  try {
    const types = _modsFilter === 'all' ? ['mod', 'resourcepack', 'shader'] : [_modsFilter];
    let allResults = [];

    const promises = [];
    for (const type of types) {
      promises.push(
        ModrinthAPI.search(query, type, 30, 0).catch(() => []),
        CurseForgeAPI.search(query, type, 10).catch(() => [])
      );
    }

    const results = await Promise.all(promises);
    allResults = results.flat();
    allResults.sort((a, b) => (b.downloads || 0) - (a.downloads || 0));
    _modsOffset = 30;
    _modsHasMore = allResults.length >= 20;
    _modsLastResults = allResults;

    if (allResults.length === 0) {
      resultsDiv.innerHTML = `<div class="mods-empty">No results found for '${query}'</div>`;
      return;
    }

    resultsDiv.innerHTML = allResults.map(mod => _renderModListItem(mod)).join('');
  } catch (e) {
    resultsDiv.innerHTML = `<div class="mods-empty">Could not reach API. Check your connection.</div>`;
  }
}

function _normModKey(s) { return String(s || '').toLowerCase().replace(/[^a-z0-9]/g, ''); }

// Installed = a file on disk matches this project by slug (filenames on
// Modrinth/CurseForge almost always contain it) or by exact mod name from
// fabric.mod.json. The old check matched on the first word of the title,
// so "Iris Shaders" lit up for anything containing "iris" and stayed lit
// after deletion because nothing re-rendered.
function _isModInstalled(mod) {
  const nameKey = _normModKey(mod.name);
  const slugKey = _normModKey(mod.slug || '');
  if (!nameKey && !slugKey) return false;
  return _modsInstalledFiles.some(f => {
    const fileKey = _normModKey(String(f.filename || '').replace(/\.(jar|zip)(\.disabled)?$/i, ''));
    const installedName = _normModKey(f.name);
    if (installedName && nameKey && installedName === nameKey) return true;
    if (slugKey && slugKey.length >= 4 && fileKey.includes(slugKey)) return true;
    if (nameKey.length >= 6 && fileKey.includes(nameKey)) return true;
    return false;
  });
}

// Re-render the browse list from the last results so Install/Installed
// badges reflect the current on-disk state (after install, delete, toggle).
function _rerenderBrowseResults() {
  const resultsDiv = document.getElementById('mods-browse-results');
  if (!resultsDiv || !_modsLastResults.length) return;
  const scrollTop = resultsDiv.scrollTop;
  resultsDiv.innerHTML = _modsLastResults.map(mod => _renderModListItem(mod)).join('');
  resultsDiv.scrollTop = scrollTop;
}

function _renderModListItem(mod) {
  const downloads = mod.downloads ? _formatNumber(mod.downloads) : '0';
  const installed = _isModInstalled(mod);
  const sourceBadge = mod.source === 'modrinth' ? 'MR' : 'CF';

  const iconHtml = mod.icon_url
    ? `<img class="mod-list-icon" src="${mod.icon_url}" alt="" onerror="this.outerHTML='<div class=\\'mod-list-icon-fallback\\'><svg viewBox=\\'0 0 24 24\\' width=\\'24\\' height=\\'24\\' fill=\\'none\\' stroke=\\'currentColor\\' stroke-width=\\'1.5\\'><path d=\\'M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z\\'/></svg></div>'">`
    : `<div class="mod-list-icon-fallback"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg></div>`;

  return `
    <div class="mod-list-item">
      ${iconHtml}
      <div class="mod-list-info">
        <div class="mod-list-name">${_escapeHtml(mod.name)}</div>
        <div class="mod-list-desc">${_escapeHtml(mod.description || '')}</div>
        <div class="mod-list-meta">
          <span class="mod-list-author">${_escapeHtml(mod.author || 'Unknown')}</span>
          <span class="mod-list-downloads">${downloads} downloads</span>
          <span class="mod-source-badge">${sourceBadge}</span>
        </div>
      </div>
      <div class="mod-list-actions">
        ${installed
          ? '<span class="badge-installed">Installed</span>'
          // data-attrs + delegated listener — inline onclick was getting
          // broken by special characters in mod names (apostrophes,
          // ampersands, backslashes), making "nothing happen" on click.
          : `<button class="btn-install-mod"
                     data-source="${_escapeHtml(mod.source)}"
                     data-mod-id="${_escapeHtml(String(mod.id))}"
                     data-mod-name="${_escapeHtml(mod.name)}"
                     data-icon="${_escapeHtml(mod.icon_url || '')}"
                     data-project-type="${_escapeHtml(mod.project_type || 'mod')}">Install</button>`
        }
      </div>
    </div>
  `;
}

async function _installModFromSearch(btn, source, modId, modName, projectType) {
  if (!_modsActiveInstallation) {
    Toast.error('Select an installation first');
    return;
  }

  // For Modrinth: show the version + platform picker modal
  if (source === 'modrinth') {
    await _showModDownloadModal(modId, modName, projectType, btn);
    return;
  }

  // CurseForge: no version picker — go straight to the progress modal.
  btn.disabled = true;
  btn.textContent = '…';
  try {
    const cfType = projectType === 'resourcepack' ? 'resourcepack'
      : projectType === 'shader' ? 'shader' : 'mod';
    const results = await CurseForgeAPI.search(modName, cfType, 5);
    const mod = results.find(r => String(r.id) === String(modId));
    const downloadInfo = mod ? CurseForgeAPI.getDownloadUrl(mod) : null;
    if (!downloadInfo || !downloadInfo.url) {
      Toast.error('No download available');
      btn.disabled = false;
      btn.textContent = 'Install';
      return;
    }
    await _doModDownload(downloadInfo.url, downloadInfo.filename, modName, projectType, btn);
  } catch (e) {
    Toast.error('Install failed: ' + e.message);
    btn.disabled = false;
    btn.textContent = 'Install';
  }
}

// ── Download modal: Modrinth version picker + live progress ───────────
let _modDlSelected = { mcVersion: null, loader: null };
let _modDlProgressOff = null;

function _modDlIconHtml(icon) {
  if (icon) return `<img class="mod-dl-icon-img" src="${_escapeAttr(icon)}" alt="" onerror="this.style.display='none'">`;
  return `<div class="mod-dl-icon"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg></div>`;
}

function _modDlTypeLabel(projectType) {
  return projectType === 'resourcepack' ? 'Resource pack' : projectType === 'shader' ? 'Shader pack' : 'Mod';
}

// Shared modal frame: icon, title, "Installing to <installation>" line, body.
function _modDlShell(modName, icon, projectType, bodyHtml) {
  const inst = _modsActiveInstallation;
  const sub = inst
    ? `${_modDlTypeLabel(projectType)} &middot; installing to <b>${_escapeHtml(inst.name)}</b> (${_escapeHtml(inst.version)})`
    : _modDlTypeLabel(projectType);
  showModal(`
    <div class="mod-dl-modal">
      <div class="mod-dl-header">
        <div class="mod-dl-title-row">
          ${_modDlIconHtml(icon)}
          <div class="mod-dl-title-block">
            <h2 class="mod-dl-title">${_escapeHtml(modName)}</h2>
            <div class="mod-dl-subtitle">${sub}</div>
          </div>
        </div>
        <button class="modal-close" onclick="_modDlClose()">
          <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
        </button>
      </div>
      <div class="mod-dl-body">${bodyHtml}</div>
    </div>
  `);
}

function _modDlClose() {
  if (_modDlProgressOff) { try { _modDlProgressOff(); } catch (_) {} _modDlProgressOff = null; }
  closeModal();
}

async function _showModDownloadModal(modId, modName, projectType, btn) {
  const icon = (btn && btn.dataset && btn.dataset.icon) || '';
  const installVersion = _modsActiveInstallation?.version || '';

  _modDlShell(modName, icon, projectType, `<div class="mod-dl-loading"><span class="mod-dl-spinner"></span>Loading versions…</div>`);

  try {
    const [mcVersions, loaders] = await Promise.all([
      ModrinthAPI.getSupportedMcVersions(modId),
      ModrinthAPI.getSupportedLoaders(modId)
    ]);

    // Release-style versions only (no snapshots like "23w14a"). Covers both
    // the 1.21.x scheme and the year-based 26.x scheme.
    const releaseVersions = mcVersions.filter(v => /^\d+\.\d+(\.\d+)?$/.test(v));
    if (releaseVersions.length === 0) releaseVersions.push(...mcVersions);

    const hasInstallVersion = releaseVersions.includes(installVersion);
    const defaultMc = hasInstallVersion ? installVersion : releaseVersions[0];
    const defaultLoader = loaders.includes('fabric') ? 'fabric' : (loaders[0] || 'fabric');
    const loaderPickable = projectType !== 'resourcepack' && projectType !== 'shader';

    const body = document.querySelector('.mod-dl-body');
    if (!body) return;
    body.innerHTML = `
      ${hasInstallVersion || !installVersion ? '' : `<div class="mod-dl-note">No build for Minecraft ${_escapeHtml(installVersion)} yet — pick the closest version below.</div>`}
      <div class="mod-dl-section">
        <div class="mod-dl-label">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="8" cy="12" r="1"/><circle cx="16" cy="12" r="1"/></svg>
          Game version
        </div>
        <div class="mod-dl-version-list" id="mod-dl-versions">
          ${releaseVersions.map(v => `
            <button class="mod-dl-version-btn ${v === defaultMc ? 'selected' : ''} ${v === installVersion ? 'is-install' : ''}" data-version="${v}" onclick="_selectModDlVersion('${v}')" title="${v === installVersion ? 'Matches this installation' : ''}">${v}</button>
          `).join('')}
        </div>
      </div>

      ${loaderPickable ? `
      <div class="mod-dl-section">
        <div class="mod-dl-label">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
          Platform
        </div>
        <div class="mod-dl-loader-row" id="mod-dl-loaders">
          ${loaders.map(l => `
            <button class="mod-dl-loader-btn ${l === defaultLoader ? 'selected' : ''}" data-loader="${l}" onclick="_selectModDlLoader('${l}')">${_loaderLabel(l)}</button>
          `).join('')}
        </div>
      </div>` : ''}

      <button class="mod-dl-install-btn" id="mod-dl-install-btn" onclick="_confirmModDownload('${modId}', '${_escapeAttr(modName)}', '${projectType}', '${_escapeAttr(icon)}')">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
        <span>Download</span>
      </button>
    `;

    _modDlSelected = { mcVersion: defaultMc, loader: loaderPickable ? defaultLoader : 'any' };
  } catch (e) {
    const body = document.querySelector('.mod-dl-body');
    if (body) body.innerHTML = `<div class="mod-dl-error">Failed to load versions: ${_escapeHtml(e.message)}</div>`;
  }
}

function _loaderLabel(loader) {
  const map = { fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge', quilt: 'Quilt', vanilla: 'Vanilla' };
  return map[loader] || loader.charAt(0).toUpperCase() + loader.slice(1);
}

function _selectModDlVersion(v) {
  _modDlSelected.mcVersion = v;
  document.querySelectorAll('.mod-dl-version-btn').forEach(b => {
    b.classList.toggle('selected', b.dataset.version === v);
  });
}

function _selectModDlLoader(l) {
  _modDlSelected.loader = l;
  document.querySelectorAll('.mod-dl-loader-btn').forEach(b => {
    b.classList.toggle('selected', b.dataset.loader === l);
  });
}

async function _confirmModDownload(modId, modName, projectType, icon) {
  const { mcVersion, loader } = _modDlSelected;
  if (!mcVersion || !loader) {
    Toast.error('Select version and platform');
    return;
  }
  const installBtn = document.getElementById('mod-dl-install-btn');
  const setBtn = (text, disabled) => {
    if (!installBtn) return;
    installBtn.disabled = disabled;
    const span = installBtn.querySelector('span');
    if (span) span.textContent = text;
  };
  setBtn('Finding file…', true);

  try {
    const downloadInfo = await ModrinthAPI.getDownloadUrl(modId, mcVersion, loader);
    if (!downloadInfo || !downloadInfo.url) {
      Toast.error('No download for ' + mcVersion + (loader !== 'any' ? ' / ' + loader : ''));
      setBtn('Download', false);
      return;
    }
    await _runModDownload({ url: downloadInfo.url, filename: downloadInfo.filename, modName, projectType, icon });
  } catch (e) {
    Toast.error(e.message);
    setBtn('Download', false);
  }
}

function _folderFor(projectType) {
  if (projectType === 'resourcepack') return 'resourcepacks';
  if (projectType === 'shader') return 'shaderpacks';
  return 'mods';
}

async function _ensureShaderDepsIfNeeded(projectType) {
  if (projectType !== 'shader' || !_modsActiveInstallation) return;
  try {
    _modDlSetStatus('Checking shader dependencies (Iris + Sodium)…');
    const result = await window.icey.ensureShaderDeps(
      _modsActiveInstallation.id,
      _modsActiveInstallation.version
    );
    if (result && result.installed && result.installed.length > 0) {
      Toast.success('Installed ' + result.installed.join(' + '));
    }
  } catch (e) { Toast.error('Shader deps failed: ' + e.message); }
}

function _modDlProgressHtml(filename) {
  return `
    <div class="mod-dl-progress" id="mod-dl-progress">
      <div class="mod-dl-progress-file" title="${_escapeAttr(filename)}">${_escapeHtml(filename)}</div>
      <div class="mod-dl-track"><div class="mod-dl-bar-fill indeterminate" id="mod-dl-bar-fill" style="width:0%"></div></div>
      <div class="mod-dl-progress-meta">
        <span id="mod-dl-status">Starting…</span>
        <span class="mod-dl-progress-nums"><span id="mod-dl-size"></span><b id="mod-dl-pct"></b></span>
      </div>
    </div>
  `;
}

function _modDlSetStatus(text) {
  const el = document.getElementById('mod-dl-status');
  if (el) el.textContent = text;
}

function _modDlShowDone(filename, projectType) {
  const box = document.getElementById('mod-dl-progress');
  if (!box) return;
  const hint = projectType === 'resourcepack'
    ? 'Enabled in this installation — it loads next time you play.'
    : projectType === 'shader'
      ? 'Pick it in-game under Options → Video → Shader Packs.'
      : 'Restart Minecraft to load it.';
  box.outerHTML = `
    <div class="mod-dl-done" id="mod-dl-done">
      <div class="mod-dl-done-icon">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
      </div>
      <div class="mod-dl-done-title">Installed</div>
      <div class="mod-dl-done-sub">${_escapeHtml(filename)}</div>
      <div class="mod-dl-done-hint">${hint}</div>
      <button class="mod-dl-install-btn" onclick="_modDlClose()"><span>Done</span></button>
    </div>
  `;
}

function _modDlShowError(message) {
  const box = document.getElementById('mod-dl-progress');
  if (!box) return;
  box.outerHTML = `
    <div class="mod-dl-done is-error">
      <div class="mod-dl-done-icon">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="6" y1="6" x2="18" y2="18"/><line x1="18" y1="6" x2="6" y2="18"/></svg>
      </div>
      <div class="mod-dl-done-title">Download failed</div>
      <div class="mod-dl-done-sub">${_escapeHtml(message)}</div>
      <button class="mod-dl-install-btn" onclick="_modDlClose()"><span>Close</span></button>
    </div>
  `;
}

// Runs one download with the modal showing live progress. Returns true on success.
async function _runModDownload({ url, filename, modName, projectType, icon }) {
  if (!url || !filename) { Toast.error('Invalid download'); return false; }

  _modDlShell(modName, icon, projectType, _modDlProgressHtml(filename));

  if (_modDlProgressOff) { try { _modDlProgressOff(); } catch (_) {} }
  _modDlProgressOff = window.icey.onDownloadProgress((p) => {
    const fill = document.getElementById('mod-dl-bar-fill');
    const pct = document.getElementById('mod-dl-pct');
    const size = document.getElementById('mod-dl-size');
    if (fill) { fill.classList.remove('indeterminate'); fill.style.width = Math.max(2, p.percent || 0) + '%'; }
    if (pct) pct.textContent = (p.percent || 0) + '%';
    if (size) size.textContent = _formatFileSize(p.downloaded || 0) + ' / ' + _formatFileSize(p.total || 0);
  });

  try {
    await _ensureShaderDepsIfNeeded(projectType);
    _modDlSetStatus('Downloading…');
    const gameDir = _modsActiveInstallation ? await window.icey.getInstallGameDir(_modsActiveInstallation.id) : await window.icey.getMcDir();
    const dest = gameDir + '/' + _folderFor(projectType) + '/' + filename;
    const result = await window.icey.downloadFile(url, dest);
    if (result.error) {
      _modDlShowError(result.error);
      Toast.error('Download failed: ' + result.error);
      return false;
    }
    if (projectType === 'resourcepack' && _modsActiveInstallation) {
      _modDlSetStatus('Enabling resource pack…');
      await window.icey.registerResourcepack(_modsActiveInstallation.id, filename);
    }
    const fill = document.getElementById('mod-dl-bar-fill');
    if (fill) { fill.classList.remove('indeterminate'); fill.style.width = '100%'; }
    _modDlShowDone(filename, projectType);
    Toast.success('Installed ' + modName);
    // Re-read the folder so both the Installed list and the browse badges
    // reflect reality (no optimistic pushes that a delete can't undo).
    await _refreshInstalledMods();
    setTimeout(() => { if (document.getElementById('mod-dl-done')) _modDlClose(); }, 1400);
    return true;
  } catch (e) {
    _modDlShowError(e.message || String(e));
    Toast.error('Download failed: ' + (e.message || e));
    return false;
  } finally {
    if (_modDlProgressOff) { try { _modDlProgressOff(); } catch (_) {} _modDlProgressOff = null; }
  }
}

// Kept for callers that pass (url, filename, modName, projectType).
async function _downloadModVersion(url, filename, modName, projectType) {
  return _runModDownload({ url, filename, modName, projectType, icon: '' });
}

// CurseForge path (no version picker): straight to the progress modal.
async function _doModDownload(url, filename, modName, projectType, btn) {
  const icon = (btn && btn.dataset && btn.dataset.icon) || '';
  const ok = await _runModDownload({ url, filename, modName, projectType, icon });
  if (!ok && btn && btn.isConnected) { btn.disabled = false; btn.textContent = 'Install'; }
}

async function _refreshInstalledMods() {
  if (!_modsActiveInstallation) return;

  const data = await window.icey.getInstalledMods(_modsActiveInstallation.id);
  const allItems = [...(data.mods || []), ...(data.resourcePacks || [])];
  _modsInstalledFiles = allItems;
  _rerenderBrowseResults();

  const countEl = document.getElementById('mods-installed-count');
  if (countEl) countEl.textContent = allItems.length;

  const list = document.getElementById('mods-installed-list');
  if (!list) return;

  if (allItems.length === 0) {
    list.innerHTML = '<div class="mods-empty">No mods or resource packs installed yet.</div>';
    return;
  }

  list.innerHTML = allItems.map(item => {
    const size = _formatFileSize(item.size);
    const typeClass = item.type === 'mod' ? 'mod' : 'resourcepack';
    const typeLabel = item.type === 'mod' ? 'Mod' : 'Resource Pack';
    const isDisabled = !!item.disabled;
    const isIncompat = item.type === 'mod' && item.compatible === false;
    const fallbackSvg = item.type === 'mod'
      ? '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>'
      : '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 3v18"/></svg>';

    const iconHtml = item.icon
      ? `<img src="${item.icon}" alt="" style="width:100%;height:100%;object-fit:cover;border-radius:6px;">`
      : fallbackSvg;

    const warningHtml = isIncompat
      ? `<span class="mod-type-badge incompatible" title="Requires MC ${_escapeAttr(item.mcConstraint || '?')}">Incompatible</span>`
      : '';
    const disabledBadge = isDisabled && !isIncompat
      ? '<span class="mod-type-badge disabled-badge">Disabled</span>'
      : '';

    const toggleTitle = isDisabled ? 'Enable mod' : 'Disable mod';
    const toggleBtn = item.type === 'mod' ? `
        <button class="btn-toggle-mod${isDisabled ? ' is-disabled' : ''}" onclick="_toggleInstalledMod('${_escapeAttr(item.filename)}')" title="${toggleTitle}">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            ${isDisabled
              ? '<rect x="1" y="5" width="22" height="14" rx="7"/><circle cx="8" cy="12" r="4" fill="currentColor"/>'
              : '<rect x="1" y="5" width="22" height="14" rx="7"/><circle cx="16" cy="12" r="4" fill="currentColor"/>'}
          </svg>
        </button>` : '';

    return `
      <div class="mod-list-item installed${isDisabled ? ' mod-disabled' : ''}">
        <div class="mod-installed-icon">${iconHtml}</div>
        <div class="mod-list-info">
          <div class="mod-list-name">${_escapeHtml(item.name)}</div>
          <div class="mod-list-meta">
            <span class="mod-type-badge ${typeClass}">${typeLabel}</span>
            ${warningHtml}${disabledBadge}
            <span class="mod-list-downloads">${size}</span>
          </div>
        </div>
        ${toggleBtn}
        <button class="btn-delete-mod" onclick="_deleteInstalledMod('${_escapeAttr(item.filename)}')" title="Delete">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    `;
  }).join('');
}

async function _deleteInstalledMod(filename) {
  if (!_modsActiveInstallation) return;
  const result = await window.icey.deleteMod(_modsActiveInstallation.id, filename);
  if (result.error) {
    Toast.error(result.error);
  } else {
    Toast.success('Removed ' + filename);
    await _refreshInstalledMods();
  }
}

async function _toggleInstalledMod(filename) {
  if (!_modsActiveInstallation) return;
  const result = await window.icey.toggleMod(_modsActiveInstallation.id, filename);
  if (result.error) {
    Toast.error(result.error);
  } else {
    const action = filename.endsWith('.disabled') ? 'Enabled' : 'Disabled';
    Toast.success(action + ' mod');
    await _refreshInstalledMods();
  }
}

function _formatNumber(num) {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return String(num);
}

function _formatFileSize(bytes) {
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return bytes + ' B';
}

function _escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function _escapeAttr(str) {
  return str.replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

async function _loadTrendingMods() {
  _modsOffset = 0;
  _modsHasMore = true;
  _modsCurrentQuery = '';
  const resultsDiv = document.getElementById('mods-browse-results');
  if (!resultsDiv) return;
  resultsDiv.innerHTML = '<div class="mod-skeleton skeleton"></div><div class="mod-skeleton skeleton"></div><div class="mod-skeleton skeleton"></div>';
  try {
    const types = _modsFilter === 'all' ? ['mod', 'resourcepack', 'shader'] : [_modsFilter];
    let allResults = [];
    const promises = [];
    for (const type of types) {
      promises.push(ModrinthAPI.search('', type, 30, 0).catch(() => []));
    }
    const results = await Promise.all(promises);
    allResults = results.flat();
    allResults.sort((a, b) => (b.downloads || 0) - (a.downloads || 0));
    _modsOffset = 30;
    _modsHasMore = allResults.length >= 20;
    _modsLastResults = allResults;
    if (allResults.length > 0) {
      resultsDiv.innerHTML = allResults.map(mod => _renderModListItem(mod)).join('');
    } else {
      resultsDiv.innerHTML = '<div class="mods-empty">No results found.</div>';
    }
  } catch (_) {
    resultsDiv.innerHTML = '';
  }
}

async function _loadMoreMods() {
  if (_modsLoading || !_modsHasMore) return;
  _modsLoading = true;
  const loadMore = document.getElementById('mods-load-more');
  if (loadMore) loadMore.style.display = 'block';

  try {
    const query = _modsCurrentQuery;
    const types = _modsFilter === 'all' ? ['mod', 'resourcepack', 'shader'] : [_modsFilter];
    let allResults = [];
    const promises = [];
    for (const type of types) {
      promises.push(ModrinthAPI.search(query, type, 30, _modsOffset).catch(() => []));
    }
    const results = await Promise.all(promises);
    allResults = results.flat();

    if (allResults.length === 0) {
      _modsHasMore = false;
    } else {
      _modsOffset += 30;
      _modsLastResults = _modsLastResults.concat(allResults);
      const resultsDiv = document.getElementById('mods-browse-results');
      if (resultsDiv) {
        resultsDiv.insertAdjacentHTML('beforeend', allResults.map(mod => _renderModListItem(mod)).join(''));
      }
    }
  } catch (_) {
    _modsHasMore = false;
  }
  _modsLoading = false;
  if (loadMore) loadMore.style.display = 'none';
}

function _switchModsTab(tab, btn) {
  _modsActiveTab = tab;
  document.querySelectorAll('.mods-tab').forEach(t => t.classList.remove('active'));
  if (btn) btn.classList.add('active');
  const page = document.getElementById('page-mods');
  if (tab === 'shaders') {
    _renderShadersView(page);
  } else {
    _modsBrowseMode = false;
    _renderModsMainView(page);
  }
}

async function _renderShadersView(page) {
  if (!page) page = document.getElementById('page-mods');
  page.innerHTML = `
    <div class="mods-main-view">
      <div class="mods-tab-bar">
        <button class="mods-tab" data-tab="mods" onclick="_switchModsTab('mods', this)">
          <img src="assets/fabric-icon.png" width="16" height="16" style="image-rendering:pixelated;object-fit:contain;">
          Mods
        </button>
        <button class="mods-tab active" data-tab="shaders" onclick="_switchModsTab('shaders', this)">
          <img src="assets/shaders-icon.png" width="16" height="16" style="image-rendering:pixelated;object-fit:contain;">
          Shaders
        </button>
      </div>
      <div class="mods-dropzone-full" id="shaders-dropzone" onclick="_shadersBrowseFiles()">
        <div class="mods-plus-icon">
          <img src="assets/shaders-icon.png" width="56" height="56" style="image-rendering:pixelated;object-fit:contain;">
        </div>
        <div class="mods-dropzone-text">Click to add shader packs</div>
        <div class="mods-dropzone-subtext">or drag and drop .zip files here</div>
      </div>
      <div class="mods-installed-section" id="shaders-installed-section">
        <div class="mods-section-header">
          <div class="mods-section-title">Installed Shader Packs</div>
          <span class="mods-section-count" id="shaders-installed-count">0</span>
        </div>
        <div id="shaders-installed-list" class="mods-installed-list"></div>
      </div>
    </div>
  `;

  _setupShadersDropzone();
  _refreshInstalledShaderpacks();
}

function _setupShadersDropzone() {
  const dropzone = document.getElementById('shaders-dropzone');
  if (!dropzone) return;

  dropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropzone.classList.add('dragover');
  });

  dropzone.addEventListener('dragleave', () => {
    dropzone.classList.remove('dragover');
  });

  dropzone.addEventListener('drop', async (e) => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    const files = Array.from(e.dataTransfer.files);
    for (const file of files) {
      await _installShaderFile(file.path, file.name);
    }
    await _refreshInstalledShaderpacks();
  });
}

async function _shadersBrowseFiles() {
  const filePath = await window.icey.selectFile([
    { name: 'Shader Packs', extensions: ['zip'] }
  ]);
  if (!filePath) return;
  const filename = filePath.split(/[/\\]/).pop();
  await _installShaderFile(filePath, filename);
  await _refreshInstalledShaderpacks();
}

async function _installShaderFile(filePath, filename) {
  if (!filename.toLowerCase().endsWith('.zip')) {
    Toast.error('Shader packs must be .zip files');
    return;
  }
  if (!_modsActiveInstallation) { Toast.error('Select an installation first'); return; }
  const gameDir = await window.icey.getInstallGameDir(_modsActiveInstallation.id);
  const dest = gameDir + '/shaderpacks/' + filename;
  const result = await window.icey.copyFile(filePath, dest);
  if (result && result.error) {
    Toast.error('Failed to install: ' + result.error);
  } else {
    Toast.success('Installed ' + filename);
  }
}

async function _refreshInstalledShaderpacks() {
  const packs = await window.icey.getInstalledShaderpacks(_modsActiveInstallation?.id);
  const countEl = document.getElementById('shaders-installed-count');
  if (countEl) countEl.textContent = packs.length;

  const list = document.getElementById('shaders-installed-list');
  if (!list) return;

  if (packs.length === 0) {
    list.innerHTML = '<div class="mods-empty">No shader packs installed yet. Add Iris Shaders via Fabric, then drop shader packs here.</div>';
    return;
  }

  list.innerHTML = packs.map(pack => {
    const size = _formatFileSize(pack.size);
    return `
      <div class="mod-list-item installed">
        <div class="mod-installed-icon">
          <img src="assets/shaders-icon.png" width="20" height="20" style="image-rendering:pixelated;object-fit:contain;">
        </div>
        <div class="mod-list-info">
          <div class="mod-list-name">${_escapeHtml(pack.name)}</div>
          <div class="mod-list-meta">
            <span class="mod-type-badge shader">Shader</span>
            <span class="mod-list-downloads">${size}</span>
          </div>
        </div>
        <button class="btn-delete-mod" onclick="_deleteShaderpack('${_escapeAttr(pack.filename)}')" title="Delete">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    `;
  }).join('');
}

async function _deleteShaderpack(filename) {
  if (!_modsActiveInstallation) { Toast.error('No installation selected'); return; }
  const result = await window.icey.deleteShaderpack(_modsActiveInstallation.id, filename);
  if (result.error) {
    Toast.error(result.error);
  } else {
    Toast.success('Removed ' + filename);
    await _refreshInstalledShaderpacks();
  }
}
