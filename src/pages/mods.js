let _modsSearchTimeout = null;
let _modsActiveInstallation = null;
let _modsFilter = 'all';
let _modsInstalledFiles = [];

async function ModsPageInit() {
  const page = document.getElementById('page-mods');
  const installations = await window.icey.getInstallations();
  const fabricInstallation = installations.find(i => i.platform === 'fabric' && i.fabricActive);

  if (!fabricInstallation) {
    page.innerHTML = `
      <div class="mods-guard">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
        <div class="mods-guard-title">No Fabric Installation Selected</div>
        <div class="mods-guard-subtitle">Mods require a Fabric installation. Go to Installations and activate one.</div>
        <button class="btn-goto-installations" onclick="switchPage('installations')">Go to Installations</button>
      </div>
    `;
    return;
  }

  _modsActiveInstallation = fabricInstallation;

  page.innerHTML = `
    <div class="mods-header">
      <div class="mods-active-pill">
        <img src="assets/fabric.png" alt="Fabric">
        <span class="mods-active-pill-name">${fabricInstallation.name}</span>
        <span class="mods-active-pill-version">${fabricInstallation.version}</span>
      </div>
      <div class="mods-search">
        <svg class="mods-search-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        <input type="text" id="mods-search-input" placeholder="Search Modrinth & CurseForge..." oninput="_modsSearchDebounced()">
      </div>
    </div>

    <div class="mods-dropzone" id="mods-dropzone" onclick="_modsBrowseFiles()">
      <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5">
        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      <div class="mods-dropzone-text">Drop mods or resource packs here</div>
      <div class="mods-dropzone-subtext">.jar → mods folder &middot; .zip → resourcepacks folder</div>
      <button class="btn-browse-files" onclick="event.stopPropagation(); _modsBrowseFiles()">Browse Files</button>
    </div>

    <div class="mods-section-header">
      <div class="mods-section-title">Browse Mods & Resource Packs</div>
    </div>
    <div class="mods-filter-pills">
      <button class="mods-filter-pill active" data-filter="all" onclick="_setModsFilter('all', this)">All</button>
      <button class="mods-filter-pill" data-filter="mod" onclick="_setModsFilter('mod', this)">Mods</button>
      <button class="mods-filter-pill" data-filter="resourcepack" onclick="_setModsFilter('resourcepack', this)">Resource Packs</button>
    </div>
    <div id="mods-browse-results" class="mods-results-grid"></div>

    <div class="mods-section-header" style="margin-top:28px;">
      <div class="mods-section-title">Installed</div>
      <span class="mods-section-count" id="mods-installed-count">0</span>
    </div>
    <div id="mods-installed-list" class="mods-results-grid"></div>
  `;

  // Setup drag and drop
  _setupModsDropzone();

  // Load installed
  await _refreshInstalledMods();
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
  if (!_modsActiveInstallation) return;
  const installationsDir = await PathUtils.getInstallationsDir();
  const base = installationsDir + '/' + _modsActiveInstallation.id + '/.minecraft';

  let destFolder;
  if (filename.endsWith('.jar')) {
    destFolder = base + '/mods';
  } else if (filename.endsWith('.zip')) {
    destFolder = base + '/resourcepacks';
  } else {
    Toast.error('Unsupported file type. Use .jar or .zip');
    return;
  }

  const dest = destFolder + '/' + filename;
  const result = await window.icey.copyFile(filePath, dest);
  if (result.error) {
    Toast.error('Failed to install: ' + result.error);
  } else {
    Toast.success('Installed ' + filename);
  }
}

function _setModsFilter(filter, btn) {
  _modsFilter = filter;
  document.querySelectorAll('.mods-filter-pill').forEach(p => p.classList.remove('active'));
  if (btn) btn.classList.add('active');

  // Re-run search if there's a query
  const input = document.getElementById('mods-search-input');
  if (input && input.value.trim()) {
    _modsSearch(input.value.trim());
  }
}

function _modsSearchDebounced() {
  clearTimeout(_modsSearchTimeout);
  const input = document.getElementById('mods-search-input');
  if (!input) return;
  const query = input.value.trim();
  if (!query) {
    document.getElementById('mods-browse-results').innerHTML = '';
    return;
  }
  _modsSearchTimeout = setTimeout(() => _modsSearch(query), 400);
}

async function _modsSearch(query) {
  const resultsDiv = document.getElementById('mods-browse-results');
  if (!resultsDiv) return;

  // Show skeletons
  resultsDiv.innerHTML = `
    <div class="mod-skeleton skeleton"></div>
    <div class="mod-skeleton skeleton"></div>
    <div class="mod-skeleton skeleton"></div>
  `;

  try {
    const types = _modsFilter === 'all' ? ['mod', 'resourcepack'] : [_modsFilter];
    let allResults = [];

    const promises = [];
    for (const type of types) {
      promises.push(
        ModrinthAPI.search(query, type, 10).catch(() => []),
        CurseForgeAPI.search(query, type, 10).catch(() => [])
      );
    }

    const results = await Promise.all(promises);
    allResults = results.flat();

    // Sort by downloads
    allResults.sort((a, b) => (b.downloads || 0) - (a.downloads || 0));

    if (allResults.length === 0) {
      resultsDiv.innerHTML = `<div class="mods-empty" style="grid-column:1/-1;">No results found for '${query}'</div>`;
      return;
    }

    resultsDiv.innerHTML = allResults.map(mod => _renderModCard(mod)).join('');
  } catch (e) {
    resultsDiv.innerHTML = `<div class="mods-empty" style="grid-column:1/-1;">Could not reach API. Check your connection.</div>`;
  }
}

