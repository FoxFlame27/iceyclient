let _skinsLookupName = '';

async function SkinsPageInit() {
  const page = document.getElementById('page-skins');
  const auth = await window.icey.getAuth();
  const displayName = (auth && auth.username) ? auth.username : '';
  _skinsLookupName = displayName;

  page.innerHTML = `
    <div class="skins-viewer">
      <div class="skins-search-bar">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input class="skins-search-input" type="text" id="skins-search" placeholder="Enter username..." value="${displayName}" spellcheck="false" maxlength="16" onkeydown="if(event.key==='Enter') _skinsLookup()">
        <button class="skins-search-btn" onclick="_skinsLookup()">Search</button>
      </div>
      <div class="skins-preview-card" id="skins-preview-card">
        ${displayName
          ? `<img class="skins-preview-body" id="skins-body-img" src="https://mineskin.eu/armor/body/${displayName}/250.png" alt="Skin">`
          : '<div class="skins-preview-empty">Search for a player to see their skin</div>'}
      </div>
      <div class="skins-preview-name" id="skins-preview-name">${displayName}</div>
      <div class="skins-preview-views" id="skins-preview-views" ${displayName ? '' : 'style="display:none"'}>
        <button class="skins-view-btn active" onclick="_skinsSwitchView('body', this)">Body</button>
        <button class="skins-view-btn" onclick="_skinsSwitchView('bust', this)">Bust</button>
        <button class="skins-view-btn" onclick="_skinsSwitchView('head', this)">Head</button>
      </div>
    </div>
  `;
}

function _skinsLookup() {
  const input = document.getElementById('skins-search');
  const name = input?.value.trim();
  if (!name) { Toast.error('Enter a username'); return; }

  _skinsLookupName = name;
  const card = document.getElementById('skins-preview-card');
  const nameEl = document.getElementById('skins-preview-name');
  const views = document.getElementById('skins-preview-views');

  if (card) card.innerHTML = `<img class="skins-preview-body" id="skins-body-img" src="https://mineskin.eu/armor/body/${name}/250.png" alt="Skin" onerror="_skinsLookupError()">`;
  if (nameEl) nameEl.textContent = name;
  if (views) views.style.display = 'flex';

  document.querySelectorAll('.skins-view-btn').forEach((b, i) => b.classList.toggle('active', i === 0));
}

function _skinsLookupError() {
  Toast.error('Could not find skin for that player');
}

function _skinsSwitchView(view, btn) {
  document.querySelectorAll('.skins-view-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  const img = document.getElementById('skins-body-img');
  if (!img || !_skinsLookupName) return;
  const name = _skinsLookupName;
  const urls = {
    body: `https://mineskin.eu/armor/body/${name}/250.png`,
    bust: `https://mineskin.eu/armor/bust/${name}/250.png`,
    head: `https://mineskin.eu/headhelm/${name}/250.png`
  };
  img.src = urls[view] || urls.body;
}
