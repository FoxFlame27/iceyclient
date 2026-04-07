const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const https = require('https');
const http = require('http');
const { spawn, execSync } = require('child_process');
const crypto = require('crypto');

let mainWindow = null;
let mcProcess = null;

// ── Paths ──────────────────────────────────────────────
function getDataDir() {
  if (process.platform === 'win32') {
    return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), 'IceyClient');
  }
  if (process.platform === 'darwin') {
    return path.join(os.homedir(), 'Library', 'Application Support', 'IceyClient');
  }
  return path.join(os.homedir(), '.iceyclient');
}

const DATA_DIR = getDataDir();
const INSTALLATIONS_FILE = path.join(DATA_DIR, 'installations.json');
const SETTINGS_FILE = path.join(DATA_DIR, 'settings.json');
const LOGS_DIR = path.join(DATA_DIR, 'logs');
const CACHE_DIR = path.join(DATA_DIR, 'cache');
const INSTALLATIONS_DIR = path.join(DATA_DIR, 'installations');

function ensureDirs() {
  [DATA_DIR, LOGS_DIR, CACHE_DIR, INSTALLATIONS_DIR].forEach(d => {
    fs.mkdirSync(d, { recursive: true });
  });
}

// ── Logger ─────────────────────────────────────────────
const LOG_FILE = path.join(LOGS_DIR, 'latest.log');

function log(level, message) {
  const timestamp = new Date().toISOString();
  const line = `[${timestamp}] [${level.toUpperCase()}] ${message}\n`;
  try {
    fs.appendFileSync(LOG_FILE, line);
  } catch (_) { /* ignore */ }
}

// ── Atomic JSON write ──────────────────────────────────
function writeJsonAtomic(filePath, data) {
  const tmp = filePath + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf-8');
  fs.renameSync(tmp, filePath);
}

// ── Installations ──────────────────────────────────────
function getDefaultInstallations() {
  return [];
}

function readInstallations() {
  try {
    if (fs.existsSync(INSTALLATIONS_FILE)) {
      const data = JSON.parse(fs.readFileSync(INSTALLATIONS_FILE, 'utf-8'));
      if (Array.isArray(data)) return data;
    }
  } catch (e) {
    log('warn', 'Corrupt installations.json, resetting: ' + e.message);
    if (mainWindow) {
      mainWindow.webContents.send('mc-event', { type: 'toast', level: 'error', message: 'installations.json was corrupt and has been reset.' });
    }
  }
  return getDefaultInstallations();
}

function writeInstallations(data) {
  writeJsonAtomic(INSTALLATIONS_FILE, data);
}

// ── Settings ───────────────────────────────────────────
function getDefaultSettings() {
  return {
    theme: 'dark',
    accentColor: '#5bc8f5',
    homeBackgroundOpacity: 80,
    showSessionTimer: true,
    javaPath: '',
    allocatedRam: 2048,
    jvmArgs: '',
    closeLauncherOnStart: false,
    username: 'Player',
    language: 'en',
    uiSounds: true,
    volume: 60,
    uuid: crypto.randomUUID()
  };
}

function readSettings() {
  try {
    if (fs.existsSync(SETTINGS_FILE)) {
      const data = JSON.parse(fs.readFileSync(SETTINGS_FILE, 'utf-8'));
      if (data && typeof data === 'object' && !Array.isArray(data)) {
        const defaults = getDefaultSettings();
        return { ...defaults, ...data };
      }
    }
  } catch (e) {
    log('warn', 'Corrupt settings.json, resetting: ' + e.message);
    if (mainWindow) {
      mainWindow.webContents.send('mc-event', { type: 'toast', level: 'error', message: 'settings.json was corrupt and has been reset.' });
    }
  }
  const defaults = getDefaultSettings();
  writeJsonAtomic(SETTINGS_FILE, defaults);
  return defaults;
}

function writeSettings(data) {
  writeJsonAtomic(SETTINGS_FILE, data);
}

