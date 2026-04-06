const PathUtils = {
  _dataDir: null,
  _installationsDir: null,

  async getDataDir() {
    if (!this._dataDir) {
      this._dataDir = await window.icey.getDataDir();
    }
    return this._dataDir;
  },

  async getInstallationsDir() {
    if (!this._installationsDir) {
      this._installationsDir = await window.icey.getInstallationsDir();
    }
    return this._installationsDir;
  }
};
