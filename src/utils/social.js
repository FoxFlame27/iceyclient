// Friends: who is online, requests, messages. One manager for the whole
// launcher: it asks the friends service every few seconds (through the
// main process, which signs the active Minecraft account in), keeps the
// latest answer, and tells whoever listens. When a friend comes online, a
// request arrives or a message comes in, it shows a notice in the top
// right corner, and passes the same notice to a running game.
const Social = {
  state: null,            // { me, friends, incoming, outgoing } from the last answer
  error: null,            // why there is no state: NO_ACCOUNT, OFFLINE_ACCOUNT, NETWORK, ...
  chats: new Map(),       // friend uuid -> messages, oldest first
  openChat: null,         // uuid of the conversation on screen (its messages count as read)
  _lastId: -1,            // newest message id seen; -1 = nothing asked yet
  _timer: null,
  _busy: false,
  _listeners: [],

  onChange(fn) { this._listeners.push(fn); return () => { this._listeners = this._listeners.filter(l => l !== fn); }; },
  _emit() { for (const l of this._listeners) { try { l(); } catch (e) { console.error(e); } } this._badge(); },

  start() {
    if (this._timer) return;
    this.sync();
    this._timer = setInterval(() => this.sync(), 12000);
  },

  // After signing in or switching account: forget the other account's friends.
  reset() {
    this.state = null; this.error = null; this.chats = new Map(); this.openChat = null; this._lastId = -1;
    this._emit();
    this.sync();
  },

  async call(method, route, body) {
    try { return await window.icey.socialCall(method, route, body); }
    catch (_) { return { error: 'NETWORK' }; }
  },

  async sync() {
    if (this._busy) return;
    this._busy = true;
    try {
      const inGame = typeof MinecraftLauncher !== 'undefined' && MinecraftLauncher.getState() === 'running';
      const r = await this.call('POST', '/social/sync', { since: this._lastId, inGame });
      if (r.error || !r.ok || !r.data || !r.data.me) {
        this.error = r.error || 'NETWORK';
        if (this.error !== 'NETWORK') this.state = null;       // a network blip keeps the list on screen
        this._emit();
        return;
      }
      const next = r.data;
      const prev = this.state && this.state.me.uuid === next.me.uuid ? this.state : null;
      if (this.state && !prev) { this.chats = new Map(); this.openChat = null; }
      const me = next.me.uuid;

      if (prev) {
        const wasOnline = new Set(prev.friends.filter(f => f.online).map(f => f.uuid));
        for (const f of next.friends) if (f.online && !wasOnline.has(f.uuid)) this._notify(f, 'online');
        const asked = new Set(prev.incoming.map(p => p.uuid));
        for (const p of next.incoming) if (!asked.has(p.uuid)) this._notify(p, 'request');
      }
      for (const m of next.messages || []) {
        const other = m.from === me ? m.to : m.from;
        this._remember(other, m);
        if (m.from !== me) {
          if (this.openChat === other && this._pageVisible()) this.call('POST', '/social/read', { with: other });
          else { const f = next.friends.find(x => x.uuid === other); if (f) this._notify(f, 'message', m.text); }
        }
      }
      // What is on screen is read: do not show a count for it.
      if (this.openChat && this._pageVisible()) { const f = next.friends.find(x => x.uuid === this.openChat); if (f) f.unread = 0; }
      this._lastId = next.lastId;
      this.state = next;
      this.error = null;
      this._emit();
    } finally { this._busy = false; }
  },

  _pageVisible() {
    const p = document.getElementById('page-friends');
    return !!p && p.classList.contains('active') && document.hasFocus();
  },

  _remember(other, m) {
    const list = this.chats.get(other) || [];
    if (!list.some(x => x.id === m.id)) { list.push(m); list.sort((a, b) => a.id - b.id); }
    this.chats.set(other, list);
  },

  // The conversation with one friend, from the service (also marks it read).
  async loadChat(uuid) {
    const r = await this.call('GET', '/social/messages?with=' + uuid);
    if (r.ok && r.data && Array.isArray(r.data.messages)) {
      for (const m of r.data.messages) this._remember(uuid, m);
      const f = this.state && this.state.friends.find(x => x.uuid === uuid);
      if (f) f.unread = 0;
      this._emit();
    }
    return this.chats.get(uuid) || [];
  },

  async send(uuid, text) {
    const r = await this.call('POST', '/social/messages', { to: uuid, text });
    if (r.ok && r.data && r.data.message) {
      this._remember(uuid, r.data.message);
      this._lastId = Math.max(this._lastId, r.data.message.id);
      this._emit();
      return true;
    }
    return false;
  },

  unreadTotal() {
    if (!this.state) return 0;
    return this.state.friends.reduce((n, f) => n + (f.unread || 0), 0) + this.state.incoming.length;
  },

  // The little count on the Friends button of the navigation.
  _badge() {
    const tab = document.querySelector('.nav-tab[data-page="friends"]');
    if (!tab) return;
    let b = tab.querySelector('.nav-badge');
    const n = this.unreadTotal();
    if (!n) { if (b) b.remove(); return; }
    if (!b) { b = document.createElement('span'); b.className = 'nav-badge'; b.setAttribute('data-no-i18n', ''); tab.appendChild(b); }
    b.textContent = n > 99 ? '99+' : String(n);
  },

  // A notice in the top right corner, with the friend's face. Click opens the chat.
  _notify(person, kind, text) {
    const title = kind === 'online' ? `${person.name} is online`
      : kind === 'request' ? `${person.name} wants to be your friend`
      : person.name;
    const body = kind === 'message' ? String(text || '') : '';
    try {
      if (typeof MinecraftLauncher !== 'undefined' && MinecraftLauncher.getState() === 'running') {
        window.icey.socialNotifyGame(title, body);
      }
    } catch (_) {}
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = 'toast social-toast';
    const face = document.createElement('img');
    face.className = 'social-toast-face';
    face.alt = '';
    face.src = 'https://nmsr.nickac.dev/face/' + encodeURIComponent(person.name) + '?overlay=true';
    face.onerror = () => { face.style.visibility = 'hidden'; };
    const textBox = document.createElement('div');
    textBox.className = 'social-toast-text';
    const t = document.createElement('div');
    t.className = 'social-toast-title';
    if (kind === 'message') t.setAttribute('data-no-i18n', '');
    t.textContent = title;
    textBox.appendChild(t);
    if (body) {
      const b = document.createElement('div');
      b.className = 'social-toast-body';
      b.setAttribute('data-no-i18n', '');
      b.textContent = body;
      textBox.appendChild(b);
    }
    toast.append(face, textBox);
    const dismiss = () => { toast.classList.add('dismissing'); setTimeout(() => toast.remove(), 300); };
    toast.addEventListener('click', () => {
      dismiss();
      if (kind !== 'request') this.openChat = person.uuid;
      if (typeof switchPage === 'function') switchPage('friends');
    });
    container.prepend(toast);
    setTimeout(dismiss, 6000);
  },
};