// ── Download helper ────────────────────────────────────
function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    const doRequest = (requestUrl, redirectCount) => {
      if (redirectCount > 5) {
        return reject(new Error('Too many redirects'));
      }
      const proto = requestUrl.startsWith('https') ? https : http;
      const req = proto.get(requestUrl, { headers: { 'User-Agent': 'IceyClient/1.0.0' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          return doRequest(res.headers.location, redirectCount + 1);
        }
        if (res.statusCode !== 200) {
          return reject(new Error(`HTTP ${res.statusCode}`));
        }
        const totalBytes = parseInt(res.headers['content-length'] || '0', 10);
        let downloadedBytes = 0;
        const dir = path.dirname(dest);
        fs.mkdirSync(dir, { recursive: true });
        const tmpDest = dest + '.tmp';
        const file = fs.createWriteStream(tmpDest);
        res.on('data', (chunk) => {
          downloadedBytes += chunk.length;
          file.write(chunk);
          if (mainWindow && totalBytes > 0) {
            mainWindow.webContents.send('download-progress', {
              downloaded: downloadedBytes,
              total: totalBytes,
              percent: Math.round((downloadedBytes / totalBytes) * 100)
            });
          }
        });
        res.on('end', () => {
          file.end(() => {
            try {
              fs.renameSync(tmpDest, dest);
              resolve(dest);
            } catch (e) {
              reject(e);
            }
          });
        });
        res.on('error', (e) => {
          file.destroy();
          try { fs.unlinkSync(tmpDest); } catch (_) { /* */ }
          reject(e);
        });
      });
      req.on('error', reject);
      req.setTimeout(30000, () => {
        req.destroy();
        reject(new Error('Download timed out'));
      });
    };
    doRequest(url, 0);
  });
}

// ── Java detection ─────────────────────────────────────
function autoDetectJava() {
  try {
    if (process.platform === 'win32') {
      const commonPaths = [
        path.join(process.env.PROGRAMFILES || 'C:\\Program Files', 'Java'),
        path.join(process.env['PROGRAMFILES(X86)'] || 'C:\\Program Files (x86)', 'Java'),
        path.join(process.env.PROGRAMFILES || 'C:\\Program Files', 'Eclipse Adoptium'),
        path.join(process.env.PROGRAMFILES || 'C:\\Program Files', 'Microsoft'),
      ];
      // Try 'where java' first
      try {
        const result = execSync('where java', { encoding: 'utf-8', timeout: 5000 }).trim().split('\n')[0].trim();
        if (result && fs.existsSync(result)) return result;
      } catch (_) { /* */ }
      // Search common paths
      for (const base of commonPaths) {
        if (fs.existsSync(base)) {
          const dirs = fs.readdirSync(base).sort().reverse();
          for (const dir of dirs) {
            const javaBin = path.join(base, dir, 'bin', 'java.exe');
            if (fs.existsSync(javaBin)) return javaBin;
          }
        }
      }
    } else {
      try {
        const result = execSync('which java', { encoding: 'utf-8', timeout: 5000 }).trim();
        if (result && fs.existsSync(result)) return result;
      } catch (_) { /* */ }
      // Check common macOS/Linux paths
      const paths = ['/usr/bin/java', '/usr/local/bin/java', '/opt/homebrew/bin/java'];
      for (const p of paths) {
        if (fs.existsSync(p)) return p;
      }
    }
  } catch (e) {
    log('error', 'Java auto-detect failed: ' + e.message);
  }
  return '';
}

