const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const https = require('https');
const http = require('http');
const { spawn, execSync } = require('child_process');
const crypto = require('crypto');

let mainWindow = null;
// Map of active MC processes, keyed by a unique launch id. Multiple instances
// (one per account/installation) can coexist — launching a new one doesn't
// terminate the previous.
const mcProcesses = new Map();
let mcLaunchCounter = 0;
let _installFabricLoader = null; // set once IPC handlers are registered

// ── Icey Client network backend ────────────────────────
// Cloudflare Worker that hosts cape PNGs + presence heartbeats so
// other Icey Client users see each other's capes and online badges.
// See backend/README.md for deploy steps. Override with the
// ICEY_NETWORK_BASE_URL env var when developing against a local
// wrangler dev server (e.g. http://localhost:8787).
const ICEY_NETWORK_BASE_URL =
  process.env.ICEY_NETWORK_BASE_URL ||
  'http://138.199.163.183:8787';

// In-memory presence heartbeat. Started when MC launches with a
// signed-in account, stopped when the last MC process exits.
let presenceHeartbeatTimer = null;
const presenceActiveUuids = new Set();

// ── Icey network: helpers (module-scope so launchMinecraft can
//    reach them — earlier they lived inside app.whenReady() which
//    caused "startPresenceHeartbeat is not defined" at launch). ─
async function pingPresence(uuid) {
  try {
    const url = `${ICEY_NETWORK_BASE_URL}/presence/${encodeURIComponent(uuid)}`;
    await fetch(url, { method: 'POST' });
  } catch (e) {
    log('warn', 'presence ping failed for ' + uuid + ': ' + e.message);
  }
}
function startPresenceHeartbeat(uuid) {
  try {
    const settings = readSettings();
    if (settings.iceyNetworkPresence === false) return;
  } catch (_) {}
  presenceActiveUuids.add(uuid);
  pingPresence(uuid);
  if (presenceHeartbeatTimer) return;
  presenceHeartbeatTimer = setInterval(() => {
    for (const u of presenceActiveUuids) pingPresence(u);
  }, 60_000);
}
function stopPresenceHeartbeat(uuid) {
  if (uuid) presenceActiveUuids.delete(uuid);
  if (presenceActiveUuids.size === 0 && presenceHeartbeatTimer) {
    clearInterval(presenceHeartbeatTimer);
    presenceHeartbeatTimer = null;
  }
}
async function uploadCapeToNetwork(uuid, buf) {
  try {
    if (!uuid || !buf || !buf.length) return;
    const settings = readSettings();
    if (settings.iceyNetworkCapeShare === false) return;
    const url = `${ICEY_NETWORK_BASE_URL}/capes/${encodeURIComponent(uuid)}`;
    const res = await fetch(url, {
      method: 'PUT',
      headers: { 'content-type': 'image/png' },
      body: buf,
    });
    if (!res.ok) log('warn', `cape network upload returned ${res.status}`);
    else log('info', `cape uploaded to Icey network for ${uuid}`);
  } catch (e) {
    log('warn', 'cape network upload failed: ' + e.message);
  }
}

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
const DELETED_FILE = path.join(DATA_DIR, 'deleted-installations.json');

function readDeletedIds() {
  try {
    if (fs.existsSync(DELETED_FILE)) {
      const data = JSON.parse(fs.readFileSync(DELETED_FILE, 'utf-8'));
      if (Array.isArray(data)) return new Set(data);
    }
  } catch (_) {}
  return new Set();
}

function addDeletedId(id) {
  const deleted = readDeletedIds();
  deleted.add(id);
  writeJsonAtomic(DELETED_FILE, [...deleted]);
}

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
  _mirrorToBackup(filePath);
}

// ── Safety net for the launcher's small state files ─────────────────
// The whole data folder has been wiped on this machine during an app
// update (by something outside the launcher — we never delete it). Keep
// a second copy of the three files that hurt most to lose — login,
// installation list, settings — outside DATA_DIR, and put them back at
// startup if DATA_DIR turns up empty. Mods/Java are re-fetched by the
// launcher anyway; worlds can't be mirrored cheaply.
const BACKUP_DIR = process.platform === 'darwin'
  ? path.join(os.homedir(), 'Library', 'Preferences', 'IceyClient-backup')
  : process.platform === 'win32'
    ? path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'IceyClient-backup')
    : path.join(os.homedir(), '.config', 'iceyclient-backup');
const BACKED_UP_FILES = new Set(['auth.json', 'installations.json', 'settings.json']);

function _mirrorToBackup(filePath) {
  try {
    const name = path.basename(filePath);
    if (!BACKED_UP_FILES.has(name) || path.dirname(filePath) !== DATA_DIR) return;
    fs.mkdirSync(BACKUP_DIR, { recursive: true });
    fs.copyFileSync(filePath, path.join(BACKUP_DIR, name));
  } catch (_) {}
}

function _restoreFromBackupIfWiped() {
  const restored = [];
  try {
    for (const name of BACKED_UP_FILES) {
      const dst = path.join(DATA_DIR, name);
      const src = path.join(BACKUP_DIR, name);
      if (fs.existsSync(dst) || !fs.existsSync(src)) continue;
      fs.mkdirSync(DATA_DIR, { recursive: true });
      fs.copyFileSync(src, dst);
      restored.push(name);
    }
  } catch (_) {}
  return restored;
}

// ── Per-installation game directory (isolated mods/config/saves) ───
function getInstallGameDir(installationId) {
  return path.join(INSTALLATIONS_DIR, installationId, 'game');
}

// ── Installations ──────────────────────────────────────
function getDefaultMcDir() {
  if (process.platform === 'win32') {
    return path.join(process.env.APPDATA || '', '.minecraft');
  } else if (process.platform === 'darwin') {
    return path.join(os.homedir(), 'Library', 'Application Support', 'minecraft');
  }
  return path.join(os.homedir(), '.minecraft');
}

function readIceyInstallations() {
  try {
    if (fs.existsSync(INSTALLATIONS_FILE)) {
      const data = JSON.parse(fs.readFileSync(INSTALLATIONS_FILE, 'utf-8'));
      if (Array.isArray(data)) return data;
    }
  } catch (e) {
    log('warn', 'Corrupt installations.json, resetting: ' + e.message);
  }
  return [];
}

function readInstallations() {
  let iceyInstalls;
  try {
    iceyInstalls = readIceyInstallations();
  } catch (e) {
    log('error', 'readIceyInstallations failed: ' + e.message);
    iceyInstalls = [];
  }
  const deletedIds = readDeletedIds();
  iceyInstalls = iceyInstalls.filter(i => !deletedIds.has(i.id));
  const knownIds = new Set(iceyInstalls.map(i => i.id));

  // Scan real .minecraft/versions/ for MC launcher installations
  try {
    const mcDir = getDefaultMcDir();
    const versionsDir = path.join(mcDir, 'versions');
    log('info', 'Scanning for installations in: ' + versionsDir);
    if (fs.existsSync(versionsDir)) {
      const dirNames = fs.readdirSync(versionsDir);
      log('info', 'Found version dirs: ' + dirNames.join(', '));
      for (const versionId of dirNames) {
        if (versionId.startsWith('fabric-loader')) continue;
        if (knownIds.has(versionId)) continue;
        if (deletedIds.has(versionId)) continue;

        const vDir = path.join(versionsDir, versionId);
        try {
          const stat = fs.statSync(vDir);
          if (!stat.isDirectory()) continue;
        } catch (_) { continue; }

        const jsonPath = path.join(vDir, versionId + '.json');
        if (!fs.existsSync(jsonPath)) continue;

        let hasFabric = false;
        try {
          hasFabric = dirNames.some(v => v.startsWith('fabric-loader') && v.endsWith(versionId));
        } catch (_) { /* */ }

        iceyInstalls.push({
          id: versionId,
          name: versionId,
          version: versionId,
          platform: hasFabric ? 'fabric' : 'vanilla',
          fabricActive: hasFabric,
          selected: false,
          image: null,
          createdAt: 0,
          fromMcLauncher: true
        });
        knownIds.add(versionId);
      }
    }
  } catch (e) {
    log('warn', 'Failed to scan .minecraft/versions: ' + e.message);
  }

  // Auto-select first installation if none selected
  if (iceyInstalls.length > 0 && !iceyInstalls.some(i => i.selected)) {
    iceyInstalls[0].selected = true;
  }

  return iceyInstalls;
}

