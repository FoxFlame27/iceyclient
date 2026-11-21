// The player's character on the Frost home page: what armor he wears, what
// he holds, how he moves when the game starts and the sound that goes
// with it. The choice is made on the Character page (right-click him) and
// kept in settings under `character`.
//
// Armor and items use the game's own textures, read from the player's
// installed game by the main process (get-gear-textures). The 3D viewer
// only knows skins, so the gear is built here and hung on the model's
// body parts, where it follows every animation:
//   - armor: a second "skin" per slot whose texture is the armor sheet,
//     with only that slot's parts kept and blown up a little, like the
//     game's armor model;
//   - held items: the 16x16 item picture turned into a slab one pixel
//     thick, the way the game draws flat items in the hand.
const CharacterRig = {
  ARMOR: ['none', 'chainmail', 'iron', 'gold', 'diamond', 'netherite'],
  SLOTS: ['helmet', 'chest', 'legs', 'boots'],
  // id → English name (the game's own item names; not translated).
  ITEMS: {
    diamond_sword: 'Diamond Sword', netherite_sword: 'Netherite Sword', iron_sword: 'Iron Sword',
    trident: 'Trident', mace: 'Mace',
    diamond_axe: 'Diamond Axe', netherite_axe: 'Netherite Axe',
    diamond_pickaxe: 'Diamond Pickaxe', netherite_pickaxe: 'Netherite Pickaxe',
    bow: 'Bow', fishing_rod: 'Fishing Rod',
    totem_of_undying: 'Totem of Undying', golden_apple: 'Golden Apple', ender_pearl: 'Ender Pearl',
    end_crystal: 'End Crystal', experience_bottle: 'Bottle o\' Enchanting', wind_charge: 'Wind Charge',
    firework_rocket: 'Firework Rocket', golden_carrot: 'Golden Carrot', cooked_beef: 'Steak',
  },
  ANIMATIONS: ['run', 'walk', 'fly', 'wave', 'crouch', 'hit'],
  SOUNDS: ['off', 'frost', 'power', 'impact'],
  DEFAULTS: { helmet: 'none', chest: 'none', legs: 'none', boots: 'none', main: 'none', off: 'none', enchanted: false, startAnim: 'run', startSound: 'frost' },

  get() {
    const saved = (typeof SettingsManager !== 'undefined' && SettingsManager.get('character')) || {};
    const cfg = { ...this.DEFAULTS, ...saved };
    for (const s of this.SLOTS) if (!this.ARMOR.includes(cfg[s])) cfg[s] = 'none';
    for (const h of ['main', 'off']) if (cfg[h] !== 'none' && !this.ITEMS[cfg[h]]) cfg[h] = 'none';
    if (!this.ANIMATIONS.includes(cfg.startAnim)) cfg.startAnim = 'run';
    if (!this.SOUNDS.includes(cfg.startSound)) cfg.startSound = 'frost';
    cfg.enchanted = !!cfg.enchanted;
    return cfg;
  },

  async save(cfg) { await SettingsManager.set('character', { ...cfg }); },

  // The inventory picture of an armor piece, e.g. ('gold', 'chest') → item/golden_chestplate.
  armorIcon(material, slot) {
    const piece = { helmet: 'helmet', chest: 'chestplate', legs: 'leggings', boots: 'boots' }[slot];
    return 'item/' + (material === 'gold' ? 'golden' : material) + '_' + piece;
  },

  // ── Textures ──
  _tex: new Map(),      // name → data URL | null
  async textures(names) {
    const need = names.filter(n => !this._tex.has(n));
    if (need.length) {
      let got = {};
      try { got = (await window.icey.getGearTextures(need)) || {}; } catch (_) {}
      for (const n of need) this._tex.set(n, got[n] || null);
    }
    const out = {};
    for (const n of names) out[n] = this._tex.get(n) || null;
    return out;
  },

  _image(src) {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => resolve(null);
      img.src = src;
    });
  },

  // ── Putting the gear on a viewer ──
  // Safe to call again with another choice: the previous gear is taken off first.
  async apply(viewer, cfg) {
    if (!viewer || !viewer.playerObject) return;
    const ticket = (viewer.__gearTicket = (viewer.__gearTicket || 0) + 1);
    const names = [];
    for (const s of this.SLOTS) if (cfg[s] !== 'none') names.push((s === 'legs' ? 'leggings/' : 'armor/') + cfg[s]);
    for (const h of ['main', 'off']) if (cfg[h] !== 'none') names.push('item/' + cfg[h]);
    // The game's own glint pictures (their names changed over the versions).
    const GLINTS = ['misc/enchanted_glint_armor', 'misc/enchanted_glint_entity', 'misc/enchanted_glint_item', 'misc/enchanted_item_glint'];
    if (cfg.enchanted && names.length) names.push(...GLINTS);
    const tex = await this.textures(names);
    const images = {};
    await Promise.all(names.map(async (n) => { if (tex[n]) images[n] = await this._image(tex[n]); }));
    if (viewer.__gearTicket !== ticket || !viewer.playerObject) return;     // a newer choice came in meanwhile
    this.clear(viewer);
    const made = (viewer.__gear = []);
    try {
      for (const s of this.SLOTS) {
        const img = cfg[s] !== 'none' && images[(s === 'legs' ? 'leggings/' : 'armor/') + cfg[s]];
        if (img) this._addArmor(viewer, s, img, made);
      }
      if (cfg.main !== 'none' && images['item/' + cfg.main]) this._addItem(viewer, 'rightArm', images['item/' + cfg.main], made, cfg.main);
      if (cfg.off !== 'none' && images['item/' + cfg.off]) this._addItem(viewer, 'leftArm', images['item/' + cfg.off], made, cfg.off);
      if (cfg.enchanted && made.length) {
        const pick = (list) => { for (const n of list) if (images[n]) return images[n]; return null; };
        this._addGlint(viewer, made,
          pick(['misc/enchanted_glint_armor', 'misc/enchanted_glint_entity', 'misc/enchanted_glint_item', 'misc/enchanted_item_glint']),
          pick(['misc/enchanted_glint_item', 'misc/enchanted_item_glint', 'misc/enchanted_glint_entity', 'misc/enchanted_glint_armor']));
      }
    } catch (e) {
      console.warn('[character] gear failed:', e);
      this.clear(viewer);
    }
  },

  clear(viewer) {
    if (viewer && viewer.__glintFrame) { cancelAnimationFrame(viewer.__glintFrame); viewer.__glintFrame = 0; }
    for (const g of (viewer && viewer.__gear) || []) {
      try {
        if (g.object.parent) g.object.parent.remove(g.object);
        if (g.geometry) g.geometry.dispose();
        if (g.material) g.material.dispose();
        if (g.texture) g.texture.dispose();
      } catch (_) {}
    }
    if (viewer) viewer.__gear = [];
  },

  _texture(viewer, canvas) {
    const like = viewer.skinTexture;
    const t = new like.constructor(canvas);
    t.magFilter = like.magFilter;
    t.minFilter = like.minFilter;
    t.colorSpace = like.colorSpace;
    t.needsUpdate = true;
    return t;
  },

  // The armor sheet (64x32) laid out as a 64x64 skin: its top half already
  // is one; the left arm and leg are the right ones mirrored, exactly as
  // old one-sided skins are converted.
  _armorCanvas(img) {
    const c = document.createElement('canvas');
    c.width = c.height = 64;
    const g = c.getContext('2d');
    g.imageSmoothingEnabled = false;
    g.drawImage(img, 0, 0, img.width, img.height, 0, 0, 64, 64 * img.height / img.width);
    const flip = (sx, sy, w, h, dx, dy) => {
      g.save();
      g.translate(dx + w, dy);
      g.scale(-1, 1);
      g.drawImage(c, sx, sy, w, h, 0, 0, w, h);
      g.restore();
    };
    flip(4, 16, 4, 4, 20, 48); flip(8, 16, 4, 4, 24, 48);                       // leg: top, bottom
    flip(0, 20, 4, 12, 24, 52); flip(4, 20, 4, 12, 20, 52); flip(8, 20, 4, 12, 16, 52); flip(12, 20, 4, 12, 28, 52);
    flip(44, 16, 4, 4, 36, 48); flip(48, 16, 4, 4, 40, 48);                     // arm: top, bottom
    flip(40, 20, 4, 12, 40, 52); flip(44, 20, 4, 12, 36, 52); flip(48, 20, 4, 12, 32, 52); flip(52, 20, 4, 12, 44, 52);
    return c;
  },

  // The game's armor model (HumanoidArmorModel), number for number.
  // Every piece is the body part's own box grown by a fixed amount on
  // every side, with the same texture layout:
  //   helmet, chestplate, boots: the outer model, grown by 1.0 pixel
  //   leggings:                  the inner model, grown by 0.5
  //   legs in both models:       0.1 less (0.9 for boots, 0.4 for leggings)
  //   the helmet's "hat" layer:  0.5 on top of the helmet (1.5)
  // Boxes: head 8x8x8, body 8x12x4, arms and legs 4x12x4. `grow` is that
  // amount; `hat` adds the head's second layer.
  _ARMOR_PARTS: {
    helmet: { head: { grow: 1.0, hat: 1.5 } },
    chest:  { body: { grow: 1.0 }, rightArm: { grow: 1.0 }, leftArm: { grow: 1.0 } },
    legs:   { body: { grow: 0.5 }, rightLeg: { grow: 0.4 }, leftLeg: { grow: 0.4 } },
    boots:  { rightLeg: { grow: 0.9 }, leftLeg: { grow: 0.9 } },
  },
  _BOX: { head: [8, 8, 8], body: [8, 12, 4], rightArm: [4, 12, 4], leftArm: [4, 12, 4], rightLeg: [4, 12, 4], leftLeg: [4, 12, 4] },

  _addArmor(viewer, slot, img, made) {
    const real = viewer.playerObject.skin;
    const texture = this._texture(viewer, this._armorCanvas(img));
    const armor = new skinview3d.SkinObject();        // always the wide-armed model, as in the game
    armor.map = texture;
    const parts = this._ARMOR_PARTS[slot];
    const dress = (mesh) => {
      // The sheet is mostly empty: empty parts show the body through. Both
      // sides are drawn, as the game does for armor.
      const material = mesh.material.clone();
      material.map = texture;
      material.transparent = true;
      material.alphaTest = 0.05;
      material.side = 2;
      mesh.material = material;
      made.push({ object: mesh, material });
    };
    for (const name of Object.keys(parts)) {
      const box = this._BOX[name];
      const spec = parts[name];
      const mesh = armor[name].innerLayer;
      dress(mesh);
      // The viewer's part boxes are the game's boxes; growing one by g on
      // every side is a scale of (size + 2g) / size about its centre. (On
      // top of the scale the part already has: the viewer sizes limbs that way.)
      const g = spec.grow;
      mesh.scale.set(mesh.scale.x * (box[0] + 2 * g) / box[0], mesh.scale.y * (box[1] + 2 * g) / box[1], mesh.scale.z * (box[2] + 2 * g) / box[2]);
      if (name === 'rightArm' || name === 'leftArm') {
        // The armor's arm is 4 wide and hangs where the game hangs it from
        // the shoulder joint, whatever the player's own arms are (a slim
        // skin's 3-wide arm sits half a pixel off).
        mesh.position.set(name === 'rightArm' ? -1 : 1, -4, 0);
        real[name].add(mesh);
      } else {
        // Same group as the player's own box for that part, so it turns with it.
        real[name].innerLayer.parent.add(mesh);
      }
      if (spec.hat) {
        // The head's second layer: the viewer builds it 0.5 out already.
        const hat = armor[name].outerLayer;
        dress(hat);
        const k = (box[0] + 2 * spec.hat) / (box[0] + 1);
        hat.scale.set(hat.scale.x * k, hat.scale.y * k, hat.scale.z * k);
        real[name].outerLayer.parent.add(hat);
      }
    }
    made.push({ object: armor, texture });
  },

  // A flat item as a slab one pixel thick: front, back, and an edge
  // wherever a pixel has no neighbour.
  _itemGeometry(viewer, img, place) {
    const c = document.createElement('canvas');
    c.width = c.height = 16;
    const g = c.getContext('2d');
    g.imageSmoothingEnabled = false;
    g.drawImage(img, 0, 0, img.width, img.width, 0, 0, 16, 16);   // animated sheets: first frame
    const px = g.getImageData(0, 0, 16, 16).data;
    const solid = (x, y) => x >= 0 && y >= 0 && x < 16 && y < 16 && px[(y * 16 + x) * 4 + 3] > 40;
    const pos = [], nor = [], uv = [], idx = [];
    const quad = (v, n, u, w) => {
      const base = pos.length / 3;
      const nn = place ? place.normal(n) : n;
      for (const p of v) {
        const pp = place ? place.point(p) : p;
        pos.push(pp[0], pp[1], pp[2]); nor.push(nn[0], nn[1], nn[2]); uv.push(u, w);
      }
      idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    };
    const T = 0.5;
    for (let j = 0; j < 16; j++) for (let i = 0; i < 16; i++) {
      if (!solid(i, j)) continue;
      const x = i - 8, y = 8 - j, u = (i + 0.5) / 16, w = 1 - (j + 0.5) / 16;
      quad([[x, y - 1, T], [x + 1, y - 1, T], [x + 1, y, T], [x, y, T]], [0, 0, 1], u, w);
      quad([[x + 1, y - 1, -T], [x, y - 1, -T], [x, y, -T], [x + 1, y, -T]], [0, 0, -1], u, w);
      if (!solid(i - 1, j)) quad([[x, y - 1, -T], [x, y - 1, T], [x, y, T], [x, y, -T]], [-1, 0, 0], u, w);
      if (!solid(i + 1, j)) quad([[x + 1, y - 1, T], [x + 1, y - 1, -T], [x + 1, y, -T], [x + 1, y, T]], [1, 0, 0], u, w);
      if (!solid(i, j - 1)) quad([[x, y, T], [x + 1, y, T], [x + 1, y, -T], [x, y, -T]], [0, 1, 0], u, w);
      if (!solid(i, j + 1)) quad([[x, y - 1, -T], [x + 1, y - 1, -T], [x + 1, y - 1, T], [x, y - 1, T]], [0, -1, 0], u, w);
    }
    const box = viewer.playerObject.skin.head.innerLayer.geometry;
    const Geometry = Object.getPrototypeOf(box.constructor);          // BufferGeometry
    const Attribute = box.attributes.position.constructor;            // Float32BufferAttribute
    const geometry = new Geometry();
    geometry.setAttribute('position', new Attribute(pos, 3));
    geometry.setAttribute('normal', new Attribute(nor, 3));
    geometry.setAttribute('uv', new Attribute(uv, 2));
    geometry.setIndex(idx);
    return { geometry, canvas: c };
  },

  // How the game holds an item in third person: the `thirdperson_righthand`
  // display values of its item models (degrees, pixels, scale).
  _DISPLAY: {
    handheld:  { rot: [0, -90, 55],    move: [0, 4, 0.5],   scale: 0.85 },   // swords, axes, pickaxes, mace
    rod:       { rot: [0, 90, 55],     move: [0, 4, 2.5],   scale: 0.85 },   // fishing rod
    bow:       { rot: [-80, 260, -40], move: [-1, -2, 2.5], scale: 0.9 },
    generated: { rot: [0, 0, 0],       move: [0, 3, 1],     scale: 0.55 },   // everything flat: totem, apple, pearl...
  },
  _displayFor(id) {
    if (id === 'bow') return this._DISPLAY.bow;
    if (id === 'fishing_rod') return this._DISPLAY.rod;
    if (/_(sword|axe|pickaxe)$/.test(id) || id === 'mace') return this._DISPLAY.handheld;
    return this._DISPLAY.generated;
  },

  // The game's own chain for an item in the hand, worked out once:
  //   hand:  rotate X -90, rotate Y 180, move (±1, 2, -10) pixels
  //   item:  move, rotate X then Y then Z, scale   (the display values)
  // and then from the game's model axes (y down, facing -z) to the
  // viewer's (y up, facing +z). With g a point of the slab, centred, in
  // pixels:   q = move + scale * Rx*Ry*Rz * g
  //           out = (-q.x - side, q.z - 10, q.y + 2)
  // measured from the arm's shoulder joint; side is +1 for the right arm.
  // The left hand mirrors: move.x and the Y and Z angles change sign.
  _handTransform(id, left) {
    const d = this._displayFor(id);
    const k = Math.PI / 180;
    const rx = d.rot[0] * k, ry = d.rot[1] * k * (left ? -1 : 1), rz = d.rot[2] * k * (left ? -1 : 1);
    const mv = [d.move[0] * (left ? -1 : 1), d.move[1], d.move[2]];
    const cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry), cz = Math.cos(rz), sz = Math.sin(rz);
    const rotate = (v) => {
      let x = v[0] * cz - v[1] * sz, y = v[0] * sz + v[1] * cz, z = v[2];        // Z
      const x2 = x * cy + z * sy, z2 = -x * sy + z * cy;                          // Y
      const y3 = y * cx - z2 * sx, z3 = y * sx + z2 * cx;                         // X
      return [x2, y3, z3];
    };
    const side = left ? -1 : 1;
    if (id === 'trident') {
      // A trident is not held like a sword: the game carries it upright
      // like a staff, prongs up and tipped forward, the fist a good third
      // of the way up the shaft. The picture's diagonal (butt lower left,
      // prongs upper right) is laid along that line, edge-on to the body,
      // and stretched to the length of the game's trident.
      const lean = 28 * k, size = 1.22, r2 = Math.SQRT1_2;
      const along = [0, Math.cos(lean), Math.sin(lean)];        // up the shaft
      const across = [0, -Math.sin(lean), Math.cos(lean)];      // across the picture
      const grip = [-3.3, -3.3];                                // where the fist closes, in the picture
      const map = (g) => {
        const a = (g[0] + g[1]) * r2, b = (g[1] - g[0]) * r2;
        return [g[2], a * along[1] + b * across[1], a * along[2] + b * across[2]];
      };
      return {
        point: (g) => { const m = map([g[0] - grip[0], g[1] - grip[1], g[2]]); return [-side + size * m[0], -9.6 + size * m[1], 0.6 + size * m[2]]; },
        normal: (n) => map(n),
      };
    }
    return {
      point: (g) => { const r = rotate(g); const q = [mv[0] + d.scale * r[0], mv[1] + d.scale * r[1], mv[2] + d.scale * r[2]]; return [-q[0] - side, q[2] - 10, q[1] + 2]; },
      normal: (n) => { const r = rotate(n); return [-r[0], r[2], r[1]]; },
    };
  },

  _addItem(viewer, arm, img, made, id) {
    const real = viewer.playerObject.skin;
    const limb = real[arm].innerLayer;
    const { geometry, canvas } = this._itemGeometry(viewer, img, this._handTransform(id, arm === 'leftArm'));
    const texture = this._texture(viewer, canvas);
    const material = limb.material.clone();
    material.map = texture;
    material.transparent = false;
    material.alphaTest = 0;
    material.side = 2;
    const mesh = new limb.constructor(geometry, material);
    // The points are already where the game puts them, measured from the
    // shoulder joint, which is the arm's own group: it swings with the arm.
    real[arm].add(mesh);
    made.push({ object: mesh, geometry, material, texture });
  },

  // ── Enchantment glint ──
  // The game draws an enchanted thing twice: once normally, then again
  // with the glint picture added on top as light, sliding slowly across.
  // Same here: every piece of gear gets a twin with the same shape whose
  // material adds only the glint (its own colour is black, the glint is
  // its glow), cut out by the piece's own picture so it shines only
  // where there is armor. One loop slides the glint for the whole model.
  _glintCanvas(img) {
    const c = document.createElement('canvas');
    c.width = c.height = 64;
    const g = c.getContext('2d');
    if (img) { g.imageSmoothingEnabled = false; g.drawImage(img, 0, 0, img.width, img.width, 0, 0, 64, 64); return c; }
    // No glint picture in this game version's files: streaks of the same purple.
    g.fillStyle = '#000'; g.fillRect(0, 0, 64, 64);
    for (let i = 0; i < 46; i++) {
      const x = Math.random() * 64, y = Math.random() * 64, len = 6 + Math.random() * 16;
      const grad = g.createLinearGradient(x, y, x + len, y + len * 0.4);
      grad.addColorStop(0, 'rgba(128,64,204,0)'); grad.addColorStop(0.5, 'rgba(150,84,230,0.9)'); grad.addColorStop(1, 'rgba(128,64,204,0)');
      g.strokeStyle = grad; g.lineWidth = 1 + Math.random() * 2;
      g.beginPath(); g.moveTo(x, y); g.lineTo(x + len, y + len * 0.4); g.stroke();
    }
    return c;
  },

  _addGlint(viewer, made, armorImg, itemImg) {
    const REPEAT = 1000, ADD = 2;                    // three.js: RepeatWrapping, AdditiveBlending
    const layer = (img, repeat) => {
      const t = this._texture(viewer, this._glintCanvas(img));
      t.wrapS = t.wrapT = REPEAT;
      t.repeat.set(repeat, repeat);
      t.rotation = 0.17;                             // the game turns the glint about 10 degrees
      t.needsUpdate = true;
      return t;
    };
    const armorGlint = layer(armorImg, 2.2);
    const itemGlint = layer(itemImg || armorImg, 1.6);
    const twins = [];
    for (const piece of made.slice()) {
      const mesh = piece.object;
      if (!mesh.isMesh || !mesh.parent) continue;
      const isItem = !!piece.geometry;                // held items own their geometry, armor borrows the model's
      const material = mesh.material.clone();
      material.color.setRGB(0, 0, 0);
      material.emissive.setRGB(1, 1, 1);
      material.emissiveMap = isItem ? itemGlint : armorGlint;
      material.emissiveIntensity = isItem ? 0.34 : 0.26;   // a shimmer over the piece, not a coat of paint
      material.blending = ADD;
      material.transparent = true;
      material.depthWrite = false;
      material.alphaTest = isItem ? 0 : 0.05;         // armor: only where the armor picture has something
      material.polygonOffset = true;
      material.polygonOffsetFactor = -1;
      material.polygonOffsetUnits = -1;
      const twin = new mesh.constructor(mesh.geometry, material);
      twin.position.copy(mesh.position);
      twin.rotation.copy(mesh.rotation);
      twin.scale.copy(mesh.scale);
      twin.renderOrder = 2;
      mesh.parent.add(twin);
      twins.push({ object: twin, material });
    }
    made.push(...twins, { object: { parent: null }, texture: armorGlint }, { object: { parent: null }, texture: itemGlint });
    const t0 = performance.now();
    const slide = () => {
      if (!viewer.__gear || viewer.__gear !== made) return;
      const t = (performance.now() - t0) / 1000;
      armorGlint.offset.set((t * 0.055) % 1, (t * 0.02) % 1);
      itemGlint.offset.set((t * 0.09) % 1, (t * 0.03) % 1);
      viewer.__glintFrame = requestAnimationFrame(slide);
    };
    viewer.__glintFrame = requestAnimationFrame(slide);
  },

  // ── Start animation ──
  animation(name) {
    const S = window.skinview3d;
    const make = (Cls, speed) => { const a = new Cls(); if (speed) a.speed = speed; return a; };
    switch (name) {
      case 'walk': return make(S.WalkingAnimation, 0.9);
      case 'fly': return make(S.FlyingAnimation, 1);
      case 'wave': return make(S.WaveAnimation, 1.2);
      case 'crouch': return make(S.CrouchAnimation, 0.6);
      case 'hit': return make(S.HitAnimation, 1.1);
      default: return make(S.RunningAnimation, 0.75);
    }
  },

  // ── Start sound ──
  // Made on the spot with the Web Audio API: no sound files to ship.
  _audio: null,
  playSound(name) {
    if (!name || name === 'off') return;
    try {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      const ctx = this._audio || (this._audio = new Ctx());
      if (ctx.state === 'suspended') ctx.resume();
      const now = ctx.currentTime + 0.02;
      const out = ctx.createGain();
      out.gain.value = 0.55;
      out.connect(ctx.destination);

      const tone = (type, f0, f1, at, dur, vol) => {
        const o = ctx.createOscillator(), g = ctx.createGain();
        o.type = type;
        o.frequency.setValueAtTime(f0, now + at);
        if (f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, now + at + dur);
        g.gain.setValueAtTime(0.0001, now + at);
        g.gain.exponentialRampToValueAtTime(vol, now + at + Math.min(0.02, dur * 0.2));
        g.gain.exponentialRampToValueAtTime(0.0001, now + at + dur);
        o.connect(g); g.connect(out);
        o.start(now + at); o.stop(now + at + dur + 0.05);
      };
      const noise = (at, dur, vol, f0, f1, q) => {
        const n = Math.floor(ctx.sampleRate * dur);
        const buf = ctx.createBuffer(1, n, ctx.sampleRate);
        const d = buf.getChannelData(0);
        for (let i = 0; i < n; i++) d[i] = Math.random() * 2 - 1;
        const s = ctx.createBufferSource(); s.buffer = buf;
        const f = ctx.createBiquadFilter(); f.type = 'bandpass'; f.Q.value = q || 1.2;
        f.frequency.setValueAtTime(f0, now + at);
        f.frequency.exponentialRampToValueAtTime(f1, now + at + dur);
        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, now + at);
        g.gain.exponentialRampToValueAtTime(vol, now + at + dur * 0.35);
        g.gain.exponentialRampToValueAtTime(0.0001, now + at + dur);
        s.connect(f); f.connect(g); g.connect(out);
        s.start(now + at); s.stop(now + at + dur + 0.05);
      };

      if (name === 'power') {
        // A quick climb of bright notes, then one that rings.
        [392, 523.25, 659.25, 783.99].forEach((f, i) => tone('square', f, f, i * 0.07, 0.11, 0.10));
        tone('triangle', 1046.5, 1046.5, 0.30, 0.55, 0.22);
        tone('sine', 2093, 2093, 0.30, 0.45, 0.07);
      } else if (name === 'impact') {
        // A low hit with air rushing after it.
        tone('sine', 150, 38, 0, 0.5, 0.75);
        tone('triangle', 90, 45, 0, 0.3, 0.3);
        noise(0, 0.22, 0.35, 2600, 300, 0.8);
        noise(0.08, 0.7, 0.16, 500, 3200, 1.4);
      } else {
        // Frost: a rush of cold air and a few notes of ice.
        noise(0, 0.75, 0.24, 500, 5200, 1.0);
        [1318.5, 1760, 2093, 2637].forEach((f, i) => {
          tone('sine', f, f, 0.16 + i * 0.075, 0.75, 0.11);
          tone('triangle', f * 2, f * 2, 0.16 + i * 0.075, 0.3, 0.035);
        });
        tone('sine', 220, 440, 0, 0.45, 0.10);
      }
    } catch (e) { console.warn('[character] sound failed:', e); }
  },
};