// ── Find official Minecraft launcher ──────────────────
function findMinecraftLauncher() {
  if (process.platform === 'win32') {
    const candidates = [
      path.join(process.env['PROGRAMFILES(X86)'] || 'C:\\Program Files (x86)', 'Minecraft Launcher', 'MinecraftLauncher.exe'),
      path.join(process.env.PROGRAMFILES || 'C:\\Program Files', 'Minecraft Launcher', 'MinecraftLauncher.exe'),
      path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Minecraft Launcher', 'MinecraftLauncher.exe'),
      path.join(process.env.APPDATA || '', '.minecraft', 'launcher', 'MinecraftLauncher.exe'),
    ];
    // Also try Microsoft Store version
    const msStorePath = path.join(process.env.LOCALAPPDATA || '', 'Packages');
    if (fs.existsSync(msStorePath)) {
      try {
        const dirs = fs.readdirSync(msStorePath).filter(d => d.includes('Microsoft.4297127D64EC6'));
        for (const d of dirs) {
          const exe = path.join(msStorePath, d, 'LocalCache', 'Local', 'runtime', 'Minecraft.exe');
          if (fs.existsSync(exe)) return exe;
        }
      } catch (_) { /* */ }
    }
    for (const p of candidates) {
      if (fs.existsSync(p)) return p;
    }
    // Try PATH
    try {
      const result = execSync('where MinecraftLauncher.exe', { encoding: 'utf-8', timeout: 5000 }).trim().split('\n')[0].trim();
      if (result && fs.existsSync(result)) return result;
    } catch (_) { /* */ }
  } else if (process.platform === 'darwin') {
    const macPath = '/Applications/Minecraft.app/Contents/MacOS/launcher';
    if (fs.existsSync(macPath)) return macPath;
  } else {
    // Linux
    try {
      const result = execSync('which minecraft-launcher', { encoding: 'utf-8', timeout: 5000 }).trim();
      if (result && fs.existsSync(result)) return result;
    } catch (_) { /* */ }
    const linuxPaths = ['/usr/bin/minecraft-launcher', '/opt/minecraft-launcher/minecraft-launcher'];
    for (const p of linuxPaths) {
      if (fs.existsSync(p)) return p;
    }
  }
  return null;
}

// ── Minecraft launch ───────────────────────────────────
function launchMinecraft(installationId) {
  return new Promise(async (resolve, reject) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) {
      return reject(new Error('Installation not found'));
    }

    const settings = readSettings();

    // Find the official Minecraft launcher
    const launcherPath = settings.minecraftLauncherPath || findMinecraftLauncher();
    if (!launcherPath) {
      return reject(new Error('LAUNCHER_NOT_FOUND'));
    }

    log('info', `Using Minecraft launcher: ${launcherPath}`);

    if (mainWindow) {
      mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Starting Minecraft via official launcher...', level: 'info' });
      mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Launcher: ' + launcherPath, level: 'info' });
      mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Version: ' + installation.version, level: 'info' });
    }

    try {
      mcProcess = spawn(launcherPath, [], {
        stdio: 'pipe',
        detached: false,
        windowsHide: false
      });

      mcProcess.stdout.on('data', (data) => {
        const text = data.toString().trim();
        if (text) {
          log('info', '[MC] ' + text);
          if (mainWindow) {
            mainWindow.webContents.send('mc-event', { type: 'console-log', message: text, level: 'info' });
          }
        }
      });

      mcProcess.stderr.on('data', (data) => {
        const text = data.toString().trim();
        if (text) {
          log('error', '[MC-ERR] ' + text);
          if (mainWindow) {
            mainWindow.webContents.send('mc-event', { type: 'console-log', message: text, level: 'error' });
          }
        }
      });

      mcProcess.on('error', (err) => {
        log('error', 'MC launcher error: ' + err.message);
        mcProcess = null;
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-error', message: err.message });
        }
      });

      mcProcess.on('close', (code) => {
        log('info', `MC launcher exited with code ${code}`);
        mcProcess = null;
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-stopped', code });
        }
      });

      // Signal started after short delay
      setTimeout(() => {
        if (mcProcess && mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-started' });
        }
      }, 2000);

      if (settings.closeLauncherOnStart) {
        setTimeout(() => {
          if (mainWindow) mainWindow.hide();
        }, 3000);
      }

      resolve({ success: true });
    } catch (e) {
      log('error', 'Failed to spawn MC launcher: ' + e.message);
      reject(e);
    }
  });
}

