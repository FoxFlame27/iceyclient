// Character page: opened by right-clicking the player on the Frost home
// page. Pick his armor piece by piece, what he holds in each hand, the
// animation he plays while the game starts and the sound that goes with
// it. Every choice is saved at once (CharacterRig.save) and shown on the
// model here and on the home page.
let _chrViewer = null;
let _chrObserver = null;
let _chrCfg = null;
let _chrAnimTimer = null;

const _CHR_SLOT_LABEL = { helmet: 'Helmet', chest: 'Chestplate', legs: 'Leggings', boots: 'Boots' };
const _CHR_ANIM_LABEL = { run: 'Run', walk: 'Walk', fly: 'Fly', wave: 'Wave', crouch: 'Crouch', hit: 'Attack' };
const _CHR_SOUND_LABEL = { off: 'Off', frost: 'Frost', power: 'Power Up', impact: 'Impact' };
const _CHR_ARMOR_NAME = { chainmail: 'Chainmail', iron: 'Iron', gold: 'Gold', diamond: 'Diamond', netherite: 'Netherite' };

function _chrDispose() {
  clearTimeout(_chrAnimTimer);
  if (_chrObserver) { _chrObserver.disconnect(); _chrObserver = null; }
  if (_chrViewer) { try { CharacterRig.clear(_chrViewer); _chrViewer.dispose(); } catch (_) {} _chrViewer = null; }
}

async function CharacterPageInit() {
  const page = document.getElementById('page-character');
  if (!page) return;
  _chrDispose();
  _chrCfg = CharacterRig.get();

  // Pictures for every choice on the page, in one request.
  const names = [];
  for (const m of CharacterRig.ARMOR) if (m !== 'none') for (const s of CharacterRig.SLOTS) names.push(CharacterRig.armorIcon(m, s));
  for (const id of Object.keys(CharacterRig.ITEMS)) names.push('item/' + id);
  const icons = await CharacterRig.textures(names);
  const haveIcons = names.some(n => icons[n]);

  const noneIcon = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="8.5"/><line x1="6" y1="18" x2="18" y2="6"/></svg>';
  const chip = (group, value, inner, title, selected) =>
    `<button class="chr-chip ${selected ? 'selected' : ''}" data-group="${group}" data-value="${value}" title="${_escapeHtml(title)}" onclick="_chrPick('${group}', '${value}')">${inner}</button>`;
  const pic = (name, fallback) => icons[name]
    ? `<img src="${icons[name]}" alt="" draggable="false">`
    : `<span class="chr-chip-text" data-no-i18n>${_escapeHtml(fallback)}</span>`;

  const armorRows = CharacterRig.SLOTS.map(slot => `
    <div class="chr-row">
      <span class="chr-row-label">${_CHR_SLOT_LABEL[slot]}</span>
      <div class="chr-chips">
        ${chip(slot, 'none', noneIcon, 'None', _chrCfg[slot] === 'none')}
        ${CharacterRig.ARMOR.filter(m => m !== 'none').map(m =>
          chip(slot, m, pic(CharacterRig.armorIcon(m, slot), _CHR_ARMOR_NAME[m]), _CHR_ARMOR_NAME[m], _chrCfg[slot] === m)).join('')}
      </div>
    </div>`).join('');

  const hand = (group) => `
    <div class="chr-chips chr-chips-grid">
      ${chip(group, 'none', noneIcon, 'None', _chrCfg[group] === 'none')}
      ${Object.entries(CharacterRig.ITEMS).map(([id, name]) => chip(group, id, pic('item/' + id, name), name, _chrCfg[group] === id)).join('')}
    </div>`;

  const words = (group, list, labels) => `
    <div class="chr-chips">
      ${list.map(v => `<button class="chr-chip chr-chip-word ${_chrCfg[group] === v ? 'selected' : ''}" data-group="${group}" data-value="${v}" onclick="_chrPick('${group}', '${v}')">${labels[v]}</button>`).join('')}
    </div>`;

  page.innerHTML = `
    <div class="chr">
      <div class="chr-head">
        <button class="chr-back" onclick="switchPage('home')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
          Back
        </button>
        <h1 class="chr-title">Character</h1>
      </div>
      <div class="chr-body">
        <div class="chr-stage">
          <div class="chr-stage-glow"></div>
          <div class="chr-stage-floor"></div>
          <canvas class="chr-canvas" id="chr-canvas"></canvas>
          <div class="chr-stage-hint">Drag to turn</div>
          <div class="chr-presets">
            <button class="chr-preset" onclick="_chrPreset('diamond')">Full Diamond</button>
            <button class="chr-preset" onclick="_chrPreset('netherite')">Full Netherite</button>
            <button class="chr-preset" onclick="_chrPreset('none')">Clear</button>
          </div>
        </div>
        <div class="chr-panel">
          ${haveIcons ? '' : '<div class="chr-note">Start the game once to load armor and items.</div>'}
          <section class="chr-section">
            <h2 class="chr-section-title">Armor</h2>
            ${armorRows}
          </section>
          <section class="chr-section">
            <h2 class="chr-section-title">Main Hand</h2>
            ${hand('main')}
          </section>
          <section class="chr-section">
            <h2 class="chr-section-title">Off Hand</h2>
            ${hand('off')}
          </section>
          <section class="chr-section chr-section-split">
            <div>
              <h2 class="chr-section-title">Start Animation</h2>
              ${words('startAnim', CharacterRig.ANIMATIONS, _CHR_ANIM_LABEL)}
            </div>
            <div>
              <h2 class="chr-section-title">Start Sound</h2>
              ${words('startSound', CharacterRig.SOUNDS, _CHR_SOUND_LABEL)}
            </div>
          </section>
        </div>
      </div>
    </div>`;

  _chrInitViewer();
}

