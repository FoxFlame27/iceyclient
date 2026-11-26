// Friends tab: the people you added, who is online, and a chat with each.
// Left: add someone by Minecraft name, open requests, the list. Right:
// the conversation with the chosen friend and their skin, alive in 3D.
// All data comes from the Social manager (utils/social.js); this file
// only draws it.
let _frOff = null;
let _frViewer = null;
let _frViewerFor = null;
let _frObserver = null;
let _frDrawnChat = '';      // what the message list shows now, to redraw only on change

function _frFace(name) { return 'https://nmsr.nickac.dev/face/' + encodeURIComponent(name) + '?overlay=true'; }

function _frDisposeViewer() {
  if (_frObserver) { _frObserver.disconnect(); _frObserver = null; }
  if (_frViewer) { try { _frViewer.dispose(); } catch (_) {} _frViewer = null; }
  _frViewerFor = null;
}

function FriendsPageInit() {
  const page = document.getElementById('page-friends');
  if (!page) return;
  if (!page.querySelector('.fr')) {
    page.innerHTML = `
      <div class="fr">
        <div class="fr-side">
          <h1 class="fr-title">Friends</h1>
          <form class="fr-add" onsubmit="event.preventDefault(); _frAdd();">
            <input class="fr-add-input" id="fr-add-input" type="text" maxlength="16" spellcheck="false" autocomplete="off" placeholder="Minecraft name">
            <button class="fr-add-btn" type="submit">Add friend</button>
          </form>
          <div class="fr-scroll">
            <div id="fr-requests"></div>
            <div id="fr-list"></div>
          </div>
          <button class="fr-mini fr-invite" type="button" onclick="_frInvite()">Invite a friend</button>
        </div>
        <div class="fr-main" id="fr-main"></div>
      </div>`;
  }
  if (!_frOff) _frOff = Social.onChange(_frRender);
  Social.start();
  Social.sync();
  _frDrawnChat = '';
  _frRender();
  if (Social.openChat) Social.loadChat(Social.openChat);
}

function _frRender() {
  const page = document.getElementById('page-friends');
  if (!page || !page.querySelector('.fr')) return;
  _frRenderSide();
  _frRenderMain();
}