// ── App ready ──────────────────────────────────────────
app.whenReady().then(() => {
  ensureDirs();

  // Reset log file on start
  try { fs.writeFileSync(LOG_FILE, `[${new Date().toISOString()}] [INFO] Icey Client started\n`); } catch (_) { /* */ }

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 700,
    frame: false,
    transparent: false,
    titleBarStyle: 'hidden',
    backgroundColor: '#080c18',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    },
    icon: path.join(__dirname, 'src', 'assets', 'icon.png'),
    show: false
  });

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
    if (mcProcess) {
      try { mcProcess.kill(); } catch (_) { /* */ }
    }
  });

  // ── IPC Handlers ───────────────────────────────────
  // Window controls
  ipcMain.on('window-minimize', () => mainWindow && mainWindow.minimize());
  ipcMain.on('window-maximize', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.on('window-close', () => mainWindow && mainWindow.close());

  // Minecraft
  ipcMain.handle('launch-mc', async (_, installationId) => {
    try {
      return await launchMinecraft(installationId);
    } catch (e) {
      return { error: e.message };
    }
  });

  ipcMain.on('stop-mc', () => {
    if (mcProcess) {
      try {
        mcProcess.kill();
        mcProcess = null;
        log('info', 'MC process killed by user');
      } catch (e) {
        log('error', 'Failed to kill MC: ' + e.message);
      }
    }
  });

  // Installations
  ipcMain.handle('get-installations', () => readInstallations());

  ipcMain.handle('save-installation', (_, data) => {
    const installations = readInstallations();
    const idx = installations.findIndex(i => i.id === data.id);
    if (idx >= 0) {
      installations[idx] = data;
    } else {
      installations.push(data);
    }
    writeInstallations(installations);
    return installations;
  });

  ipcMain.handle('delete-installation', (_, id) => {
    let installations = readInstallations();
    installations = installations.filter(i => i.id !== id);
    writeInstallations(installations);
    // Remove installation directory
    const installDir = path.join(INSTALLATIONS_DIR, id);
    try {
      fs.rmSync(installDir, { recursive: true, force: true });
    } catch (e) {
      log('warn', 'Failed to remove installation dir: ' + e.message);
    }
    return installations;
  });

  ipcMain.handle('update-installation-image', async (_, id, imagePath) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === id);
    if (!installation) return { error: 'Installation not found' };
    // Copy image to installation directory
    const destDir = path.join(INSTALLATIONS_DIR, id);
    fs.mkdirSync(destDir, { recursive: true });
    const ext = path.extname(imagePath);
    const destFile = path.join(destDir, 'cover' + ext);
    fs.copyFileSync(imagePath, destFile);
    installation.image = destFile;
    writeInstallations(installations);
    return installation;
  });

  // Settings
  ipcMain.handle('get-settings', () => readSettings());

  ipcMain.handle('save-settings', (_, data) => {
    const current = readSettings();
    const merged = { ...current, ...data };
    writeSettings(merged);
    return merged;
  });

  // Files & system
  ipcMain.on('open-folder', (_, folderPath) => {
    shell.openPath(folderPath).catch(e => log('error', 'Failed to open folder: ' + e.message));
  });

  ipcMain.on('open-external', (_, url) => {
    shell.openExternal(url).catch(e => log('error', 'Failed to open URL: ' + e.message));
  });

  ipcMain.handle('select-file', async (_, filters) => {
    const result = await dialog.showOpenDialog(mainWindow, {
      properties: ['openFile'],
      filters: filters || [{ name: 'All Files', extensions: ['*'] }]
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
  });

  ipcMain.handle('copy-file', async (_, src, dest) => {
    try {
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(src, dest);
      return { success: true };
    } catch (e) {
      return { error: e.message };
    }
  });

  ipcMain.handle('get-installed-mods', (_, installationId) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return { mods: [], resourcePacks: [] };

    const installDir = path.join(INSTALLATIONS_DIR, installation.id, '.minecraft');
    const modsDir = path.join(installDir, 'mods');
    const rpDir = path.join(installDir, 'resourcepacks');

    const mods = [];
    const resourcePacks = [];

    if (fs.existsSync(modsDir)) {
      for (const file of fs.readdirSync(modsDir)) {
        if (file.endsWith('.jar')) {
          const filePath = path.join(modsDir, file);
          const stats = fs.statSync(filePath);
          mods.push({
            filename: file,
            name: file.replace('.jar', '').replace(/-/g, ' '),
            size: stats.size,
            type: 'mod'
          });
        }
      }
    }

    if (fs.existsSync(rpDir)) {
      for (const file of fs.readdirSync(rpDir)) {
        if (file.endsWith('.zip')) {
          const filePath = path.join(rpDir, file);
          const stats = fs.statSync(filePath);
          resourcePacks.push({
            filename: file,
            name: file.replace('.zip', '').replace(/-/g, ' '),
            size: stats.size,
            type: 'resourcepack'
          });
        }
      }
    }

    return { mods, resourcePacks };
  });

  ipcMain.handle('delete-mod', (_, installationId, filename) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return { error: 'Installation not found' };

    const installDir = path.join(INSTALLATIONS_DIR, installation.id, '.minecraft');
    // Check in mods and resourcepacks
    const modsPath = path.join(installDir, 'mods', filename);
    const rpPath = path.join(installDir, 'resourcepacks', filename);

    try {
      if (fs.existsSync(modsPath)) fs.unlinkSync(modsPath);
      else if (fs.existsSync(rpPath)) fs.unlinkSync(rpPath);
      return { success: true };
    } catch (e) {
      return { error: e.message };
    }
  });

  // Downloads
  ipcMain.handle('download-file', async (_, url, dest) => {
    try {
      await downloadFile(url, dest);
      return { success: true, path: dest };
    } catch (e) {
      return { error: e.message };
    }
  });

  // Java
  ipcMain.handle('auto-detect-java', () => {
    return autoDetectJava();
  });

  // App version
  ipcMain.handle('get-app-version', () => {
    try {
      const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, 'package.json'), 'utf-8'));
      return pkg.version || '1.0.0';
    } catch (_) {
      return '1.0.0';
    }
  });

  // Get data directory
  ipcMain.handle('get-data-dir', () => DATA_DIR);

  // Get installations directory
  ipcMain.handle('get-installations-dir', () => INSTALLATIONS_DIR);

  // Install Fabric (via Fabric Meta API — no Java required)
  ipcMain.handle('install-fabric', async (_, installationId, mcVersion) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return { error: 'Installation not found' };

    // Find the default .minecraft directory (where the official launcher looks)
    let mcDir;
    if (process.platform === 'win32') {
      mcDir = path.join(process.env.APPDATA || '', '.minecraft');
    } else if (process.platform === 'darwin') {
      mcDir = path.join(os.homedir(), 'Library', 'Application Support', 'minecraft');
    } else {
      mcDir = path.join(os.homedir(), '.minecraft');
    }

    try {
      // Step 1: Get latest stable Fabric loader version
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'fabric-progress', message: 'Fetching Fabric loader versions...' });

      const loaderVersions = await new Promise((resolve, reject) => {
        https.get(`https://meta.fabricmc.net/v2/versions/loader/${mcVersion}`, { headers: { 'User-Agent': 'IceyClient/1.0.0' } }, (res) => {
          let data = '';
          res.on('data', (chunk) => data += chunk);
          res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
          res.on('error', reject);
        }).on('error', reject);
      });

      if (!loaderVersions || loaderVersions.length === 0) {
        return { error: 'No Fabric loader available for ' + mcVersion };
      }

      // Pick the first (latest) loader version
      const loaderVersion = loaderVersions[0].loader.version;
      log('info', `Installing Fabric: MC ${mcVersion} + Loader ${loaderVersion}`);

      // Step 2: Get the profile JSON
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'fabric-progress', message: 'Downloading Fabric profile...' });

      const profileJson = await new Promise((resolve, reject) => {
        https.get(`https://meta.fabricmc.net/v2/versions/loader/${mcVersion}/${loaderVersion}/profile/json`, { headers: { 'User-Agent': 'IceyClient/1.0.0' } }, (res) => {
          let data = '';
          res.on('data', (chunk) => data += chunk);
          res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
          res.on('error', reject);
        }).on('error', reject);
      });

      const versionId = profileJson.id; // e.g. "fabric-loader-0.16.14-1.21.4"

      // Step 3: Save the version JSON to .minecraft/versions/<id>/<id>.json
      const versionDir = path.join(mcDir, 'versions', versionId);
      fs.mkdirSync(versionDir, { recursive: true });
      const jsonPath = path.join(versionDir, versionId + '.json');
      fs.writeFileSync(jsonPath, JSON.stringify(profileJson, null, 2), 'utf-8');
      log('info', 'Saved Fabric profile JSON to ' + jsonPath);

      // Step 4: Download all Fabric libraries
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'fabric-progress', message: 'Downloading Fabric libraries...' });

      const libDir = path.join(mcDir, 'libraries');
      const libs = profileJson.libraries || [];
      let downloaded = 0;

      for (const lib of libs) {
        if (lib.name) {
          // Parse maven coordinate: group:artifact:version
          const parts = lib.name.split(':');
          if (parts.length >= 3) {
            const groupPath = parts[0].replace(/\./g, '/');
            const artifactId = parts[1];
            const version = parts[2];
            const jarName = `${artifactId}-${version}.jar`;
            const mavenPath = `${groupPath}/${artifactId}/${version}/${jarName}`;
            const destPath = path.join(libDir, mavenPath.replace(/\//g, path.sep));

            if (!fs.existsSync(destPath)) {
              // Determine download URL
              let url;
              if (lib.url) {
                url = lib.url + mavenPath;
              } else {
                url = 'https://maven.fabricmc.net/' + mavenPath;
              }

              try {
                await downloadFile(url, destPath);
                downloaded++;
              } catch (e) {
                // Try alternative maven repos
                const altUrls = [
                  'https://maven.fabricmc.net/' + mavenPath,
                  'https://libraries.minecraft.net/' + mavenPath,
                  'https://repo.maven.apache.org/maven2/' + mavenPath,
                ];
                let success = false;
                for (const altUrl of altUrls) {
                  if (altUrl === url) continue;
                  try {
                    await downloadFile(altUrl, destPath);
                    downloaded++;
                    success = true;
                    break;
                  } catch (_) { /* try next */ }
                }
                if (!success) {
                  log('warn', 'Failed to download Fabric lib: ' + lib.name);
                }
              }
            } else {
              downloaded++;
            }
          }
        }

        if (mainWindow) {
          mainWindow.webContents.send('mc-event', {
            type: 'fabric-progress',
            message: `Downloading Fabric libraries (${downloaded}/${libs.length})...`
          });
        }
      }

      // Step 5: Add Fabric profile to launcher_profiles.json
      const profilesPath = path.join(mcDir, 'launcher_profiles.json');
      try {
        let profiles = {};
        if (fs.existsSync(profilesPath)) {
          profiles = JSON.parse(fs.readFileSync(profilesPath, 'utf-8'));
        }
        if (!profiles.profiles) profiles.profiles = {};

        const profileKey = 'icey-fabric-' + mcVersion;
        profiles.profiles[profileKey] = {
          name: installation.name || ('Fabric ' + mcVersion),
          type: 'custom',
          lastVersionId: versionId,
          icon: 'Furnace',
          created: new Date().toISOString(),
          lastUsed: new Date().toISOString(),
        };

        fs.writeFileSync(profilesPath, JSON.stringify(profiles, null, 2), 'utf-8');
        log('info', 'Added Fabric profile to launcher_profiles.json');
      } catch (e) {
        log('warn', 'Could not update launcher_profiles.json: ' + e.message);
      }

      // Also save into our own installation directory for reference
      const ownInstallDir = path.join(INSTALLATIONS_DIR, installationId, '.minecraft', 'versions', versionId);
      fs.mkdirSync(ownInstallDir, { recursive: true });
      fs.writeFileSync(path.join(ownInstallDir, versionId + '.json'), JSON.stringify(profileJson, null, 2), 'utf-8');

      log('info', `Fabric installed: ${versionId} (${downloaded} libraries)`);
      return { success: true, versionId };
    } catch (e) {
      log('error', 'Fabric install error: ' + e.message);
      return { error: 'Fabric installation failed: ' + e.message };
    }
  });

  // Download vanilla libraries from version JSON
  ipcMain.handle('download-libraries', async (_, installationId, versionJsonUrl) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return { error: 'Installation not found' };

    const installDir = path.join(INSTALLATIONS_DIR, installationId, '.minecraft');
    const libDir = path.join(installDir, 'libraries');

    try {
      // Fetch version JSON
      const jsonData = await new Promise((resolve, reject) => {
        const proto = versionJsonUrl.startsWith('https') ? https : http;
        proto.get(versionJsonUrl, { headers: { 'User-Agent': 'IceyClient/1.0.0' } }, (res) => {
          let data = '';
          res.on('data', (chunk) => data += chunk);
          res.on('end', () => {
            try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
          });
          res.on('error', reject);
        }).on('error', reject);
      });

      if (!jsonData.libraries || !Array.isArray(jsonData.libraries)) {
        return { error: 'No libraries found in version JSON' };
      }

      // Also download asset index
      if (jsonData.assetIndex) {
        const assetsDir = path.join(installDir, 'assets', 'indexes');
        const assetIndexPath = path.join(assetsDir, jsonData.assetIndex.id + '.json');
        if (!fs.existsSync(assetIndexPath)) {
          try {
            await downloadFile(jsonData.assetIndex.url, assetIndexPath);
          } catch (e) {
            log('warn', 'Failed to download asset index: ' + e.message);
          }
        }
      }

      const total = jsonData.libraries.length;
      let completed = 0;
      let failed = 0;

      for (const lib of jsonData.libraries) {
        // Check rules (some libraries are OS-specific)
        if (lib.rules) {
          let allowed = false;
          for (const rule of lib.rules) {
            if (rule.action === 'allow') {
              if (!rule.os) allowed = true;
              else if (rule.os.name === 'windows' && process.platform === 'win32') allowed = true;
              else if (rule.os.name === 'osx' && process.platform === 'darwin') allowed = true;
              else if (rule.os.name === 'linux' && process.platform === 'linux') allowed = true;
            }
            if (rule.action === 'disallow') {
              if (rule.os && rule.os.name === 'windows' && process.platform === 'win32') allowed = false;
              else if (rule.os && rule.os.name === 'osx' && process.platform === 'darwin') allowed = false;
              else if (rule.os && rule.os.name === 'linux' && process.platform === 'linux') allowed = false;
            }
          }
          if (!allowed) { completed++; continue; }
        }

        // Get artifact download info
        const artifact = lib.downloads?.artifact;
        if (artifact && artifact.url && artifact.path) {
          const destPath = path.join(libDir, artifact.path);
          if (!fs.existsSync(destPath)) {
            try {
              await downloadFile(artifact.url, destPath);
            } catch (e) {
              log('warn', 'Failed to download library: ' + (lib.name || artifact.path) + ' - ' + e.message);
              failed++;
            }
          }
        } else if (lib.name) {
          // Construct path from maven-style name (group:artifact:version)
          const parts = lib.name.split(':');
          if (parts.length >= 3) {
            const groupPath = parts[0].replace(/\./g, '/');
            const artifactId = parts[1];
            const version = parts[2];
            const jarName = `${artifactId}-${version}.jar`;
            const mavenPath = `${groupPath}/${artifactId}/${version}/${jarName}`;
            const destPath = path.join(libDir, mavenPath);
            if (!fs.existsSync(destPath)) {
              // Try Mojang's library repo
              const url = `https://libraries.minecraft.net/${mavenPath}`;
              try {
                await downloadFile(url, destPath);
              } catch (e) {
                log('warn', 'Failed to download library from maven: ' + lib.name + ' - ' + e.message);
                failed++;
              }
            }
          }
        }

        completed++;
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', {
            type: 'lib-progress',
            completed,
            total,
            name: lib.name || 'unknown'
          });
        }
      }

      log('info', `Libraries downloaded: ${completed - failed}/${total} (${failed} failed)`);
      return { success: true, total, failed };
    } catch (e) {
      log('error', 'Library download error: ' + e.message);
      return { error: e.message };
    }
  });

  // Get cache dir
  ipcMain.handle('get-cache-dir', () => CACHE_DIR);
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    // Re-create window on macOS dock click
  }
});
