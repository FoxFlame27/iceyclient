const SettingsManager = {
  _settings: null,
  _listeners: [],

  async load() {
    this._settings = await window.icey.getSettings();
    this._applyTheme();
    this._applyAccent();
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
    this._notifyListeners(key, value);
  },

  async setMultiple(obj) {
    if (!this._settings) await this.load();
    Object.assign(this._settings, obj);
    await window.icey.saveSettings(obj);
    if ('theme' in obj) this._applyTheme();
    if ('accentColor' in obj) this._applyAccent();
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

  _applyAccent() {
    const color = this._settings?.accentColor || '#5bc8f5';
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