function _frRenderSide() {
  const reqBox = document.getElementById('fr-requests');
  const listBox = document.getElementById('fr-list');
  if (!reqBox || !listBox) return;
  const st = Social.state;
  reqBox.textContent = '';
  listBox.textContent = '';
  const form = document.querySelector('.fr-add');
  if (form) form.style.display = st ? '' : 'none';

  if (!st) {
    const note = document.createElement('div');
    note.className = 'fr-note';
    note.textContent = (Social.error === 'NO_ACCOUNT' || Social.error === 'OFFLINE_ACCOUNT' || Social.error === 'SESSION_EXPIRED')
      ? 'Sign in with a Microsoft account to use friends.'
      : Social.error ? 'Friends are not available right now.' : 'Loading...';
    listBox.appendChild(note);
    return;
  }

  if (st.incoming.length) {
    const h = document.createElement('div'); h.className = 'fr-heading'; h.textContent = 'Friend requests';
    reqBox.appendChild(h);
    for (const p of st.incoming) {
      const row = document.createElement('div'); row.className = 'fr-row fr-request';
      const face = document.createElement('img'); face.className = 'fr-face'; face.alt = ''; face.src = _frFace(p.name);
      const name = document.createElement('span'); name.className = 'fr-name'; name.setAttribute('data-no-i18n', ''); name.textContent = p.name;
      const yes = document.createElement('button'); yes.className = 'fr-mini yes'; yes.textContent = 'Accept'; yes.onclick = () => _frRespond(p.uuid, true);
      const no = document.createElement('button'); no.className = 'fr-mini'; no.textContent = 'Decline'; no.onclick = () => _frRespond(p.uuid, false);
      row.append(face, name, yes, no);
      reqBox.appendChild(row);
    }
  }

  // Online first, then by name.
  const friends = st.friends.slice().sort((a, b) => (b.online - a.online) || a.name.localeCompare(b.name));
  if (!friends.length && !st.incoming.length && !st.outgoing.length) {
    const note = document.createElement('div'); note.className = 'fr-note';
    note.textContent = 'No friends yet. Add someone by their Minecraft name.';
    listBox.appendChild(note);
  }
  for (const f of friends) {
    const row = document.createElement('button');
    row.className = 'fr-row fr-friend' + (Social.openChat === f.uuid ? ' selected' : '') + (f.online ? ' online' : '');
    row.onclick = () => _frOpen(f.uuid);
    const face = document.createElement('img'); face.className = 'fr-face'; face.alt = ''; face.src = _frFace(f.name);
    const text = document.createElement('span'); text.className = 'fr-row-text';
    const name = document.createElement('span'); name.className = 'fr-name'; name.setAttribute('data-no-i18n', ''); name.textContent = f.name;
    const status = document.createElement('span'); status.className = 'fr-status';
    status.textContent = f.inGame ? 'In game' : f.online ? 'Online' : 'Offline';
    text.append(name, status);
    row.append(face, text);
    if (f.unread) { const b = document.createElement('span'); b.className = 'fr-unread'; b.setAttribute('data-no-i18n', ''); b.textContent = f.unread > 99 ? '99+' : String(f.unread); row.appendChild(b); }
    listBox.appendChild(row);
  }
  if (st.outgoing.length) {
    const h = document.createElement('div'); h.className = 'fr-heading'; h.textContent = 'Sent requests';
    listBox.appendChild(h);
    for (const p of st.outgoing) {
      const row = document.createElement('div'); row.className = 'fr-row fr-request';
      const face = document.createElement('img'); face.className = 'fr-face'; face.alt = ''; face.src = _frFace(p.name);
      const name = document.createElement('span'); name.className = 'fr-name'; name.setAttribute('data-no-i18n', ''); name.textContent = p.name;
      const no = document.createElement('button'); no.className = 'fr-mini'; no.textContent = 'Cancel'; no.onclick = () => _frRemove(p.uuid);
      row.append(face, name, no);
      listBox.appendChild(row);
    }
  }
}

// With no conversation open the page shows everyone: a wall of cards, one
// per friend, with the friend's skin, who is online and what is unread.
// (It used to be an empty picture with one line of text in the middle.)
let _frWallKey = '';
function _frRenderWall(main, st) {
  const friends = st ? st.friends.slice().sort((a, b) => (b.online - a.online) || a.name.localeCompare(b.name)) : [];
  const key = friends.map(f => f.uuid + (f.online ? 1 : 0) + (f.inGame ? 1 : 0) + ':' + (f.unread || 0)).join('|');
  if (main.querySelector('.fr-wall') && key === _frWallKey) return;
  _frWallKey = key;
  main.textContent = '';
  const wall = document.createElement('div'); wall.className = 'fr-wall';
  if (!friends.length) {
    const none = document.createElement('div'); none.className = 'fr-empty';
    if (st) none.textContent = 'No friends yet. Add someone by their Minecraft name.';
    wall.appendChild(none);
    main.appendChild(wall);
    return;
  }
  const head = document.createElement('div'); head.className = 'fr-wall-head';
  head.textContent = friends.filter(f => f.online).length + ' online · ' + friends.length + ' friends';
  const grid = document.createElement('div'); grid.className = 'fr-wall-grid';
  for (const f of friends) {
    const card = document.createElement('button');
    card.className = 'fr-card' + (f.online ? ' online' : '');
    card.onclick = () => _frOpen(f.uuid);
    const bust = document.createElement('img'); bust.className = 'fr-card-bust'; bust.alt = ''; bust.draggable = false;
    bust.src = 'https://nmsr.nickac.dev/bust/' + encodeURIComponent(f.name) + '?overlay=true';
    const name = document.createElement('span'); name.className = 'fr-card-name'; name.setAttribute('data-no-i18n', ''); name.textContent = f.name;
    const status = document.createElement('span'); status.className = 'fr-status' + (f.online ? ' on' : '');
    status.textContent = f.inGame ? 'In game' : f.online ? 'Online' : 'Offline';
    card.append(bust, name, status);
    if (f.unread) { const b = document.createElement('span'); b.className = 'fr-unread fr-card-unread'; b.setAttribute('data-no-i18n', ''); b.textContent = f.unread > 99 ? '99+' : String(f.unread); card.appendChild(b); }
    grid.appendChild(card);
  }
  wall.append(head, grid);
  main.appendChild(wall);
}