function _renderModCard(mod) {
  const downloads = mod.downloads ? _formatNumber(mod.downloads) : '0';
  const installed = _modsInstalledFiles.some(f =>
    f.name.toLowerCase().includes(mod.name.toLowerCase().split(' ')[0])
  );
  const sourceBadge = mod.source === 'modrinth' ? 'MR' : 'CF';

  const iconHtml = mod.icon_url
    ? `<img class="mod-result-thumb" src="${mod.icon_url}" alt="" onerror="this.outerHTML='<div class=\\'mod-result-thumb-fallback\\'><svg viewBox=\\'0 0 24 24\\' width=\\'24\\' height=\\'24\\' fill=\\'none\\' stroke=\\'currentColor\\' stroke-width=\\'1.5\\'><path d=\\'M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z\\'/></svg></div>'">`
    : `<div class="mod-result-thumb-fallback"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg></div>`;

  return `
    <div class="mod-result-card">
      <span class="mod-source-badge">${sourceBadge}</span>
      ${iconHtml}
      <div class="mod-result-info">
        <div class="mod-result-name">${_escapeHtml(mod.name)}</div>
        <div class="mod-result-desc">${_escapeHtml(mod.description || '')}</div>
        <div class="mod-result-meta">
          <span class="mod-result-author">${_escapeHtml(mod.author || 'Unknown')}</span>
          <span class="mod-result-downloads">${downloads} downloads</span>
        </div>
      </div>
      <div class="mod-result-right">
        ${installed
          ? '<span class="badge-installed">Installed</span>'
          : `<button class="btn-install-mod" onclick="_installModFromSearch(this, '${mod.source}', '${mod.id}', '${_escapeAttr(mod.name)}', '${mod.project_type || 'mod'}')">Install</button>`
        }
      </div>
    </div>
  `;
}

async function _installModFromSearch(btn, source, modId, modName, projectType) {
  if (!_modsActiveInstallation) return;
  btn.disabled = true;
  btn.textContent = '...';

  try {
    let downloadInfo;
    if (source === 'modrinth') {
      downloadInfo = await ModrinthAPI.getDownloadUrl(modId, _modsActiveInstallation.version);
    } else {
      // CurseForge - need to get the mod info again
      const installations = await window.icey.getInstallations();
      // For CurseForge, we stored latestFiles in the search result; use ID to re-search
      const results = await CurseForgeAPI.search(modName, projectType === 'resourcepack' ? 'resourcepack' : 'mod', 5);
      const mod = results.find(r => String(r.id) === String(modId));
      if (mod) {
        downloadInfo = CurseForgeAPI.getDownloadUrl(mod);
      }
    }

    if (!downloadInfo || !downloadInfo.url) {
      Toast.error('Could not find download for this mod');
      btn.disabled = false;
      btn.textContent = 'Install';
      return;
    }

    const installationsDir = await PathUtils.getInstallationsDir();
    const base = installationsDir + '/' + _modsActiveInstallation.id + '/.minecraft';
    const folder = projectType === 'resourcepack' ? 'resourcepacks' : 'mods';
    const dest = base + '/' + folder + '/' + downloadInfo.filename;

    const result = await window.icey.downloadFile(downloadInfo.url, dest);
    if (result.error) {
      Toast.error('Download failed: ' + result.error);
      btn.disabled = false;
      btn.textContent = 'Install';
    } else {
      btn.outerHTML = '<span class="badge-installed">Installed</span>';
      Toast.success('Installed ' + modName);
      await _refreshInstalledMods();
    }
  } catch (e) {
    Toast.error('Install failed: ' + e.message);
    btn.disabled = false;
    btn.textContent = 'Install';
  }
}

async function _refreshInstalledMods() {
  if (!_modsActiveInstallation) return;

  const data = await window.icey.getInstalledMods(_modsActiveInstallation.id);
  const allItems = [...(data.mods || []), ...(data.resourcePacks || [])];
  _modsInstalledFiles = allItems;

  const countEl = document.getElementById('mods-installed-count');
  if (countEl) countEl.textContent = allItems.length;

  const list = document.getElementById('mods-installed-list');
  if (!list) return;

  if (allItems.length === 0) {
    list.innerHTML = '<div class="mods-empty" style="grid-column:1/-1;">No mods or resource packs installed yet.</div>';
    return;
  }

  list.innerHTML = allItems.map(item => {
    const size = _formatFileSize(item.size);
    const typeClass = item.type === 'mod' ? 'mod' : 'resourcepack';
    const typeLabel = item.type === 'mod' ? 'Mod' : 'Resource Pack';
    const iconSvg = item.type === 'mod'
      ? '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>'
      : '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 3v18"/></svg>';

    return `
      <div class="mod-installed-card">
        <div class="mod-installed-icon">${iconSvg}</div>
        <div class="mod-installed-info">
          <div class="mod-installed-name">${_escapeHtml(item.name)}</div>
          <div class="mod-installed-meta">
            <span class="mod-type-badge ${typeClass}">${typeLabel}</span>
            <span class="mod-installed-size">${size}</span>
          </div>
        </div>
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
