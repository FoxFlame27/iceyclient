const MinecraftLauncher = {
  _state: 'idle', // idle | starting | running
  _sessionStart: null,
  _timerInterval: null,
  _listeners: [],
  _mcEventCleanup: null,

  init() {
    if (this._mcEventCleanup) return;
    this._mcEventCleanup = window.icey.onMcEvent((data) => {
      switch (data.type) {
        case 'mc-started':
          this._setState('running');
          break;
        case 'mc-stopped':
          this._setState('idle');
          break;
        case 'mc-error':
          this._setState('idle');
          Toast.error(data.message || 'Minecraft encountered an error');
          break;
        case 'toast':
          Toast.show(data.message, data.level || 'info');
          break;
      }
    });
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
      if (result.error === 'LAUNCHER_NOT_FOUND') {
        showModal(`
          <div class="modal-header">
            <h2 class="modal-title">Minecraft Launcher Not Found</h2>
            <button class="modal-close" onclick="closeModal()">
              <svg width="14" height="14" viewBox="0 0 12 12"><line x1="2" y1="2" x2="10" y2="10" stroke="currentColor" stroke-width="1.5"/><line x1="10" y1="2" x2="2" y2="10" stroke="currentColor" stroke-width="1.5"/></svg>
            </button>
          </div>
          <div class="modal-body">The official Minecraft Launcher was not found on your system. Please install it from minecraft.net or set the path in Settings.</div>
          <div class="modal-footer">
            <button class="modal-btn modal-btn-primary" onclick="window.icey.openExternal('https://www.minecraft.net/download'); closeModal();">Download Minecraft</button>
          </div>
        `);
      } else if (result.error === 'JAVA_NOT_FOUND') {
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
          <div class="modal-body">The version files for this installation are missing. Try recreating the installation.</div>
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
      this._sessionStart = Date.now();
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