function _frRenderMain() {
  const main = document.getElementById('fr-main');
  if (!main) return;
  const st = Social.state;
  const friend = st && Social.openChat ? st.friends.find(f => f.uuid === Social.openChat) : null;
  if (!friend) {
    _frDisposeViewer();
    _frDrawnChat = '';
    delete main.dataset.friend;
    _frRenderWall(main, st);
    return;
  }
  // The frame of the conversation is built once per friend; after that only
  // the status line and the messages change, so typing is never interrupted.
  if (main.dataset.friend !== friend.uuid) {
    main.dataset.friend = friend.uuid;
    _frDrawnChat = '';
    main.innerHTML = `
      <div class="fr-chat">
        <div class="fr-chat-head">
          <button class="fr-mini fr-back" title="Back" onclick="_frOpen('')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
          <img class="fr-face fr-face-lg" id="fr-head-face" alt="">
          <div class="fr-chat-who">
            <div class="fr-chat-name" id="fr-head-name" data-no-i18n></div>
            <div class="fr-status" id="fr-head-status"></div>
          </div>
          <button class="fr-mini" onclick="_frRemove('${friend.uuid}')">Remove friend</button>
        </div>
        <div class="fr-messages" id="fr-messages"></div>
        <form class="fr-send" onsubmit="event.preventDefault(); _frSend();">
          <input class="fr-send-input" id="fr-send-input" type="text" maxlength="500" autocomplete="off" placeholder="Type a message...">
          <button class="fr-send-btn" type="submit">Send</button>
        </form>
      </div>
      <div class="fr-skin">
        <div class="fr-skin-glow"></div>
        <canvas class="fr-skin-canvas" id="fr-skin-canvas"></canvas>
      </div>`;
    document.getElementById('fr-head-face').src = _frFace(friend.name);
    document.getElementById('fr-head-name').textContent = friend.name;
    _frShowSkin(friend);
    setTimeout(() => { const i = document.getElementById('fr-send-input'); if (i) i.focus(); }, 60);
  }
  const status = document.getElementById('fr-head-status');
  if (status) {
    status.textContent = friend.inGame ? 'In game' : friend.online ? 'Online' : 'Offline';
    status.classList.toggle('on', !!friend.online);
  }
  const list = Social.chats.get(friend.uuid) || [];
  const key = friend.uuid + ':' + list.length + ':' + (list.length ? list[list.length - 1].id : 0);
  if (key === _frDrawnChat) return;
  _frDrawnChat = key;
  const box = document.getElementById('fr-messages');
  if (!box) return;
  const me = st.me.uuid;
  box.textContent = '';
  let lastDay = '';
  for (const m of list) {
    const when = new Date(m.at);
    const day = when.toLocaleDateString(undefined, { day: 'numeric', month: 'long' });
    if (day !== lastDay) {
      lastDay = day;
      const d = document.createElement('div'); d.className = 'fr-day'; d.setAttribute('data-no-i18n', ''); d.textContent = day;
      box.appendChild(d);
    }
    const line = document.createElement('div');
    line.className = 'fr-msg' + (m.from === me ? ' mine' : '');
    line.setAttribute('data-no-i18n', '');
    const bubble = document.createElement('div'); bubble.className = 'fr-bubble'; bubble.textContent = m.text;
    const time = document.createElement('span'); time.className = 'fr-time';
    time.textContent = when.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    line.append(bubble, time);
    box.appendChild(line);
  }
  box.scrollTop = box.scrollHeight;
}