function writeInstallations(data) {
  // Save all installations (including auto-detected ones that the user has interacted with)
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
    allocatedRam: 4096,
    jvmArgs: '',
    closeLauncherOnStart: false,
    username: 'Player',
    language: 'en',
    uiSounds: true,
    volume: 60,
    uuid: crypto.randomUUID(),
    // Icey network — community features. Both default on.
    iceyNetworkCapeShare: true,        // PUT cape to backend after upload
    iceyNetworkPresence: true,         // POST presence heartbeat while MC is running
    iceyNetworkShowBadges: true        // (read by mod) draw Icey badge next to other players
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

// ── Natives helper: pick the correct classifier for this platform+arch ──
function getNativesClassifier() {
  const arch = process.arch; // 'x64', 'arm64', etc.
  if (process.platform === 'win32') return 'natives-windows';
  if (process.platform === 'darwin') return arch === 'arm64' ? 'natives-macos-arm64' : 'natives-macos';
  if (process.platform === 'linux') return arch === 'arm64' ? 'natives-linux-arm64' : 'natives-linux';
  return 'natives-linux';
}

// Extract native .so/.dll/.dylib files from a JAR (zip) into nativesDir
function extractNatives(jarPath, nativesDir) {
  const zlib = require('zlib');
  const buf = fs.readFileSync(jarPath);
  fs.mkdirSync(nativesDir, { recursive: true });
  // Simple ZIP reader — JARs are ZIP files
  let offset = 0;
  while (offset < buf.length - 4) {
    // Local file header signature = 0x04034b50
    if (buf.readUInt32LE(offset) !== 0x04034b50) break;
    const compMethod = buf.readUInt16LE(offset + 8);
    const compSize = buf.readUInt32LE(offset + 18);
    const uncompSize = buf.readUInt32LE(offset + 22);
    const nameLen = buf.readUInt16LE(offset + 26);
    const extraLen = buf.readUInt16LE(offset + 28);
    const fileName = buf.toString('utf8', offset + 30, offset + 30 + nameLen);
    const dataStart = offset + 30 + nameLen + extraLen;
    const dataEnd = dataStart + compSize;

    // Only extract native libraries, skip META-INF and directories
    if (!fileName.endsWith('/') && !fileName.startsWith('META-INF') &&
        (fileName.endsWith('.so') || fileName.endsWith('.dll') || fileName.endsWith('.dylib') || fileName.endsWith('.jnilib'))) {
      const baseName = path.basename(fileName);
      const destFile = path.join(nativesDir, baseName);
      if (!fs.existsSync(destFile)) {
        let data;
        if (compMethod === 0) {
          data = buf.slice(dataStart, dataEnd);
        } else if (compMethod === 8) {
          data = zlib.inflateRawSync(buf.slice(dataStart, dataEnd));
        }
        if (data) {
          fs.writeFileSync(destFile, data);
          log('info', 'Extracted native: ' + baseName);
        }
      }
    }
    offset = dataEnd;
  }
}

// Download and extract natives for a library into nativesDir
async function downloadAndExtractNatives(lib, libDir, nativesDir) {
  const classifier = getNativesClassifier();
  // Try classifiers object first
  const classifiers = lib.downloads?.classifiers;
  if (classifiers) {
    // Try exact classifier, then fallback variants
    const variants = [classifier];
    if (classifier === 'natives-linux-arm64') variants.push('natives-linux-aarch64');
    if (classifier === 'natives-macos-arm64') variants.push('natives-osx-arm64', 'natives-macos');
    if (classifier === 'natives-macos') variants.push('natives-osx');
    if (classifier === 'natives-linux') variants.push('natives-linux');

    for (const variant of variants) {
      const native = classifiers[variant];
      if (native?.url && native?.path) {
        const destPath = path.join(libDir, native.path);
        if (!fs.existsSync(destPath)) {
          try {
            await downloadFile(native.url, destPath);
          } catch (e) {
            log('warn', 'Failed to download native: ' + native.path + ' - ' + e.message);
            continue;
          }
        }
        extractNatives(destPath, nativesDir);
        return;
      }
    }
  }
  // Try natives map (older format): e.g. lib.natives = { linux: "natives-linux" }
  if (lib.natives) {
    const osKey = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'osx' : 'linux';
    const nativeClassifier = lib.natives[osKey];
    if (nativeClassifier && classifiers?.[nativeClassifier]) {
      const native = classifiers[nativeClassifier];
      if (native?.url && native?.path) {
        const destPath = path.join(libDir, native.path);
        if (!fs.existsSync(destPath)) {
          try { await downloadFile(native.url, destPath); } catch (e) {
            log('warn', 'Failed to download native (legacy): ' + native.path);
          }
        }
        extractNatives(destPath, nativesDir);
      }
    }
  }
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

// ── Minecraft launch (direct Java, uses existing .minecraft) ─────
// ── Prism Launcher file detection for Linux arm64 ──
function getPrismDataDir() {
  // Flatpak path (most common on Asahi/Fedora)
  const flatpakDir = path.join(os.homedir(), '.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher');
  if (fs.existsSync(flatpakDir)) return flatpakDir;
  // Standard paths
  const xdgData = process.env.XDG_DATA_HOME || path.join(os.homedir(), '.local/share');
  const standardDir = path.join(xdgData, 'PrismLauncher');
  if (fs.existsSync(standardDir)) return standardDir;
  return null;
}

function findPrismJava() {
  const prismDir = getPrismDataDir();
  if (!prismDir) return null;
  // Check known Java paths inside Prism's data
  const candidates = [
    path.join(prismDir, 'java', 'java-runtime-delta', 'bin', 'java'),
    path.join(prismDir, 'java', 'java-runtime-gamma', 'bin', 'java'),
    path.join(prismDir, 'java', 'java-runtime-beta', 'bin', 'java'),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return p;
  }
  // Search any java dir
  const javaDir = path.join(prismDir, 'java');
  if (fs.existsSync(javaDir)) {
    try {
      for (const entry of fs.readdirSync(javaDir)) {
        const javaBin = path.join(javaDir, entry, 'bin', 'java');
        if (fs.existsSync(javaBin)) return javaBin;
      }
    } catch (_) {}
  }
  return null;
}

function findPrismLibraries(version) {
  const prismDir = getPrismDataDir();
  if (!prismDir) return null;
  const libDir = path.join(prismDir, 'libraries');
  if (fs.existsSync(libDir)) return libDir;
  return null;
}

function findPrismNatives(version) {
  const prismDir = getPrismDataDir();
  if (!prismDir) return null;
  // Prism stores natives per-instance
  const instancesDir = path.join(prismDir, 'instances');
  if (!fs.existsSync(instancesDir)) return null;
  try {
    for (const inst of fs.readdirSync(instancesDir)) {
      // Match instance name to version (e.g. "1.21.11" or contains the version)
      if (inst === version || inst.includes(version)) {
        const nativesDir = path.join(instancesDir, inst, 'natives');
        if (fs.existsSync(nativesDir)) return nativesDir;
      }
    }
  } catch (_) {}
  return null;
}

// Locate the installed fabric-loader profile for this MC version and
// collect its libraries (downloading any that are missing). Returns the
// values launchMinecraft needs; falls back to vanilla when no loader dir exists.
async function _loadFabricProfile(mcDir, version, libDir) {
  let versionId = version;
  let mainClass = 'net.minecraft.client.main.Main';
  let extraJvmArgs = [];
  let fabricLibs = [];
      // Find fabric version directory
      const versionsDir = path.join(mcDir, 'versions');
      if (fs.existsSync(versionsDir)) {
        // Match `fabric-loader-<loader>-<version>` exactly on the trailing
        // version. `includes` would false-match shorter versions inside
        // longer ones — e.g. version "1.21.1" finding "...-1.21.11", which
        // pairs the wrong intermediary with the client jar and crashes
        // TinyRemapper with "Unfixable conflicts" during deobfuscation.
        const loaderVerOf = (d) => d.slice('fabric-loader-'.length, d.length - version.length - 1);
        // Newest loader first (several may exist after an in-place upgrade).
        const fabricDirs = fs.readdirSync(versionsDir)
          .filter(d => d.startsWith('fabric-loader') && d.endsWith('-' + version))
          .sort((a, b) => _compareVersions(_parseVersion(loaderVerOf(b)), _parseVersion(loaderVerOf(a))));
        if (fabricDirs.length === 0) {
          log('warn', 'No fabric-loader dir for MC ' + version + ' — install Fabric for this version (or recreate the installation)');
          if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'No Fabric loader installed for ' + version + ' — launching vanilla', level: 'warn' });
        }
        if (fabricDirs.length > 0) {
          versionId = fabricDirs[0];
          const fabricJsonPath = path.join(versionsDir, fabricDirs[0], fabricDirs[0] + '.json');
          if (fs.existsSync(fabricJsonPath)) {
            try {
              const fabricJson = JSON.parse(fs.readFileSync(fabricJsonPath, 'utf-8'));
              if (fabricJson.mainClass) mainClass = fabricJson.mainClass;
              if (fabricJson.arguments?.jvm) {
                extraJvmArgs = fabricJson.arguments.jvm.filter(a => typeof a === 'string');
              }
              if (fabricJson.libraries) {
                const defaultLibDir = path.join(mcDir, 'libraries');
                for (const lib of fabricJson.libraries) {
                  if (lib.name) {
                    const parts = lib.name.split(':');
                    if (parts.length >= 3) {
                      const gp = parts[0].replace(/\./g, path.sep);
                      const jarName = `${parts[1]}-${parts[2]}.jar`;
                      // Check both Prism libs and default .minecraft libs
                      const candidates = [
                        path.join(libDir, gp, parts[1], parts[2], jarName),
                        path.join(defaultLibDir, gp, parts[1], parts[2], jarName)
                      ];
                      // Also try downloading if missing
                      let found = false;
                      for (const jarPath of candidates) {
                        if (fs.existsSync(jarPath)) { fabricLibs.push(jarPath); found = true; break; }
                      }
                      if (!found && lib.url) {
                        // Download from Fabric maven
                        const mavenPath = gp.replace(/\\/g, '/') + '/' + parts[1] + '/' + parts[2] + '/' + jarName;
                        const dlUrl = lib.url + mavenPath;
                        const destPath = path.join(defaultLibDir, gp, parts[1], parts[2], jarName);
                        try {
                          await downloadFile(dlUrl, destPath);
                          fabricLibs.push(destPath);
                          found = true;
                        } catch (e) {
                          log('warn', 'Failed to download fabric lib: ' + lib.name);
                        }
                      }
                      if (!found) log('warn', 'Fabric lib not found: ' + lib.name);
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
  return { versionId, mainClass, extraJvmArgs, fabricLibs };
}

// Add fabric libraries to the classpath, replacing vanilla duplicates
// (e.g. asm-9.6 vs asm-9.9).
function _mergeFabricLibsIntoClasspath(cpParts, fabricLibs) {
    for (const fp of fabricLibs) {
      if (cpParts.includes(fp)) continue;
      // Extract artifact name from path (e.g. "asm" from ".../asm/9.9/asm-9.9.jar")
      const fpParts = fp.replace(/\\/g, '/').split('/');
      const fpJarName = fpParts[fpParts.length - 1]; // e.g. "asm-9.9.jar"
      const fpArtifact = fpJarName.replace(/-[\d].*$/, ''); // e.g. "asm"
      // Remove any vanilla version of the same artifact
      const toRemove = [];
      for (let i = 0; i < cpParts.length; i++) {
        const cpJar = cpParts[i].replace(/\\/g, '/').split('/').pop();
        const cpArtifact = cpJar.replace(/-[\d].*$/, '');
        if (cpArtifact === fpArtifact && cpJar !== fpJarName) {
          toRemove.push(i);
        }
      }
      for (let i = toRemove.length - 1; i >= 0; i--) {
        cpParts.splice(toRemove[i], 1);
      }
      cpParts.push(fp);
    }
}

function launchMinecraft(installationId) {
  return new Promise(async (resolve, reject) => {
    try {
    log('info', '[LAUNCH] Starting launch for: ' + installationId);
    log('info', '[LAUNCH] Platform: ' + process.platform + ' Arch: ' + process.arch);

    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return reject(new Error('Installation not found'));
    log('info', '[LAUNCH] Installation found: ' + installation.version + ' (' + installation.platform + ')');

    const settings = readSettings();

    // On Linux arm64: use Prism Launcher's Java and libraries if available
    let javaPath = settings.javaPath;
    log('info', '[LAUNCH] Settings javaPath: ' + (javaPath || 'not set'));

    if (!javaPath && process.platform === 'linux' && process.arch === 'arm64') {
      log('info', '[LAUNCH] Checking for Prism Java...');
      const prismDir = getPrismDataDir();
      log('info', '[LAUNCH] Prism data dir: ' + (prismDir || 'NOT FOUND'));
      const prismJava = findPrismJava();
      if (prismJava) {
        javaPath = prismJava;
        log('info', '[LAUNCH] Using Prism Java: ' + javaPath);
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Using Prism Java: ' + javaPath, level: 'info' });
      } else {
        log('warn', '[LAUNCH] Prism Java NOT found');
      }
    }
    if (!javaPath) javaPath = autoDetectJava();
    if (!javaPath) {
      log('error', '[LAUNCH] No Java found anywhere!');
      return reject(new Error('JAVA_NOT_FOUND'));
    }
    log('info', '[LAUNCH] Final Java path: ' + javaPath);
    if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Java: ' + javaPath, level: 'info' });

    // Shared .minecraft for libraries/assets/versions
    const mcDir = getDefaultMcDir();
    const version = installation.version;
    log('info', '[LAUNCH] MC dir: ' + mcDir + ', version: ' + version);

    // On arm64 Linux: prefer Prism's library dir (has arm64 LWJGL natives)
    const prismLibDir = (process.platform === 'linux' && process.arch === 'arm64') ? findPrismLibraries(version) : null;
    const libDir = prismLibDir || path.join(mcDir, 'libraries');
    log('info', '[LAUNCH] Library dir: ' + libDir + (prismLibDir ? ' (from Prism)' : ' (default)'));
    if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Libraries: ' + libDir, level: 'info' });
    const assetsDir = path.join(mcDir, 'assets');

    // Per-installation game directory for isolated mods/config/saves
    const installGameDir = path.join(INSTALLATIONS_DIR, installation.id, 'game');
    fs.mkdirSync(installGameDir, { recursive: true });

    // Determine which version JSON to read (fabric or vanilla)
    let versionId = version;
    let mainClass = 'net.minecraft.client.main.Main';
    let extraJvmArgs = [];
    let fabricLibs = [];

    if (installation.platform === 'fabric') {
      const fp = await _loadFabricProfile(mcDir, version, libDir);
      versionId = fp.versionId; mainClass = fp.mainClass; extraJvmArgs = fp.extraJvmArgs; fabricLibs = fp.fabricLibs;
    }

    // Read the vanilla version JSON for the library list
    const versionJsonPath = path.join(mcDir, 'versions', version, version + '.json');
    if (!fs.existsSync(versionJsonPath)) {
      return reject(new Error('VERSION_NOT_FOUND'));
    }

    let versionJson;
    try {
      versionJson = JSON.parse(fs.readFileSync(versionJsonPath, 'utf-8'));
    } catch (e) {
      return reject(new Error('Failed to parse version JSON: ' + e.message));
    }

    // Java major required by this MC version (21 for 1.21.x, 25 for 26.x).
    // Swap in / download a suitable runtime when the detected one is too old.
    try {
      const requiredJava = versionJson.javaVersion?.majorVersion || 0;
      javaPath = await ensureJavaForVersion(javaPath, requiredJava);
      log('info', '[LAUNCH] Java after version gate: ' + javaPath);
    } catch (e) {
      log('error', '[LAUNCH] ' + e.message);
      return reject(e);
    }

    // Build classpath from version JSON (exact versions only — no conflicts)
    const sep = process.platform === 'win32' ? ';' : ':';
    const cpParts = [];

    const isArm64 = process.arch === 'arm64';
    const wrongNativesRe = isArm64 ? /natives-linux(?!-arm64|-aarch64)/ : /natives-linux-(arm64|aarch64)/;
    const arm64NativesToDownload = []; // collect LWJGL libs that need arm64 replacement

    for (const lib of (versionJson.libraries || [])) {
      // Check OS rules
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
            if (rule.os?.name === 'windows' && process.platform === 'win32') allowed = false;
            if (rule.os?.name === 'osx' && process.platform === 'darwin') allowed = false;
            if (rule.os?.name === 'linux' && process.platform === 'linux') allowed = false;
          }
        }
        if (!allowed) continue;
      }

      // On arm64 Linux: skip x64 native JARs and collect them for arm64 replacement
      if (lib.name && wrongNativesRe.test(lib.name)) {
        if (isArm64) {
          // Remember this lib so we can download the arm64 variant later
          const nameParts = lib.name.split(':');
          if (nameParts.length >= 3) {
            arm64NativesToDownload.push({
              group: nameParts[0],
              artifact: nameParts[1],
              version: nameParts[2].replace(/:.*/, '')
            });
          }
        }
        log('info', 'Skipping wrong-arch native: ' + lib.name);
        continue;
      }

      // Try downloads.artifact.path first
      const artifact = lib.downloads?.artifact;
      if (artifact?.path) {
        const jarPath = path.join(libDir, artifact.path);
        if (!fs.existsSync(jarPath) && artifact.url) {
          // Auto-download missing library
          try {
            log('info', 'Downloading missing lib: ' + (lib.name || artifact.path));
            if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Downloading library: ' + (lib.name || artifact.path), level: 'info' });
            await downloadFile(artifact.url, jarPath);
          } catch (dlErr) {
            log('warn', 'Failed to download lib: ' + (lib.name || artifact.path) + ' - ' + dlErr.message);
          }
        }
        if (fs.existsSync(jarPath)) { cpParts.push(jarPath); continue; }
      }

      // Fallback: resolve from maven-style name (group:artifact:version)
      if (lib.name) {
        const parts = lib.name.split(':');
        if (parts.length >= 3) {
          const groupPath = parts[0].replace(/\./g, path.sep);
          const artifactId = parts[1];
          const ver = parts[2];
          const mavenPath = groupPath.replace(/\\/g, '/') + '/' + artifactId + '/' + ver + '/' + artifactId + '-' + ver + '.jar';
          const jarPath = path.join(libDir, groupPath, artifactId, ver, `${artifactId}-${ver}.jar`);
          if (!fs.existsSync(jarPath)) {
            // Try downloading from Mojang's library server
            const libUrl = 'https://libraries.minecraft.net/' + mavenPath;
            try {
              log('info', 'Downloading missing lib (maven): ' + lib.name);
              if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Downloading library: ' + lib.name, level: 'info' });
              await downloadFile(libUrl, jarPath);
            } catch (dlErr) {
              log('warn', 'Failed to download lib from maven: ' + lib.name + ' - ' + dlErr.message);
            }
          }
          if (fs.existsSync(jarPath)) { cpParts.push(jarPath); continue; }
        }
      }

      // Log still-missing libs
      log('warn', 'Library not found after download attempt: ' + (lib.name || JSON.stringify(lib.downloads?.artifact?.path)));
    }

    // On arm64: download and add arm64 LWJGL native JARs to replace skipped x64 ones
    if (isArm64 && arm64NativesToDownload.length > 0) {
      for (const { group, artifact, version: libVer } of arm64NativesToDownload) {
        // artifact is e.g. "lwjgl" or "lwjgl-opengl" — the module name
        // Build the arm64 native jar name: e.g. lwjgl-3.3.3-natives-linux-arm64.jar
        const groupPath = group.replace(/\./g, '/');
        const arm64JarName = `${artifact}-${libVer}-natives-linux-arm64.jar`;
        const arm64MavenPath = `${groupPath}/${artifact}/${libVer}/${arm64JarName}`;
        const arm64JarPath = path.join(libDir, groupPath, artifact, libVer, arm64JarName);

        if (!fs.existsSync(arm64JarPath)) {
          // Try Maven Central first (LWJGL publishes arm64 natives there)
          const urls = [
            `https://repo1.maven.org/maven2/${arm64MavenPath}`,
            `https://libraries.minecraft.net/${arm64MavenPath}`
          ];
          let downloaded = false;
          for (const url of urls) {
            try {
              log('info', 'Downloading arm64 native: ' + arm64JarName + ' from ' + url);
              if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Downloading arm64 native: ' + arm64JarName, level: 'info' });
              await downloadFile(url, arm64JarPath);
              downloaded = true;
              break;
            } catch (e) {
              log('warn', 'Failed from ' + url + ': ' + e.message);
            }
          }
          if (!downloaded) {
            log('warn', 'Could not download arm64 native for ' + artifact);
            continue;
          }
        }
        cpParts.push(arm64JarPath);
        // Also extract native .so files
        try {
          const nDir = path.join(mcDir, 'versions', version, 'natives');
          extractNatives(arm64JarPath, nDir);
        } catch (e) {
          log('warn', 'Failed to extract arm64 native: ' + e.message);
        }
      }
    }

    _mergeFabricLibsIntoClasspath(cpParts, fabricLibs);

    // Add the client jar - download if missing
    const clientJar = path.join(mcDir, 'versions', version, version + '.jar');
    if (!fs.existsSync(clientJar)) {
      // Try to download the client jar from version JSON
      const clientDl = versionJson.downloads?.client;
      if (clientDl?.url) {
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Downloading client jar...', level: 'info' });
        try {
          await downloadFile(clientDl.url, clientJar);
        } catch (dlErr) {
          return reject(new Error('Failed to download client jar: ' + dlErr.message));
        }
      } else {
        return reject(new Error('VERSION_NOT_FOUND'));
      }
    }
    cpParts.push(clientJar);

    const classpath = cpParts.join(sep);

    // Determine asset index and download if missing
    const assetIndex = versionJson.assetIndex?.id || version;
    const assetIndexPath = path.join(assetsDir, 'indexes', assetIndex + '.json');
    if (!fs.existsSync(assetIndexPath) && versionJson.assetIndex?.url) {
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Downloading asset index...', level: 'info' });
      try {
        await downloadFile(versionJson.assetIndex.url, assetIndexPath);
      } catch (e) {
        log('warn', 'Failed to download asset index: ' + e.message);
      }
    }

    // Download missing asset objects
    if (fs.existsSync(assetIndexPath)) {
      try {
        const assetData = JSON.parse(fs.readFileSync(assetIndexPath, 'utf-8'));
        const objects = assetData.objects || {};
        const objectKeys = Object.keys(objects);
        let missing = 0;
        for (const key of objectKeys) {
          const hash = objects[key].hash;
          const prefix = hash.substring(0, 2);
          const objPath = path.join(assetsDir, 'objects', prefix, hash);
          if (!fs.existsSync(objPath)) missing++;
        }
        if (missing > 0) {
          if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: `Downloading ${missing} asset files...`, level: 'info' });
          let done = 0;
          // Download in batches of 10 for speed
          const missingList = [];
          for (const key of objectKeys) {
            const hash = objects[key].hash;
            const prefix = hash.substring(0, 2);
            const objPath = path.join(assetsDir, 'objects', prefix, hash);
            if (!fs.existsSync(objPath)) {
              missingList.push({ hash, prefix, objPath });
            }
          }
          for (let i = 0; i < missingList.length; i += 10) {
            const batch = missingList.slice(i, i + 10);
            await Promise.all(batch.map(async ({ hash, prefix, objPath }) => {
              try {
                await downloadFile(`https://resources.download.minecraft.net/${prefix}/${hash}`, objPath);
              } catch (_) { /* skip failed assets */ }
            }));
            done += batch.length;
            if (mainWindow && done % 50 === 0) {
              mainWindow.webContents.send('mc-event', { type: 'console-log', message: `Assets: ${done}/${missing}`, level: 'info' });
            }
          }
          if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: `Assets downloaded: ${done}/${missing}`, level: 'info' });
        }
      } catch (e) {
        log('warn', 'Asset download error: ' + e.message);
      }
    }

    // Clear build cache for this version on every launch
    const versionCacheDir = path.join(CACHE_DIR, 'builds', installation.id);
    try {
      if (fs.existsSync(versionCacheDir)) {
        fs.rmSync(versionCacheDir, { recursive: true, force: true });
      }
      fs.mkdirSync(versionCacheDir, { recursive: true });
      log('info', `Cleared build cache for installation ${installation.id}`);
    } catch (_) { /* */ }

    // Clean corrupted Fabric remapped jars
    if (installation.platform === 'fabric') {
      const fabricCacheDir = path.join(mcDir, '.fabric', 'remappedJars');
      if (fs.existsSync(fabricCacheDir)) {
        try {
          fs.rmSync(fabricCacheDir, { recursive: true, force: true });
          log('info', 'Cleaned Fabric remapped jars cache');
        } catch (_) { /* */ }
      }
    }

    // Settings-driven toggles (default: Icey mods ON, skin changer OFF,
    // health-indicator + architectury ON unless explicitly opted out
    // in Advanced settings — architectury is HealthIndicators' Fabric
    // dependency so they ride together by default).
    const iceyModsEnabled = settings.iceyModsEnabled !== false;
    const skinChangerEnabled = !!settings.skinChangerEnabled;
    const healthIndicatorsEnabled = settings.healthIndicatorsEnabled !== false;
    const architecturyEnabled = settings.architecturyEnabled !== false;
    const javaStuffEnabled = !!settings.javaStuffEnabled;

    // Panorama pack installs on ANY installation (vanilla or Fabric) because
    // it's just a resource pack — MC loads it regardless of mod loader.
    {
      const resourcepacksDir = path.join(installGameDir, 'resourcepacks');
      fs.mkdirSync(resourcepacksDir, { recursive: true });
      const iceyPackName = 'IceyPanorama.zip';
      const destIceyPack = path.join(resourcepacksDir, iceyPackName);
      try {
        for (const old of ['IceyModResourcePack.zip', 'IceyNetherPanorama.zip']) {
          const p = path.join(resourcepacksDir, old);
          if (fs.existsSync(p)) { fs.unlinkSync(p); log('info', 'Removed stale pack: ' + old); }
          _unregisterResourcepackLine(installGameDir, old);
        }
      } catch (_) {}

      if (iceyModsEnabled) {
        try {
          const selectedPanorama = settings.selectedPanorama || 'Nether Panorama.zip';
          const srcPanorama = path.join(__dirname, 'resources', 'panoramas', selectedPanorama);
          const altPanorama = path.join(process.resourcesPath || '', 'panoramas', selectedPanorama);
          const panoSrc = fs.existsSync(srcPanorama) ? srcPanorama : (fs.existsSync(altPanorama) ? altPanorama : null);
          if (panoSrc) {
            const srcStat = fs.statSync(panoSrc);
            const destStat = fs.existsSync(destIceyPack) ? fs.statSync(destIceyPack) : null;
            if (!destStat || srcStat.size !== destStat.size || srcStat.mtimeMs > destStat.mtimeMs) {
              fs.copyFileSync(panoSrc, destIceyPack);
              log('info', 'Installed panorama: ' + selectedPanorama);
              if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Panorama: ' + selectedPanorama, level: 'info' });
            }
            _registerResourcepackLine(installGameDir, iceyPackName);
          } else {
            log('warn', 'Selected panorama not found: ' + selectedPanorama);
          }
        } catch (e) {
          log('warn', 'Panorama install failed: ' + e.message);
        }
      } else {
        try {
          if (fs.existsSync(destIceyPack)) fs.unlinkSync(destIceyPack);
          _unregisterResourcepackLine(installGameDir, iceyPackName);
        } catch (_) {}
      }
    }

    // Auto-install Icey mod + Fabric API + skin-changer for Fabric installations
    if (installation.platform === 'fabric') {
      const modsDir = path.join(installGameDir, 'mods');
      fs.mkdirSync(modsDir, { recursive: true });

      // 1) Install/UPDATE Icey mod jar. CI builds one jar per MC version
      // (iceymod-mc<MC_VER>-1.0.0.jar). _pickIceyJar returns the exact
      // build, or the closest lower build whose declared MC range still
      // covers this version (1.21.9 / 1.21.10 use the 1.21.8 jar), or
      // null on 26.x where yarn-built jars can't load at all.
      const iceyJar = _pickIceyJar('client', installation.version);
      const modJarName = iceyJar ? iceyJar.name : null;
      const destJar = modJarName ? path.join(modsDir, modJarName) : null;
      try {
        // Clean up any stale iceymod jars (wrong MC version, or the old
        // single-jar "iceymod-1.0.0.jar" name from before the matrix build).
        // Match `iceymod-` with a hyphen so we don't sweep up iceymodplus.
        for (const f of fs.readdirSync(modsDir)) {
          if (/^iceymod-.*\.jar$/i.test(f) && f !== modJarName) {
            fs.unlinkSync(path.join(modsDir, f));
            log('info', 'Removed stale Icey mod jar: ' + f);
          }
        }
      } catch (_) {}

      if (iceyModsEnabled) {
        if (iceyJar) {
          try {
            const srcStat = fs.statSync(iceyJar.path);
            const destStat = fs.existsSync(destJar) ? fs.statSync(destJar) : null;
            if (!destStat || srcStat.size !== destStat.size || srcStat.mtimeMs > destStat.mtimeMs) {
              fs.copyFileSync(iceyJar.path, destJar);
              log('info', 'Updated Icey mod to ' + destJar);
              _mcConsole(iceyJar.ver === installation.version
                ? 'Icey mod updated'
                : `Icey mod: using the ${iceyJar.ver} build on MC ${installation.version}`, 'info');
            }
          } catch (e) {
            log('warn', 'Failed to install Icey mod: ' + e.message);
          }
        } else if (!_isYarnEraVersion(installation.version)) {
          log('warn', `Icey mod not available for MC ${installation.version} (new mapping system)`);
          _mcConsole(`Icey mod isn't available for Minecraft ${installation.version} yet (Mojang changed the modding system in 26.1) — HUDs unavailable, everything else works`, 'warn');
        } else {
          log('warn', `Icey mod (client) jar not bundled for MC ${installation.version}`);
          _mcConsole(`Icey mod not bundled for MC ${installation.version} — HUDs unavailable`, 'warn');
        }
      } else if (destJar) {
        try {
          if (fs.existsSync(destJar)) {
            fs.unlinkSync(destJar);
            log('info', 'Icey mod disabled by user — removed ' + modJarName);
            _mcConsole('Icey mod disabled', 'info');
          }
        } catch (_) {}
      }

      // 1b) iceymod+ (the SMP server mod) is no longer shipped with the
      // client. Sweep any copy an older launcher version dropped into
      // mods/ so it can't crash a newer MC version.
      try {
        const smpPattern = /^(iceysmp|iceymodplus).*\.jar$/i;
        for (const f of fs.readdirSync(modsDir)) {
          if (smpPattern.test(f)) {
            try { fs.unlinkSync(path.join(modsDir, f)); log('info', 'Removed iceymod+ jar (no longer bundled): ' + f); } catch (_) {}
          }
        }
      } catch (_) {}

      // 2) Install correct Fabric API for THIS MC version. Hardcoded version
      // maps drift fast and skip MC versions silently — query Modrinth for the
      // latest fabric-api version compatible with the installation's MC version
      // and trust whatever it returns. Falls back to skipping the install if the
      // network call fails (better to not auto-install than pin a wrong jar).
      let fabricApiFile = null;
      try {
        const modrinthUrl = `https://api.modrinth.com/v2/project/fabric-api/version?game_versions=[%22${installation.version}%22]&loaders=[%22fabric%22]`;
        const versions = await new Promise((resolve, reject) => {
          https.get(modrinthUrl, { headers: { 'User-Agent': 'IceyClient/1.0.0' } }, (res) => {
            let data = '';
            res.on('data', (c) => data += c);
            res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
            res.on('error', reject);
          }).on('error', reject);
        });
        if (Array.isArray(versions) && versions.length > 0) {
          const file = versions[0].files.find(f => f.primary) || versions[0].files[0];
          if (file && file.filename && file.url) {
            fabricApiFile = { filename: file.filename, url: file.url };
          }
        }
      } catch (e) {
        log('warn', 'Modrinth lookup for Fabric API failed: ' + e.message);
      }

      if (fabricApiFile) {
        const correctApiJar = fabricApiFile.filename;
        const correctApiDest = path.join(modsDir, correctApiJar);

        // Delete any wrong-version fabric-api jars
        const fabricApiPattern = /^fabric-api.*\.jar$/i;
        try {
          for (const f of fs.readdirSync(modsDir)) {
            if (fabricApiPattern.test(f) && f !== correctApiJar) {
              fs.unlinkSync(path.join(modsDir, f));
              log('info', 'Removed wrong-version Fabric API: ' + f);
              if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Removed wrong Fabric API: ' + f, level: 'info' });
            }
          }
        } catch (_) {}

        // Download correct Fabric API if not already present
        if (!fs.existsSync(correctApiDest)) {
          log('info', 'Downloading Fabric API ' + correctApiJar + ' for MC ' + installation.version);
          if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Downloading ' + correctApiJar, level: 'info' });
          try {
            await downloadFile(fabricApiFile.url, correctApiDest);
            log('info', 'Fabric API installed to ' + correctApiDest);
            if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'Fabric API installed', level: 'info' });
          } catch (e) {
            log('warn', 'Failed to download Fabric API: ' + e.message);
          }
        }
      } else {
        log('warn', 'No Fabric API version found on Modrinth for MC ' + installation.version + ' — skipping auto-install');
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'No Fabric API for MC ' + installation.version + ' on Modrinth — skipping', level: 'warn' });
      }

      // 3) Settings-toggle mods (SkinShuffle, Architectury, Health
      // Indicators) plus whatever they declare as hard dependencies on
      // Modrinth (YACL, …). Each is resolved for THIS MC version, so the
      // toggles keep working on new Minecraft releases without a launcher
      // update. See _ensureBundledMods.
      try {
        const enabledKeys = new Set();
        if (skinChangerEnabled) enabledKeys.add('skinshuffle');
        if (iceyModsEnabled && healthIndicatorsEnabled) enabledKeys.add('healthindicators');
        if (architecturyEnabled) enabledKeys.add('architectury');
        await _ensureBundledMods(installGameDir, installation.version, enabledKeys);
      } catch (e) {
        log('warn', 'Bundled mods block failed: ' + e.message);
        _mcConsole('Bundled mods: ' + e.message, 'warn');
      }

      // 4) Java & Stuff modpack (Settings toggle). Installs the pack's
      // mods/resource packs/shaders into this installation, re-matched to
      // its MC version. Armor-look packs stay disabled by default.
      try {
        await _ensureJavaStuffPack(installGameDir, installation.version, javaStuffEnabled);
      } catch (e) {
        log('warn', 'Java & Stuff block failed: ' + e.message);
        _mcConsole('Java & Stuff: ' + e.message, 'warn');
      }

      // 5) Fabric loader floor. Newer mods (Health Indicators / SkinShuffle
      // on 1.21.11+) require loader 0.18+, while installations created a
      // while ago may still run 0.16. Upgrade in place, then reload the
      // profile and re-merge its libraries into the classpath.
      try {
        const needLoader = _requiredFabricLoader(modsDir);
        const haveLoader = versionId.startsWith('fabric-loader-')
          ? versionId.slice('fabric-loader-'.length, versionId.length - version.length - 1) : null;
        if (needLoader && haveLoader && _installFabricLoader
            && _compareVersions(_parseVersion(haveLoader), _parseVersion(needLoader)) < 0) {
          _mcConsole(`Fabric loader ${haveLoader} is older than ${needLoader} required by your mods — updating`, 'warn');
          const r = await _installFabricLoader(installation.id, version);
          if (r && !r.error) {
            for (const old of fabricLibs) { const i = cpParts.indexOf(old); if (i >= 0) cpParts.splice(i, 1); }
            const fp = await _loadFabricProfile(mcDir, version, libDir);
            versionId = fp.versionId; mainClass = fp.mainClass; extraJvmArgs = fp.extraJvmArgs; fabricLibs = fp.fabricLibs;
            _mergeFabricLibsIntoClasspath(cpParts, fabricLibs);
            _mcConsole('Fabric loader updated → ' + versionId, 'info');
          } else {
            _mcConsole('Fabric loader update failed: ' + (r && r.error), 'warn');
          }
        }
      } catch (e) {
        log('warn', 'Fabric loader check failed: ' + e.message);
      }

      // 5b) Mods that can't run on this OS at all (e.g. 'splashscreen'
      // deadlocks macOS). Applies to anything in mods/, however it got there.
      try { _sweepPlatformIncompatibleMods(modsDir); } catch (_) {}

      // 6) Some mods need a newer Java than the game itself (C2ME on
      // 1.21.11 wants 22+). Bump to the LTS that covers it.
      try {
        const needJava = _requiredJavaFromMods(modsDir);
        const haveJava = _javaMajorOf(javaPath);
        if (needJava && needJava > haveJava) {
          const target = needJava <= 21 ? 21 : (needJava <= 25 ? 25 : needJava);
          _mcConsole(`Installed mods need Java ${needJava}+ (have ${haveJava}) — switching to Java ${target}`, 'warn');
          javaPath = await ensureJavaForVersion(javaPath, target);
        }
      } catch (e) {
        log('error', '[LAUNCH] ' + e.message);
        return reject(e);
      }

      // 7) Any conflict still declared between installed jars gets logged
      // here, so a Fabric "Incompatible mods" screen is never a mystery.
      try {
        const mods = _scanMods(modsDir);
        for (const c of _findModConflicts(mods)) _mcConsole('Mod conflict: ' + _describeConflict(c, mods), 'warn');
      } catch (_) {}

      // Mod-compatibility check removed entirely — the fabric.mod.json
      // mcConstraint check produced false positives on mods that actually
      // work fine across adjacent MC versions. The launcher leaves every
      // mod file alone; manage them yourself from the Mods tab.
    } else if (javaStuffEnabled || skinChangerEnabled) {
      _mcConsole('Java & Stuff / Skin Changer need a Fabric installation — this one is vanilla, so they were skipped', 'warn');
    }

    // Build arguments
    const ram = settings.allocatedRam || 4096;
    const username = settings.username || 'Player';
    const uuid = settings.uuid || crypto.randomUUID();

    // If another MC instance is already running, sanity-check that total Xmx
    // won't exceed a safe share of system RAM. Just warn — don't block.
    if (mcProcesses.size > 0) {
      const totalMcMb = (mcProcesses.size * ram) + ram; // existing + this one
      const systemMb = Math.floor(os.totalmem() / (1024 * 1024));
      const safeMb = Math.floor(systemMb * 0.75);
      if (totalMcMb > safeMb) {
        const msg = `WARNING: Launching instance #${mcProcesses.size + 1} would allocate ${totalMcMb} MB of Xmx across all MC instances (system has ${systemMb} MB). Consider closing one or lowering RAM per installation to avoid freezing.`;
        log('warn', msg);
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: msg, level: 'warn' });
      } else {
        log('info', `Launching concurrent instance #${mcProcesses.size + 1} (total Xmx ${totalMcMb} / ${systemMb} MB system)`);
      }
    }

    const args = [];
    // JVM args
    if (process.platform === 'darwin') args.push('-XstartOnFirstThread');
    // Xms kept small (512M) so a second MC instance doesn't commit the full
    // Xmx upfront — with AlwaysPreTouch removed, memory is allocated lazily
    // as MC needs it. This is what prevents two instances from freezing the
    // machine when the total heap would exceed physical RAM.
    args.push(`-Xmx${ram}M`, `-Xms512M`);
    // High-performance G1GC tuning (Aikar-style) + network boosters.
    // NOTE: AlwaysPreTouch intentionally omitted — it forces the JVM to commit
    // the entire Xmx at startup, which makes running multiple instances very
    // dangerous on anything under 16-32 GB of RAM.
    args.push(
      '-XX:+UseG1GC',
      '-XX:+ParallelRefProcEnabled',
      '-XX:+UnlockExperimentalVMOptions',
      '-XX:MaxGCPauseMillis=50',
      '-XX:G1HeapRegionSize=32M',
      '-XX:G1NewSizePercent=30',
      '-XX:G1MaxNewSizePercent=40',
      '-XX:G1ReservePercent=20',
      '-XX:InitiatingHeapOccupancyPercent=15',
      '-XX:G1MixedGCLiveThresholdPercent=90',
      '-XX:G1RSetUpdatingPauseTimePercent=5',
      '-XX:SurvivorRatio=32',
      '-XX:+PerfDisableSharedMem',
      '-XX:MaxTenuringThreshold=1',
      '-XX:+DisableExplicitGC',
      '-XX:+UseStringDeduplication',
      '-Djava.net.preferIPv4Stack=true',
      '-Dsun.net.inetaddr.ttl=60',
      '-Dio.netty.tcp.nodelay=true',
      '-Dio.netty.allocator.maxOrder=9',
      '-Dfml.ignoreInvalidMinecraftCertificates=true'
    );
    // Natives: use Prism's natives on arm64 Linux, otherwise extract ourselves
    const prismNatives = (process.platform === 'linux' && process.arch === 'arm64') ? findPrismNatives(version) : null;
    let nativesDir;
    if (prismNatives) {
      nativesDir = prismNatives;
      log('info', 'Using Prism natives dir: ' + nativesDir);
    } else {
      nativesDir = path.join(mcDir, 'versions', version, 'natives');
      fs.mkdirSync(nativesDir, { recursive: true });
      // Download and extract natives for all libraries that have them
      for (const lib of (versionJson.libraries || [])) {
        if (lib.downloads?.classifiers || lib.natives) {
          try {
            await downloadAndExtractNatives(lib, libDir, nativesDir);
          } catch (e) {
            log('warn', 'Native extraction failed for ' + (lib.name || 'unknown') + ': ' + e.message);
          }
        }
      }
    }
    args.push('-Djava.library.path=' + nativesDir);
    if (extraJvmArgs.length > 0) args.push(...extraJvmArgs);
    if (settings.jvmArgs) args.push(...settings.jvmArgs.split(/\s+/).filter(Boolean));
    args.push('-cp', classpath);
    args.push(mainClass);
    // Choose auth based on active account: Microsoft (refreshed if needed) or offline/cracked.
    // ensureFreshAuth silently swaps an expired MC token for a fresh one
    // using the stored refresh_token, so a launch the day after login
    // still works without the popup.
    const storedAuth = readAuth();
    const auth = await ensureFreshAuth(storedAuth);
    let launchUsername, launchUuid, launchToken, launchUserType;
    if (auth && auth.type === 'offline') {
      // Cracked/offline account — always legacy with deterministic offline UUID
      launchUsername = auth.username || username;
      launchUuid = offlineUuid(launchUsername);
      launchToken = '0';
      launchUserType = 'legacy';
    } else if (auth && auth.accessToken && auth.expiresAt > Date.now()) {
      // Valid Microsoft session (either fresh from login or just refreshed)
      launchUsername = auth.username;
      launchUuid = auth.uuid;
      launchToken = auth.accessToken;
      launchUserType = 'msa';
    } else {
      // No active account, or the refresh chain is dead — fall back to settings as vanilla-offline
      launchUsername = username;
      launchUuid = offlineUuid(launchUsername);
      launchToken = '0';
      launchUserType = 'legacy';
    }
    log('info', `[LAUNCH] Auth: ${launchUsername} (${launchUserType})`);

    // Game args
    args.push('--username', launchUsername);
    args.push('--version', versionId);
    args.push('--gameDir', installGameDir);
    args.push('--assetsDir', assetsDir);
    args.push('--assetIndex', assetIndex);
    args.push('--uuid', launchUuid);
    args.push('--accessToken', launchToken);
    args.push('--userType', launchUserType);

    log('info', `Launching MC directly: java ${version} (${cpParts.length} libs, main: ${mainClass})`);

    if (mainWindow) {
      mainWindow.webContents.send('mc-event', { type: 'console-log', message: `Launching ${version} (${cpParts.length} libraries)`, level: 'info' });
    }

    try {
      const launchId = ++mcLaunchCounter;
      const proc = spawn(javaPath, args, {
        cwd: installGameDir,
        stdio: 'pipe',
        detached: false,
        windowsHide: false
      });
      // Ring buffer of the last ~120 output lines per process — used to show
      // a crash-log modal if MC exits with a non-zero code.
      const tail = [];
      const pushTail = (line) => { tail.push(line); if (tail.length > 120) tail.shift(); };
      mcProcesses.set(launchId, { proc, installationId, username: launchUsername, uuid: launchUuid, tail });
      // Start Icey-network presence heartbeat for this player. Only
      // for online MSA accounts (offline accounts have synthesized
      // UUIDs that mean nothing on the network).
      if (launchUserType === 'msa' && launchUuid) {
        startPresenceHeartbeat(launchUuid);
        // Auto-sync the locally-stored cape PNG to the backend so
        // capes uploaded BEFORE the backend existed still reach the
        // network without the user having to re-upload. Best-effort.
        try {
          const capePath = path.join(INSTALLATIONS_DIR, installationId, 'game', 'config', 'iceyclient', 'cape.png');
          if (fs.existsSync(capePath)) {
            const buf = fs.readFileSync(capePath);
            uploadCapeToNetwork(launchUuid, buf);
          }
        } catch (e) {
          log('warn', 'cape auto-sync on launch failed: ' + e.message);
        }
      }

      proc.stdout.on('data', (data) => {
        const text = data.toString().trim();
        if (text) {
          pushTail(text);
          log('info', `[MC #${launchId} ${launchUsername}] ` + text);
          if (mainWindow) {
            mainWindow.webContents.send('mc-event', { type: 'console-log', message: `[${launchUsername}] ${text}`, level: 'info', launchId });
          }
          if (text.includes('Setting user:') || text.includes('LWJGL') || text.includes('OpenAL')) {
            if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-started', launchId });
          }
        }
      });

      proc.stderr.on('data', (data) => {
        const text = data.toString().trim();
        if (text) {
          pushTail('[err] ' + text);
          log('error', `[MC #${launchId} ${launchUsername}-ERR] ` + text);
          if (mainWindow) {
            mainWindow.webContents.send('mc-event', { type: 'console-log', message: `[${launchUsername}] ${text}`, level: 'error', launchId });
          }
        }
      });

      proc.on('error', (err) => {
        log('error', `MC #${launchId} process error: ` + err.message);
        mcProcesses.delete(launchId);
        if (!Array.from(mcProcesses.values()).some(p => p.uuid === launchUuid)) {
          stopPresenceHeartbeat(launchUuid);
        }
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-error', message: err.message, launchId });
      });

      proc.on('close', (code) => {
        log('info', `MC #${launchId} (${launchUsername}) exited with code ${code}`);
        mcProcesses.delete(launchId);
        if (!Array.from(mcProcesses.values()).some(p => p.uuid === launchUuid)) {
          stopPresenceHeartbeat(launchUuid);
        }
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'mc-stopped', code, launchId });

          // Only surface a crash modal if the exit *looks* like a crash.
          // Normal closes can produce many different codes across platforms
          // (Windows JVM sometimes returns 1 even on a clean window-close),
          // so we also require the tail to contain error-like output.
          const cleanCode = code === 0 || code === null || code === 143 || code === 130;
          const lastLines = tail.slice(-60).join('\n');
          const looksLikeCrash = /(Exception|Error|SEVERE|Traceback|crash-reports?|Minecraft has crashed|OutOfMemory|SIGSEGV)/i.test(lastLines);
          if (!cleanCode && looksLikeCrash) {
            mainWindow.webContents.send('mc-event', {
              type: 'mc-crashed',
              launchId,
              code,
              username: launchUsername,
              tail: tail.slice(-60),
            });
          } else if (!cleanCode) {
            log('info', `MC #${launchId} exit code ${code} but tail has no crash markers — suppressing crash modal.`);
          }
        }
      });

      // Signal started quickly
      setTimeout(() => {
        if (mcProcesses.has(launchId) && mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-started', launchId });
      }, 1500);

      if (settings.closeLauncherOnStart) {
        setTimeout(() => { if (mainWindow) mainWindow.hide(); }, 2000);
      }

      resolve({ success: true, launchId });
    } catch (e) {
      log('error', 'Failed to spawn MC: ' + e.message);
      reject(e);
    }
    } catch (fatalErr) {
      log('error', '[LAUNCH] FATAL: ' + fatalErr.message + '\n' + fatalErr.stack);
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message: 'FATAL ERROR: ' + fatalErr.message, level: 'error' });
      if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-stopped', code: 1 });
      reject(fatalErr);
    }
  });
}

