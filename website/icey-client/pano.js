// The game's title-screen panoramas as the background: six faces of a
// cube around the camera, turning slowly, a new place every three
// minutes with a short cross-fade. Without WebGL the wallpaper stays.
(function () {
  var SETS = ['cherry-grove', 'end-city-v2', 'trial-chamber', 'snowy-mountain', 'lush-cave-v2', 'ancient-city-v2',
              'pale-garden', 'coral-reef', 'mountain', 'nether', 'dripstone', 'sinkhole', 'sv-spawn',
              'sv-winter-spawn', 'amplified-terrain', 'ancient-city'];
  var HOLD = 180000, FADE = 2500, SPIN = 2.2; // ms, ms, degrees per second
  var canvas = document.getElementById('pano');
  var gl = canvas && (canvas.getContext('webgl', { antialias: false, alpha: false, depth: false, powerPreference: 'low-power' })
                      || canvas.getContext('experimental-webgl'));
  if (!gl) return;
  var still = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function shader(type, src) {
    var sh = gl.createShader(type); gl.shaderSource(sh, src); gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(sh));
    return sh;
  }
  var prog = gl.createProgram();
  gl.attachShader(prog, shader(gl.VERTEX_SHADER,
    'attribute vec2 p; varying vec2 v; void main() { v = p; gl_Position = vec4(p, 0.0, 1.0); }'));
  gl.attachShader(prog, shader(gl.FRAGMENT_SHADER,
    'precision mediump float; varying vec2 v;' +
    'uniform sampler2D f0, f1, f2, f3, f4, f5; uniform float yaw, pitch, aspect, tanHalf, alpha;' +
    'vec4 face(int i, vec2 st) {' +
    '  if (i == 0) return texture2D(f0, st); if (i == 1) return texture2D(f1, st);' +
    '  if (i == 2) return texture2D(f2, st); if (i == 3) return texture2D(f3, st);' +
    '  if (i == 4) return texture2D(f4, st); return texture2D(f5, st); }' +
    'void main() {' +
    '  vec3 r = normalize(vec3(v.x * tanHalf * aspect, v.y * tanHalf, 1.0));' +
    '  float cp = cos(pitch), sp = sin(pitch);' +
    '  r = vec3(r.x, r.y * cp - r.z * sp, r.y * sp + r.z * cp);' +
    '  float cy = cos(yaw), sy = sin(yaw);' +
    '  r = vec3(r.x * cy + r.z * sy, r.y, -r.x * sy + r.z * cy);' +
    '  float h = atan(r.x, r.z);' +   // 0 = front face, grows turning right
    '  float q = floor((h + 0.7853982) / 1.5707963);' +
    '  float hl = h - q * 1.5707963;' +
    '  float horiz = length(r.xz);' +
    '  float u = tan(hl), w = r.y / (horiz * cos(hl));' +
    '  vec4 c;' +
    '  if (w > 1.0)       c = face(4, vec2(r.x / r.y, r.z / r.y) * 0.5 + 0.5);' +
    '  else if (w < -1.0) c = face(5, vec2(r.x / -r.y, -r.z / -r.y) * 0.5 + 0.5);' +
    '  else { int i = int(mod(q + 4.0, 4.0)); c = face(i, vec2(u * 0.5 + 0.5, 0.5 - w * 0.5)); }' +
    '  gl_FragColor = vec4(c.rgb, alpha); }'));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) return;
  gl.useProgram(prog);
  var buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  var loc = gl.getAttribLocation(prog, 'p');
  gl.enableVertexAttribArray(loc);
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  for (var i = 0; i < 6; i++) gl.uniform1i(gl.getUniformLocation(prog, 'f' + i), i);
  var U = {};
  ['yaw', 'pitch', 'aspect', 'tanHalf', 'alpha'].forEach(function (n) { U[n] = gl.getUniformLocation(prog, n); });
  gl.enable(gl.BLEND);
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);

  // Load one set's six faces into textures; resolves with the textures.
  function load(name) {
    return Promise.all([0, 1, 2, 3, 4, 5].map(function (i) {
      return new Promise(function (ok, fail) {
        var img = new Image();
        img.onload = function () { ok(img); };
        img.onerror = fail;
        img.src = '/icey-client/panoramas/' + name + '/' + i + '.jpg';
      });
    })).then(function (imgs) {
      return imgs.map(function (img) {
        var t = gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D, t);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, img);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        return t;
      });
    });
  }
  function drop(texs) { texs.forEach(function (t) { gl.deleteTexture(t); }); }
  function bind(texs) {
    for (var i = 0; i < 6; i++) { gl.activeTexture(gl.TEXTURE0 + i); gl.bindTexture(gl.TEXTURE_2D, texs[i]); }
  }

  // Visit the places in a random order, then shuffle again.
  var order = [], next = 0;
  function pick() {
    if (next >= order.length) {
      order = SETS.slice();
      for (var i = order.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = order[i]; order[i] = order[j]; order[j] = t; }
      if (current && order[0] === current.name && order.length > 1) { var t2 = order[0]; order[0] = order[1]; order[1] = t2; }
      next = 0;
    }
    return order[next++];
  }

  var current = null, incoming = null, fadeStart = 0, lastSwitch = 0, loading = false;
  var start = performance.now(), yaw0 = Math.random() * Math.PI * 2;

  function size() {
    var dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    var w = Math.round(canvas.clientWidth * dpr), h = Math.round(canvas.clientHeight * dpr);
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; gl.viewport(0, 0, w, h); }
    return w / h;
  }

  function draw(texs, alpha, t) {
    bind(texs);
    gl.uniform1f(U.alpha, alpha);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  }

  function frame(now) {
    if (!current) return;
    var t = (now - start) / 1000;
    var aspect = size();
    var yaw = still ? yaw0 : yaw0 + t * SPIN * Math.PI / 180;
    var pitch = still ? -0.06 : -0.08 + Math.sin(t * 0.09) * 0.07;
    gl.uniform1f(U.aspect, aspect);
    gl.uniform1f(U.tanHalf, Math.tan(0.5 * (aspect > 1 ? 1.15 : 1.5)));
    gl.uniform1f(U.yaw, yaw);
    gl.uniform1f(U.pitch, pitch);
    draw(current.texs, 1, t);
    if (incoming) {
      var k = Math.min(1, (now - fadeStart) / FADE);
      draw(incoming.texs, k, t);
      if (k >= 1) { drop(current.texs); current = incoming; incoming = null; lastSwitch = now; }
    } else if (now - lastSwitch > HOLD && !loading && !document.hidden) {
      loading = true;
      var name = pick();
      load(name).then(function (texs) { incoming = { name: name, texs: texs }; fadeStart = performance.now(); })
        .catch(function () {}).then(function () { loading = false; });
    }
    if (!still || !drawnOnce) requestAnimationFrame(frame);
    drawnOnce = true;
  }
  var drawnOnce = false;

  var first = pick();
  load(first).then(function (texs) {
    current = { name: first, texs: texs };
    lastSwitch = performance.now();
    canvas.classList.add('on');
    requestAnimationFrame(frame);
  }).catch(function () {});

  // Nothing to animate while the tab is hidden; pick up again when it shows.
  document.addEventListener('visibilitychange', function () { if (!document.hidden && current) requestAnimationFrame(frame); });
  window.addEventListener('resize', function () { if (still && current) requestAnimationFrame(frame); });
})();
