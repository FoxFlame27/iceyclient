// The launcher's background as a turning panorama (Settings → Appearance →
// Background, or the Theme step of Setup). The other choice, and the
// default, is the snow picture, which this file leaves alone.
//
// The panoramas are the ones the title screen in game is given: the pack
// picked in Settings, or all of Icey's packs when that is on shuffle
// (main.js: get-bg-panoramas).
// They are shown the way the game shows them: the six pictures form a cube
// around the viewer, who slowly turns. On shuffle: a different one at every
// start, and a new one fades in every few minutes.
//
// One WebGL canvas, one draw call per frame, 30 frames a second. Nothing is
// drawn while the window is hidden, or while a game runs and the launcher
// is not the window in use (the "game-running" class set by app.js), and
// with "reduce motion" switched on in the system the view stands still.
//
// styles/pano-bg.css places it: behind the blurred backdrop's veil in
// Classic and Liquid, sharp inside the frame in Frost.
const PanoBg = (() => {
  const SWITCH_MS = 4 * 60 * 1000;     // a new panorama this often
  const FADE_MS = 2600;                // one fades into the next
  const DEG_PER_S = 1.5;               // one full turn in four minutes
  const PITCH = 8 * Math.PI / 180;     // looking down a little, as the game does
  const TAN_HALF_FOV = Math.tan(40 * Math.PI / 180);
  const FACE = 1024;
  const root = document.documentElement;

  let wrap = null, canvas = null, shade = null;
  let gl = null, prog = null, uni = null, buf = null;
  let texA = null, texB = null;        // showing, and the one fading in
  let fadeStart = 0;
  let yaw = Math.random() * 360;
  let active = false;                  // the setting asks for it and it can be shown
  let ready = false;                   // a panorama is on screen
  let timer = 0, raf = 0, switchTimer = 0, lastTick = 0, lastDraw = 0, dirty = true;
  let list = null, queue = [], shownId = '';
  let generation = 0;                  // apply() calls; a slow load from an earlier one is dropped
  let lostContext = false;

  const reduced = () => { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (_) { return false; } };
  const paused = () => document.hidden || root.classList.contains('game-running');
  const frost = () => root.getAttribute('data-layout') === 'frost';

  function wanted() {
    const s = (typeof SettingsManager !== 'undefined' && SettingsManager.getAll()) || {};
    return s.launcherBackground === 'panorama' && !s.skiflameMode;
  }

  // ── The element ──
  function build() {
    if (wrap) return;
    wrap = document.createElement('div');
    wrap.id = 'pano-bg';
    wrap.setAttribute('aria-hidden', 'true');
    canvas = document.createElement('canvas');
    shade = document.createElement('div');
    shade.className = 'pano-bg-shade';
    wrap.appendChild(canvas);
    wrap.appendChild(shade);
    canvas.addEventListener('webglcontextlost', (e) => { e.preventDefault(); lostContext = true; texA = texB = null; prog = null; });
    canvas.addEventListener('webglcontextrestored', () => { lostContext = false; gl = null; if (active) { ready = false; showNext(true); } });
  }

  // In Frost the picture belongs inside the page frame, in the other two
  // behind the whole window.
  function place() {
    if (!wrap) return;
    const parent = frost() ? document.getElementById('page-container') : document.body;
    if (parent && wrap.parentElement !== parent) parent.insertBefore(wrap, parent.firstChild);
    dirty = true;
  }

  // ── WebGL ──
  function initGl() {
    if (gl && prog) return true;
    const opts = { alpha: false, antialias: false, depth: false, stencil: false, powerPreference: 'low-power', preserveDrawingBuffer: false };
    gl = canvas.getContext('webgl2', opts) || canvas.getContext('webgl', opts);
    if (!gl) return false;
    const compile = (type, src) => {
      const s = gl.createShader(type);
      gl.shaderSource(s, src);
      gl.compileShader(s);
      return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null;
    };
    const vs = compile(gl.VERTEX_SHADER, 'attribute vec2 p; varying vec2 v; void main(){ v = p; gl_Position = vec4(p, 0.0, 1.0); }');
    // A ray per pixel: tilted down by the pitch, turned by the yaw, looked
    // up in the cube. Two cubes while one fades into the other.
    const fs = compile(gl.FRAGMENT_SHADER, [
      'precision mediump float;',
      'uniform samplerCube a; uniform samplerCube b; uniform float m; uniform vec2 k; uniform vec4 r; varying vec2 v;',
      'void main(){',
      '  vec3 d = vec3(v.x * k.x, v.y * k.y, 1.0);',
      '  d = vec3(d.x, d.y * r.w - d.z * r.z, d.y * r.z + d.z * r.w);',
      '  d = vec3(d.x * r.y + d.z * r.x, d.y, d.z * r.y - d.x * r.x);',
      '  vec3 c = textureCube(a, d).rgb;',
      '  if (m > 0.0) c = mix(c, textureCube(b, d).rgb, m);',
      '  gl_FragColor = vec4(c, 1.0);',
      '}'].join('\n'));
    if (!vs || !fs) { gl = null; return false; }
    prog = gl.createProgram();
    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.bindAttribLocation(prog, 0, 'p');
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) { gl = null; prog = null; return false; }
    gl.useProgram(prog);
    uni = { a: gl.getUniformLocation(prog, 'a'), b: gl.getUniformLocation(prog, 'b'), m: gl.getUniformLocation(prog, 'm'),
      k: gl.getUniformLocation(prog, 'k'), r: gl.getUniformLocation(prog, 'r') };
    gl.uniform1i(uni.a, 0);
    gl.uniform1i(uni.b, 1);
    buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);   // one triangle over the whole view
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    return true;
  }

  // The game's order is front, right, back, left, up, down. Seen from the
  // inside of a GL cube that is +Z, +X, -Z, -X, +Y, -Y.
  function upload(images) {
    const targets = [gl.TEXTURE_CUBE_MAP_POSITIVE_Z, gl.TEXTURE_CUBE_MAP_POSITIVE_X, gl.TEXTURE_CUBE_MAP_NEGATIVE_Z,
      gl.TEXTURE_CUBE_MAP_NEGATIVE_X, gl.TEXTURE_CUBE_MAP_POSITIVE_Y, gl.TEXTURE_CUBE_MAP_NEGATIVE_Y];
    const tex = gl.createTexture();
    gl.activeTexture(gl.TEXTURE2);                 // a spare unit: the two in use stay bound
    gl.bindTexture(gl.TEXTURE_CUBE_MAP, tex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    let scratch = null;
    images.forEach((img, i) => {
      let source = img;
      if (img.naturalWidth !== FACE || img.naturalHeight !== FACE) {       // every face the same size, a power of two
        scratch = scratch || document.createElement('canvas');
        scratch.width = scratch.height = FACE;
        scratch.getContext('2d').drawImage(img, 0, 0, FACE, FACE);
        source = scratch;
      }
      gl.texImage2D(targets[i], 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, source);
    });
    gl.generateMipmap(gl.TEXTURE_CUBE_MAP);
    gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_CUBE_MAP, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return tex;
  }

  function resize() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return false;
    // Sharp in Frost; behind the blur of the other two half the pixels do.
    const scale = frost() ? Math.min(window.devicePixelRatio || 1, 1.5) : 0.5;
    const pw = Math.max(2, Math.round(w * scale)), ph = Math.max(2, Math.round(h * scale));
    if (canvas.width !== pw || canvas.height !== ph) { canvas.width = pw; canvas.height = ph; dirty = true; }
    return true;
  }

  function draw(now) {
    if (!gl || !prog || !texA || lostContext) return;
    if (!resize()) return;
    let m = 0;
    if (texB) {
      m = reduced() ? 1 : Math.min(1, (now - fadeStart) / FADE_MS);
      m = m * m * (3 - 2 * m);
    }
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_CUBE_MAP, texA);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_CUBE_MAP, texB || texA);
    const y = yaw * Math.PI / 180;
    gl.uniform1f(uni.m, m);
    gl.uniform2f(uni.k, TAN_HALF_FOV * canvas.width / canvas.height, TAN_HALF_FOV);
    gl.uniform4f(uni.r, Math.sin(y), Math.cos(y), Math.sin(PITCH), Math.cos(PITCH));
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    if (texB && m >= 1) {                          // the new one has arrived
      gl.deleteTexture(texA);
      texA = texB;
      texB = null;
    }
    dirty = false;
  }

  // ── The loop ──
  function schedule() {
    if (raf || timer || !active) return;
    // Nothing to show while paused: look again twice a second, without
    // asking the window for frames.
    if (paused()) { timer = setTimeout(() => { timer = 0; lastTick = 0; schedule(); }, 500); return; }
    raf = requestAnimationFrame(tick);
  }

  function tick(now) {
    raf = 0;
    if (!active) return;
    if (!paused() && ready) {
      const frozen = reduced();
      if (!frozen && lastTick) yaw = (yaw + Math.min(200, now - lastTick) * DEG_PER_S / 1000) % 360;
      lastTick = now;
      const due = now - lastDraw >= 31;            // 30 frames a second are plenty for a slow turn
      if ((frozen ? (dirty || texB) : true) && due) { lastDraw = now; draw(now); }
    }
    schedule();
  }

  // ── Which panorama ──
  function shuffled(ids) {
    const a = ids.slice();
    for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  }

  function nextId() {
    if (!queue.length) {
      queue = shuffled(list.map(p => p.id));
      let last = shownId;
      if (!last) { try { last = localStorage.getItem('iceyPanoBgLast') || ''; } catch (_) {} }
      // Never the same one twice in a row, also not across two starts.
      if (queue.length > 1 && queue[0] === last) queue.push(queue.shift());
    }
    return queue.shift();
  }

  function decode(url) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error('picture'));
      img.src = url;
    });
  }

  // A small soft copy of the front face for the blurred backdrop behind
  // the bars (and under the canvas while it fades in).
  function still(img) {
    try {
      const c = document.createElement('canvas');
      c.width = c.height = 256;
      c.getContext('2d').drawImage(img, 0, 0, 256, 256);
      root.style.setProperty('--pano-still', 'url("' + c.toDataURL('image/jpeg', 0.7) + '")');
    } catch (_) {}
  }

  async function showNext(first) {
    clearTimeout(switchTimer);
    const gen = generation;
    if (!active) return;
    if (!first && (paused() || texB)) { switchTimer = setTimeout(showNext, 15000); return; }   // not while nobody is looking
    let shown = false;
    for (let tries = 0; tries < 4 && !shown && list.length; tries++) {
      const id = nextId();
      try {
        const p = await window.icey.getBgPanorama(id);
        if (gen !== generation || !active) return;
        if (!p || !Array.isArray(p.faces) || p.faces.length !== 6) throw new Error('missing');
        const images = await Promise.all(p.faces.map(decode));
        if (gen !== generation || !active) return;
        if (!initGl()) { fallback(images); return; }
        const tex = upload(images);
        if (texA) { texB = tex; fadeStart = performance.now(); } else texA = tex;
        still(images[0]);
        shownId = id;
        try { localStorage.setItem('iceyPanoBgLast', id); } catch (_) {}
        shown = true;
      } catch (_) {
        if (gen !== generation || !active) return;
        list = list.filter(x => x.id !== id);      // one that cannot be read is not tried again
        queue = queue.filter(x => x !== id);
      }
    }
    if (!shown) { if (!ready) stop(true); return; }
    dirty = true;
    lastDraw = 0;
    if (!ready) {
      ready = true;
      draw(performance.now());
      root.setAttribute('data-bg', 'panorama');
    }
    schedule();
    if (list.length > 1) switchTimer = setTimeout(showNext, SWITCH_MS);
  }

  // No WebGL on this machine: the front face as a still picture.
  function fallback(images) {
    try {
      wrap.style.backgroundImage = 'url("' + images[0].src + '")';
      wrap.classList.add('pano-bg-flat');
      still(images[0]);
      ready = true;
      root.setAttribute('data-bg', 'panorama');
    } catch (_) { stop(true); }
  }

  function stop(failed) {
    active = false;
    ready = false;
    generation++;
    clearTimeout(switchTimer);
    clearTimeout(timer);
    timer = 0;
    if (raf) { cancelAnimationFrame(raf); raf = 0; }
    root.removeAttribute('data-bg');
    root.style.removeProperty('--pano-still');
    try {
      if (gl && texA) gl.deleteTexture(texA);
      if (gl && texB) gl.deleteTexture(texB);
    } catch (_) {}
    texA = texB = null;
    if (wrap) { wrap.style.backgroundImage = ''; wrap.classList.remove('pano-bg-flat'); }
    api.unavailable = !!failed;
  }

  // Called at start and whenever the setting, the layout or the secret
  // mode changes.
  async function apply() {
    if (!wanted()) { if (active) stop(false); api.unavailable = false; return; }
    build();
    place();
    if (active) return;
    active = true;
    api.unavailable = false;
    const gen = ++generation;
    let found = [];
    try { found = (await window.icey.getBgPanoramas()) || []; } catch (_) {}
    if (gen !== generation) return;
    list = found.filter(p => p && p.id);
    queue = [];
    if (!list.length) { stop(true); return; }
    showNext(true);
  }

  document.addEventListener('visibilitychange', () => { if (active && !document.hidden) { lastTick = 0; dirty = true; schedule(); } });
  window.addEventListener('resize', () => { dirty = true; });

  const api = {
    apply,
    // Another panorama was picked for the title screen: show that one.
    reload: async () => { if (active) stop(false); await apply(); },
    // True when panoramas were asked for and none could be shown: the
    // snow picture stays, and Settings says why.
    unavailable: false,
    isOn: () => active && ready
  };
  return api;
})();

(function _panoBgStart() {
  if (typeof SettingsManager === 'undefined') return;
  SettingsManager.onChange((key) => {
    if (key === 'launcherBackground' || key === 'layoutTheme' || key === 'skiflameMode') PanoBg.apply();
    else if (key === 'selectedPanorama') PanoBg.reload();
  });
  const wait = () => { if (SettingsManager._settings) PanoBg.apply(); else setTimeout(wait, 40); };
  wait();
})();