// ── Small helpers shared by the launch-time installers ────────────────
function _mcConsole(message, level) {
  if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'console-log', message, level: level || 'info' });
}
function _mcToast(message, level) {
  if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'toast', level: level || 'info', message });
}

function _fetchJson(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const doRequest = (requestUrl, redirects) => {
      if (redirects > 5) return reject(new Error('Too many redirects'));
      const proto = requestUrl.startsWith('https') ? https : http;
      const req = proto.get(requestUrl, { headers: { 'User-Agent': 'IceyClient/1.0.0', 'Accept': 'application/json' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          return doRequest(res.headers.location, redirects + 1);
        }
        let data = '';
        res.on('data', (c) => data += c);
        res.on('end', () => {
          if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));
          try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
        });
        res.on('error', reject);
      });
      req.on('error', reject);
      req.setTimeout(timeoutMs || 15000, () => { req.destroy(); reject(new Error('timeout')); });
    };
    doRequest(url, 0);
  });
}

function _sha1File(filePath) {
  try { return crypto.createHash('sha1').update(fs.readFileSync(filePath)).digest('hex'); } catch (_) { return null; }
}

// Java major demanded by a "java" depends entry (">=22" → 22), else 0.
function _javaNeedOf(dependsJava) {
  const list = Array.isArray(dependsJava) ? dependsJava : (dependsJava ? [dependsJava] : []);
  let best = 0;
  for (const cond of list) {
    const m = String(cond).match(/>=?\s*(\d+)/);
    if (m) best = Math.max(best, parseInt(m[1], 10));
  }
  return best;
}

// Read fabric.mod.json out of a jar → { id, name, version, depends, breaks,
// provides, javaNeed } or null. Nested jars (Jar-in-Jar, e.g. C2ME's
// sub-modules) are folded in: their ids count as provided, and the highest
// Java they ask for becomes javaNeed.
function _readFabricModMeta(jarPath) {
  try {
    const buf = fs.readFileSync(jarPath);
    return _readFabricModMetaFromBuffer(buf, 0);
  } catch (_) { return null; }
}

function _readFabricModMetaFromBuffer(buf, depth) {
  const raw = _extractFileFromZip(buf, 'fabric.mod.json');
  if (!raw) return null;
  let meta;
  try { meta = JSON.parse(raw.toString('utf-8')); } catch (_) { return null; }
  const out = {
    id: meta.id || null, name: meta.name || null, version: meta.version || null,
    depends: meta.depends || {}, breaks: meta.breaks || {},
    provides: Array.isArray(meta.provides) ? meta.provides.slice() : [],
    javaNeed: _javaNeedOf((meta.depends || {}).java),
  };
  if (depth < 2 && Array.isArray(meta.jars)) {
    for (const j of meta.jars) {
      if (!j || !j.file) continue;
      try {
        const inner = _extractFileFromZip(buf, j.file);
        if (!inner) continue;
        const sub = _readFabricModMetaFromBuffer(inner, depth + 1);
        if (!sub) continue;
        if (sub.id && sub.id !== out.id && !out.provides.includes(sub.id)) out.provides.push(sub.id);
        for (const p of sub.provides) if (!out.provides.includes(p)) out.provides.push(p);
        out.javaNeed = Math.max(out.javaNeed, sub.javaNeed || 0);
      } catch (_) {}
    }
  }
  return out;
}

// Minecraft 26.1 dropped intermediary mappings — jars compiled against
// yarn (everything the Icey CI builds today) can't load there at all.
function _isYarnEraVersion(mcVersion) {
  try { return _parseVersion(mcVersion)[0] < 26; } catch (_) { return true; }
}

// ── Java runtime management ──────────────────────────────────────────
// Minecraft 1.21+ needs Java 21, and the 26.x line needs Java 25. The
// version JSON tells us the required major; we make sure the java we
// spawn is at least that, downloading a Temurin JRE into DATA_DIR/java
// when nothing on the machine qualifies.
// { major, arch } of a JVM. arch is the JVM's own architecture ("aarch64",
// "x86_64", "amd64"), which on Apple Silicon tells us whether the game
// would run natively or through Rosetta.
const _javaInfoCache = new Map();
function _javaInfoOf(javaPath) {
  if (_javaInfoCache.has(javaPath)) return _javaInfoCache.get(javaPath);
  let info = { major: 0, arch: null };
  try {
    const r = require('child_process').spawnSync(javaPath, ['-XshowSettings:properties', '-version'], { encoding: 'utf-8', timeout: 10000 });
    const text = (r.stderr || '') + (r.stdout || '');
    const m = text.match(/version "(\d+)(?:\.(\d+))?/);
    if (m) { const a = parseInt(m[1], 10); info.major = a === 1 ? parseInt(m[2] || '0', 10) : a; }
    const arch = text.match(/os\.arch\s*=\s*(\S+)/);
    if (arch) info.arch = arch[1].toLowerCase();
  } catch (_) {}
  _javaInfoCache.set(javaPath, info);
  return info;
}
function _javaMajorOf(javaPath) { return _javaInfoOf(javaPath).major; }

// Architecture the JVM should have to run natively on this machine, or
// null when it doesn't matter. Only Apple Silicon is special-cased: an
// Intel JDK works there via Rosetta but starts and renders far slower.
function _wantJavaArch() {
  return (process.platform === 'darwin' && process.arch === 'arm64') ? 'aarch64' : null;
}
function _javaArchOk(info) {
  const want = _wantJavaArch();
  return !want || !info.arch || info.arch === want;
}

function _javaBinName() { return process.platform === 'win32' ? 'java.exe' : 'java'; }

// Find a java binary somewhere under `root` (depth-limited).
function _findJavaBinUnder(root, depth) {
  if (!root || !fs.existsSync(root)) return null;
  const direct = [
    path.join(root, 'bin', _javaBinName()),
    path.join(root, 'Contents', 'Home', 'bin', _javaBinName()),
  ];
  for (const p of direct) if (fs.existsSync(p)) return p;
  if (depth <= 0) return null;
  try {
    for (const entry of fs.readdirSync(root)) {
      const sub = path.join(root, entry);
      try { if (!fs.statSync(sub).isDirectory()) continue; } catch (_) { continue; }
      const found = _findJavaBinUnder(sub, depth - 1);
      if (found) return found;
    }
  } catch (_) {}
  return null;
}

function _findJavaWithMajor(required) {
  const candidates = [];
  const pushDirChildren = (base) => {
    try {
      if (!base || !fs.existsSync(base)) return;
      for (const entry of fs.readdirSync(base)) {
        const bin = _findJavaBinUnder(path.join(base, entry), 1);
        if (bin) candidates.push(bin);
      }
    } catch (_) {}
  };
  // Runtimes we downloaded ourselves
  pushDirChildren(path.join(DATA_DIR, 'java'));
  if (process.platform === 'darwin') {
    try {
      const home = execSync(`/usr/libexec/java_home -v ${required} 2>/dev/null`, { encoding: 'utf-8', timeout: 8000 }).trim();
      if (home) candidates.push(path.join(home, 'bin', 'java'));
    } catch (_) {}
    pushDirChildren('/Library/Java/JavaVirtualMachines');
    pushDirChildren(path.join(os.homedir(), 'Library', 'Java', 'JavaVirtualMachines'));
    pushDirChildren('/opt/homebrew/opt');
  } else if (process.platform === 'win32') {
    const pf = [process.env.PROGRAMFILES || 'C:\\Program Files', process.env['PROGRAMFILES(X86)'] || 'C:\\Program Files (x86)'];
    for (const base of pf) {
      for (const vendor of ['Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'BellSoft', 'Amazon Corretto', 'Eclipse Foundation']) {
        pushDirChildren(path.join(base, vendor));
      }
    }
  } else {
    pushDirChildren('/usr/lib/jvm');
    pushDirChildren('/usr/lib64/jvm');
    pushDirChildren('/opt');
  }
  // Prefer a native-arch JVM, then the lowest major that still qualifies.
  const seen = new Set();
  let best = null;
  for (const bin of candidates) {
    if (seen.has(bin)) continue;
    seen.add(bin);
    const info = _javaInfoOf(bin);
    if (info.major < required) continue;
    const score = (_javaArchOk(info) ? 0 : 1000) + info.major;
    if (!best || score < best.score) best = { bin, score };
  }
  return best ? best.bin : null;
}

async function _downloadJavaRuntime(required) {
  const osName = process.platform === 'darwin' ? 'mac' : process.platform === 'win32' ? 'windows' : 'linux';
  const arch = process.arch === 'arm64' ? 'aarch64' : 'x64';
  const url = `https://api.adoptium.net/v3/binary/latest/${required}/ga/${osName}/${arch}/jre/hotspot/normal/eclipse`;
  const javaRoot = path.join(DATA_DIR, 'java');
  const destDir = path.join(javaRoot, `jre-${required}`);
  const archive = path.join(javaRoot, `jre-${required}.` + (osName === 'windows' ? 'zip' : 'tar.gz'));
  fs.mkdirSync(javaRoot, { recursive: true });
  await downloadFile(url, archive);
  try { fs.rmSync(destDir, { recursive: true, force: true }); } catch (_) {}
  fs.mkdirSync(destDir, { recursive: true });
  // `tar` handles both .tar.gz and .zip on macOS/Linux and on Windows 10+ (bsdtar).
  execSync(`tar -xf "${archive}" -C "${destDir}"`, { timeout: 180000, stdio: 'ignore' });
  try { fs.unlinkSync(archive); } catch (_) {}
  const bin = _findJavaBinUnder(destDir, 3);
  if (!bin) throw new Error('Downloaded runtime has no java binary');
  if (process.platform !== 'win32') { try { fs.chmodSync(bin, 0o755); } catch (_) {} }
  return bin;
}

async function ensureJavaForVersion(javaPath, requiredMajor) {
  if (!requiredMajor) return javaPath;
  const info = javaPath ? _javaInfoOf(javaPath) : { major: 0, arch: null };
  const current = info.major;
  if (current >= requiredMajor && _javaArchOk(info)) return javaPath;

  if (current >= requiredMajor) {
    // Right version, wrong architecture (Intel JDK on Apple Silicon →
    // Rosetta). Swap to a native one if we can; otherwise keep going slow.
    _mcConsole(`Java at ${javaPath} is an Intel build running under Rosetta — looking for a native Apple Silicon Java (much faster)`, 'warn');
    const found = _findJavaWithMajor(requiredMajor);
    if (found && _javaArchOk(_javaInfoOf(found))) {
      _mcConsole('Using native Java ' + _javaMajorOf(found) + ' at ' + found, 'info');
      return found;
    }
    _mcToast(`Downloading native Apple Silicon Java ${requiredMajor} (one-time, ~50 MB) — Minecraft will start and run much faster`, 'info');
    try {
      const bin = await _downloadJavaRuntime(requiredMajor);
      _mcConsole('Native Java ' + requiredMajor + ' installed at ' + bin, 'info');
      return bin;
    } catch (e) {
      log('warn', '[JAVA] Native runtime download failed: ' + e.message);
      _mcConsole('Could not download a native Java (' + e.message + ') — continuing with the Intel one', 'warn');
      return javaPath;
    }
  }

  log('info', `[JAVA] Need Java ${requiredMajor}, have ${current || 'none'} — searching`);
  _mcConsole(`This Minecraft version needs Java ${requiredMajor} (found Java ${current || 'none'}) — looking for a newer runtime`, 'warn');
  const found = _findJavaWithMajor(requiredMajor);
  if (found) {
    _mcConsole('Using Java ' + _javaMajorOf(found) + ' at ' + found, 'info');
    return found;
  }
  _mcToast(`Downloading Java ${requiredMajor} runtime (one-time, ~50 MB)…`, 'info');
  _mcConsole(`Downloading Java ${requiredMajor} from Adoptium…`, 'info');
  try {
    const bin = await _downloadJavaRuntime(requiredMajor);
    _mcConsole('Java ' + requiredMajor + ' installed at ' + bin, 'info');
    return bin;
  } catch (e) {
    log('error', '[JAVA] Runtime download failed: ' + e.message);
    throw new Error(`JAVA_TOO_OLD:${requiredMajor}:${current}`);
  }
}

// ── Modrinth resolution with on-disk cache ────────────────────────────
// DATA_DIR/modcache/resolve/<mcVersion>/<key>.json  → cached resolution
// DATA_DIR/modcache/files/<filename>                → downloaded jar/zip
// DATA_DIR/modcache/projects.json                   → project id → slug/title
const MODCACHE_DIR = path.join(DATA_DIR, 'modcache');
const MODCACHE_TTL_MS = 3 * 24 * 60 * 60 * 1000;
// Dependencies the launcher already provides another way (Fabric API is
// block 2 of the launch flow) or that aren't real mods.
const MODRINTH_SKIP_DEPS = new Set(['P7dR8mSH' /* fabric-api */]);

function _readJsonSafe(p) { try { return JSON.parse(fs.readFileSync(p, 'utf-8')); } catch (_) { return null; } }
function _writeJsonSafe(p, obj) { try { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, 2)); } catch (_) {} }

