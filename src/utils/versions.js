const VersionManager = {
  _versions: null,
  _loading: false,

  async fetchVersions() {
    if (this._versions) return this._versions;
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
      const response = await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json');
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