// How big the skin stands: the whole body with both arms has to fit the
// panel, which is tall and narrow, so the width decides.
function _frSkinZoom(w, h) {
  return Math.max(0.3, Math.min(0.66, 1.08 * (w / Math.max(1, h))));
}

// The friend's own skin, standing next to the conversation.
async function _frShowSkin(friend) {
  _frDisposeViewer();
  const canvas = document.getElementById('fr-skin-canvas');
  if (!canvas || !window.skinview3d) return;
  const stage = canvas.parentElement;
  _frViewerFor = friend.uuid;
  let tex = null;
  try { tex = await window.icey.getSkinTexture(friend.name); } catch (_) {}
  if (!canvas.isConnected || _frViewerFor !== friend.uuid || !tex || tex.error || !tex.dataUrl) return;
  try {
    const viewer = new skinview3d.SkinViewer({ canvas, width: Math.max(1, stage.clientWidth), height: Math.max(1, stage.clientHeight) });
    await viewer.loadSkin(tex.dataUrl, { model: tex.slim ? 'slim' : 'default' });
    if (!canvas.isConnected || _frViewerFor !== friend.uuid) { viewer.dispose(); return; }
    viewer.fov = 36;
    viewer.zoom = _frSkinZoom(stage.clientWidth, stage.clientHeight);
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    viewer.playerWrapper.rotation.y = -0.45;
    viewer.globalLight.intensity = 2.6;
    viewer.cameraLight.intensity = 1.1;
    viewer.animation = new skinview3d.WaveAnimation();
    setTimeout(() => { if (_frViewer === viewer) viewer.animation = new skinview3d.IdleAnimation(); }, 2600);
    _frViewer = viewer;
    canvas.classList.add('shown');
    _frObserver = new ResizeObserver(() => {
      const w = stage.clientWidth, h = stage.clientHeight;
      if (w > 0 && h > 0) { if (_frViewer) { _frViewer.setSize(w, h); _frViewer.zoom = _frSkinZoom(w, h); } }
      else { _frDisposeViewer(); const m = document.getElementById('fr-main'); if (m) delete m.dataset.friend; }   // another page shows: free the 3D view
    });
    _frObserver.observe(stage);
  } catch (e) { console.warn('[friends] skin failed:', e); _frDisposeViewer(); }
}

function _frOpen(uuid) {
  Social.openChat = uuid || null;       // no uuid: back to the wall
  _frRender();
  if (uuid) Social.loadChat(uuid);
}

async function _frSend() {
  const input = document.getElementById('fr-send-input');
  const text = input ? input.value.trim() : '';
  if (!text || !Social.openChat) return;
  input.value = '';
  const ok = await Social.send(Social.openChat, text);
  if (!ok) { input.value = text; Toast.error(Social.sendError === 'rude' ? 'Not sent: no bad words, please' : 'Message not sent'); }
}

// A friend without Icey Client needs it first: hand out the download link.
function _frInvite() {
  navigator.clipboard.writeText('https://iceymc.com/?from=invite')
    .then(() => Toast.success('Invite link copied'))
    .catch(() => Toast.info('https://iceymc.com'));
}

async function _frAdd() {
  const input = document.getElementById('fr-add-input');
  const name = input ? input.value.trim() : '';
  if (!name) return;
  const r = await Social.call('POST', '/social/friends/add', { name });
  const status = r.data && r.data.status;
  const shown = (r.data && r.data.name) || name;
  if (status === 'requested') { Toast.success('Request sent to ' + shown); input.value = ''; }
  else if (status === 'friends') { Toast.success('You are now friends with ' + shown); input.value = ''; }
  else if (status === 'self') Toast.info('That is you');
  else if (status === 'unknown') Toast.error('No player with that name');
  else Toast.error('Friends are not available right now.');
  Social.sync();
}

async function _frRespond(uuid, accept) {
  await Social.call('POST', '/social/friends/respond', { uuid, accept });
  Social.sync();
}

async function _frRemove(uuid) {
  await Social.call('POST', '/social/friends/remove', { uuid });
  if (Social.openChat === uuid) Social.openChat = null;
  Social.chats.delete(uuid);
  Social.sync();
}