async function _modrinthProjectInfo(projectIdOrSlug) {
  const cachePath = path.join(MODCACHE_DIR, 'projects.json');
  const cache = _readJsonSafe(cachePath) || {};
  if (cache[projectIdOrSlug]) return cache[projectIdOrSlug];
  const p = await _fetchJson(`https://api.modrinth.com/v2/project/${encodeURIComponent(projectIdOrSlug)}`);
  const info = { id: p.id, slug: p.slug, title: p.title, type: p.project_type };
  cache[projectIdOrSlug] = info; cache[p.id] = info; cache[p.slug] = info;
  _writeJsonSafe(cachePath, cache);
  return info;
}

// Resolve the newest Modrinth version of a project for a given MC version.
// loader: 'fabric' for mods, null for resource/shader packs. Returns
// { projectId, slug, title, filename, url, sha1, versionId, versionNumber,
//   dependencies:[{project_id, dependency_type}] } or null when the project
// simply has no build for that MC version.
async function _resolveModForMc(projectIdOrSlug, mcVersion, loader) {
  const key = String(projectIdOrSlug).replace(/[^a-z0-9_\-]/gi, '_') + (loader ? '' : '.any');
  const cachePath = path.join(MODCACHE_DIR, 'resolve', mcVersion, key + '.json');
  const cached = _readJsonSafe(cachePath);
  if (cached && cached.checkedAt && (Date.now() - cached.checkedAt) < MODCACHE_TTL_MS) return cached.result;
  let url = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectIdOrSlug)}/version?game_versions=[%22${encodeURIComponent(mcVersion)}%22]`;
  if (loader) url += `&loaders=[%22${loader}%22]`;
  try {
    const versions = await _fetchJson(url);
    let result = null;
    if (Array.isArray(versions) && versions.length > 0) {
      const v = versions.find(x => x.version_type === 'release') || versions[0];
      const file = (v.files || []).find(f => f.primary) || (v.files || [])[0];
      if (file && file.url && file.filename) {
        let slug = null, title = null;
        try { const info = await _modrinthProjectInfo(v.project_id); slug = info.slug; title = info.title; } catch (_) {}
        result = {
          projectId: v.project_id, slug, title,
          filename: file.filename, url: file.url, sha1: file.hashes?.sha1 || null,
          versionId: v.id, versionNumber: v.version_number,
          dependencies: (v.dependencies || []).map(d => ({ project_id: d.project_id, dependency_type: d.dependency_type })),
        };
      }
    }
    _writeJsonSafe(cachePath, { checkedAt: Date.now(), result });
    return result;
  } catch (e) {
    log('warn', `[MODRINTH] resolve ${projectIdOrSlug} for ${mcVersion} failed: ${e.message}`);
    // Offline: reuse whatever we resolved last time, however old.
    if (cached) return cached.result;
    throw e;
  }
}

// Every Modrinth version of a project for one MC version (newest first),
// in the same shape _resolveModForMc returns. Cached like resolutions.
async function _listModVersionsForMc(projectIdOrSlug, mcVersion, loader) {
  const key = String(projectIdOrSlug).replace(/[^a-z0-9_\-]/gi, '_') + (loader ? '' : '.any') + '.list';
  const cachePath = path.join(MODCACHE_DIR, 'resolve', mcVersion, key + '.json');
  const cached = _readJsonSafe(cachePath);
  if (cached && cached.checkedAt && (Date.now() - cached.checkedAt) < MODCACHE_TTL_MS) return cached.list;
  let url = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectIdOrSlug)}/version?game_versions=[%22${encodeURIComponent(mcVersion)}%22]`;
  if (loader) url += `&loaders=[%22${loader}%22]`;
  try {
    const versions = await _fetchJson(url);
    const list = [];
    for (const v of (Array.isArray(versions) ? versions : [])) {
      const file = (v.files || []).find(f => f.primary) || (v.files || [])[0];
      if (!file || !file.url || !file.filename) continue;
      list.push({
        projectId: v.project_id, filename: file.filename, url: file.url, sha1: file.hashes?.sha1 || null,
        versionId: v.id, versionNumber: v.version_number, versionType: v.version_type, date: v.date_published,
        dependencies: (v.dependencies || []).map(d => ({ project_id: d.project_id, dependency_type: d.dependency_type })),
      });
    }
    _writeJsonSafe(cachePath, { checkedAt: Date.now(), list });
    return list;
  } catch (e) {
    if (cached) return cached.list;
    throw e;
  }
}

// Download a resolved file into the cache (verifying sha1) and return its path.
async function _ensureCachedFile(res) {
  const cacheFile = path.join(MODCACHE_DIR, 'files', res.filename);
  const ok = fs.existsSync(cacheFile) && (!res.sha1 || _sha1File(cacheFile) === res.sha1);
  if (!ok) {
    await downloadFile(res.url, cacheFile);
    if (res.sha1 && _sha1File(cacheFile) !== res.sha1) {
      try { fs.unlinkSync(cacheFile); } catch (_) {}
      throw new Error('checksum mismatch for ' + res.filename);
    }
  }
  return cacheFile;
}

// Download (or reuse from cache) a resolved file and place it at destPath.
async function _installResolvedFile(res, destPath) {
  const cacheFile = await _ensureCachedFile(res);
  const srcStat = fs.statSync(cacheFile);
  const destStat = fs.existsSync(destPath) ? fs.statSync(destPath) : null;
  if (!destStat || destStat.size !== srcStat.size) {
    fs.mkdirSync(path.dirname(destPath), { recursive: true });
    fs.copyFileSync(cacheFile, destPath);
    return true;
  }
  return false;
}

// modId → filename for every jar in modsDir. Built once per pass so the
// duplicate checks below don't re-read a 100-jar folder for every file.
function _buildModIdIndex(modsDir) {
  const idx = new Map();
  try {
    for (const f of fs.readdirSync(modsDir)) {
      if (!f.endsWith('.jar')) continue;
      const meta = _readFabricModMeta(path.join(modsDir, f));
      if (meta && meta.id) idx.set(meta.id, f);
    }
  } catch (_) {}
  return idx;
}

// Find a jar in modsDir whose fabric.mod.json id matches (excluding `except`).
function _findJarByModId(modsDir, modId, except, idx) {
  if (!modId) return null;
  const index = idx || _buildModIdIndex(modsDir);
  const f = index.get(modId);
  if (f && f !== except && fs.existsSync(path.join(modsDir, f))) return f;
  return null;
}

// Highest fabric-loader version any jar in modsDir asks for ("fabricloader": ">=0.18.3").
function _requiredFabricLoader(modsDir) {
  let best = null;
  try {
    for (const f of fs.readdirSync(modsDir)) {
      if (!f.endsWith('.jar')) continue;
      const meta = _readFabricModMeta(path.join(modsDir, f));
      const raw = meta?.depends?.fabricloader;
      const list = Array.isArray(raw) ? raw : (raw ? [raw] : []);
      for (const cond of list) {
        const m = String(cond).match(/>=?\s*v?(\d+\.\d+(?:\.\d+)?)/);
        if (!m) continue;
        if (!best || _compareVersions(_parseVersion(m[1]), _parseVersion(best)) > 0) best = m[1];
      }
    }
  } catch (_) {}
  return best;
}

// Mod ids that come from the loader / game itself, or from Fabric API (block 2).
const BUILTIN_MOD_IDS = new Set(['minecraft', 'java', 'fabricloader', 'fabric-loader', 'fabric', 'fabric-api', 'fabric-api-base', 'mixinextras', 'mixin']);
// Well-known mod id → Modrinth slug pairs where the two differ.
const MOD_ID_TO_MODRINTH = {
  yet_another_config_lib_v3: 'yacl', yet_another_config_lib: 'yacl',
  'cloth-config2': 'cloth-config', cloth_config: 'cloth-config',
  architectury: 'architectury-api', owo: 'owo-lib', forgeconfigapiport: 'forge-config-api-port',
  puzzleslib: 'puzzles-lib', resourcefullib: 'resourceful-lib', 'cardinal-components-base': 'cardinal-components-api',
  ferritecore: 'ferrite-core', fzzy_config: 'fzzy-config', 'placeholder-api': 'placeholder-api',
  'fabric-language-kotlin': 'fabric-language-kotlin', midnightlib: 'midnightlib', modmenu: 'modmenu',
  geckolib: 'geckolib', collective: 'collective', libipn: 'libipn', 'balm-fabric': 'balm', balm: 'balm',
  spectrelib: 'spectrelib', konkrete: 'konkrete', melody: 'melody', improperui: 'improperui',
};

// Map a Fabric mod id (from a jar's depends block) to a Modrinth project.
async function _modrinthProjectForModId(modId) {
  const cachePath = path.join(MODCACHE_DIR, 'projects.json');
  const cache = _readJsonSafe(cachePath) || {};
  const key = 'modid:' + modId;
  if (cache[key] !== undefined) return cache[key];
  let ref = null;
  const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  try {
    if (MOD_ID_TO_MODRINTH[modId]) ref = MOD_ID_TO_MODRINTH[modId];
    else {
      // Most projects use their mod id as slug.
      try { const info = await _modrinthProjectInfo(modId); if (info && info.type === 'mod') ref = info.slug; } catch (_) {}
      if (!ref) {
        const q = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(modId)}&facets=${encodeURIComponent('[["project_type:mod"],["categories:fabric"]]')}&limit=6`;
        const data = await _fetchJson(q);
        const hits = (data && data.hits) || [];
        const exact = hits.find(h => norm(h.slug) === norm(modId) || norm(h.title) === norm(modId));
        const partial = hits.find(h => norm(modId).length >= 4 && (norm(h.slug).includes(norm(modId)) || norm(modId).includes(norm(h.slug))));
        const hit = exact || partial;
        ref = hit ? hit.slug : null;
      }
    }
  } catch (e) {
    log('warn', '[MODRINTH] modid lookup ' + modId + ': ' + e.message);
    return null; // don't cache network failures
  }
  cache[key] = ref;
  _writeJsonSafe(cachePath, cache);
  return ref;
}

// ── Bundled mods (Settings toggles) ───────────────────────────────────
// Every entry resolves against Modrinth for the installation's exact MC
// version, so they keep working when Mojang ships a new release. Hard
// dependencies come from the Modrinth version metadata and are installed
// as Icey-<slug>.jar. Everything we place is recorded in
// <gameDir>/.icey-managed-mods.json so disabling a toggle removes exactly
// what we added and nothing the user installed themselves.
const BUNDLED_MOD_REGISTRY = [
  { key: 'architectury',     label: 'Architectury',      slug: 'architectury-api', dest: 'IceyArchitectury.jar',     stale: /^architectury.*\.jar$/i,     bundledDir: 'architectury' },
  { key: 'healthindicators', label: 'Health Indicators', slug: 'healthindicators', dest: 'IceyHealthIndicators.jar', stale: /^healthindicators.*\.jar$/i, bundledDir: 'healthindicators' },
  { key: 'skinshuffle',      label: 'SkinShuffle',       slug: 'skinshuffle',      dest: 'IceySkinShuffle.jar',      stale: /skinshuffle/i,              bundledDir: 'skinshuffle' },
];

function _bundledModDirs(sub) {
  return [
    path.join(__dirname, 'resources', 'mods', sub),
    path.join(process.resourcesPath || '', 'mods', sub),
  ].filter(p => p && fs.existsSync(p));
}

// Offline fallback: a jar shipped inside the app whose declared MC range
// covers this version.
function _bundledJarFor(entry, mcVersion) {
  if (!entry.bundledDir) return null;
  for (const dir of _bundledModDirs(entry.bundledDir)) {
    try {
      for (const f of fs.readdirSync(dir)) {
        if (!f.endsWith('.jar')) continue;
        const meta = _readFabricModMeta(path.join(dir, f));
        const range = meta?.depends?.minecraft;
        if (!range || _satisfiesVersionRange(mcVersion, range)) return path.join(dir, f);
      }
    } catch (_) {}
  }
  return null;
}

async function _ensureBundledMods(installGameDir, mcVersion, enabledKeys) {
  const modsDir = path.join(installGameDir, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });
  const manifestPath = path.join(installGameDir, '.icey-managed-mods.json');
  const manifest = _readJsonSafe(manifestPath) || {};

  // Legacy sweep: stray copies of these mods under other names would
  // duplicate the one we manage → Fabric refuses to boot.
  for (const entry of BUNDLED_MOD_REGISTRY) {
    try {
      for (const f of fs.readdirSync(modsDir)) {
        if (entry.stale.test(f) && f !== entry.dest && !/^Icey/.test(f)) {
          try { fs.unlinkSync(path.join(modsDir, f)); log('info', 'Removed duplicate ' + entry.label + ' jar: ' + f); } catch (_) {}
        }
      }
    } catch (_) {}
  }

  const wanted = new Map(); // dest → { res, label }
  const queue = [];
  const seenProjects = new Set();
  for (const entry of BUNDLED_MOD_REGISTRY) {
    if (enabledKeys.has(entry.key)) queue.push({ ref: entry.slug, dest: entry.dest, label: entry.label, entry, depth: 0 });
  }

  while (queue.length) {
    const item = queue.shift();
    let res = null;
    try {
      res = await _resolveModForMc(item.ref, mcVersion, 'fabric');
    } catch (e) {
      // Network down and nothing cached: fall back to the shipped jar.
      const local = item.entry ? _bundledJarFor(item.entry, mcVersion) : null;
      if (local) {
        res = { projectId: item.ref, slug: item.ref, title: item.label, filename: path.basename(local), localPath: local, dependencies: [] };
        _mcConsole(item.label + ': offline — using bundled jar', 'warn');
      }
    }
    if (!res) {
      _mcConsole(`${item.label} has no build for Minecraft ${mcVersion} yet — skipped`, 'warn');
      continue;
    }
    if (res.projectId && seenProjects.has(res.projectId)) continue;
    if (res.projectId) seenProjects.add(res.projectId);
    wanted.set(item.dest, { res, label: item.label });
    if (item.depth >= 3) continue;
    for (const dep of (res.dependencies || [])) {
      if (dep.dependency_type !== 'required' || !dep.project_id) continue;
      if (MODRINTH_SKIP_DEPS.has(dep.project_id) || seenProjects.has(dep.project_id)) continue;
      let info = null;
      try { info = await _modrinthProjectInfo(dep.project_id); } catch (_) {}
      const slug = info?.slug || dep.project_id;
      // A dependency that is itself a registry entry keeps its canonical name.
      const reg = BUNDLED_MOD_REGISTRY.find(r => r.slug === slug);
      queue.push({ ref: dep.project_id, dest: reg ? reg.dest : `Icey-${slug}.jar`, label: info?.title || slug, entry: reg || null, depth: item.depth + 1 });
    }
  }

  // Install everything wanted.
  const nextManifest = {};
  const idIndex = _buildModIdIndex(modsDir);
  for (const [dest, { res, label }] of wanted) {
    const destPath = path.join(modsDir, dest);
    try {
      // Look at the jar BEFORE placing it: if the user (or a modpack such
      // as Java & Stuff) already provides the same mod id under another
      // file, keep theirs and don't copy ours — avoids both a duplicate-mod
      // crash and the install/delete churn on every launch.
      const srcFile = res.localPath || await _ensureCachedFile(res);
      const meta = _readFabricModMeta(srcFile);
      const other = meta && meta.id ? _findJarByModId(modsDir, meta.id, dest, idIndex) : null;
      if (other && !/^Icey/.test(other)) {
        if (fs.existsSync(destPath)) { try { fs.unlinkSync(destPath); } catch (_) {} }
        log('info', `[MODS] ${label} already provided by ${other} — skipped`);
        continue;
      }
      let changed = false;
      const s = fs.statSync(srcFile);
      const d = fs.existsSync(destPath) ? fs.statSync(destPath) : null;
      if (!d || d.size !== s.size) { fs.copyFileSync(srcFile, destPath); changed = true; }
      if (meta && meta.id) idIndex.set(meta.id, dest);
      nextManifest[dest] = { slug: res.slug, versionId: res.versionId || null, versionNumber: res.versionNumber || null, mcVersion };
      if (changed) _mcConsole(`${label} ${res.versionNumber ? 'v' + res.versionNumber + ' ' : ''}installed for MC ${mcVersion}`, 'info');
    } catch (e) {
      log('warn', `[MODS] ${label} install failed: ${e.message}`);
      _mcConsole(`${label} install failed: ${e.message}`, 'warn');
    }
  }

  // Second pass: dependencies the jars themselves declare (fabric.mod.json
  // "depends") that Modrinth's metadata left out — e.g. SkinShuffle needs
  // YACL but its Modrinth version doesn't list it. Repeat a few rounds
  // because a dependency can bring its own.
  for (let round = 0; round < 3; round++) {
    const index = _buildModIdIndex(modsDir);
    const missing = new Map();
    for (const dest of Object.keys(nextManifest)) {
      const meta = _readFabricModMeta(path.join(modsDir, dest));
      for (const id of Object.keys(meta?.depends || {})) {
        if (BUILTIN_MOD_IDS.has(id) || id.startsWith('fabric-')) continue;
        const provider = index.get(id);
        if (provider) {
          // Already on disk. If it's one we manage (from a previous launch),
          // carry it into the new manifest so the cleanup below keeps it —
          // unless it was resolved for a different MC version, in which case
          // re-resolve it.
          const prev = manifest[provider];
          if (!prev || prev.mcVersion === mcVersion) {
            if (prev && !nextManifest[provider]) nextManifest[provider] = prev;
            continue;
          }
        }
        if (!missing.has(id)) missing.set(id, dest);
      }
    }
    if (!missing.size) break;
    for (const [id, requiredBy] of missing) {
      const label = requiredBy.replace(/^Icey-?/, '').replace(/\.jar$/, '');
      try {
        const ref = await _modrinthProjectForModId(id);
        if (!ref) { _mcConsole(`${label} needs "${id}" but it couldn't be found on Modrinth — it may not load`, 'warn'); continue; }
        const res = await _resolveModForMc(ref, mcVersion, 'fabric');
        if (!res) { _mcConsole(`${label} needs ${ref}, which has no build for MC ${mcVersion} — it may not load`, 'warn'); continue; }
        const dest = `Icey-${res.slug || ref}.jar`;
        if (nextManifest[dest]) continue;
        const changed = await _installResolvedFile(res, path.join(modsDir, dest));
        nextManifest[dest] = { slug: res.slug, versionId: res.versionId || null, versionNumber: res.versionNumber || null, mcVersion, requiredBy: id };
        if (changed) _mcConsole(`${res.title || ref} ${res.versionNumber ? 'v' + res.versionNumber + ' ' : ''}installed (needed by ${label})`, 'info');
      } catch (e) {
        log('warn', `[MODS] dependency ${id}: ${e.message}`);
      }
    }
  }

  // Remove managed files that are no longer wanted (toggle turned off,
  // dependency no longer needed, or different MC version).
  const legacy = BUNDLED_MOD_REGISTRY.map(e => e.dest);
  const previously = new Set([...Object.keys(manifest), ...legacy]);
  for (const dest of previously) {
    if (nextManifest[dest]) continue;
    const p = path.join(modsDir, dest);
    if (fs.existsSync(p)) {
      try { fs.unlinkSync(p); log('info', 'Removed managed mod: ' + dest); _mcConsole('Removed ' + dest.replace(/^Icey-?/, '').replace(/\.jar$/, ''), 'info'); } catch (_) {}
    }
  }
  _writeJsonSafe(manifestPath, nextManifest);
}

// ── Icey mod jar selection ────────────────────────────────────────────
// CI builds one jar per MC version in the matrix. For versions we don't
// build (1.21.9, 1.21.10, …) pick the closest lower build whose declared
// range still covers the target — the loader accepts it and the compat
// shims inside the mod handle the API drift.
function _pickIceyJar(kind, mcVersion) {
  if (!_isYarnEraVersion(mcVersion)) return null;
  const pattern = /^iceymod-mc(.+)-1\.0\.0\.jar$/i;
  const dirs = [path.join(__dirname, 'mod', 'build', 'libs'), DATA_DIR, path.join(__dirname, 'resources')];
  const found = [];
  for (const dir of dirs) {
    try {
      if (!dir || !fs.existsSync(dir)) continue;
      for (const f of fs.readdirSync(dir)) {
        const m = f.match(pattern);
        if (m) found.push({ ver: m[1], name: f, path: path.join(dir, f) });
      }
    } catch (_) {}
  }
  if (!found.length) return null;
  const exact = found.find(j => j.ver === mcVersion);
  if (exact) return exact;
  const target = _parseVersion(mcVersion);
  const lower = found
    .filter(j => _compareVersions(_parseVersion(j.ver), target) < 0)
    .sort((a, b) => _compareVersions(_parseVersion(b.ver), _parseVersion(a.ver)));
  for (const j of lower) {
    const meta = _readFabricModMeta(j.path);
    const range = meta?.depends?.minecraft;
    if (!range || _satisfiesVersionRange(mcVersion, range)) return j;
  }
  return null;
}

