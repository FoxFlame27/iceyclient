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

// ── Minecraft launch ───────────────────────────────────
function launchMinecraft(installationId) {
  return new Promise(async (resolve, reject) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) {
      return reject(new Error('Installation not found'));
    }

    const settings = readSettings();
    let javaPath = settings.javaPath || autoDetectJava();
    if (!javaPath) {
      return reject(new Error('JAVA_NOT_FOUND'));
    }

    const installDir = path.join(INSTALLATIONS_DIR, installation.id, '.minecraft');
    const versionDir = path.join(installDir, 'versions', installation.version);
    const versionJar = path.join(versionDir, `${installation.version}.jar`);

    if (!fs.existsSync(versionJar)) {
      return reject(new Error('VERSION_NOT_FOUND'));
    }

    // Build classpath
    let classpath = versionJar;
    const libDir = path.join(installDir, 'libraries');
    if (fs.existsSync(libDir)) {
      const walkJars = (dir) => {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
          const full = path.join(dir, entry.name);
          if (entry.isDirectory()) walkJars(full);
          else if (entry.name.endsWith('.jar')) classpath += (process.platform === 'win32' ? ';' : ':') + full;
        }
      };
      walkJars(libDir);
    }

    // Check for fabric
    let mainClass = 'net.minecraft.client.main.Main';
    if (installation.platform === 'fabric') {
      const fabricVersionDir = path.join(installDir, 'versions');
      if (fs.existsSync(fabricVersionDir)) {
        const fabricDirs = fs.readdirSync(fabricVersionDir).filter(d => d.startsWith('fabric-loader'));
        if (fabricDirs.length > 0) {
          const fabricJsonPath = path.join(fabricVersionDir, fabricDirs[0], `${fabricDirs[0]}.json`);
          if (fs.existsSync(fabricJsonPath)) {
            try {
              const fabricJson = JSON.parse(fs.readFileSync(fabricJsonPath, 'utf-8'));
              if (fabricJson.mainClass) mainClass = fabricJson.mainClass;
              if (fabricJson.libraries) {
                for (const lib of fabricJson.libraries) {
                  if (lib.name) {
                    const parts = lib.name.split(':');
                    if (parts.length >= 3) {
                      const groupPath = parts[0].replace(/\./g, path.sep);
                      const artifactId = parts[1];
                      const version = parts[2];
                      const jarName = `${artifactId}-${version}.jar`;
                      const jarPath = path.join(libDir, groupPath, artifactId, version, jarName);
                      if (fs.existsSync(jarPath)) {
                        classpath += (process.platform === 'win32' ? ';' : ':') + jarPath;
                      }
                    }
                  }
                }
              }
            } catch (e) {
              log('error', 'Failed to parse fabric json: ' + e.message);
            }
          }
        }
      }
    }

    const ram = settings.allocatedRam || 2048;
    const username = settings.username || 'Player';
    const uuid = settings.uuid || crypto.randomUUID();

    const args = [];
    if (settings.jvmArgs) {
      args.push(...settings.jvmArgs.split(/\s+/).filter(Boolean));
    }
    args.push(`-Xmx${ram}M`, '-Xms512M');
    args.push('-cp', classpath);
    args.push(mainClass);
    args.push('--username', username);
    args.push('--version', installation.version);
    args.push('--gameDir', installDir);
    args.push('--assetsDir', path.join(installDir, 'assets'));
    args.push('--assetIndex', installation.version);
    args.push('--uuid', uuid);
    args.push('--accessToken', '0');
    args.push('--userType', 'legacy');

    log('info', `Launching MC: ${javaPath} ${args.join(' ')}`);

    try {
      mcProcess = spawn(javaPath, args, {
        cwd: installDir,
        stdio: 'pipe',
        detached: false,
        windowsHide: true
      });

      mcProcess.stdout.on('data', (data) => {
        const text = data.toString();
        log('info', '[MC] ' + text.trim());
        if (text.includes('Setting user:') || text.includes('LWJGL') || text.includes('Minecraft')) {
          if (mainWindow) {
            mainWindow.webContents.send('mc-event', { type: 'mc-started' });
          }
        }
      });

      mcProcess.stderr.on('data', (data) => {
        log('error', '[MC-ERR] ' + data.toString().trim());
      });

      mcProcess.on('error', (err) => {
        log('error', 'MC process error: ' + err.message);
        mcProcess = null;
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-error', message: err.message });
        }
      });

      mcProcess.on('close', (code) => {
        log('info', `MC process exited with code ${code}`);
        mcProcess = null;
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-stopped', code });
        }
      });

      // Consider it started after a short delay if no stdout signal
      setTimeout(() => {
        if (mcProcess && mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-started' });
        }
      }, 3000);

      if (settings.closeLauncherOnStart) {
        setTimeout(() => {
          if (mainWindow) mainWindow.hide();
        }, 2000);
      }

      resolve({ success: true });
    } catch (e) {
      log('error', 'Failed to spawn MC: ' + e.message);
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

  // Install Fabric
  ipcMain.handle('install-fabric', async (_, installationId, mcVersion) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return { error: 'Installation not found' };

    const javaPath = readSettings().javaPath || autoDetectJava();
    if (!javaPath) return { error: 'JAVA_NOT_FOUND' };

    const fabricJar = path.join(CACHE_DIR, 'fabric-installer.jar');

    // Download fabric installer if not cached
    if (!fs.existsSync(fabricJar)) {
      try {
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'fabric-progress', message: 'Downloading Fabric installer...' });
        await downloadFile('https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar', fabricJar);
      } catch (e) {
        return { error: 'Failed to download Fabric installer: ' + e.message };
      }
    }

    // Run fabric installer
    const installDir = path.join(INSTALLATIONS_DIR, installationId, '.minecraft');
    fs.mkdirSync(installDir, { recursive: true });

    return new Promise((resolve) => {
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'fabric-progress', message: 'Installing Fabric...' });

      const proc = spawn(javaPath, [
        '-jar', fabricJar,
        'client',
        '-dir', installDir,
        '-mcversion', mcVersion,
        '-noprofile'
      ], {
        cwd: installDir,
        stdio: 'pipe',
        windowsHide: true
      });

      let output = '';
      proc.stdout.on('data', (d) => { output += d.toString(); });
      proc.stderr.on('data', (d) => { output += d.toString(); });

      proc.on('error', (err) => {
        log('error', 'Fabric installer error: ' + err.message);
        resolve({ error: 'Java not found. Fabric requires Java to install.' });
      });

      proc.on('close', (code) => {
        if (code === 0) {
          log('info', 'Fabric installed successfully for ' + mcVersion);
          resolve({ success: true });
        } else {
          log('error', 'Fabric installer failed: ' + output);
          resolve({ error: 'Fabric installation failed. Exit code: ' + code });
        }
      });
    });
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
