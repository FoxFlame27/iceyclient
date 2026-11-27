// Bringing things over from other Minecraft clients: finds their game
// folders on this computer and copies mods, the server list and settings
// into Icey installations. Worlds are left where they are.
//
// This file only knows where other clients keep things. Everything that
// touches Icey's own data (installations, mod copy, servers.dat, accounts)
// comes in through `deps`, so it can be run against test folders.
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

module.exports = function createClientImporter(deps) {
  const log = deps.log || (() => {});
  const readJson = (p) => { try { return JSON.parse(fs.readFileSync(p, 'utf-8')); } catch (_) { return null; } };
  const isDir = (p) => { try { return fs.statSync(p).isDirectory(); } catch (_) { return false; } };
  const isFile = (p) => { try { return fs.statSync(p).isFile(); } catch (_) { return false; } };
  const listDir = (p) => { try { return fs.readdirSync(p); } catch (_) { return []; } };
  const idOf = (p) => crypto.createHash('sha1').update(path.resolve(p)).digest('hex').slice(0, 12);
  const isRelease = (v) => /^\d+\.\d+(\.\d+)?$/.test(String(v || ''));

  function countMods(gameDir) {
    return listDir(path.join(gameDir, 'mods')).filter(f => /\.jar(\.disabled)?$/i.test(f)).length;
  }

  // "fabric-loader-0.16.9-1.21.4", "1.20.1-forge-47.2.0", "quilt-loader-…",
  // "neoforge-21.1.80", "1.21.4" → what the game was started as.
  function parseVersionId(id) {
    id = String(id || '');
    let m = /^fabric-loader-[^-]+-(.+)$/.exec(id);
    if (m) return { loader: 'fabric', mcVersion: m[1] };
    m = /^quilt-loader-[^-]+-(.+)$/.exec(id);
    if (m) return { loader: 'quilt', mcVersion: m[1] };
    if (/neoforge/i.test(id)) return { loader: 'neoforge', mcVersion: null };
    m = /^(\d+\.\d+(?:\.\d+)?)-forge/i.exec(id);
    if (m) return { loader: 'forge', mcVersion: m[1] };
    if (/forge/i.test(id)) return { loader: 'forge', mcVersion: null };
    if (isRelease(id)) return { loader: 'vanilla', mcVersion: id };
    return { loader: null, mcVersion: null };
  }

  // The game writes what it is into the first lines of its log.
  function guessFromLog(gameDir) {
    let text = '';
    try {
      const fd = fs.openSync(path.join(gameDir, 'logs', 'latest.log'), 'r');
      const buf = Buffer.alloc(120000);
      const n = fs.readSync(fd, buf, 0, buf.length, 0);
      fs.closeSync(fd);
      text = buf.toString('utf8', 0, n);
    } catch (_) { return { loader: null, mcVersion: null }; }
    let m = /Loading Minecraft (\S+) with Fabric Loader/.exec(text);
    if (m) return { loader: 'fabric', mcVersion: m[1] };
    m = /Loading Minecraft (\S+) with Quilt Loader/.exec(text);
    if (m) return { loader: 'quilt', mcVersion: m[1] };
    if (/neoforge/i.test(text)) return { loader: 'neoforge', mcVersion: null };
    if (/ModLauncher|fml\.|MinecraftForge/.test(text)) return { loader: 'forge', mcVersion: null };
    return { loader: null, mcVersion: null };
  }

  function makeSource(client, name, gameDir, info) {
    const mods = countMods(gameDir);
    let loader = info && info.loader, mcVersion = info && info.mcVersion;
    if (mods && info && info.trustLog) {
      const g = guessFromLog(gameDir);
      if (g.loader === 'fabric' && g.mcVersion) { loader = g.loader; mcVersion = g.mcVersion; }
    }
    if (mods && (!loader || !mcVersion)) {
      const g = guessFromLog(gameDir);
      loader = loader || g.loader;
      if (!mcVersion && (!loader || loader === g.loader)) mcVersion = g.mcVersion;
    }
    let servers = 0;
    try { servers = deps.readServers(path.join(gameDir, 'servers.dat')).length; } catch (_) {}
    if (!mods && !servers) return null;
    // A snapshot, pre-release or release candidate is never imported: its
    // mods have no stable builds and it would become an installation that
    // breaks with the next snapshot. A folder that was last run as one
    // only offers its server list.
    if (mcVersion && !isRelease(mcVersion)) {
      if (!servers) return null;
      return {
        id: idOf(gameDir), client, name: String(name || client), path: gameDir,
        loader: null, mcVersion: null, mods: 0, servers, modsUsable: false, modsNote: null,
        lastUsed: (info && info.lastUsed) || 0,
      };
    }
    let modsNote = null;
    if (mods) {
      if (loader && loader !== 'fabric' && loader !== 'vanilla') modsNote = 'loader';
      else if (loader !== 'fabric' || !isRelease(mcVersion)) modsNote = 'version';
    }
    return {
      id: idOf(gameDir), client, name: String(name || client), path: gameDir,
      loader: loader || null, mcVersion: mcVersion || null,
      mods, servers, modsUsable: mods > 0 && !modsNote, modsNote,
      lastUsed: (info && info.lastUsed) || 0,
    };
  }

  // ── Where each client keeps its game folders ──
  function dataBases() {
    const home = os.homedir();
    if (process.platform === 'win32') return [process.env.APPDATA || path.join(home, 'AppData', 'Roaming')];
    if (process.platform === 'darwin') return [path.join(home, 'Library', 'Application Support')];
    return [process.env.XDG_DATA_HOME || path.join(home, '.local', 'share')];
  }

  // The official launcher: one game folder per profile (most share the
  // default one). Lunar, Badlion and Feather play out of this folder too.
  function scanOfficial(out) {
    const mcDir = deps.getDefaultMcDir();
    if (!isDir(mcDir)) return;
    const profiles = readJson(path.join(mcDir, 'launcher_profiles.json'));
    const byDir = new Map();
    for (const p of Object.values((profiles && profiles.profiles) || {})) {
      if (!p || typeof p !== 'object') continue;
      const dir = p.gameDir ? path.resolve(String(p.gameDir)) : mcDir;
      if (!isDir(dir)) continue;
      const info = parseVersionId(p.lastVersionId);
      const used = Date.parse(p.lastUsed || '') || 0;
      const cand = { name: p.name || '', info, used };
      const have = byDir.get(dir);
      // The profile that says most about the mods folder: Fabric first, then the newest.
      const better = !have || (info.loader === 'fabric') > (have.info.loader === 'fabric')
        || ((info.loader === 'fabric') === (have.info.loader === 'fabric') && used > have.used);
      if (better) byDir.set(dir, cand);
    }
    if (!byDir.has(mcDir)) byDir.set(mcDir, { name: '', info: {}, used: 0 });
    for (const [dir, c] of byDir) {
      const name = dir === mcDir ? 'Minecraft Launcher' : (c.name || path.basename(dir));
      // Several profiles (also the ones other launchers add) share this
      // folder, so what the game last ran as says more than any of them.
      const s = makeSource('Minecraft Launcher', name, dir, { ...c.info, lastUsed: c.used, trustLog: true });
      if (s) out.push(s);
    }
  }

  // Prism Launcher, PolyMC, MultiMC: instances/<name>/ with mmc-pack.json.
  function scanMmcFamily(out) {
    const roots = [];
    for (const base of dataBases()) {
      roots.push(['Prism Launcher', path.join(base, 'PrismLauncher'), 'prismlauncher.cfg']);
      roots.push(['PolyMC', path.join(base, 'PolyMC'), 'polymc.cfg']);
      roots.push(['MultiMC', path.join(base, 'multimc'), 'multimc.cfg']);
    }
    const home = os.homedir();
    if (process.platform === 'linux') {
      roots.push(['Prism Launcher', path.join(home, '.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher'), 'prismlauncher.cfg']);
      roots.push(['PolyMC', path.join(home, '.var/app/org.polymc.PolyMC/data/PolyMC'), 'polymc.cfg']);
    }
    if (process.platform === 'darwin') roots.push(['MultiMC', '/Applications/MultiMC.app/Data', 'multimc.cfg']);
    for (const [client, root, cfgName] of roots) {
      if (!isDir(root)) continue;
      let instDir = path.join(root, 'instances');
      try {
        const m = /^InstanceDir=(.+)$/m.exec(fs.readFileSync(path.join(root, cfgName), 'utf-8'));
        if (m && m[1].trim()) instDir = path.resolve(root, m[1].trim());
      } catch (_) {}
      for (const entry of listDir(instDir)) {
        const dir = path.join(instDir, entry);
        if (/^[._]/.test(entry) || !isDir(dir)) continue;
        const game = isDir(path.join(dir, '.minecraft')) ? path.join(dir, '.minecraft') : path.join(dir, 'minecraft');
        if (!isDir(game)) continue;
        let name = entry;
        try {
          const m = /^name=(.+)$/m.exec(fs.readFileSync(path.join(dir, 'instance.cfg'), 'utf-8'));
          if (m && m[1].trim()) name = m[1].trim();
        } catch (_) {}
        const info = { loader: 'vanilla', mcVersion: null };
        const pack = readJson(path.join(dir, 'mmc-pack.json'));
        for (const c of ((pack && pack.components) || [])) {
          if (c.uid === 'net.minecraft') info.mcVersion = c.version || null;
          else if (c.uid === 'net.fabricmc.fabric-loader') info.loader = 'fabric';
          else if (c.uid === 'org.quiltmc.quilt-loader') info.loader = 'quilt';
          else if (c.uid === 'net.minecraftforge') info.loader = 'forge';
          else if (c.uid === 'net.neoforged') info.loader = 'neoforge';
        }
        if (!pack) info.loader = null;
        const s = makeSource(client, name, game, info);
        if (s) out.push(s);
      }
    }
  }

  // Modrinth App: profiles/<name>/. Older versions kept a profile.json
  // next to the game files; newer ones keep it in a database, so the game's
  // own log has to say what version it is.
  function scanModrinth(out) {
    for (const base of dataBases()) {
      for (const folder of ['ModrinthApp', 'com.modrinth.theseus']) {
        const profilesDir = path.join(base, folder, 'profiles');
        for (const entry of listDir(profilesDir)) {
          const dir = path.join(profilesDir, entry);
          if (/^\./.test(entry) || !isDir(dir)) continue;
          const info = {};
          const meta = readJson(path.join(dir, 'profile.json'));
          const md = meta && (meta.metadata || meta);
          if (md && md.game_version) { info.mcVersion = md.game_version; info.loader = md.loader || null; }
          const s = makeSource('Modrinth App', (md && md.name) || entry, dir, info);
          if (s) out.push(s);
        }
      }
    }
  }

  // CurseForge: Instances/<name>/ with minecraftinstance.json.
  function scanCurseForge(out) {
    const home = os.homedir();
    const dirs = [
      path.join(home, 'curseforge', 'minecraft', 'Instances'),
      path.join(home, 'Documents', 'curseforge', 'minecraft', 'Instances'),
    ];
    for (const instDir of dirs) {
      for (const entry of listDir(instDir)) {
        const dir = path.join(instDir, entry);
        if (/^\./.test(entry) || !isDir(dir)) continue;
        const info = {};
        const meta = readJson(path.join(dir, 'minecraftinstance.json'));
        if (meta) {
          info.mcVersion = meta.gameVersion || null;
          const ml = String((meta.baseModLoader && meta.baseModLoader.name) || '').toLowerCase();
          info.loader = !ml ? 'vanilla' : ml.startsWith('fabric') ? 'fabric' : ml.startsWith('quilt') ? 'quilt'
            : ml.startsWith('neoforge') ? 'neoforge' : ml.startsWith('forge') ? 'forge' : null;
        }
        const s = makeSource('CurseForge', (meta && meta.name) || entry, dir, info);
        if (s) out.push(s);
      }
    }
  }

  // A folder the player points at: the game folder itself, or an instance
  // folder with the game folder inside it.
  function scanFolder(dir) {
    if (!dir || !isDir(dir)) return null;
    let game = dir;
    if (!isDir(path.join(dir, 'mods')) && !isFile(path.join(dir, 'servers.dat'))) {
      const sub = ['.minecraft', 'minecraft'].find(n => isDir(path.join(dir, n)));
      if (sub) game = path.join(dir, sub);
    }
    return makeSource('Folder', path.basename(dir), game, {});
  }

  function scan() {
    const out = [];
    for (const fn of [scanOfficial, scanMmcFamily, scanModrinth, scanCurseForge]) {
      try { fn(out); } catch (e) { log('warn', '[IMPORT] ' + fn.name + ': ' + e.message); }
    }
    const seen = new Set();
    const sources = out.filter(s => !seen.has(s.id) && seen.add(s.id));
    let accounts = [];
    try { accounts = deps.listAccounts(); } catch (_) {}
    return { sources, accounts };
  }

  // Settings, key binds, mod settings, resource packs and shaders. Never
  // replaces a file that is already there.
  async function copyExtras(srcGame, dstGame) {
    let copied = 0;
    const one = async (rel) => {
      const from = path.join(srcGame, rel), to = path.join(dstGame, rel);
      if (!fs.existsSync(from)) return;
      try {
        await fs.promises.cp(from, to, { recursive: true, force: false, errorOnExist: false });
        copied++;
      } catch (e) { log('warn', '[IMPORT] copy ' + rel + ': ' + e.message); }
    };
    for (const rel of ['options.txt', 'config', 'resourcepacks', 'shaderpacks']) await one(rel);
    return copied;
  }

  // sources: what scan() or scanFolder() returned (looked up again here by
  // path, so nothing the page sends is trusted beyond "which folders").
  async function run(wanted, opts, send) {
    send = send || (() => {});
    const results = [];
    let selected = false;
    for (let i = 0; i < wanted.length; i++) {
      const s = wanted[i];
      const res = { id: s.id, name: s.name, client: s.client, installation: null, mods: null, servers: 0, error: null };
      send({ index: i, total: wanted.length, name: s.name, step: 'start' });
      let target = null;
      if (s.modsUsable) {
        try {
          send({ index: i, total: wanted.length, name: s.name, step: 'game' });
          const inst = await deps.ensureInstallation(s, !selected);
          selected = true;
          res.installation = inst.name;
          target = deps.getInstallGameDir(inst.id);
          const r = await deps.copyMods({ name: s.name }, inst, s.path, target,
            (p) => send({ index: i, total: wanted.length, name: s.name, step: 'mods', done: p.done, of: p.total, current: p.current }));
          res.mods = {
            copied: (r.copied || []).length, skipped: (r.skipped || []).length,
            failed: (r.failed || []).map(f => ({ name: f.name, reason: f.reason })),
          };
          send({ index: i, total: wanted.length, name: s.name, step: 'settings' });
          await copyExtras(s.path, target);
        } catch (e) {
          res.error = (e && e.message) || String(e);
          log('warn', '[IMPORT] ' + s.name + ': ' + res.error);
        }
      }
      // Servers of a source that got no installation of its own go to the
      // one that is selected, so they still show up on Home.
      try {
        const dst = target || deps.defaultGameDir();
        if (dst && s.servers) res.servers = deps.mergeServers(path.join(s.path, 'servers.dat'), path.join(dst, 'servers.dat'));
      } catch (e) { log('warn', '[IMPORT] servers of ' + s.name + ': ' + e.message); }
      results.push(res);
    }
    let accounts = null;
    if (opts && opts.accounts) {
      try { accounts = deps.importAccounts(); } catch (e) { accounts = { error: e.message }; }
    }
    send({ step: 'done' });
    return { results, accounts };
  }

  return { scan, scanFolder, run, parseVersionId };
};