// ── Zip walking (for .mrpack import) ──────────────────────────────────
function _zipEntries(buf) {
  const entries = [];
  const len = buf.length;
  let eocd = -1;
  for (let i = len - 22; i >= Math.max(0, len - 65557); i--) {
    if (buf[i] === 0x50 && buf[i + 1] === 0x4b && buf[i + 2] === 0x05 && buf[i + 3] === 0x06) { eocd = i; break; }
  }
  if (eocd === -1) return entries;
  let cdOffset = buf.readUInt32LE(eocd + 16);
  let cdEntries = buf.readUInt16LE(eocd + 10);
  // ZIP64: sizes/offsets overflow → look for the zip64 EOCD locator.
  if (cdOffset === 0xffffffff || cdEntries === 0xffff) {
    const loc = eocd - 20;
    if (loc >= 0 && buf.readUInt32LE(loc) === 0x07064b50) {
      const z64 = Number(buf.readBigUInt64LE(loc + 8));
      if (buf.readUInt32LE(z64) === 0x06064b50) {
        cdEntries = Number(buf.readBigUInt64LE(z64 + 32));
        cdOffset = Number(buf.readBigUInt64LE(z64 + 48));
      }
    }
  }
  let offset = cdOffset;
  for (let i = 0; i < cdEntries && offset + 46 <= len; i++) {
    if (buf.readUInt32LE(offset) !== 0x02014b50) break;
    const method = buf.readUInt16LE(offset + 10);
    let compressedSize = buf.readUInt32LE(offset + 20);
    let size = buf.readUInt32LE(offset + 24);
    const nameLen = buf.readUInt16LE(offset + 28);
    const extraLen = buf.readUInt16LE(offset + 30);
    const commentLen = buf.readUInt16LE(offset + 32);
    let localHeaderOffset = buf.readUInt32LE(offset + 42);
    const name = buf.toString('utf-8', offset + 46, offset + 46 + nameLen);
    // zip64 extra field
    if (compressedSize === 0xffffffff || size === 0xffffffff || localHeaderOffset === 0xffffffff) {
      let e = offset + 46 + nameLen; const end = e + extraLen;
      while (e + 4 <= end) {
        const id = buf.readUInt16LE(e), sz = buf.readUInt16LE(e + 2);
        if (id === 0x0001) {
          let p = e + 4;
          if (size === 0xffffffff) { size = Number(buf.readBigUInt64LE(p)); p += 8; }
          if (compressedSize === 0xffffffff) { compressedSize = Number(buf.readBigUInt64LE(p)); p += 8; }
          if (localHeaderOffset === 0xffffffff) { localHeaderOffset = Number(buf.readBigUInt64LE(p)); p += 8; }
          break;
        }
        e += 4 + sz;
      }
    }
    entries.push({ name, method, compressedSize, size, localHeaderOffset, isDir: name.endsWith('/') });
    offset += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function _zipReadEntry(buf, entry) {
  const lh = entry.localHeaderOffset;
  if (lh + 30 > buf.length || buf.readUInt32LE(lh) !== 0x04034b50) return null;
  const nameLen = buf.readUInt16LE(lh + 26);
  const extraLen = buf.readUInt16LE(lh + 28);
  const start = lh + 30 + nameLen + extraLen;
  if (start + entry.compressedSize > buf.length) return null;
  const raw = buf.slice(start, start + entry.compressedSize);
  if (entry.method === 0) return raw;
  if (entry.method === 8) { try { return require('zlib').inflateRawSync(raw); } catch (_) { return null; } }
  return null;
}

// ── options.txt resourcePacks manipulation (arbitrary entries) ────────
function _readResourcepackEntries(installGameDir) {
  const optionsPath = path.join(installGameDir, 'options.txt');
  if (!fs.existsSync(optionsPath)) return null;
  for (const line of fs.readFileSync(optionsPath, 'utf-8').split('\n')) {
    if (line.startsWith('resourcePacks:')) {
      try { return JSON.parse(line.slice('resourcePacks:'.length)); } catch (_) { return null; }
    }
  }
  return null;
}

function _writeResourcepackEntries(installGameDir, entries) {
  const optionsPath = path.join(installGameDir, 'options.txt');
  let lines = [];
  if (fs.existsSync(optionsPath)) lines = fs.readFileSync(optionsPath, 'utf-8').split('\n');
  let found = false;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].startsWith('resourcePacks:')) { lines[i] = 'resourcePacks:' + JSON.stringify(entries); found = true; break; }
  }
  if (!found) lines.push('resourcePacks:' + JSON.stringify(entries));
  fs.mkdirSync(installGameDir, { recursive: true });
  try { fs.writeFileSync(optionsPath, lines.join('\n'), 'utf-8'); } catch (_) {}
}

// ── Mod conflict detection & repair ───────────────────────────────────
// Every jar in modsDir → Map modId → meta (+file). `provides` aliases map
// to the same record so "sodium" style aliases resolve.
function _scanMods(modsDir) {
  const mods = new Map();
  try {
    for (const f of fs.readdirSync(modsDir)) {
      if (!f.endsWith('.jar')) continue;
      const meta = _readFabricModMeta(path.join(modsDir, f));
      if (!meta || !meta.id) continue;
      const rec = { ...meta, file: f };
      mods.set(meta.id, rec);
      for (const p of meta.provides) if (!mods.has(p)) mods.set(p, rec);
    }
  } catch (_) {}
  return mods;
}

// Declared conflicts among installed jars: "A depends on B in range R but
// B is outside R", or "A breaks B in range R and B is inside R".
function _findModConflicts(mods) {
  const out = [];
  for (const [id, m] of mods) {
    if (m.id !== id) continue; // alias entry
    for (const [dep, range] of Object.entries(m.depends || {})) {
      if (BUILTIN_MOD_IDS.has(dep) || dep.startsWith('fabric-')) continue;
      const other = mods.get(dep);
      if (!other || !other.version || other === m) continue;
      if (!_satisfiesAny(other.version, range)) out.push({ kind: 'depends', a: m.id, b: other.id, range: JSON.stringify(range), have: other.version });
    }
    for (const [brk, range] of Object.entries(m.breaks || {})) {
      const other = mods.get(brk);
      if (!other || !other.version || other === m) continue;
      if (_satisfiesAny(other.version, range)) out.push({ kind: 'breaks', a: m.id, b: other.id, range: JSON.stringify(range), have: other.version });
    }
  }
  return out;
}

// Copy of `mods` with record `oldRec` swapped for `newRec` (ids + aliases).
function _withReplaced(mods, oldRec, newRec) {
  const out = new Map();
  for (const [k, v] of mods) if (v !== oldRec) out.set(k, v);
  out.set(newRec.id, newRec);
  for (const p of (newRec.provides || [])) if (!out.has(p)) out.set(p, newRec);
  return out;
}

// Do records x and y accept each other (depends + breaks, both directions)?
function _pairFits(x, y) {
  const idsOf = (r) => [r.id, ...(r.provides || [])];
  const check = (from, to) => {
    for (const id of idsOf(to)) {
      const dep = (from.depends || {})[id];
      if (dep !== undefined && !_satisfiesAny(to.version, dep)) return false;
      const brk = (from.breaks || {})[id];
      if (brk !== undefined && _satisfiesAny(to.version, brk)) return false;
    }
    return true;
  };
  return check(x, y) && check(y, x);
}

function _describeConflict(c, mods) {
  const name = (id) => { const m = mods.get(id); return m ? `${m.name || id} ${m.version || ''}`.trim() : id; };
  return c.kind === 'depends'
    ? `${name(c.a)} needs ${c.b} ${c.range}, but ${c.have} is installed`
    : `${name(c.a)} is incompatible with ${c.b} ${c.range} (installed: ${c.have})`;
}

// Swap the pack-managed jar for mod `id` to another Modrinth build of the
// same project. Candidates (newest first, stable releases before betas)
// are downloaded to read their real manifests; the winner is the one that
// resolves `conflict` with the other party AND leaves the fewest declared
// conflicts overall — remaining ones are handled in later rounds. Returns
// false when no candidate improves on the current state.
async function _repickPackMod(id, mods, modsDir, mcVersion, packMods, conflict, limit) {
  const cur = mods.get(id);
  if (!cur) return false;
  const owner = packMods.get(cur.file);
  if (!owner || !owner.projectId) return false;
  const otherId = conflict.a === cur.id ? conflict.b : conflict.a;
  const other = mods.get(otherId);
  if (!other) return false;
  let list;
  try { list = await _listModVersionsForMc(owner.projectId, mcVersion, 'fabric'); } catch (_) { return false; }
  const rank = (v) => v.versionType === 'release' ? 0 : 1;
  list = list.slice().sort((a, b) => rank(a) - rank(b));
  const baseline = _findModConflicts(mods).length;
  const scored = [];
  let tried = 0;
  for (let i = 0; i < list.length; i++) {
    const cand = list[i];
    if (cand.versionId && cand.versionId === owner.versionId) continue;
    if (cand.filename === cur.file) continue;
    if (tried++ >= (limit || 14)) break;
    try {
      const cacheFile = await _ensureCachedFile(cand);
      const meta = _readFabricModMeta(cacheFile);
      if (!meta || meta.id !== cur.id || !meta.version) continue;
      const rec = { ...meta, file: cand.filename };
      if (!_pairFits(rec, other)) continue;
      const trial = _withReplaced(mods, cur, rec);
      scored.push({ cand, cacheFile, meta, conflicts: _findModConflicts(trial).length, rank: rank(cand), idx: i });
    } catch (e) { log('warn', '[JAVASTUFF] candidate ' + cand.filename + ': ' + e.message); }
  }
  if (!scored.length) return false;
  scored.sort((a, b) => a.conflicts - b.conflicts || a.rank - b.rank || a.idx - b.idx);
  const best = scored[0];
  if (best.conflicts >= baseline) return false;
  try { fs.unlinkSync(path.join(modsDir, cur.file)); } catch (_) {}
  fs.copyFileSync(best.cacheFile, path.join(modsDir, best.cand.filename));
  packMods.delete(cur.file);
  packMods.set(best.cand.filename, { projectId: owner.projectId, versionId: best.cand.versionId, folder: 'mods' });
  _mcConsole(`Java & Stuff: ${best.meta.name || id} ${cur.version} → ${best.meta.version} so it fits ${other.name || otherId}`, 'info');
  return true;
}

// Resolve declared conflicts by re-picking pack-managed mods or, as a last
// resort, dropping the one that can't be satisfied. Returns dropped files.
async function _repairPackConflicts(modsDir, mcVersion, packMods) {
  const dropped = [];
  for (let round = 0; round < 16; round++) {
    const mods = _scanMods(modsDir);
    const conflicts = _findModConflicts(mods);
    if (!conflicts.length) break;
    const c = conflicts[0];
    const A = mods.get(c.a), B = mods.get(c.b);
    _mcConsole('Java & Stuff: ' + _describeConflict(c, mods) + ' — fixing', 'warn');
    if (await _repickPackMod(c.b, mods, modsDir, mcVersion, packMods, c)) continue;
    if (await _repickPackMod(c.a, mods, modsDir, mcVersion, packMods, c)) continue;
    // Nothing fits. A hard dependency that can't be met means the dependent
    // can't run; for "breaks", remove whichever side the pack manages.
    const victims = c.kind === 'depends' ? [A] : [B, A];
    const victim = victims.find(m => m && packMods.has(m.file));
    if (!victim) { _mcConsole('Java & Stuff: cannot fix this one (neither mod is managed by the pack) — the game may refuse to start', 'warn'); break; }
    try { fs.unlinkSync(path.join(modsDir, victim.file)); } catch (_) {}
    packMods.delete(victim.file);
    dropped.push(victim.file);
    _mcConsole(`Java & Stuff: removed ${victim.name || victim.id} ${victim.version || ''} (no build compatible with the rest for MC ${mcVersion})`, 'warn');
  }
  return dropped;
}

// Highest Java major any jar (or its nested jars) asks for ("java": ">=22").
function _requiredJavaFromMods(modsDir) {
  let best = 0;
  try {
    for (const f of fs.readdirSync(modsDir)) {
      if (!f.endsWith('.jar')) continue;
      const meta = _readFabricModMeta(path.join(modsDir, f));
      if (meta) best = Math.max(best, meta.javaNeed || 0);
    }
  } catch (_) {}
  return best;
}

// ── Java & Stuff modpack (Settings toggle) ────────────────────────────
// The bundled .mrpack is pinned to one MC version. For that version we
// use its exact file list; for any other version every Modrinth file is
// re-resolved against the installation's MC version (files with no build
// for that version are skipped and reported). Resource packs that change
// how armor looks are installed but left disabled — turn them on in-game
// under Options → Resource Packs if you want the 3D armor look.
const JAVASTUFF_PACK_FILE = 'JavaAndStuff-1.3.2.mrpack';
const JAVASTUFF_ARMOR_PACK_RE = /armor|3d trims/i;
// Mods that can never work on a given OS. 'splashscreen' opens a Java AWT
// window; on macOS Minecraft runs with -XstartOnFirstThread, so AWT's event
// loop never runs and Window.dispose() waits forever — the game freezes
// right after mod init with no error. Matched by mod id (from the jar) or
// by filename prefix (before download).
const PLATFORM_INCOMPATIBLE_MODS = {
  darwin: [{ id: 'splashscreen', file: /^splashscreen[-_.]/i, why: 'freezes Minecraft on macOS (AWT window + -XstartOnFirstThread)' }],
};

function _platformIncompatible(modId, filename) {
  const list = PLATFORM_INCOMPATIBLE_MODS[process.platform] || [];
  return list.find(m => (modId && m.id === modId) || (filename && m.file.test(filename))) || null;
}

// Remove any jar in modsDir that can't run on this OS. Returns removed names.
function _sweepPlatformIncompatibleMods(modsDir) {
  const removed = [];
  const list = PLATFORM_INCOMPATIBLE_MODS[process.platform] || [];
  if (!list.length) return removed;
  try {
    for (const f of fs.readdirSync(modsDir)) {
      if (!f.endsWith('.jar')) continue;
      let hit = _platformIncompatible(null, f);
      if (!hit) { const meta = _readFabricModMeta(path.join(modsDir, f)); hit = meta ? _platformIncompatible(meta.id, null) : null; }
      if (!hit) continue;
      try { fs.unlinkSync(path.join(modsDir, f)); removed.push(f); _mcConsole(`Removed ${f} — ${hit.why}`, 'warn'); } catch (_) {}
    }
  } catch (_) {}
  return removed;
}
const JAVASTUFF_SKIP_OVERRIDES = /^(options\.txt|replace-lines\.ps1|fixresourcepacks.*\.bat|logo\.png)$/i;

function _javaStuffPackPath() {
  const candidates = [
    path.join(__dirname, 'resources', 'modpacks', JAVASTUFF_PACK_FILE),
    path.join(process.resourcesPath || '', 'modpacks', JAVASTUFF_PACK_FILE),
    path.join(DATA_DIR, JAVASTUFF_PACK_FILE),
  ];
  for (const p of candidates) if (p && fs.existsSync(p)) return p;
  return null;
}

function _removeJavaStuffPack(installGameDir, manifest) {
  let removed = 0;
  const root = path.resolve(installGameDir);
  for (const rel of (manifest.files || [])) {
    // Only ever touch things inside mods/, resourcepacks/ or shaderpacks/.
    if (typeof rel !== 'string' || !/^(mods|resourcepacks|shaderpacks)\/[^/]/.test(rel) || rel.includes('..')) continue;
    const p = path.resolve(installGameDir, rel);
    if (!p.startsWith(root + path.sep)) continue;
    try {
      if (!fs.existsSync(p)) continue;
      fs.rmSync(p, { recursive: true, force: true });
      removed++;
    } catch (_) {}
  }
  const registered = new Set(manifest.registeredPacks || []);
  if (registered.size) {
    const entries = _readResourcepackEntries(installGameDir);
    if (entries) _writeResourcepackEntries(installGameDir, entries.filter(e => !registered.has(e)));
  }
  return removed;
}

async function _ensureJavaStuffPack(installGameDir, mcVersion, enabled) {
  const manifestPath = path.join(installGameDir, '.icey-javastuff.json');
  const manifest = _readJsonSafe(manifestPath);

  if (!enabled) {
    if (manifest) {
      const n = _removeJavaStuffPack(installGameDir, manifest);
      try { fs.unlinkSync(manifestPath); } catch (_) {}
      _mcConsole(`Java & Stuff disabled — removed ${n} files`, 'info');
    }
    return;
  }

  const packPath = _javaStuffPackPath();
  if (!packPath) { _mcConsole('Java & Stuff pack file is missing from this build — skipped', 'warn'); return; }
  const buf = fs.readFileSync(packPath);
  const entries = _zipEntries(buf);
  const indexEntry = entries.find(e => e.name === 'modrinth.index.json');
  if (!indexEntry) { _mcConsole('Java & Stuff pack is corrupt (no index) — skipped', 'warn'); return; }
  const index = JSON.parse(_zipReadEntry(buf, indexEntry).toString('utf-8'));
  const packVersion = index.versionId || '0';
  const packMc = index.dependencies?.minecraft || '';

  if (manifest && manifest.complete && manifest.mcVersion === mcVersion && manifest.packVersion === packVersion) return;
  if (manifest && manifest.mcVersion !== mcVersion) {
    // Installation switched MC version: throw away the old file set first.
    _removeJavaStuffPack(installGameDir, manifest);
  }

  const exact = packMc === mcVersion;
  _mcToast(`Installing Java & Stuff for Minecraft ${mcVersion}${exact ? '' : ' (re-matching each mod to this version)'} — this takes a few minutes the first time`, 'info');
  _mcConsole(`Java & Stuff: installing pack v${packVersion} into MC ${mcVersion}`, 'info');

  const modsDir = path.join(installGameDir, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });
  const installed = new Set((manifest && manifest.mcVersion === mcVersion) ? (manifest.files || []) : []);
  const skipped = [];
  const packMods = new Map(); // mods/<filename> we placed → { projectId, versionId }
  const files = (index.files || []).filter(f => !(f.env && f.env.client === 'unsupported'));

  // Resolve + download with a small worker pool.
  let done = 0;
  const work = files.slice();
  const idIndex = _buildModIdIndex(modsDir);
  const worker = async () => {
    while (work.length) {
      const f = work.shift();
      const relPath = String(f.path || '').replace(/\\/g, '/');
      if (!relPath || relPath.includes('..')) continue;
      const folder = relPath.split('/')[0];
      const originalName = relPath.split('/').pop();
      if (folder === 'mods' && _platformIncompatible(null, originalName)) {
        skipped.push(originalName + ' (not usable on this OS)');
        continue;
      }
      const dlUrl = (f.downloads || [])[0] || '';
      const m = dlUrl.match(/cdn\.modrinth\.com\/data\/([^/]+)\/versions\/([^/]+)\//);
      let res = null;
      try {
        if (exact || !m) {
          res = { filename: originalName, url: dlUrl, sha1: f.hashes?.sha1 || null, projectId: m ? m[1] : null, versionId: m ? m[2] : null };
        } else {
          const loader = folder === 'mods' ? 'fabric' : null;
          res = await _resolveModForMc(m[1], mcVersion, loader);
          if (!res) { skipped.push(originalName); continue; }
        }
        if (folder === 'mods' && _platformIncompatible(null, res.filename)) {
          skipped.push(res.filename + ' (not usable on this OS)');
          continue;
        }
        const destPath = path.join(installGameDir, folder, res.filename);
        await _installResolvedFile(res, destPath);
        installed.add(folder + '/' + res.filename);
        if (folder === 'mods') {
          const dlMeta = _readFabricModMeta(destPath);
          const bad = dlMeta ? _platformIncompatible(dlMeta.id, null) : null;
          if (bad) {
            try { fs.unlinkSync(destPath); } catch (_) {}
            installed.delete(folder + '/' + res.filename);
            skipped.push(res.filename + ' (not usable on this OS)');
            continue;
          }
          packMods.set(res.filename, { projectId: res.projectId || (m ? m[1] : null), versionId: res.versionId || null, folder });
          // Drop older duplicates of the same mod id (user copies or a
          // previous pack version). Launcher-managed Icey*.jar copies
          // yield to the pack's copy so nothing is installed twice.
          const meta = _readFabricModMeta(destPath);
          const other = meta && meta.id ? _findJarByModId(modsDir, meta.id, res.filename, idIndex) : null;
          if (other) {
            try { fs.unlinkSync(path.join(modsDir, other)); _mcConsole(`Java & Stuff: replaced duplicate ${other}`, 'info'); } catch (_) {}
          }
          if (meta && meta.id) idIndex.set(meta.id, res.filename);
        }
      } catch (e) {
        log('warn', '[JAVASTUFF] ' + originalName + ': ' + e.message);
        skipped.push(originalName);
      } finally {
        done++;
        if (done % 10 === 0 || done === files.length) _mcConsole(`Java & Stuff: ${done}/${files.length} files`, 'info');
      }
    }
  };
  await Promise.all([worker(), worker(), worker(), worker()]);

  // Newest-of-each isn't always a coherent set (Sodium 0.8.14 breaks Iris
  // ≤1.10.7, SodiumCoreShaderSupport pins Sodium 0.8.12, …). Read the real
  // manifests and re-pick / drop until Fabric's own checks would pass.
  if (packMods.size) {
    try {
      const dropped = await _repairPackConflicts(modsDir, mcVersion, packMods);
      for (const f of dropped) skipped.push(f + ' (incompatible with the rest)');
      for (const key of [...installed]) if (key.startsWith('mods/') && !packMods.has(key.slice(5))) installed.delete(key);
      for (const f of packMods.keys()) installed.add('mods/' + f);
    } catch (e) {
      log('warn', '[JAVASTUFF] conflict repair failed: ' + e.message);
    }
  }

  // Overrides: configs, resource packs, shader packs shipped inside the pack.
  let overrideCount = 0;
  for (const e of entries) {
    if (e.isDir || !e.name.startsWith('overrides/')) continue;
    const rel = e.name.slice('overrides/'.length);
    if (!rel || rel.includes('..')) continue;
    const top = rel.split('/')[0];
    if (JAVASTUFF_SKIP_OVERRIDES.test(rel)) continue;
    const dest = path.join(installGameDir, rel);
    // Keep the user's own edits to config files across re-installs.
    if (top === 'config' && fs.existsSync(dest)) continue;
    const data = _zipReadEntry(buf, e);
    if (!data) continue;
    try {
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, data);
      overrideCount++;
      if (top === 'resourcepacks' || top === 'shaderpacks') {
        installed.add(rel.split('/').slice(0, 2).join('/'));
      }
    } catch (err) {
      log('warn', '[JAVASTUFF] override ' + rel + ': ' + err.message);
    }
  }

  // Enable the pack's resource packs (minus the armor ones) in options.txt.
  const registered = new Set(manifest?.registeredPacks || []);
  const optEntry = entries.find(e => e.name === 'overrides/options.txt');
  if (optEntry) {
    const text = (_zipReadEntry(buf, optEntry) || Buffer.alloc(0)).toString('utf-8');
    const line = text.split('\n').find(l => l.startsWith('resourcePacks:'));
    let packList = [];
    try { packList = line ? JSON.parse(line.slice('resourcePacks:'.length)) : []; } catch (_) {}
    const current = _readResourcepackEntries(installGameDir) || ['vanilla'];
    for (const entry of packList) {
      if (JAVASTUFF_ARMOR_PACK_RE.test(entry)) continue;
      if (entry.startsWith('file/')) {
        const p = path.join(installGameDir, 'resourcepacks', entry.slice(5));
        if (!fs.existsSync(p)) continue;
      }
      if (!current.includes(entry)) { current.push(entry); registered.add(entry); }
    }
    _writeResourcepackEntries(installGameDir, current);
  }

  _writeJsonSafe(manifestPath, {
    mcVersion, packVersion, complete: true,
    files: [...installed],
    registeredPacks: [...registered],
    skipped,
    installedAt: Date.now(),
  });
  const summary = `Java & Stuff installed: ${installed.size} files` + (skipped.length ? `, ${skipped.length} not available for MC ${mcVersion}` : '');
  _mcConsole(summary, skipped.length ? 'warn' : 'info');
  _mcToast(summary + '. Armor packs are installed but off — enable them in-game under Resource Packs.', 'success');
}

