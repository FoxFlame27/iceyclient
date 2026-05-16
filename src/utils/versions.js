const VersionManager = {
  _versions: null,
  _versionsAt: 0,
  _loading: false,
  /** Cache lifetime — 10 minutes. Long enough that opening + closing
   *  "Create Installation" several times in a row doesn't refetch, short
   *  enough that a new MC release picked up by Mojang's manifest will
   *  show up without restarting the launcher. */
  _TTL_MS: 10 * 60 * 1000,

  async fetchVersions() {
    if (this._versions && (Date.now() - this._versionsAt) < this._TTL_MS) return this._versions;
    if (this._loading) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (this._versions) {
            clearInterval(check);
            resolve(this._versions);
          }
        }, 100);
      });
    }

    this._loading = true;
    try {
      // Cache-bust the URL so a Service Worker / CDN doesn't serve us
      // an old manifest. Mojang's manifest is small (~50KB).
      const response = await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json?t=' + Date.now());
      if (!response.ok) throw new Error('Failed to fetch version manifest');
      const data = await response.json();

      // Filter versions from 1.8.9 to latest, releases only
      const minVersion = [1, 8, 9];
      this._versions = data.versions.filter(v => {
        if (v.type !== 'release') return false;
        const parts = v.id.split('.').map(Number);
        if (parts[0] > minVersion[0]) return true;
        if (parts[0] === minVersion[0] && (parts[1] || 0) > minVersion[1]) return true;
        if (parts[0] === minVersion[0] && (parts[1] || 0) === minVersion[1] && (parts[2] || 0) >= minVersion[2]) return true;
        return false;
      });

      this._versionsAt = Date.now();
      this._loading = false;
      return this._versions;
    } catch (e) {
      this._loading = false;
      throw e;
    }
  },

  async getVersionDetail(versionUrl) {
    const response = await fetch(versionUrl);
    if (!response.ok) throw new Error('Failed to fetch version detail');
    return response.json();
  }
};
