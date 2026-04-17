let _serversList = [];

async function ServersPageInit() {
  const page = document.getElementById('page-servers');
  _serversList = await window.icey.getServers();
  _renderServersPage(page);
  _refreshAllPings();
}

function _renderServersPage(page) {
  if (!page) page = document.getElementById('page-servers');

  page.innerHTML = `
    <div class="servers-inner">
      <div class="servers-header">
        <div class="servers-title">Servers</div>
        <button class="btn-create-install" onclick="_openAddServerModal()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          Add Server
        </button>
      </div>

      ${_serversList.length === 0 ? `
        <div class="servers-empty">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/>
            <line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/>
          </svg>
          <div class="servers-empty-title">No servers yet</div>
          <div class="servers-empty-subtitle">Add your favorite Minecraft servers to quickly track their status.</div>
        </div>
      ` : `
        <div class="servers-list" id="servers-list">
          ${_serversList.map(s => _renderServerCard(s)).join('')}
        </div>
      `}
    </div>
  `;
}

function _renderServerCard(s) {
  return `
    <div class="server-card" id="server-${s.id}">
      <div class="server-icon">
        <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="2" y="4" width="20" height="16" rx="2"/>
          <line x1="6" y1="8" x2="6.01" y2="8"/>
          <line x1="6" y1="12" x2="6.01" y2="12"/>
          <line x1="6" y1="16" x2="6.01" y2="16"/>
        </svg>
      </div>
      <div class="server-info">
        <div class="server-name">${_escapeHtml(s.name)}</div>
        <div class="server-address">${_escapeHtml(s.address)}</div>
        <div class="server-status" id="server-status-${s.id}">
          <span class="server-status-dot pinging"></span>
          <span class="server-status-text">Pinging...</span>
        </div>
      </div>
      <div class="server-actions">
        <button class="install-card-action-btn" onclick="_refreshServerPing('${_escapeAttr(s.id)}')" title="Refresh">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
        </button>
        <button class="install-card-action-btn delete" onclick="_deleteServer('${_escapeAttr(s.id)}')" title="Delete">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    </div>
  `;
}

async function _refreshAllPings() {
  for (const s of _serversList) {
    _refreshServerPing(s.id);
  }
}

async function _refreshServerPing(id) {
  const s = _serversList.find(x => x.id === id);
  if (!s) return;
  const statusEl = document.getElementById('server-status-' + id);
  if (statusEl) statusEl.innerHTML = '<span class="server-status-dot pinging"></span><span class="server-status-text">Pinging...</span>';
  const result = await window.icey.pingServer(s.address);
  if (!statusEl) return;
  if (result.online) {
    statusEl.innerHTML = `
      <span class="server-status-dot online"></span>
      <span class="server-status-text">${result.players.online}/${result.players.max} online • ${_escapeHtml(result.version)}</span>
    `;
  } else {
    statusEl.innerHTML = '<span class="server-status-dot offline"></span><span class="server-status-text">Offline</span>';
  }
}

function _openAddServerModal() {
  showModal(`
    <div class="create-install-modal">
      <div class="create-modal-header">
        <button class="modal-close" onclick="closeModal()">&times;</button>
        <div class="create-modal-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8">
            <rect x="2" y="4" width="20" height="16" rx="2"/>
            <line x1="6" y1="8" x2="6.01" y2="8"/>
            <line x1="6" y1="12" x2="6.01" y2="12"/>
          </svg>
        </div>
        <div class="create-modal-title">Add Server</div>
        <div class="create-modal-subtitle">Track a Minecraft server's status</div>
      </div>
      <div class="create-modal-form">
        <div class="form-group">
          <label class="form-label">Server Name</label>
          <input class="form-input" id="add-server-name" placeholder="My Server" maxlength="64">
        </div>
        <div class="form-group">
          <label class="form-label">Address</label>
          <input class="form-input" id="add-server-address" placeholder="play.example.com:25565" maxlength="128">
        </div>
        <button class="btn-create-submit" onclick="_submitAddServer()">Add Server</button>
      </div>
    </div>
  `);
}

async function _submitAddServer() {
  const name = document.getElementById('add-server-name').value.trim();
  const address = document.getElementById('add-server-address').value.trim();
  if (!name || !address) { Toast.error('Name and address required'); return; }
  const result = await window.icey.saveServer({ name, address });
  if (result.error) { Toast.error(result.error); return; }
  _serversList = result.servers;
  closeModal();
  Toast.success('Added ' + name);
  _renderServersPage();
  _refreshAllPings();
}

async function _deleteServer(id) {
  const result = await window.icey.deleteServer(id);
  if (result.error) { Toast.error(result.error); return; }
  _serversList = result.servers;
  Toast.info('Server removed');
  _renderServersPage();
}

function _escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]); }
function _escapeAttr(s) { return String(s).replace(/"/g, '&quot;'); }