// ── Mod version compatibility checking ──────────────────────────────────
// Strip semver build metadata / pre-release so "0.8.13+mc1.21.11",
// "1.10.7+1.21.11-fabric" and "0.9-" all compare on their core numbers.
function _fabricVersionCore(v) {
  return String(v || '').trim().replace(/^[vV]/, '').split('+')[0].split('-')[0];
}

function _parseVersion(str) {
  const parts = _fabricVersionCore(str).split('.').map(p => { const n = parseInt(p, 10); return Number.isFinite(n) ? n : 0; });
  while (parts.length < 3) parts.push(0);
  return parts;
}

// fabric.mod.json allows a single range string or an array meaning "any of".
function _satisfiesAny(version, rangeOrList) {
  const list = Array.isArray(rangeOrList) ? rangeOrList : [rangeOrList];
  if (!list.length) return true;
  return list.some(r => _satisfiesVersionRange(version, r));
}

function _compareVersions(a, b) {
  for (let i = 0; i < 3; i++) {
    if ((a[i] || 0) > (b[i] || 0)) return 1;
    if ((a[i] || 0) < (b[i] || 0)) return -1;
  }
  return 0;
}

function _satisfiesVersionRange(version, range) {
  if (!range || range === '*') return true;
  // Handle OR conditions (||)
  const orParts = range.split('||').map(s => s.trim());
  for (const orPart of orParts) {
    const conditions = orPart.split(/\s+/).filter(Boolean);
    let allMet = true;
    for (const cond of conditions) {
      if (cond === '*') continue;
      // Tilde range ~1.21.1 -> >=1.21.1 <1.22.0
      if (cond.startsWith('~')) {
        const ver = _parseVersion(cond.slice(1));
        const v = _parseVersion(version);
        if (_compareVersions(v, ver) < 0 || v[0] !== ver[0] || v[1] !== ver[1]) { allMet = false; break; }
        continue;
      }
      // Caret range ^1.21.1 -> >=1.21.1 <2.0.0
      if (cond.startsWith('^')) {
        const ver = _parseVersion(cond.slice(1));
        const v = _parseVersion(version);
        if (_compareVersions(v, ver) < 0 || v[0] !== ver[0]) { allMet = false; break; }
        continue;
      }
      // Wildcard versions like 1.21.x (checked on the core part so build
      // metadata like "+mc1.21.11-fabric" can't be mistaken for a wildcard)
      const condCore = _fabricVersionCore(cond.replace(/^[><=~^]+/, ''));
      if (condCore.includes('x') || condCore.includes('*')) {
        const parts = condCore.split('.');
        const v = _parseVersion(version);
        let match = true;
        for (let i = 0; i < parts.length; i++) {
          if (parts[i] === 'x' || parts[i] === '*') continue;
          if (v[i] !== Number(parts[i])) { match = false; break; }
        }
        if (!match) { allMet = false; break; }
        continue;
      }
      // Operators: >=, <=, >, <, =
      let op = '=', verStr = cond;
      if (cond.startsWith('>=')) { op = '>='; verStr = cond.slice(2); }
      else if (cond.startsWith('<=')) { op = '<='; verStr = cond.slice(2); }
      else if (cond.startsWith('>')) { op = '>'; verStr = cond.slice(1); }
      else if (cond.startsWith('<')) { op = '<'; verStr = cond.slice(1); }
      else if (cond.startsWith('=')) { op = '='; verStr = cond.slice(1); }
      const ver = _parseVersion(verStr);
      const v = _parseVersion(version);
      const cmp = _compareVersions(v, ver);
      if (op === '>=' && cmp < 0) { allMet = false; break; }
      if (op === '<=' && cmp > 0) { allMet = false; break; }
      if (op === '>' && cmp <= 0) { allMet = false; break; }
      if (op === '<' && cmp >= 0) { allMet = false; break; }
      if (op === '=' && cmp !== 0) { allMet = false; break; }
    }
    if (allMet) return true;
  }
  return false;
}

// ── ZIP file extractor using central directory (handles all JARs) ──────
// ── Helpers: options.txt resource-pack list manipulation ────────────────
function _registerResourcepackLine(installGameDir, filename) {
  const optionsPath = path.join(installGameDir, 'options.txt');
  let lines = [];
  if (fs.existsSync(optionsPath)) lines = fs.readFileSync(optionsPath, 'utf-8').split('\n');
  const entry = 'file/' + filename;
  let found = false;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].startsWith('resourcePacks:')) {
      found = true;
      try {
        const packs = JSON.parse(lines[i].slice('resourcePacks:'.length));
        if (!packs.includes(entry)) packs.push(entry);
        lines[i] = 'resourcePacks:' + JSON.stringify(packs);
      } catch (_) {
        lines[i] = 'resourcePacks:' + JSON.stringify(['vanilla', entry]);
      }
      break;
    }
  }
  if (!found) lines.push('resourcePacks:' + JSON.stringify(['vanilla', entry]));
  fs.mkdirSync(installGameDir, { recursive: true });
  try { fs.writeFileSync(optionsPath, lines.join('\n'), 'utf-8'); } catch (_) {}
}

function _unregisterResourcepackLine(installGameDir, filename) {
  const optionsPath = path.join(installGameDir, 'options.txt');
  if (!fs.existsSync(optionsPath)) return;
  const lines = fs.readFileSync(optionsPath, 'utf-8').split('\n');
  const entry = 'file/' + filename;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].startsWith('resourcePacks:')) {
      try {
        const packs = JSON.parse(lines[i].slice('resourcePacks:'.length));
        const filtered = packs.filter(p => p !== entry);
        lines[i] = 'resourcePacks:' + JSON.stringify(filtered);
      } catch (_) {}
      break;
    }
  }
  try { fs.writeFileSync(optionsPath, lines.join('\n'), 'utf-8'); } catch (_) {}
}

// ── Panorama catalog ────────────────────────────────────────────────────
function getPanoramasDir() {
  const candidates = [
    path.join(__dirname, 'resources', 'panoramas'),
    path.join(process.resourcesPath || '', 'panoramas'),
  ];
  for (const p of candidates) if (p && fs.existsSync(p)) return p;
  return candidates[0];
}

function listPanoramaFiles() {
  const dir = getPanoramasDir();
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter(f => f.toLowerCase().endsWith('.zip')).sort();
}

/**
 * Extract a Minecraft world ZIP into the installation's saves dir.
 *
 * Walks the central directory once to detect the layout:
 *   - All entries share the same root folder → extract verbatim.
 *   - No common root → wrap everything under <zip-basename>/ so MC
 *     finds level.dat at saves/<wrapper>/level.dat.
 *
 * If the resulting world folder name already exists in saves/, append
 * " (2)", " (3)" etc. Never silently overwrites.
 *
 * Returns { worldName, fileCount } or throws on bad zip / IO error.
 */
function importWorldZip(zipPath, savesDir) {
  const zlib = require('zlib');
  const buf = fs.readFileSync(zipPath);
  const len = buf.length;

  // Locate End Of Central Directory record (search backwards).
  let eocdOffset = -1;
  for (let i = len - 22; i >= Math.max(0, len - 65557); i--) {
    if (buf[i] === 0x50 && buf[i+1] === 0x4b && buf[i+2] === 0x05 && buf[i+3] === 0x06) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) throw new Error('Not a valid ZIP file');

  const cdOffset = buf.readUInt32LE(eocdOffset + 16);
  const cdEntries = buf.readUInt16LE(eocdOffset + 10);

  // Pass 1: collect entry metadata + figure out common root prefix.
  const entries = [];
  let commonRoot = null;
  let hasLevelDat = false;
  let offset = cdOffset;
  for (let i = 0; i < cdEntries && offset < len - 46; i++) {
    if (buf[offset] !== 0x50 || buf[offset+1] !== 0x4b
        || buf[offset+2] !== 0x01 || buf[offset+3] !== 0x02) {
      throw new Error('Corrupt central directory at entry ' + i);
    }
    const compMethod = buf.readUInt16LE(offset + 10);
    const compSize = buf.readUInt32LE(offset + 20);
    const nameLen = buf.readUInt16LE(offset + 28);
    const extraLen = buf.readUInt16LE(offset + 30);
    const commentLen = buf.readUInt16LE(offset + 32);
    const localHeaderOffset = buf.readUInt32LE(offset + 42);
    const name = buf.toString('utf-8', offset + 46, offset + 46 + nameLen);
    offset += 46 + nameLen + extraLen + commentLen;

    if (name.length === 0) continue;
    entries.push({ name, compMethod, compSize, localHeaderOffset });

    // Sanity check: a real world archive contains level.dat somewhere.
    if (name.endsWith('/level.dat') || name === 'level.dat') {
      hasLevelDat = true;
    }

    // Track common top-level folder.
    const slash = name.indexOf('/');
    const top = slash === -1 ? null : name.substring(0, slash);
    if (commonRoot === null && top) {
      commonRoot = top;
    } else if (commonRoot !== null && top !== commonRoot) {
      // Mixed root or top-level file → no common prefix.
      commonRoot = '';
    }
  }

  if (!hasLevelDat) {
    throw new Error('No level.dat found in zip — not a Minecraft world archive');
  }

  // Decide final wrapper name.
  let wrapper;
  if (commonRoot && commonRoot !== '') {
    wrapper = commonRoot;
  } else {
    wrapper = path.basename(zipPath, path.extname(zipPath));
  }

  // Resolve a non-colliding final dir.
  fs.mkdirSync(savesDir, { recursive: true });
  let finalName = wrapper;
  let suffix = 2;
  while (fs.existsSync(path.join(savesDir, finalName))) {
    finalName = wrapper + ' (' + (suffix++) + ')';
  }
  const targetRoot = path.join(savesDir, finalName);
  fs.mkdirSync(targetRoot, { recursive: true });

  // Pass 2: write each entry. If commonRoot existed, strip it from the
  // entry path so we end up at <savesDir>/<finalName>/<rest>. Otherwise
  // the entry path is used verbatim under the wrapper.
  let written = 0;
  for (const e of entries) {
    if (e.name.endsWith('/')) continue; // directory marker

    let relPath;
    if (commonRoot && commonRoot !== '') {
      // strip "<commonRoot>/" prefix
      relPath = e.name.substring(commonRoot.length + 1);
    } else {
      relPath = e.name;
    }
    if (!relPath) continue;

    // Defense: zip spec uses '/' for separators, but a malicious or
    // mis-encoded zip might embed '\' or '..' segments. Normalize and
    // reject anything that tries to escape targetRoot. On Windows
    // path.join treats '\' as a separator too, which is exactly the
    // surface we need to neutralize.
    if (relPath.includes('\\')) {
      throw new Error('Backslash in zip entry name (rejected): ' + e.name);
    }
    if (relPath.split('/').some(seg => seg === '..' || seg === '')) {
      throw new Error('Suspicious entry path: ' + e.name);
    }

    const destPath = path.join(targetRoot, relPath);
    // Belt-and-braces path-traversal guard.
    const resolved = path.resolve(destPath);
    const resolvedRoot = path.resolve(targetRoot);
    if (!resolved.startsWith(resolvedRoot + path.sep) && resolved !== resolvedRoot) {
      throw new Error('Path traversal blocked: ' + e.name);
    }

    // Read local file header to find data start.
    const lh = e.localHeaderOffset;
    if (lh + 30 > len) throw new Error('Truncated local header for ' + e.name);
    const lhNameLen = buf.readUInt16LE(lh + 26);
    const lhExtraLen = buf.readUInt16LE(lh + 28);
    const dataStart = lh + 30 + lhNameLen + lhExtraLen;
    if (dataStart + e.compSize > len) throw new Error('Truncated entry data for ' + e.name);
    const rawData = buf.slice(dataStart, dataStart + e.compSize);

    let data;
    if (e.compMethod === 0) data = rawData;
    else if (e.compMethod === 8) data = zlib.inflateRawSync(rawData);
    else throw new Error('Unsupported compression method ' + e.compMethod + ' for ' + e.name);

    fs.mkdirSync(path.dirname(destPath), { recursive: true });
    fs.writeFileSync(destPath, data);
    written++;
  }

  log('info', 'Imported world "' + finalName + '" (' + written + ' files) into ' + savesDir);
  return { worldName: finalName, fileCount: written };
}

function _extractFileFromZip(zipBuffer, targetPath) {
  try {
    const buf = zipBuffer;
    const len = buf.length;

    // Find End of Central Directory record (search backwards)
    let eocdOffset = -1;
    for (let i = len - 22; i >= Math.max(0, len - 65557); i--) {
      if (buf[i] === 0x50 && buf[i+1] === 0x4b && buf[i+2] === 0x05 && buf[i+3] === 0x06) {
        eocdOffset = i;
        break;
      }
    }
    if (eocdOffset === -1) return null;

    const cdOffset = buf.readUInt32LE(eocdOffset + 16);
    const cdEntries = buf.readUInt16LE(eocdOffset + 10);

    let offset = cdOffset;
    for (let i = 0; i < cdEntries && offset < len - 46; i++) {
      // Central directory file header: PK\x01\x02
      if (buf[offset] !== 0x50 || buf[offset+1] !== 0x4b || buf[offset+2] !== 0x01 || buf[offset+3] !== 0x02) break;

      const compressionMethod = buf.readUInt16LE(offset + 10);
      const compressedSize = buf.readUInt32LE(offset + 20);
      const nameLen = buf.readUInt16LE(offset + 28);
      const extraLen = buf.readUInt16LE(offset + 30);
      const commentLen = buf.readUInt16LE(offset + 32);
      const localHeaderOffset = buf.readUInt32LE(offset + 42);
      const name = buf.toString('utf-8', offset + 46, offset + 46 + nameLen);

      offset += 46 + nameLen + extraLen + commentLen;

      if (name !== targetPath) continue;

      // Read from local file header to get actual data
      const lh = localHeaderOffset;
      if (lh + 30 > len) return null;
      const lhNameLen = buf.readUInt16LE(lh + 26);
      const lhExtraLen = buf.readUInt16LE(lh + 28);
      const dataStart = lh + 30 + lhNameLen + lhExtraLen;

      if (compressedSize === 0) return null;
      if (dataStart + compressedSize > len) return null;

      const rawData = buf.slice(dataStart, dataStart + compressedSize);
      if (compressionMethod === 0) return rawData; // stored
      if (compressionMethod === 8) { // deflate
        try { return require('zlib').inflateRawSync(rawData); } catch (_) { return null; }
      }
      return null;
    }
  } catch (_) { /* */ }
  return null;
}

// ── Microsoft Auth ─────────────────────────────────────
const MS_CLIENT_ID = '00000000402b5328'; // Official Minecraft client ID
const MS_REDIRECT = 'https://login.live.com/oauth20_desktop.srf';
const AUTH_FILE = path.join(DATA_DIR, 'auth.json');

/**
 * Auth store schema: { activeUuid: string, accounts: [{username, uuid, accessToken, refreshToken, skinUrl, expiresAt}] }
 * readAuthStore() returns the full store. readAuth() returns the currently active account for backwards compat.
 */
const MAX_ACCOUNTS = 5;

function offlineUuid(username) {
  // Matches vanilla offline UUID: UUID.nameUUIDFromBytes("OfflinePlayer:" + name)
  const hash = crypto.createHash('md5').update('OfflinePlayer:' + username, 'utf8').digest();
  hash[6] = (hash[6] & 0x0F) | 0x30; // version 3
  hash[8] = (hash[8] & 0x3F) | 0x80; // variant
  const hex = hash.toString('hex');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20,32)}`;
}

function readAuthStore() {
  try {
    if (!fs.existsSync(AUTH_FILE)) return { activeUuid: null, accounts: [] };
    const raw = JSON.parse(fs.readFileSync(AUTH_FILE, 'utf-8'));
    // Migrate old single-account format
    if (raw && raw.accessToken && raw.uuid && !raw.accounts) {
      return { activeUuid: raw.uuid, accounts: [{ ...raw, type: raw.type || 'microsoft' }] };
    }
    if (!raw || !Array.isArray(raw.accounts)) return { activeUuid: null, accounts: [] };
    // Fill in missing type field on legacy entries
    const accounts = raw.accounts.map(a => ({ ...a, type: a.type || 'microsoft' }));
    return { activeUuid: raw.activeUuid || (accounts[0]?.uuid ?? null), accounts };
  } catch (_) {
    return { activeUuid: null, accounts: [] };
  }
}

function writeAuthStore(store) { writeJsonAtomic(AUTH_FILE, store); }

function readAuth() {
  const store = readAuthStore();
  if (!store.activeUuid) return null;
  return store.accounts.find(a => a.uuid === store.activeUuid) || null;
}

function upsertAccount(account) {
  const store = readAuthStore();
  const idx = store.accounts.findIndex(a => a.uuid === account.uuid);
  if (idx >= 0) {
    store.accounts[idx] = account;
  } else {
    if (store.accounts.length >= MAX_ACCOUNTS) {
      throw new Error('Max ' + MAX_ACCOUNTS + ' accounts. Remove one first.');
    }
    store.accounts.push(account);
  }
  store.activeUuid = account.uuid;
  writeAuthStore(store);
  return store;
}

// In-place update of an existing account by uuid, WITHOUT touching
// activeUuid. Used by the refresh flow so a background refresh of a
// non-active account doesn't silently switch the user's active session.
function updateAccountInPlace(account) {
  const store = readAuthStore();
  const idx = store.accounts.findIndex(a => a.uuid === account.uuid);
  if (idx < 0) return upsertAccount(account); // first time → fall back
  store.accounts[idx] = account;
  writeAuthStore(store);
  return store;
}

async function httpPost(url, body, contentType = 'application/x-www-form-urlencoded') {
  return new Promise((resolve, reject) => {
    const proto = url.startsWith('https') ? https : http;
    const parsed = new URL(url);
    const req = proto.request({ hostname: parsed.hostname, path: parsed.pathname + parsed.search, method: 'POST', headers: { 'Content-Type': contentType, 'Content-Length': Buffer.byteLength(body) } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => { try { resolve(JSON.parse(data)); } catch (_) { resolve(data); } });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function httpGet(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const proto = url.startsWith('https') ? https : http;
    proto.get(url, { headers: { 'User-Agent': 'IceyClient/1.0.0', ...headers } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => { try { resolve(JSON.parse(data)); } catch (_) { resolve(data); } });
    }).on('error', reject);
  });
}

async function microsoftLogin() {
  return new Promise((resolve, reject) => {
    const authUrl = `https://login.live.com/oauth20_authorize.srf?client_id=${MS_CLIENT_ID}&response_type=code&redirect_uri=${encodeURIComponent(MS_REDIRECT)}&scope=XboxLive.signin%20XboxLive.offline_access&prompt=select_account`;

    const authWin = new BrowserWindow({ width: 520, height: 700, title: 'Microsoft Login', icon: path.join(__dirname, 'src', 'assets', 'icon.png'), webPreferences: { nodeIntegration: false, contextIsolation: true } });
    authWin.setMenuBarVisibility(false);
    authWin.loadURL(authUrl);

    let handled = false;
    const handleRedirect = async (url) => {
      if (handled) return;
      if (url.startsWith(MS_REDIRECT)) {
        handled = true;
        const code = new URL(url).searchParams.get('code');
        if (!code) { authWin.close(); return reject(new Error('No auth code')); }
        authWin.close();
        try {
          const result = await exchangeMicrosoftTokens(code);
          resolve(result);
        } catch (e) { reject(e); }
      }
    };

    authWin.webContents.on('will-redirect', (event, url) => {
      if (url.startsWith(MS_REDIRECT)) { event.preventDefault(); handleRedirect(url); }
    });
    authWin.webContents.on('will-navigate', (event, url) => {
      if (url.startsWith(MS_REDIRECT)) { event.preventDefault(); handleRedirect(url); }
    });
    authWin.webContents.on('did-navigate', (event, url) => { handleRedirect(url); });

    authWin.on('closed', () => { if (!handled) reject(new Error('Login cancelled')); });
  });
}

// MS refresh tokens with periodic rotation last up to ~8 months. Cap
// the silent-refresh chain at that point and force an interactive
// re-login, since the refresh endpoint will start rejecting with
// invalid_grant past that window anyway.
const MAX_SESSION_AGE_MS = 8 * 30 * 24 * 60 * 60 * 1000; // ~8 months
// Refresh proactively a few minutes before the MC token expires so a
// launch that happens right around the expiry doesn't race the auth
// servers.
const REFRESH_BUFFER_MS = 5 * 60 * 1000; // 5 minutes

/**
 * Steps 2-5 of the MS auth chain (XBL → XSTS → MC token → profile).
 * Shared between interactive login and silent refresh.
 *
 * `prevAccount` is the existing stored account when called from refresh;
 * it lets us preserve `loggedInAt` and fall back to the old
 * refresh_token when MS doesn't rotate it on this response.
 */