async function _chrInitViewer() {
  const canvas = document.getElementById('chr-canvas');
  if (!canvas || !window.skinview3d) return;
  const stage = canvas.parentElement;
  let name = 'MHF_Steve';
  try { const auth = await window.icey.getAuth(); if (auth && auth.username) name = auth.username; } catch (_) {}
  let tex = null;
  try { tex = await window.icey.getSkinTexture(name); } catch (_) {}
  if (!canvas.isConnected || !tex || tex.error || !tex.dataUrl) return;
  try {
    const viewer = new skinview3d.SkinViewer({ canvas, width: Math.max(1, stage.clientWidth), height: Math.max(1, stage.clientHeight) });
    await viewer.loadSkin(tex.dataUrl, { model: tex.slim ? 'slim' : 'default' });
    if (!canvas.isConnected) { viewer.dispose(); return; }
    viewer.fov = 36;
    viewer.zoom = 0.66;
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    viewer.playerWrapper.rotation.y = -0.5;
    viewer.globalLight.intensity = 2.6;
    viewer.cameraLight.intensity = 1.1;
    viewer.animation = new skinview3d.IdleAnimation();
    _chrViewer = viewer;
    await CharacterRig.apply(viewer, _chrCfg);
    canvas.classList.add('shown');
    _chrObserver = new ResizeObserver(() => {
      const w = stage.clientWidth, h = stage.clientHeight;
      if (w > 0 && h > 0) { if (_chrViewer) _chrViewer.setSize(w, h); }
      else _chrDispose();                              // another page is showing: free the 3D view
    });
    _chrObserver.observe(stage);
  } catch (e) {
    console.warn('[character] preview failed:', e);
    _chrDispose();
  }
}

function _chrMark(group) {
  document.querySelectorAll(`.chr-chip[data-group="${group}"]`).forEach(b => b.classList.toggle('selected', b.dataset.value === _chrCfg[group]));
}

async function _chrPick(group, value) {
  if (!_chrCfg) return;
  _chrCfg[group] = value;
  _chrMark(group);
  await CharacterRig.save(_chrCfg);
  if (group === 'startSound') { CharacterRig.playSound(value); return; }
  if (group === 'startAnim') { _chrPlayStart(); return; }
  if (_chrViewer) CharacterRig.apply(_chrViewer, _chrCfg);
}

// Show the chosen start animation for a moment, then stand again.
function _chrPlayStart() {
  if (!_chrViewer) return;
  clearTimeout(_chrAnimTimer);
  _chrViewer.animation = CharacterRig.animation(_chrCfg.startAnim);
  _chrAnimTimer = setTimeout(() => { if (_chrViewer) _chrViewer.animation = new skinview3d.IdleAnimation(); }, 3600);
}

async function _chrPreset(material) {
  if (!_chrCfg) return;
  for (const s of CharacterRig.SLOTS) { _chrCfg[s] = material; _chrMark(s); }
  if (material === 'none') { _chrCfg.main = 'none'; _chrCfg.off = 'none'; _chrMark('main'); _chrMark('off'); }
  await CharacterRig.save(_chrCfg);
  if (_chrViewer) CharacterRig.apply(_chrViewer, _chrCfg);
}
