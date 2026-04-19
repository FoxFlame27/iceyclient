const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('icey', {
  // Minecraft
  launchMinecraft: (installationId) => ipcRenderer.invoke('launch-mc', installationId),
  stopMinecraft: (launchId) => ipcRenderer.send('stop-mc', launchId),
  getRunningMc: () => ipcRenderer.invoke('get-running-mc'),
  onMcEvent: (callback) => {
    const handler = (_, data) => callback(data);
    ipcRenderer.on('mc-event', handler);
    return () => ipcRenderer.removeListener('mc-event', handler);
  },

  // Installations
  getInstallations: () => ipcRenderer.invoke('get-installations'),
  saveInstallation: (data) => ipcRenderer.invoke('save-installation', data),
  deleteInstallation: (id) => ipcRenderer.invoke('delete-installation', id),
  updateInstallationImage: (id, imagePath) => ipcRenderer.invoke('update-installation-image', id, imagePath),

  // Settings
  getSettings: () => ipcRenderer.invoke('get-settings'),
  saveSettings: (data) => ipcRenderer.invoke('save-settings', data),

  // Files & system
  openFolder: (folderPath) => ipcRenderer.send('open-folder', folderPath),
  selectFile: (filters) => ipcRenderer.invoke('select-file', filters),
  copyFile: (src, dest) => ipcRenderer.invoke('copy-file', src, dest),
  getInstalledMods: (installationId) => ipcRenderer.invoke('get-installed-mods', installationId),
  deleteMod: (installationId, filename) => ipcRenderer.invoke('delete-mod', installationId, filename),
  toggleMod: (installationId, filename) => ipcRenderer.invoke('toggle-mod', installationId, filename),
  registerResourcepack: (installationId, filename) => ipcRenderer.invoke('register-resourcepack', installationId, filename),

  // Downloads
  downloadFile: (url, dest) => ipcRenderer.invoke('download-file', url, dest),
  onDownloadProgress: (callback) => {
    const handler = (_, data) => callback(data);
    ipcRenderer.on('download-progress', handler);
    return () => ipcRenderer.removeListener('download-progress', handler);
  },

  // Libraries
  downloadLibraries: (installationId, versionJsonUrl) => ipcRenderer.invoke('download-libraries', installationId, versionJsonUrl),
  downloadMcLibraries: (versionJsonUrl) => ipcRenderer.invoke('download-mc-libraries', versionJsonUrl),
  getMcDir: () => ipcRenderer.invoke('get-mc-dir'),
  getInstallGameDir: (installationId) => ipcRenderer.invoke('get-install-game-dir', installationId),

  // Fabric
  installFabric: (installationId, mcVersion) => ipcRenderer.invoke('install-fabric', installationId, mcVersion),

  // Shaders
  installIris: (mcVersion) => ipcRenderer.invoke('install-iris', mcVersion),
  getInstalledShaderpacks: (installationId) => ipcRenderer.invoke('get-installed-shaderpacks', installationId),
  deleteShaderpack: (filename) => ipcRenderer.invoke('delete-shaderpack', filename),

  // Panoramas
  getPanoramas: () => ipcRenderer.invoke('get-panoramas'),
  getPanoramaPreview: (filename) => ipcRenderer.invoke('get-panorama-preview', filename),

  // Updates
  checkUpdate: () => ipcRenderer.invoke('check-update'),

  // Misc
  autoDetectJava: () => ipcRenderer.invoke('auto-detect-java'),
  getAppVersion: () => ipcRenderer.invoke('get-app-version'),
  openExternal: (url) => ipcRenderer.send('open-external', url),
  getDataDir: () => ipcRenderer.invoke('get-data-dir'),
  getInstallationsDir: () => ipcRenderer.invoke('get-installations-dir'),
  getCacheDir: () => ipcRenderer.invoke('get-cache-dir'),

  // Auth
  msLogin: () => ipcRenderer.invoke('ms-login'),
  msLogout: () => ipcRenderer.invoke('ms-logout'),
  getAuth: () => ipcRenderer.invoke('get-auth'),
  getAccounts: () => ipcRenderer.invoke('get-accounts'),
  switchAccount: (uuid) => ipcRenderer.invoke('switch-account', uuid),
  removeAccount: (uuid) => ipcRenderer.invoke('remove-account', uuid),
  addOfflineAccount: (username) => ipcRenderer.invoke('add-offline-account', username),
  uploadSkin: (skinPath, variant) => ipcRenderer.invoke('upload-skin', skinPath, variant),
  uploadSkinFromUrl: (url, variant) => ipcRenderer.invoke('upload-skin-from-url', url, variant),
  getMcProfile: () => ipcRenderer.invoke('get-mc-profile'),

  // Window controls
  minimizeWindow: () => ipcRenderer.send('window-minimize'),
  maximizeWindow: () => ipcRenderer.send('window-maximize'),
  closeWindow: () => ipcRenderer.send('window-close')
});