async function finishMcAuth(msToken, prevAccount) {
  // Step 2: MS Token -> Xbox Live Token
  const xblRes = await httpPost('https://user.auth.xboxlive.com/user/authenticate',
    JSON.stringify({ Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: 'd=' + msToken.access_token }, RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT' }), 'application/json');
  if (!xblRes.Token) { log('error', 'Xbox Live auth failed: ' + JSON.stringify(xblRes)); throw new Error('Xbox Live auth failed'); }

  // Step 3: XBL -> XSTS Token
  const xstsRes = await httpPost('https://xsts.auth.xboxlive.com/xsts/authorize',
    JSON.stringify({ Properties: { SandboxId: 'RETAIL', UserTokens: [xblRes.Token] }, RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT' }), 'application/json');
  if (!xstsRes.Token) { log('error', 'XSTS auth failed: ' + JSON.stringify(xstsRes)); throw new Error('XSTS auth failed'); }
  const userHash = xstsRes.DisplayClaims?.xui?.[0]?.uhs;

  // Step 4: XSTS -> Minecraft Token
  const mcRes = await httpPost('https://api.minecraftservices.com/authentication/login_with_xbox',
    JSON.stringify({ identityToken: `XBL3.0 x=${userHash};${xstsRes.Token}` }), 'application/json');
  if (!mcRes.access_token) { log('error', 'Minecraft auth failed: ' + JSON.stringify(mcRes)); throw new Error('MC auth failed: ' + (mcRes.error || mcRes.errorMessage || JSON.stringify(mcRes))); }

  // Step 5: Get profile
  const profile = await httpGet('https://api.minecraftservices.com/minecraft/profile', { Authorization: 'Bearer ' + mcRes.access_token });

  return {
    type: 'microsoft',
    accessToken: mcRes.access_token,
    username: profile.name || prevAccount?.username || 'Player',
    uuid: profile.id || prevAccount?.uuid || crypto.randomUUID(),
    skinUrl: profile.skins?.[0]?.url || prevAccount?.skinUrl || null,
    // MS rotates refresh tokens — persist the new one when supplied,
    // fall back to the previous one otherwise.
    refreshToken: msToken.refresh_token || prevAccount?.refreshToken || null,
    expiresAt: Date.now() + (mcRes.expires_in || 86400) * 1000,
    // Preserve the original interactive-login timestamp across refreshes
    // so the 8-month cap is measured from real login, not the most
    // recent silent refresh.
    loggedInAt: prevAccount?.loggedInAt || Date.now()
  };
}

async function exchangeMicrosoftTokens(code) {
  // Step 1: Code -> MS Token (interactive login)
  const msToken = await httpPost('https://login.live.com/oauth20_token.srf',
    `client_id=${MS_CLIENT_ID}&code=${code}&grant_type=authorization_code&redirect_uri=${encodeURIComponent(MS_REDIRECT)}&scope=XboxLive.signin%20XboxLive.offline_access`);
  if (!msToken.access_token) { log('error', 'MS token failed: ' + JSON.stringify(msToken)); throw new Error('MS token failed'); }

  const authData = await finishMcAuth(msToken, null);
  upsertAccount(authData);
  return authData;
}

/**
 * Trade the stored refresh_token for a fresh MS access token + (usually
 * rotated) refresh_token, then run the rest of the auth chain to mint
 * a new Minecraft access token. Throws on hard MS errors (invalid_grant,
 * etc.) — the caller surfaces those as "expired" so the user re-logs.
 */
async function refreshMicrosoftTokens(refreshToken, prevAccount) {
  const msToken = await httpPost('https://login.live.com/oauth20_token.srf',
    `client_id=${MS_CLIENT_ID}&grant_type=refresh_token&refresh_token=${encodeURIComponent(refreshToken)}&redirect_uri=${encodeURIComponent(MS_REDIRECT)}&scope=XboxLive.signin%20XboxLive.offline_access`);
  if (!msToken.access_token) {
    // invalid_grant means the refresh chain is dead — surface as a
    // distinct error so callers can mark the account expired instead
    // of just retrying.
    const err = new Error('MS refresh failed: ' + (msToken.error || JSON.stringify(msToken)));
    err.code = msToken.error || 'refresh_failed';
    throw err;
  }
  return finishMcAuth(msToken, prevAccount);
}

// Per-uuid in-flight refresh cache so a flurry of get-auth / get-accounts
// calls from the UI doesn't fire five parallel refresh requests at MS.
const _refreshInflight = new Map();

/**
 * If the account's MC token is fresh, return as-is. Otherwise attempt
 * a silent refresh and return the updated account. Returns null when
 * the account is truly expired (no refresh token, refresh rejected,
 * or hard 8-month cap exceeded) — caller treats null as "needs
 * interactive re-login".
 *
 * Offline accounts pass through unchanged.
 */
async function ensureFreshAuth(account) {
  if (!account) return null;
  if (account.type === 'offline') return account;
  // Token still valid (with a few-minute buffer to avoid races).
  if (account.expiresAt && account.expiresAt - Date.now() > REFRESH_BUFFER_MS) {
    return account;
  }
  if (!account.refreshToken) return null;
  // Hard 8-month cap since the last interactive login — MS rejects past
  // this anyway, surface it locally before burning a network round-trip.
  // Accounts saved before loggedInAt existed get a one-time grace where
  // we treat the cap as "from now" — the refresh chain on those is still
  // healthy enough that re-login isn't urgent.
  if (account.loggedInAt && Date.now() - account.loggedInAt > MAX_SESSION_AGE_MS) {
    return null;
  }

  const cached = _refreshInflight.get(account.uuid);
  if (cached) return cached;

  const p = (async () => {
    try {
      const refreshed = await refreshMicrosoftTokens(account.refreshToken, account);
      updateAccountInPlace(refreshed);
      if (mainWindow && !mainWindow.isDestroyed()) {
        try { mainWindow.webContents.send('account-refreshed', { uuid: refreshed.uuid }); } catch (_) {}
      }
      return refreshed;
    } catch (e) {
      log('warn', `MS refresh for ${account.username} failed: ${e.message}`);
      return null;
    } finally {
      _refreshInflight.delete(account.uuid);
    }
  })();
  _refreshInflight.set(account.uuid, p);
  return p;
}

/** Cheap synchronous check used by UI badge logic — does this account
 *  have a viable refresh path, even if the current accessToken is
 *  past expiry? Mirrors the rejection conditions in ensureFreshAuth so
 *  the badge matches reality without doing a network call. */
function canRefreshAccount(account) {
  if (!account || account.type === 'offline') return false;
  if (!account.refreshToken) return false;
  if (account.loggedInAt && Date.now() - account.loggedInAt > MAX_SESSION_AGE_MS) return false;
  return true;
}

// ── App ready ──────────────────────────────────────────
app.whenReady().then(() => {
  ensureDirs();
  // If the data folder was wiped (see _restoreFromBackupIfWiped), bring
  // back login/installations/settings before anything reads them.
  const restored = _restoreFromBackupIfWiped();
  if (restored.length) {
    log('warn', 'Data folder was empty — restored from backup: ' + restored.join(', '));
    setTimeout(() => _mcToast('Your login, installations and settings were restored from backup. Mods will re-download on next launch.', 'info'), 4000);
  }

  // ── Linux GNOME: auto-create .desktop file + install icon ──
  if (process.platform === 'linux') {
    try {
      // Try dev path first, then packaged resources path
      let iconSrc = path.join(__dirname, 'src', 'assets', 'iconlinux.png');
      if (!fs.existsSync(iconSrc)) iconSrc = path.join(process.resourcesPath, 'iconlinux.png');
      if (!fs.existsSync(iconSrc)) iconSrc = path.join(__dirname, 'src', 'assets', 'icon.png');
      const iconDir = path.join(os.homedir(), '.local', 'share', 'icons', 'hicolor', '512x512', 'apps');
      const iconDest = path.join(iconDir, 'icey-client.png');
      fs.mkdirSync(iconDir, { recursive: true });
      if (fs.existsSync(iconSrc)) fs.copyFileSync(iconSrc, iconDest);
      // Also place in flat icons dir for simple lookups
      const flatDir = path.join(os.homedir(), '.local', 'share', 'icons');
      const flatDest = path.join(flatDir, 'iceyclient.png');
      if (fs.existsSync(iconSrc)) fs.copyFileSync(iconSrc, flatDest);

      const desktopDir = path.join(os.homedir(), '.local', 'share', 'applications');
      fs.mkdirSync(desktopDir, { recursive: true });
      const desktopPath = path.join(desktopDir, 'iceyclient.desktop');
      // Prefer APPIMAGE env var when running from an AppImage (execPath points to a mount)
      const exePath = process.env.APPIMAGE || process.execPath;
      const desktopContent = [
        '[Desktop Entry]',
        'Name=Icey Client',
        'Comment=A premium Minecraft launcher',
        'Exec="' + exePath + '" %U',
        'Icon=icey-client',
        'Type=Application',
        'Categories=Game;',
        'StartupWMClass=icey-client',
        'Terminal=false',
      ].join('\n') + '\n';
      fs.writeFileSync(desktopPath, desktopContent);
      fs.chmodSync(desktopPath, 0o755);
      log('info', 'Installed .desktop file: ' + desktopPath);
    } catch (e) {
      log('warn', 'Failed to create .desktop file: ' + e.message);
    }
  }

  // Reset log file on start
  try { fs.writeFileSync(LOG_FILE, `[${new Date().toISOString()}] [INFO] Icey Client started\n`); } catch (_) { /* */ }

  // ── Splash Screen ──
  const appIcon = process.platform === 'linux'
    ? path.join(__dirname, 'src', 'assets', 'iconlinux.png')
    : path.join(__dirname, 'src', 'assets', 'icon.png');
  const splash = new BrowserWindow({
    width: 400, height: 300, frame: false, transparent: true, alwaysOnTop: true, center: true,
    icon: appIcon,
    webPreferences: { nodeIntegration: false, contextIsolation: true }
  });
  splash.loadFile(path.join(__dirname, 'src', 'splash.html'));

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
    icon: appIcon,
    show: false
  });

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));

  mainWindow.once('ready-to-show', () => {
    splash.destroy();
    mainWindow.show();

  });

  // On macOS, hide instead of close so dock re-activation works
  mainWindow.on('close', (e) => {
    if (process.platform === 'darwin' && !app.isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
    for (const [id, entry] of mcProcesses.entries()) {
      try { entry.proc.kill(); } catch (_) {}
      mcProcesses.delete(id);
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

  ipcMain.on('stop-mc', (_, launchId) => {
    // If a specific launchId is provided, stop just that one. Otherwise stop all.
    if (launchId) {
      const entry = mcProcesses.get(launchId);
      if (entry) {
        try { entry.proc.kill(); } catch (_) {}
        mcProcesses.delete(launchId);
        log('info', `MC #${launchId} killed by user`);
      }
    } else {
      for (const [id, entry] of mcProcesses.entries()) {
        try { entry.proc.kill(); } catch (_) {}
        mcProcesses.delete(id);
      }
      log('info', 'All MC processes killed by user');
    }
  });

  ipcMain.handle('get-running-mc', () => {
    return Array.from(mcProcesses.entries()).map(([id, e]) => ({
      launchId: id,
      installationId: e.installationId,
      username: e.username,
    }));
  });

  // Installations
  ipcMain.handle('get-installations', () => readInstallations());

  ipcMain.handle('save-installation', (_, data) => {
    // Remove from deleted list if re-creating
    const deleted = readDeletedIds();
    if (deleted.has(data.id)) {
      deleted.delete(data.id);
      writeJsonAtomic(DELETED_FILE, [...deleted]);
    }
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
    addDeletedId(id);
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

  ipcMain.handle('import-world', async (_, installationId, zipPath) => {
    try {
      if (!installationId) return { error: 'No installation selected' };
      if (!zipPath || !fs.existsSync(zipPath)) return { error: 'File not found' };
      const gameDir = getInstallGameDir(installationId);
      const savesDir = path.join(gameDir, 'saves');
      const result = importWorldZip(zipPath, savesDir);
      return { success: true, worldName: result.worldName, fileCount: result.fileCount };
    } catch (e) {
      log('warn', 'Import world failed: ' + e.message);
      return { error: e.message };
    }
  });

  ipcMain.handle('get-installed-mods', (_, installationId) => {
    const gameDir = getInstallGameDir(installationId);
    fs.mkdirSync(gameDir, { recursive: true });
    const modsDir = path.join(gameDir, 'mods');
    const rpDir = path.join(gameDir, 'resourcepacks');

    // Look up MC version for this installation
    const installations = readInstallations();
    const inst = installations.find(i => i.id === installationId);
    const mcVersion = inst?.version || null;

    const mods = [];
    const resourcePacks = [];

    if (fs.existsSync(modsDir)) {
      for (const file of fs.readdirSync(modsDir)) {
        const isDisabled = file.endsWith('.jar.disabled');
        if (!file.endsWith('.jar') && !isDisabled) continue;
        const filePath = path.join(modsDir, file);
        try {
          const stats = fs.statSync(filePath);
          let modName = file.replace('.jar.disabled', '').replace('.jar', '').replace(/-/g, ' ');
          let iconBase64 = null;
          let mcConstraint = null;
          let compatible = true;

          // Try to extract mod info from fabric.mod.json inside the jar
          try {
            const jarBuf = fs.readFileSync(filePath);
            const modJson = _extractFileFromZip(jarBuf, 'fabric.mod.json');
            if (modJson) {
              const meta = JSON.parse(modJson.toString('utf-8'));
              if (meta.name) modName = meta.name;
              if (meta.icon) {
                const iconData = _extractFileFromZip(jarBuf, meta.icon);
                if (iconData) {
                  iconBase64 = 'data:image/png;base64,' + iconData.toString('base64');
                }
              }
              mcConstraint = meta.depends?.minecraft || null;
              if (mcConstraint && mcVersion) {
                compatible = _satisfiesVersionRange(mcVersion, mcConstraint);
              }
            }
          } catch (_) { /* not a fabric mod or parse error */ }

          mods.push({
            filename: file,
            name: modName,
            size: stats.size,
            type: 'mod',
            icon: iconBase64,
            disabled: isDisabled,
            compatible,
            mcConstraint,
          });
        } catch (_) { /* skip unreadable */ }
      }
    }

    if (fs.existsSync(rpDir)) {
      for (const file of fs.readdirSync(rpDir)) {
        if (!file.endsWith('.zip')) continue;
        const filePath = path.join(rpDir, file);
        try {
          const stats = fs.statSync(filePath);
          let rpName = file.replace('.zip', '').replace(/-/g, ' ');
          let iconBase64 = null;

          // Try to extract pack.png
          try {
            const zipBuf = fs.readFileSync(filePath);
            const iconData = _extractFileFromZip(zipBuf, 'pack.png');
            if (iconData) {
              iconBase64 = 'data:image/png;base64,' + iconData.toString('base64');
            }
            const packMcmeta = _extractFileFromZip(zipBuf, 'pack.mcmeta');
            if (packMcmeta) {
              const meta = JSON.parse(packMcmeta.toString('utf-8'));
              if (meta.pack?.description) rpName = file.replace('.zip', '');
            }
          } catch (_) { /* */ }

          resourcePacks.push({ filename: file, name: rpName, size: stats.size, type: 'resourcepack', icon: iconBase64 });
        } catch (_) { /* skip */ }
      }
    }

    return { mods, resourcePacks };
  });

  ipcMain.handle('delete-mod', (_, installationId, filename) => {
    const gameDir = getInstallGameDir(installationId);
    const mcDir = getDefaultMcDir();
    let deleted = false;

    // Try per-installation dir first, then shared .minecraft as fallback
    const candidates = [
      path.join(gameDir, 'mods', filename),
      path.join(gameDir, 'resourcepacks', filename),
      path.join(mcDir, 'mods', filename),
      path.join(mcDir, 'resourcepacks', filename),
    ];

    for (const filePath of candidates) {
      try {
        if (fs.existsSync(filePath)) {
          fs.unlinkSync(filePath);
          log('info', 'Deleted mod file: ' + filePath);
          deleted = true;
          // Verify it's actually gone
          if (fs.existsSync(filePath)) {
            log('warn', 'File still exists after delete: ' + filePath);
            return { error: 'File could not be deleted (may be in use)' };
          }
        }
      } catch (e) {
        log('error', 'Failed to delete ' + filePath + ': ' + e.message);
        return { error: 'Failed to delete: ' + e.message };
      }
    }

    return deleted ? { success: true } : { error: 'File not found' };
  });

  // Cleanup stale iceymod+ / iceysmp jars before a fresh install. Pattern
  // matches both naming variants (server-mod-mc / mc- / iceysmp-mc-)
  // because the artifact name has changed across releases and stale
  // 404-HTML-saved-as-jar files would otherwise block a working install.
  ipcMain.handle('cleanup-smp-mods', (_, installationId) => {
    try {
      const gameDir = getInstallGameDir(installationId);
      const modsDir = path.join(gameDir, 'mods');
      if (!fs.existsSync(modsDir)) return { removed: 0 };
      const re = /^(iceysmp|iceymodplus).*\.jar$/i;
      let removed = 0;
      for (const f of fs.readdirSync(modsDir)) {
        if (re.test(f)) {
          try { fs.unlinkSync(path.join(modsDir, f)); removed++; log('info', 'cleaned stale SMP jar: ' + f); }
          catch (e) { log('warn', 'failed to remove ' + f + ': ' + e.message); }
        }
      }
      return { removed };
    } catch (e) { return { error: e.message }; }
  });

  // Verify a downloaded jar is real. HTML 404 pages are ~1KB; real
  // Fabric mod jars are 5KB+. Anything below the threshold gets nuked
  // so Fabric doesn't try to load it as a mod and silently fail.
  ipcMain.handle('verify-jar', (_, filePath, minBytes) => {
    try {
      if (!fs.existsSync(filePath)) return { ok: false, reason: 'missing' };
      const sz = fs.statSync(filePath).size;
      if (sz < (minBytes || 5000)) {
        try { fs.unlinkSync(filePath); } catch (_) {}
        return { ok: false, reason: 'too_small', size: sz };
      }
      return { ok: true, size: sz };
    } catch (e) { return { ok: false, reason: 'error', error: e.message }; }
  });

  ipcMain.handle('toggle-mod', (_, installationId, filename) => {
    const gameDir = getInstallGameDir(installationId);
    const modsDir = path.join(gameDir, 'mods');
    const filePath = path.join(modsDir, filename);

    try {
      if (!fs.existsSync(filePath)) return { error: 'File not found' };

      let newPath;
      if (filename.endsWith('.jar.disabled')) {
        newPath = path.join(modsDir, filename.replace('.jar.disabled', '.jar'));
      } else if (filename.endsWith('.jar')) {
        newPath = filePath + '.disabled';
      } else {
        return { error: 'Not a mod file' };
      }

      fs.renameSync(filePath, newPath);
      const newFilename = path.basename(newPath);
      log('info', 'Toggled mod: ' + filename + ' -> ' + newFilename);
      return { success: true, filename: newFilename };
    } catch (e) {
      log('error', 'Failed to toggle mod ' + filename + ': ' + e.message);
      return { error: 'Failed to toggle: ' + e.message };
    }
  });

  // Iris Shaders install via Modrinth API
  ipcMain.handle('install-iris', async (_, mcVersion, installationId) => {
    const gameDir = installationId ? getInstallGameDir(installationId) : getDefaultMcDir();
    try {
      // Find Iris versions compatible with this MC version
      const versionsData = await new Promise((resolve, reject) => {
        https.get(`https://api.modrinth.com/v2/project/YL57xq9U/version?game_versions=["${mcVersion}"]&loaders=["fabric"]`, {
          headers: { 'User-Agent': 'IceyClient/1.0.0' }
        }, (res) => {
          let data = '';
          res.on('data', (chunk) => data += chunk);
          res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
          res.on('error', reject);
        }).on('error', reject);
      });

      if (!versionsData || versionsData.length === 0) {
        return { error: 'Iris is not available for ' + mcVersion };
      }

      const version = versionsData[0];
      const file = version.files.find(f => f.primary) || version.files[0];
      if (!file) return { error: 'No download file found for Iris' };

      const modsDir = path.join(gameDir, 'mods');
      fs.mkdirSync(modsDir, { recursive: true });
      const destPath = path.join(modsDir, file.filename);
      await downloadFile(file.url, destPath);
      log('info', 'Iris Shaders installed: ' + file.filename);

      // Also install Sodium (required dependency for Iris)
      try {
        const sodiumVersions = await new Promise((resolve, reject) => {
          https.get(`https://api.modrinth.com/v2/project/AANobbMI/version?game_versions=["${mcVersion}"]&loaders=["fabric"]`, {
            headers: { 'User-Agent': 'IceyClient/1.0.0' }
          }, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
            res.on('error', reject);
          }).on('error', reject);
        });

        if (sodiumVersions && sodiumVersions.length > 0) {
          const sodiumFile = sodiumVersions[0].files.find(f => f.primary) || sodiumVersions[0].files[0];
          if (sodiumFile) {
            const sodiumDest = path.join(modsDir, sodiumFile.filename);
            await downloadFile(sodiumFile.url, sodiumDest);
            log('info', 'Sodium installed (Iris dependency): ' + sodiumFile.filename);
          }
        }
      } catch (e) {
        log('warn', 'Could not install Sodium (Iris dependency): ' + e.message);
      }

      // Create shaderpacks directory
      const shaderpacksDir = path.join(gameDir, 'shaderpacks');
      fs.mkdirSync(shaderpacksDir, { recursive: true });

      return { success: true };
    } catch (e) {
      log('error', 'Iris install error: ' + e.message);
      return { error: e.message };
    }
  });

  // Ensures Iris + Sodium are present in the installation's mods/ folder.
  // Returns which ones (if any) were newly installed so the UI can toast.
  ipcMain.handle('ensure-shader-deps', async (_, installationId, mcVersion) => {
    try {
      if (!installationId) return { error: 'No installation selected' };
      const gameDir = getInstallGameDir(installationId);
      const modsDir = path.join(gameDir, 'mods');
      fs.mkdirSync(modsDir, { recursive: true });

      const existing = fs.existsSync(modsDir) ? fs.readdirSync(modsDir) : [];
      const hasIris = existing.some(f => /^iris.*\.jar$/i.test(f));
      const hasSodium = existing.some(f => /^sodium.*\.jar$/i.test(f));

      const installed = [];
      const fetchLatest = (projectId) => new Promise((resolve, reject) => {
        https.get(`https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["${mcVersion}"]&loaders=["fabric"]`, {
          headers: { 'User-Agent': 'IceyClient/1.0.0' }
        }, (res) => {
          let data = '';
          res.on('data', (c) => data += c);
          res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
          res.on('error', reject);
        }).on('error', reject);
      });

      if (!hasIris) {
        const v = await fetchLatest('YL57xq9U');
        if (v && v.length > 0) {
          const file = v[0].files.find(f => f.primary) || v[0].files[0];
          if (file) {
            await downloadFile(file.url, path.join(modsDir, file.filename));
            log('info', 'Iris auto-installed for shader: ' + file.filename);
            installed.push('Iris');
          }
        }
      }
      if (!hasSodium) {
        const v = await fetchLatest('AANobbMI');
        if (v && v.length > 0) {
          const file = v[0].files.find(f => f.primary) || v[0].files[0];
          if (file) {
            await downloadFile(file.url, path.join(modsDir, file.filename));
            log('info', 'Sodium auto-installed for shader: ' + file.filename);
            installed.push('Sodium');
          }
        }
      }

      const shaderpacksDir = path.join(gameDir, 'shaderpacks');
      fs.mkdirSync(shaderpacksDir, { recursive: true });
      return { success: true, installed };
    } catch (e) {
      log('error', 'ensure-shader-deps error: ' + e.message);
      return { error: e.message };
    }
  });

  // Shaderpacks management
  ipcMain.handle('get-installed-shaderpacks', (_, installationId) => {
    const gameDir = installationId ? getInstallGameDir(installationId) : getDefaultMcDir();
    const shaderpacksDir = path.join(gameDir, 'shaderpacks');
    const packs = [];

    if (fs.existsSync(shaderpacksDir)) {
      for (const file of fs.readdirSync(shaderpacksDir)) {
        if (file.startsWith('.')) continue;
        const filePath = path.join(shaderpacksDir, file);
        try {
          const stats = fs.statSync(filePath);
          if (stats.isFile() && (file.endsWith('.zip') || file.endsWith('.jar'))) {
            packs.push({ filename: file, name: file.replace(/\.(zip|jar)$/, '').replace(/-/g, ' '), size: stats.size });
          }
        } catch (_) {}
      }
    }
    return packs;
  });

  ipcMain.handle('delete-shaderpack', (_, installationId, filename) => {
    const gameDir = installationId ? getInstallGameDir(installationId) : getDefaultMcDir();
    const filePath = path.join(gameDir, 'shaderpacks', filename);
    try {
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
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

  ipcMain.handle('register-resourcepack', (_, installationId, filename) => {
    try {
      const gameDir = getInstallGameDir(installationId);
      const optionsPath = path.join(gameDir, 'options.txt');

      let lines = [];
      if (fs.existsSync(optionsPath)) {
        lines = fs.readFileSync(optionsPath, 'utf-8').split('\n');
      }

      const packEntry = 'file/' + filename;
      let found = false;
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].startsWith('resourcePacks:')) {
          found = true;
          try {
            const packs = JSON.parse(lines[i].slice('resourcePacks:'.length));
            if (!packs.includes(packEntry)) {
              packs.push(packEntry);
              lines[i] = 'resourcePacks:' + JSON.stringify(packs);
            }
          } catch (_) {
            lines[i] = 'resourcePacks:' + JSON.stringify(['vanilla', packEntry]);
          }
          break;
        }
      }
      if (!found) {
        lines.push('resourcePacks:' + JSON.stringify(['vanilla', packEntry]));
      }

      fs.mkdirSync(gameDir, { recursive: true });
      fs.writeFileSync(optionsPath, lines.join('\n'), 'utf-8');
      log('info', 'Registered resource pack in options.txt: ' + packEntry);
      return { success: true };
    } catch (e) {
      log('warn', 'Failed to register resource pack: ' + e.message);
      return { error: e.message };
    }
  });

  // Panoramas
  ipcMain.handle('get-panoramas', () => {
    return listPanoramaFiles().map(filename => ({
      filename,
      name: filename.replace(/\.zip$/i, '').replace(/\s+V\d+\b/i, m => ' ' + m.trim())
    }));
  });

  ipcMain.handle('get-panorama-preview', (_, filename) => {
    try {
      if (!filename) return null;
      const file = path.join(getPanoramasDir(), filename);
      if (!fs.existsSync(file)) return null;
      const zipBuf = fs.readFileSync(file);
      // Minecraft panorama packs have 6 faces; panorama_1.png is usually north-facing
      const preview = _extractFileFromZip(zipBuf, 'assets/minecraft/textures/gui/title/background/panorama_1.png')
                   || _extractFileFromZip(zipBuf, 'assets/minecraft/textures/gui/title/background/panorama_0.png');
      if (!preview) return null;
      return 'data:image/png;base64,' + preview.toString('base64');
    } catch (_) {
      return null;
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

  // Check for updates via GitHub releases API
  ipcMain.handle('check-update', async () => {
    try {
      const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, 'package.json'), 'utf-8'));
      const current = pkg.version || '1.0.0';
      const res = await new Promise((resolve, reject) => {
        const req = https.get('https://api.github.com/repos/FoxFlame27/iceyclient/releases/latest', {
          headers: { 'User-Agent': 'IceyClient-Updater' }
        }, (r) => {
          let data = '';
          r.on('data', c => data += c);
          r.on('end', () => resolve({ status: r.statusCode, body: data }));
        });
        req.on('error', reject);
        req.setTimeout(10000, () => { req.destroy(); reject(new Error('timeout')); });
      });
      if (res.status !== 200) return { error: 'HTTP ' + res.status };
      const json = JSON.parse(res.body);
      const latest = (json.tag_name || '').replace(/^v/, '');
      if (!latest) return { error: 'no release found' };
      const cmp = (a, b) => {
        const pa = a.split('.').map(Number), pb = b.split('.').map(Number);
        for (let i = 0; i < 3; i++) {
          if ((pa[i] || 0) > (pb[i] || 0)) return 1;
          if ((pa[i] || 0) < (pb[i] || 0)) return -1;
        }
        return 0;
      };
      return {
        current,
        latest,
        updateAvailable: cmp(latest, current) > 0,
        url: json.html_url,
        notes: json.body || ''
      };
    } catch (e) {
      return { error: e.message };
    }
  });

  // Get data directory
  ipcMain.handle('get-data-dir', () => DATA_DIR);

  // Get installations directory
  ipcMain.handle('get-installations-dir', () => INSTALLATIONS_DIR);

  // Install Fabric (via Fabric Meta API — no Java required)
  const installFabricLoader = async (installationId, mcVersion) => {
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
  };
  _installFabricLoader = installFabricLoader;
  ipcMain.handle('install-fabric', (_, installationId, mcVersion) => installFabricLoader(installationId, mcVersion));

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

        // Download natives if this library has them
        if (lib.downloads?.classifiers || lib.natives) {
          const nativesDir = path.join(installDir, 'versions', jsonData.id || 'unknown', 'natives');
          try {
            await downloadAndExtractNatives(lib, libDir, nativesDir);
          } catch (e) {
            log('warn', 'Native extraction failed: ' + (lib.name || '') + ' - ' + e.message);
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

  // Get real .minecraft directory (shared libraries/assets)
  ipcMain.handle('get-mc-dir', () => getDefaultMcDir());

  // Get per-installation game dir (isolated mods/config/saves)
  ipcMain.handle('get-install-game-dir', (_, installationId) => {
    const dir = getInstallGameDir(installationId);
    fs.mkdirSync(dir, { recursive: true });
    return dir;
  });

  // Microsoft Auth
  ipcMain.handle('ms-login', async () => {
    const existing = readAuthStore();
    if (existing.accounts.length >= MAX_ACCOUNTS) {
      // Allow only if we'd just be updating an existing account (same uuid after login)
      // — we only know after login, so just return the friendly error up-front.
      return { error: 'Max ' + MAX_ACCOUNTS + ' accounts saved. Remove one first.' };
    }
    try {
      return await microsoftLogin();
    } catch (e) {
      return { error: e.message };
    }
  });

  ipcMain.handle('ms-logout', () => {
    // Legacy endpoint: remove the currently-active account only
    const store = readAuthStore();
    if (!store.activeUuid) { try { if (fs.existsSync(AUTH_FILE)) fs.unlinkSync(AUTH_FILE); } catch (_) {} return { success: true }; }
    store.accounts = store.accounts.filter(a => a.uuid !== store.activeUuid);
    store.activeUuid = store.accounts[0]?.uuid || null;
    writeAuthStore(store);
    return { success: true };
  });

  ipcMain.handle('get-auth', async () => {
    const auth = readAuth();
    if (!auth) return null;
    if (auth.type === 'offline') return auth;
    // Silent refresh when needed — null means the chain is truly dead
    // and the UI should treat the account as expired (re-login required).
    return await ensureFreshAuth(auth);
  });

  // Multi-account support. The "expired" badge here reflects whether
  // the account has a viable refresh path, NOT whether the current
  // accessToken is still inside its 24h window. That way an account
  // shows as launchable as long as we can silently refresh it.
  ipcMain.handle('get-accounts', () => {
    const store = readAuthStore();
    return {
      activeUuid: store.activeUuid,
      maxAccounts: MAX_ACCOUNTS,
      accounts: store.accounts.map(a => ({
        username: a.username,
        uuid: a.uuid,
        skinUrl: a.skinUrl || null,
        type: a.type || 'microsoft',
        expired: a.type === 'offline' ? false : !canRefreshAccount(a)
      }))
    };
  });

  ipcMain.handle('add-offline-account', (_, username) => {
    try {
      const name = String(username || '').trim();
      if (!name) return { error: 'Username required' };
      if (name.length > 16) return { error: 'Username max 16 chars' };
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) return { error: 'Only letters, digits, underscore' };
      const uuid = offlineUuid(name);
      const account = { username: name, uuid, accessToken: '0', type: 'offline' };
      upsertAccount(account);
      return { success: true, account };
    } catch (e) {
      return { error: e.message };
    }
  });

  ipcMain.handle('switch-account', (_, uuid) => {
    const store = readAuthStore();
    if (!store.accounts.find(a => a.uuid === uuid)) return { error: 'Account not found' };
    store.activeUuid = uuid;
    writeAuthStore(store);
    const active = store.accounts.find(a => a.uuid === uuid);
    return { success: true, active: { username: active.username, uuid: active.uuid, skinUrl: active.skinUrl || null } };
  });

  ipcMain.handle('remove-account', (_, uuid) => {
    const store = readAuthStore();
    store.accounts = store.accounts.filter(a => a.uuid !== uuid);
    if (store.activeUuid === uuid) store.activeUuid = store.accounts[0]?.uuid || null;
    writeAuthStore(store);
    return { success: true, activeUuid: store.activeUuid };
  });

  ipcMain.handle('upload-skin', async (_, skinPath, variant) => {
    const auth = readAuth();
    if (!auth || !auth.accessToken) return { error: 'Not logged in' };
    try {
      const skinData = fs.readFileSync(skinPath);
      const boundary = '----IceyClient' + Date.now();
      const body = Buffer.concat([
        Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="variant"\r\n\r\n${variant || 'classic'}\r\n`),
        Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="skin.png"\r\nContent-Type: image/png\r\n\r\n`),
        skinData,
        Buffer.from(`\r\n--${boundary}--\r\n`)
      ]);
      const result = await new Promise((resolve, reject) => {
        const req = https.request({
          hostname: 'api.minecraftservices.com', path: '/minecraft/profile/skins', method: 'POST',
          headers: { 'Authorization': 'Bearer ' + auth.accessToken, 'Content-Type': 'multipart/form-data; boundary=' + boundary, 'Content-Length': body.length }
        }, (res) => {
          let data = '';
          res.on('data', c => data += c);
          res.on('end', () => {
            if (res.statusCode >= 200 && res.statusCode < 300) resolve({ success: true });
            else resolve({ error: 'Upload failed (HTTP ' + res.statusCode + ')' });
          });
        });
        req.on('error', e => reject(e));
        req.write(body);
        req.end();
      });
      return result;
    } catch (e) {
      return { error: e.message };
    }
  });

  ipcMain.handle('upload-skin-from-url', async (_, skinUsername, variant) => {
    const auth = readAuth();
    if (!auth || !auth.accessToken) return { error: 'Not logged in' };
    try {
      // Step 1: Get UUID from Mojang
      const profileData = await new Promise((resolve, reject) => {
        https.get(`https://api.mojang.com/users/profiles/minecraft/${skinUsername}`, (res) => {
          let data = '';
          res.on('data', c => data += c);
          res.on('end', () => {
            if (res.statusCode !== 200) { reject(new Error('Player not found')); return; }
            try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
          });
        }).on('error', reject);
      });

      // Step 2: Get session profile with skin texture URL
      const sessionData = await new Promise((resolve, reject) => {
        https.get(`https://sessionserver.mojang.com/session/minecraft/profile/${profileData.id}`, (res) => {
          let data = '';
          res.on('data', c => data += c);
          res.on('end', () => {
            if (res.statusCode !== 200) { reject(new Error('Could not fetch profile')); return; }
            try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
          });
        }).on('error', reject);
      });

      // Step 3: Decode texture URL from base64 property
      const texProp = sessionData.properties?.find(p => p.name === 'textures');
      if (!texProp) return { error: 'No skin found for this player' };
      const textures = JSON.parse(Buffer.from(texProp.value, 'base64').toString());
      const skinUrl = textures.textures?.SKIN?.url;
      if (!skinUrl) return { error: 'No skin found for this player' };

      // Step 4: Use Mojang API to set skin by URL (simpler, no file upload needed)
      const payload = JSON.stringify({ variant: variant || 'classic', url: skinUrl });
      const result = await new Promise((resolve, reject) => {
        const req = https.request({
          hostname: 'api.minecraftservices.com', path: '/minecraft/profile/skins', method: 'POST',
          headers: {
            'Authorization': 'Bearer ' + auth.accessToken,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payload)
          }
        }, (res) => {
          let data = '';
          res.on('data', c => data += c);
          res.on('end', () => {
            log('info', 'Skin change response: HTTP ' + res.statusCode + ' ' + data);
            if (res.statusCode >= 200 && res.statusCode < 300) resolve({ success: true });
            else resolve({ error: 'Skin change failed (HTTP ' + res.statusCode + '): ' + data });
          });
        });
        req.on('error', e => reject(e));
        req.write(payload);
        req.end();
      });
      return result;
    } catch (e) {
      log('error', 'Skin change error: ' + e.message);
      return { error: e.message };
    }
  });

  ipcMain.handle('get-mc-profile', async () => {
    const auth = readAuth();
    if (!auth || !auth.accessToken) return null;
    try {
      return await httpGet('https://api.minecraftservices.com/minecraft/profile', { Authorization: 'Bearer ' + auth.accessToken });
    } catch (_) { return null; }
  });

  // Info page — download a player's 64x64 raw skin PNG via Save dialog.
  // Uses Mojang's session profile endpoint to resolve the texture URL,
  // then streams the PNG to the user's chosen path.
  ipcMain.handle('download-skin-png', async (_, username) => {
    try {
      const name = String(username || '').trim();
      if (!name) return { error: 'No username' };
      // 1) Resolve UUID
      const profileResp = await httpGet('https://api.mojang.com/users/profiles/minecraft/' + encodeURIComponent(name));
      const uuid = profileResp && profileResp.id;
      if (!uuid) return { error: 'No Minecraft account found for ' + name };
      // 2) Get session profile (contains textures payload)
      const sessProfile = await httpGet('https://sessionserver.mojang.com/session/minecraft/profile/' + uuid);
      const propValue = sessProfile?.properties?.find(p => p.name === 'textures')?.value;
      if (!propValue) return { error: 'No texture data for ' + name };
      const textures = JSON.parse(Buffer.from(propValue, 'base64').toString('utf8'));
      const skinUrl = textures?.textures?.SKIN?.url;
      if (!skinUrl) return { error: 'No skin URL for ' + name };
      // 3) Save dialog
      const win = BrowserWindow.getFocusedWindow() || mainWindow;
      const sel = await dialog.showSaveDialog(win, {
        title: 'Save ' + name + " skin as 64x64 PNG",
        defaultPath: name + '.png',
        filters: [{ name: 'PNG image', extensions: ['png'] }]
      });
      if (sel.canceled || !sel.filePath) return { canceled: true };
      // 4) Download PNG
      await new Promise((resolve, reject) => {
        const proto = skinUrl.startsWith('https') ? https : http;
        proto.get(skinUrl, { headers: { 'User-Agent': 'IceyClient/1.0' } }, (res) => {
          if (res.statusCode !== 200) { reject(new Error('HTTP ' + res.statusCode)); return; }
          const out = fs.createWriteStream(sel.filePath);
          res.pipe(out);
          out.on('finish', () => out.close(resolve));
          out.on('error', reject);
        }).on('error', reject);
      });
      return { success: true, savedTo: sel.filePath };
    } catch (e) {
      log('warn', 'download-skin-png failed: ' + e.message);
      return { error: e.message };
    }
  });

  // Info page — install a user-uploaded cape PNG.
  //
  // The launcher uses per-installation game dirs (each install
  // launches with --gameDir ~/.iceyclient/installations/<id>/game),
  // NOT the global ~/.minecraft. Writing only to the global
  // .minecraft/assets/skins would put the file in a folder MC isn't
  // even reading from when launching through Icey Client.
  //
  // Strategy: write the cape to a known iceymod-conventional path
  // under EVERY installation's game dir:
  //     <gameDir>/config/iceyclient/cape.png
  // The (upcoming) iceymod mixin reads from this path and injects it
  // as the local player's cape texture client-side. Writing to all
  // installs means whichever one the user launches, the cape is
  // already there — no per-install selector UI needed.
  //
  // Also still drops a copy in the global .minecraft/assets/skins/
  // for users who launch through the vanilla launcher and want to
  // try the "rename to a vanilla cache hash" trick manually.
  ipcMain.handle('install-custom-cape', async (_, bytes, originalName) => {
    try {
      if (!bytes || !bytes.length) return { error: 'Empty file' };
      const buf = Buffer.from(bytes);

      // Sanitize filename — strip path separators, force .png ext.
      let safe = String(originalName || 'iceyclient-cape.png')
                   .replace(/[\\/]/g, '_')
                   .replace(/[^A-Za-z0-9._-]/g, '_');
      if (!/\.png$/i.test(safe)) safe = safe.replace(/\.[^.]*$/, '') + '.png';

      const installs = readInstallations();
      const written = [];

      // 1) Per-installation iceymod cape path (the one that will
      //    actually drive the in-game render via the mixin).
      for (const inst of installs) {
        try {
          const capeDir = path.join(INSTALLATIONS_DIR, inst.id, 'game', 'config', 'iceyclient');
          fs.mkdirSync(capeDir, { recursive: true });
          const dest = path.join(capeDir, 'cape.png');
          fs.writeFileSync(dest, buf);
          // Also keep the user's original-named copy alongside in case
          // they want multiple cape options.
          if (safe !== 'cape.png') {
            try { fs.writeFileSync(path.join(capeDir, safe), buf); } catch (_) {}
          }
          written.push(dest);
        } catch (e) {
          log('warn', 'cape install for installation ' + inst.id + ' failed: ' + e.message);
        }
      }

      // 2) Global .minecraft/assets/skins/ — manual-rename escape
      //    hatch for users who know the cape-hash trick.
      try {
        const skinsDir = path.join(getDefaultMcDir(), 'assets', 'skins');
        fs.mkdirSync(skinsDir, { recursive: true });
        const dest = path.join(skinsDir, safe);
        fs.writeFileSync(dest, buf);
        written.push(dest);
      } catch (e) {
        log('warn', 'cape install to global .minecraft failed: ' + e.message);
      }

      if (written.length === 0) return { error: 'No installations to write to' };

      // Fire-and-forget upload to the Icey network so other Icey
      // Client players can see this cape. Use the currently
      // signed-in account's UUID — if no auth, skip.
      try {
        const auth = readAuth();
        if (auth && auth.uuid) {
          uploadCapeToNetwork(auth.uuid, buf);
        }
      } catch (_) {}

      return {
        success: true,
        savedTo: written[0],
        installCount: installs.length,
        copies: written.length
      };
    } catch (e) {
      log('warn', 'install-custom-cape failed: ' + e.message);
      return { error: e.message };
    }
  });

  // Info page — open the per-installation cape folder so the user
  // can see / rename their cape PNG. Prefers the active installation;
  // falls back to the first one.
  ipcMain.handle('open-cape-folder', () => {
    try {
      const installs = readInstallations();
      if (!installs.length) return { error: 'No installations' };
      const target = installs.find(i => i.selected) || installs[0];
      const capeDir = path.join(INSTALLATIONS_DIR, target.id, 'game', 'config', 'iceyclient');
      try { fs.mkdirSync(capeDir, { recursive: true }); } catch (_) {}
      shell.openPath(capeDir);
      return { success: true, path: capeDir };
    } catch (e) {
      return { error: e.message };
    }
  });

  // Info page — proxy mcsrvstat.us so renderer can query any IP for
  // live player count + icon without CORS / fetch trouble. Returns
  // the parsed JSON, an error, or null on network failure.
  ipcMain.handle('query-server-status', async (_, address) => {
    const ip = String(address || '').trim();
    if (!ip) return { error: 'Empty IP' };
    try {
      const data = await new Promise((resolve, reject) => {
        https.get('https://api.mcsrvstat.us/3/' + encodeURIComponent(ip), {
          headers: { 'User-Agent': 'IceyClient/1.0' }
        }, (res) => {
          let body = '';
          res.on('data', c => body += c);
          res.on('end', () => { try { resolve(JSON.parse(body)); } catch (e) { reject(e); } });
        }).on('error', reject);
      });
      return data;
    } catch (e) {
      return { error: e.message };
    }
  });

  // Download libraries into real .minecraft/libraries from version JSON URL
  ipcMain.handle('download-mc-libraries', async (_, versionJsonUrl) => {
    const mcDir = getDefaultMcDir();
    const libDir = path.join(mcDir, 'libraries');

    try {
      const jsonData = await new Promise((resolve, reject) => {
        const proto = versionJsonUrl.startsWith('https') ? https : http;
        proto.get(versionJsonUrl, { headers: { 'User-Agent': 'IceyClient/1.0.0' } }, (res) => {
          let data = '';
          res.on('data', (chunk) => data += chunk);
          res.on('end', () => { try { resolve(JSON.parse(data)); } catch (e) { reject(e); } });
          res.on('error', reject);
        }).on('error', reject);
      });

      if (!jsonData.libraries) return { error: 'No libraries in version JSON' };

      const total = jsonData.libraries.length;
      let completed = 0;

      for (const lib of jsonData.libraries) {
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
              if (rule.os?.name === 'windows' && process.platform === 'win32') allowed = false;
              if (rule.os?.name === 'osx' && process.platform === 'darwin') allowed = false;
              if (rule.os?.name === 'linux' && process.platform === 'linux') allowed = false;
            }
          }
          if (!allowed) { completed++; continue; }
        }

        const artifact = lib.downloads?.artifact;
        if (artifact?.url && artifact?.path) {
          const destPath = path.join(libDir, artifact.path);
          if (!fs.existsSync(destPath)) {
            try { await downloadFile(artifact.url, destPath); } catch (e) {
              log('warn', 'Lib download failed: ' + (lib.name || '') + ' - ' + e.message);
            }
          }
        }

        completed++;
        if (mainWindow) {
          mainWindow.webContents.send('mc-event', { type: 'lib-progress', completed, total });
        }
      }

      return { success: true, total, completed };
    } catch (e) {
      return { error: e.message };
    }
  });
});

app.on('before-quit', () => {
  app.isQuitting = true;
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (mainWindow) {
    mainWindow.show();
  }
});
