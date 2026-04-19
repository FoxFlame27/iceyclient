const MinecraftLauncher = {
  _state: 'idle', // idle | starting | running
  _sessionStart: null,      // kept for getSessionTime UI — tracks the oldest live session
  _sessions: new Map(),     // launchId -> startTime (ms)
  _timerInterval: null,
  _listeners: [],
  _mcEventCleanup: null,

  init() {
    if (this._mcEventCleanup) return;
    this._mcEventCleanup = window.icey.onMcEvent((data) => {
      switch (data.type) {
        case 'mc-started':
          this._onStarted(data.launchId);
          break;
        case 'mc-stopped':
          this._onStopped(data.launchId);
          break;
        case 'mc-error':
          this._onStopped(data.launchId);
          Toast.error(data.message || 'Minecraft encountered an error');
          break;
        case 'mc-crashed':
          _showCrashModal(data);
          break;
        case 'toast':
          Toast.show(data.message, data.level || 'info');
          break;
      }
    });
  },

  _onStarted(launchId) {
    // mc-started fires once per matching log line (LWJGL/OpenAL/Setting user:),
    // so we only record the start the FIRST time for each launchId.
    const id = launchId || 'legacy';
    if (!this._sessions.has(id)) {
      this._sessions.set(id, Date.now());
    }
    this._setState('running');
  },

  _onStopped(launchId) {
    const id = launchId || 'legacy';
    const startedAt = this._sessions.get(id);
    if (startedAt) {
      const elapsed = Math.floor((Date.now() - startedAt) / 1000);
      if (elapsed > 0) {
        const prev = SettingsManager.get('totalPlaytime') || 0;
        SettingsManager.set('totalPlaytime', prev + elapsed);
      }
      this._sessions.delete(id);
    }
    // Only return to idle when ALL instances have stopped.
    if (this._sessions.size === 0) {
      this._setState('idle');
    } else {
      // Keep in running state for UI.
      this._sessionStart = Math.min(...this._sessions.values());
      this._notifyListeners();
    }
  },

  getState() {
    return this._state;
  },

  async launch(installationId) {
    if (this._state !== 'idle') return;
    if (!installationId) {
      Toast.info('Select an installation first');
      return;
    }

    this._setState('starting');
    const result = await window.icey.launchMinecraft(installationId);

    if (result.error) {
      this._setState('idle');
      if (result.error === 'JAVA_NOT_FOUND') {
        showModal(`
          <div class="modal-header">
            <h2 class="modal-title">Java Required</h2>
            <button class="modal-close" onclick="closeModal()">
              <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
            </button>
          </div>
          <div class="modal-body">Java is not installed or could not be found. Please install Java 17+ to launch Minecraft.</div>
          <div class="modal-footer">
            <button class="modal-btn modal-btn-primary" onclick="window.icey.openExternal('https://adoptium.net'); closeModal();">Open Java Download</button>
          </div>
        `);
      } else if (result.error === 'VERSION_NOT_FOUND') {
        showModal(`
          <div class="modal-header">
            <h2 class="modal-title">Minecraft Not Found</h2>
            <button class="modal-close" onclick="closeModal()">
              <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
            </button>
          </div>
          <div class="modal-body">Version ${this._currentVersion || ''} was not found in your .minecraft folder. Launch this version once from the official Minecraft launcher first, then try again.</div>
          <div class="modal-footer">
            <button class="modal-btn modal-btn-primary" onclick="switchPage('installations'); closeModal();">Go to Installations</button>
          </div>
        `);
      } else {
        Toast.error(result.error);
      }
    }
  },

  stop() {
    if (this._state === 'running') {
      window.icey.stopMinecraft();
      this._setState('idle');
    }
  },

  _setState(state) {
    this._state = state;
    if (state === 'running') {
      // Oldest live session determines the timer shown in the UI.
      if (this._sessions.size > 0) {
        this._sessionStart = Math.min(...this._sessions.values());
      } else if (!this._sessionStart) {
        this._sessionStart = Date.now();
      }
    } else {
      this._sessionStart = null;
    }
    this._notifyListeners();
  },

  getSessionTime() {
    if (!this._sessionStart) return '00:00:00';
    const elapsed = Math.floor((Date.now() - this._sessionStart) / 1000);
    const h = String(Math.floor(elapsed / 3600)).padStart(2, '0');
    const m = String(Math.floor((elapsed % 3600) / 60)).padStart(2, '0');
    const s = String(elapsed % 60).padStart(2, '0');
    return `${h}:${m}:${s}`;
  },

  onChange(callback) {
    this._listeners.push(callback);
    return () => {
      this._listeners = this._listeners.filter(l => l !== callback);
    };
  },

  _notifyListeners() {
    this._listeners.forEach(l => l(this._state));
  }
};

MinecraftLauncher.init();

function _showCrashModal(data) {
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
  const linesHtml = (data.tail || []).map(l => `<div>${esc(l)}</div>`).join('') || '<div style="color:var(--text-muted)">(no output captured)</div>';
  const user = data.username ? ` &bull; ${esc(data.username)}` : '';
  showModal(`
    <div class="crash-modal">
      <div class="crash-modal-header">
        <div>
          <div class="crash-modal-title">Minecraft crashed</div>
          <div class="crash-modal-sub">Exit code ${data.code}${user}</div>
        </div>
        <button class="modal-close" onclick="closeModal()">&times;</button>
      </div>
      <div class="crash-modal-body" id="crash-modal-body">${linesHtml}</div>
      <div class="crash-modal-footer">
        <button class="options-btn" onclick="_copyCrashLog()">Copy</button>
        <button class="options-btn" onclick="closeModal()">Close</button>
      </div>
    </div>
  `);
  // Scroll the body to the bottom so the latest / likely-crash lines are visible
  setTimeout(() => {
    const body = document.getElementById('crash-modal-body');
    if (body) body.scrollTop = body.scrollHeight;
  }, 20);
  window._lastCrashTail = (data.tail || []).join('\n');
}

function _copyCrashLog() {
  try {
    navigator.clipboard.writeText(window._lastCrashTail || '');
    Toast.success('Crash log copied');
  } catch (_) { Toast.error('Copy failed'); }
}
