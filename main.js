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

// ── Minecraft launch (direct Java, uses existing .minecraft) ─────
function launchMinecraft(installationId) {
  return new Promise(async (resolve, reject) => {
    const installations = readInstallations();
    const installation = installations.find(i => i.id === installationId);
    if (!installation) return reject(new Error('Installation not found'));

    const settings = readSettings();
    const javaPath = settings.javaPath || autoDetectJava();
    if (!javaPath) return reject(new Error('JAVA_NOT_FOUND'));

    // Use the real .minecraft folder (already has libraries from official launcher)
    const mcDir = getDefaultMcDir();
    const version = installation.version;
    const libDir = path.join(mcDir, 'libraries');
    const assetsDir = path.join(mcDir, 'assets');

    // Determine which version JSON to read (fabric or vanilla)
    let versionId = version;
    let mainClass = 'net.minecraft.client.main.Main';
    let extraJvmArgs = [];
    let fabricLibs = [];

    if (installation.platform === 'fabric') {
      // Find fabric version directory
      const versionsDir = path.join(mcDir, 'versions');
      if (fs.existsSync(versionsDir)) {
        const fabricDirs = fs.readdirSync(versionsDir).filter(d => d.startsWith('fabric-loader') && d.includes(version));
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
                for (const lib of fabricJson.libraries) {
                  if (lib.name) {
                    const parts = lib.name.split(':');
                    if (parts.length >= 3) {
                      const gp = parts[0].replace(/\./g, path.sep);
                      const jarPath = path.join(libDir, gp, parts[1], parts[2], `${parts[1]}-${parts[2]}.jar`);
                      if (fs.existsSync(jarPath)) fabricLibs.push(jarPath);
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

    // Build classpath from version JSON (exact versions only — no conflicts)
    const sep = process.platform === 'win32' ? ';' : ':';
    const cpParts = [];

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

    // Add fabric libraries, replacing vanilla duplicates (e.g. asm-9.6 vs asm-9.9)
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

    // Build arguments
    const ram = settings.allocatedRam || 2048;
    const username = settings.username || 'Player';
    const uuid = settings.uuid || crypto.randomUUID();

    const args = [];
    // JVM args
    args.push(`-Xmx${ram}M`, '-Xms512M');
    args.push('-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled');
    // Natives: try version-specific folder, then shared natives folder
    const nativesDir = path.join(mcDir, 'versions', version, 'natives');
    const nativesDir2 = path.join(mcDir, 'versions', version, version + '-natives');
    const actualNatives = fs.existsSync(nativesDir) ? nativesDir : fs.existsSync(nativesDir2) ? nativesDir2 : nativesDir;
    args.push('-Djava.library.path=' + actualNatives);
    if (extraJvmArgs.length > 0) args.push(...extraJvmArgs);
    if (settings.jvmArgs) args.push(...settings.jvmArgs.split(/\s+/).filter(Boolean));
    args.push('-cp', classpath);
    args.push(mainClass);
    // Use Microsoft auth if available
    const auth = readAuth();
    const launchUsername = (auth && auth.username) ? auth.username : username;
    const launchUuid = (auth && auth.uuid) ? auth.uuid : uuid;
    const launchToken = (auth && auth.accessToken && auth.expiresAt > Date.now()) ? auth.accessToken : '0';
    const launchUserType = launchToken !== '0' ? 'msa' : 'legacy';

    // Game args
    args.push('--username', launchUsername);
    args.push('--version', versionId);
    args.push('--gameDir', mcDir);
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
      mcProcess = spawn(javaPath, args, {
        cwd: mcDir,
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
          if (text.includes('Setting user:') || text.includes('LWJGL') || text.includes('OpenAL')) {
            if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-started' });
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
        log('error', 'MC process error: ' + err.message);
        mcProcess = null;
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-error', message: err.message });
      });

      mcProcess.on('close', (code) => {
        log('info', `MC exited with code ${code}`);
        mcProcess = null;
        if (mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-stopped', code });
      });

      // Signal started quickly
      setTimeout(() => {
        if (mcProcess && mainWindow) mainWindow.webContents.send('mc-event', { type: 'mc-started' });
      }, 1500);

      if (settings.closeLauncherOnStart) {
        setTimeout(() => { if (mainWindow) mainWindow.hide(); }, 2000);
      }

      resolve({ success: true });
    } catch (e) {
      log('error', 'Failed to spawn MC: ' + e.message);
      reject(e);
    }
  });
}

// ── ZIP file extractor using central directory (handles all JARs) ──────
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
const MS_CLIENT_ID = '96f76b51-c7a9-4d29-91bd-bf2148bb4a30';
const MS_REDIRECT = 'https://login.microsoftonline.com/common/oauth2/nativeclient';
const AUTH_FILE = path.join(DATA_DIR, 'auth.json');

function readAuth() {
  try {
    if (fs.existsSync(AUTH_FILE)) return JSON.parse(fs.readFileSync(AUTH_FILE, 'utf-8'));
  } catch (_) {}
  return null;
}
function writeAuth(data) { writeJsonAtomic(AUTH_FILE, data); }

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
    const authUrl = `https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=${MS_CLIENT_ID}&response_type=code&redirect_uri=${encodeURIComponent(MS_REDIRECT)}&scope=XboxLive.signin%20offline_access&prompt=select_account`;

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

async function exchangeMicrosoftTokens(code) {
  // Step 1: Code -> MS Token
  const msToken = await httpPost('https://login.microsoftonline.com/consumers/oauth2/v2.0/token',
    `client_id=${MS_CLIENT_ID}&code=${code}&grant_type=authorization_code&redirect_uri=${encodeURIComponent(MS_REDIRECT)}&scope=XboxLive.signin%20offline_access`);
  if (!msToken.access_token) { log('error', 'MS token failed: ' + JSON.stringify(msToken)); throw new Error('MS token failed'); }

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
  log('info', 'MC auth response: ' + JSON.stringify(mcRes));
  if (!mcRes.access_token) { log('error', 'Minecraft auth failed: ' + JSON.stringify(mcRes)); throw new Error('MC auth failed: ' + (mcRes.error || mcRes.errorMessage || JSON.stringify(mcRes))); }

  // Step 5: Get profile
  const profile = await httpGet('https://api.minecraftservices.com/minecraft/profile', { Authorization: 'Bearer ' + mcRes.access_token });

  const authData = {
    accessToken: mcRes.access_token,
    username: profile.name || 'Player',
    uuid: profile.id || crypto.randomUUID(),
    skinUrl: profile.skins?.[0]?.url || null,
    refreshToken: msToken.refresh_token || null,
    expiresAt: Date.now() + (mcRes.expires_in || 86400) * 1000
  };
  writeAuth(authData);
  return authData;
}

// ── App ready ──────────────────────────────────────────
app.whenReady().then(() => {
  ensureDirs();

  // Reset log file on start
  try { fs.writeFileSync(LOG_FILE, `[${new Date().toISOString()}] [INFO] Icey Client started\n`); } catch (_) { /* */ }

  // ── Splash Screen ──
  const splash = new BrowserWindow({
    width: 400, height: 300, frame: false, transparent: true, alwaysOnTop: true, center: true,
    icon: path.join(__dirname, 'src', 'assets', 'icon.png'),
    webPreferences: { nodeIntegration: false, contextIsolation: true }
  });
  splash.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(`
    <html><body style="margin:0;display:flex;align-items:center;justify-content:center;height:100vh;background:rgba(8,12,24,0.95);border-radius:20px;overflow:hidden;font-family:system-ui;">
      <div style="text-align:center">
        <img src="file://${path.join(__dirname, 'src', 'assets', 'splash-logo.png').replace(/\\/g, '/')}" width="100" height="100" style="image-rendering:pixelated;margin-bottom:16px;">
        <div style="color:#bae6fd;font-size:22px;font-weight:700;letter-spacing:0.1em;">ICEY CLIENT</div>
        <div style="color:#475569;font-size:12px;margin-top:8px;">Loading...</div>
      </div>
    </body></html>
  `));

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
    splash.destroy();
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

  ipcMain.handle('get-installed-mods', (_, installationId) => {
    const mcDir = getDefaultMcDir();
    const modsDir = path.join(mcDir, 'mods');
    const rpDir = path.join(mcDir, 'resourcepacks');

    const mods = [];
    const resourcePacks = [];

    if (fs.existsSync(modsDir)) {
      for (const file of fs.readdirSync(modsDir)) {
        if (!file.endsWith('.jar')) continue;
        const filePath = path.join(modsDir, file);
        try {
          const stats = fs.statSync(filePath);
          let modName = file.replace('.jar', '').replace(/-/g, ' ');
          let iconBase64 = null;

          // Try to extract mod info from fabric.mod.json inside the jar
          try {
            const zlib2 = require('zlib');
            const jarBuf = fs.readFileSync(filePath);
            // Simple ZIP parsing: find fabric.mod.json
            const modJson = _extractFileFromZip(jarBuf, 'fabric.mod.json');
            if (modJson) {
              const meta = JSON.parse(modJson.toString('utf-8'));
              if (meta.name) modName = meta.name;
              // Extract icon
              if (meta.icon) {
                const iconData = _extractFileFromZip(jarBuf, meta.icon);
                if (iconData) {
                  iconBase64 = 'data:image/png;base64,' + iconData.toString('base64');
                }
              }
            }
          } catch (_) { /* not a fabric mod or parse error */ }

          mods.push({ filename: file, name: modName, size: stats.size, type: 'mod', icon: iconBase64 });
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
    const mcDir = getDefaultMcDir();
    const modsPath = path.join(mcDir, 'mods', filename);
    const rpPath = path.join(mcDir, 'resourcepacks', filename);

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

  // Get real .minecraft directory
  ipcMain.handle('get-mc-dir', () => getDefaultMcDir());

  // Microsoft Auth
  ipcMain.handle('ms-login', async () => {
    try {
      return await microsoftLogin();
    } catch (e) {
      return { error: e.message };
    }
  });

  ipcMain.handle('ms-logout', () => {
    try { if (fs.existsSync(AUTH_FILE)) fs.unlinkSync(AUTH_FILE); } catch (_) {}
    return { success: true };
  });

  ipcMain.handle('get-auth', () => {
    const auth = readAuth();
    if (auth && auth.expiresAt && Date.now() > auth.expiresAt) {
      return null; // Expired
    }
    return auth;
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

  ipcMain.handle('get-mc-profile', async () => {
    const auth = readAuth();
    if (!auth || !auth.accessToken) return null;
    try {
      return await httpGet('https://api.minecraftservices.com/minecraft/profile', { Authorization: 'Bearer ' + auth.accessToken });
    } catch (_) { return null; }
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

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    // Re-create window on macOS dock click
  }
});
