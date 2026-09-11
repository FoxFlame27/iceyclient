const SettingsManager = {
  _settings: null,
  _listeners: [],

  async load() {
    this._settings = await window.icey.getSettings();
    this._applyTheme();
    this._applyAccent();
    this._applyLayout();
    this._applySkin();
    return this._settings;
  },

  get(key) {
    return this._settings ? this._settings[key] : undefined;
  },

  getAll() {
    return this._settings || {};
  },

  async set(key, value) {
    if (!this._settings) await this.load();
    this._settings[key] = value;
    await window.icey.saveSettings({ [key]: value });
    if (key === 'theme') this._applyTheme();
    if (key === 'accentColor') this._applyAccent();
    if (key === 'layoutTheme') this._applyLayout();
    if (key === 'skiflameMode') { this._applySkin(); this._applyAccent(); }
    this._notifyListeners(key, value);
  },

  async setMultiple(obj) {
    if (!this._settings) await this.load();
    Object.assign(this._settings, obj);
    await window.icey.saveSettings(obj);
    if ('theme' in obj) this._applyTheme();
    if ('accentColor' in obj) this._applyAccent();
    if ('layoutTheme' in obj) this._applyLayout();
    if ('skiflameMode' in obj) { this._applySkin(); this._applyAccent(); }
    for (const [k, v] of Object.entries(obj)) {
      this._notifyListeners(k, v);
    }
  },

  onChange(callback) {
    this._listeners.push(callback);
    return () => {
      this._listeners = this._listeners.filter(l => l !== callback);
    };
  },

  _notifyListeners(key, value) {
    this._listeners.forEach(l => l(key, value));
  },

  _applyTheme() {
    const theme = this._settings?.theme || 'dark';
    document.documentElement.setAttribute('data-theme', theme);
  },

  // Toggles the entire layout shell between two presets:
  //   'classic' — sidebar on the left (default)
  //   'liquid'  — nav bar at the bottom + 4-button home page,
  //               LiquidBounce-style
  // CSS keys off [data-layout="liquid"] on <html>.
  _applyLayout() {
    const layout = this._settings?.layoutTheme || 'classic';
    document.documentElement.setAttribute('data-layout', layout);
  },

  // Secret "Skiflame" skin: flame palette, Skiflame logo + background.
  // CSS keys off [data-skin="skiflame"] on <html>; pages read
  // SettingsManager.isSkiflame() for the branding images.
  isSkiflame() { return !!this._settings?.skiflameMode; },

  _applySkin() {
    const on = this.isSkiflame();
    document.documentElement.setAttribute('data-skin', on ? 'skiflame' : 'icey');
    document.title = on ? 'Skiflame' : 'Icey Client';
    const t = document.querySelector('.titlebar-title');
    if (t) t.textContent = on ? 'SKIFLAME' : 'ICEY CLIENT';
  },

  _applyAccent() {
    // Skiflame overrides the accent with its blue-violet flame colour.
    const color = this.isSkiflame() ? '#ff8a3d' : (this._settings?.accentColor || '#5bc8f5');
    const r = parseInt(color.slice(1, 3), 16);
    const g = parseInt(color.slice(3, 5), 16);
    const b = parseInt(color.slice(5, 7), 16);
    document.documentElement.style.setProperty('--accent', color);
    document.documentElement.style.setProperty('--accent-bright', color);
    document.documentElement.style.setProperty('--accent-dim', `rgba(${r},${g},${b},0.12)`);
    document.documentElement.style.setProperty('--accent-glow', `rgba(${r},${g},${b},0.25)`);
    document.documentElement.style.setProperty('--border', `rgba(${r},${g},${b},0.12)`);
    document.documentElement.style.setProperty('--border-hover', `rgba(${r},${g},${b},0.3)`);
    document.documentElement.style.setProperty('--border-active', `rgba(${r},${g},${b},0.6)`);
  }
};
