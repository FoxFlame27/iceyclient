const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('icey', {
  // Minecraft
  launchMinecraft: (installationId) => ipcRenderer.invoke('launch-mc', installationId),
  stopMinecraft: () => ipcRenderer.send('stop-mc'),
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

  // Downloads
  downloadFile: (url, dest) => ipcRenderer.invoke('download-file', url, dest),
  onDownloadProgress: (callback) => {
    const handler = (_, data) => callback(data);
    ipcRenderer.on('download-progress', handler);
    return () => ipcRenderer.removeListener('download-progress', handler);
  },

  // Libraries
  downloadLibraries: (installationId, versionJsonUrl) => ipcRenderer.invoke('download-libraries', installationId, versionJsonUrl),

  // Fabric
  installFabric: (installationId, mcVersion) => ipcRenderer.invoke('install-fabric', installationId, mcVersion),

  // Misc
  autoDetectJava: () => ipcRenderer.invoke('auto-detect-java'),
  getAppVersion: () => ipcRenderer.invoke('get-app-version'),
  openExternal: (url) => ipcRenderer.send('open-external', url),
  getDataDir: () => ipcRenderer.invoke('get-data-dir'),
  getInstallationsDir: () => ipcRenderer.invoke('get-installations-dir'),
  getCacheDir: () => ipcRenderer.invoke('get-cache-dir'),

  // Window controls
  minimizeWindow: () => ipcRenderer.send('window-minimize'),
  maximizeWindow: () => ipcRenderer.send('window-maximize'),
  closeWindow: () => ipcRenderer.send('window-close')
});
