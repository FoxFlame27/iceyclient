// Launcher language. The pages are written in English; this turns what
// they put on screen into the chosen language as it appears, so every
// page, dialog and toast is covered without each one asking for it.
//
// Text is matched whole (the English as shown, see i18n-strings.js);
// keys with {name}-style parts also match text built around a value,
// e.g. "Step 2 of 5". Anything without a translation stays English.
// The English original of every piece is remembered, so switching
// language, or back to English, needs no reload.
const I18N = {
  languages: [
    { code: 'en', name: 'English' },
    { code: 'de', name: 'Deutsch' },
    { code: 'es', name: 'Español' },
    { code: 'fr', name: 'Français' },
    { code: 'pt', name: 'Português' },
    { code: 'it', name: 'Italiano' },
    { code: 'nl', name: 'Nederlands' },
    { code: 'pl', name: 'Polski' },
    { code: 'tr', name: 'Türkçe' },
    { code: 'ru', name: 'Русский' }
  ],
  current: 'en',

  ATTRS: ['title', 'placeholder', 'data-tooltip'],
  // Never translated: game output, and anything a page marks as its own.
  SKIP: '.console-output, [data-no-i18n], script, style, textarea',

  _exact: new Map(),
  _patterns: [],
  _text: new WeakMap(),    // text node -> { orig, shown }
  _attrs: new WeakMap(),   // element   -> { attr: { orig, shown } }
  _observer: null,

  has(code) { return this.languages.some(l => l.code === code); },

  // The system language when the launcher has it, else English.
  detect() {
    const code = String(navigator.language || '').slice(0, 2).toLowerCase();
    return this.has(code) ? code : 'en';
  },

  _norm(s) { return String(s).replace(/\s+/g, ' ').trim(); },

  setLanguage(code) {
    this.current = this.has(code) ? code : 'en';
    this._exact = new Map();
    this._patterns = [];
    const dict = (typeof I18N_STRINGS !== 'undefined' && I18N_STRINGS[this.current]) || {};
    for (const [rawKey, rawVal] of Object.entries(dict)) {
      const key = this._norm(rawKey), val = this._norm(rawVal);
      if (!key) continue;
      if (!/\{\w+\}/.test(key)) { this._exact.set(key, val); continue; }
      const names = [];
      const source = key.replace(/[.*+?^$()|[\]\\]/g, '\\$&')
        .replace(/\{(\w+)\}/g, (_, n) => { names.push(n); return '(.+?)'; });
      this._patterns.push({ re: new RegExp('^' + source + '$'), names, val, weight: key.replace(/\{\w+\}/g, '').length });
    }
    // The most specific pattern first: "Added {name} (cracked)" before "Added {name}".
    this._patterns.sort((a, b) => b.weight - a.weight);
    document.documentElement.lang = this.current;
    if (document.body) this.translateTree(document.body);
  },

  _lookup(norm) {
    const hit = this._exact.get(norm);
    if (hit != null) return hit;
    // "Open setup ›": the arrow is decoration, not part of the words.
    if (norm.endsWith(' ›')) {
      const head = this._exact.get(norm.slice(0, -2));
      if (head != null) return head + ' ›';
    }
    for (const p of this._patterns) {
      const m = p.re.exec(norm);
      if (!m) continue;
      let out = p.val;
      p.names.forEach((name, i) => {
        const value = m[i + 1];
        const inner = this._exact.get(value);   // "Panel menu on. …": the value is words too
        out = out.split('{' + name + '}').join(inner != null ? inner : value);
      });
      return out;
    }
    return null;
  },

  // The given English in the current language, keeping its outer spaces.
  t(raw) {
    const text = String(raw);
    if (this.current === 'en') return text;
    const norm = this._norm(text);
    if (!norm) return text;
    const out = this._lookup(norm);
    if (out == null) return text;
    return text.match(/^\s*/)[0] + out + text.match(/\s*$/)[0];
  },

  _skipped(el) { return !el || !!(el.closest && el.closest(this.SKIP)); },

  _textNode(node) {
    if (this._skipped(node.parentElement)) return;
    const cur = node.nodeValue;
    let rec = this._text.get(node);
    // Still showing what we wrote: translate again from the English.
    // Anything else was written by the page: that is the new English.
    if (!rec || rec.shown !== cur) { rec = { orig: cur, shown: cur }; this._text.set(node, rec); }
    const out = this.t(rec.orig);
    rec.shown = out;
    if (out !== cur) node.nodeValue = out;
  },

  _attr(el, attr) {
    if (!el.hasAttribute(attr) || this._skipped(el)) return;
    const cur = el.getAttribute(attr);
    let all = this._attrs.get(el);
    if (!all) { all = {}; this._attrs.set(el, all); }
    let rec = all[attr];
    if (!rec || rec.shown !== cur) { rec = all[attr] = { orig: cur, shown: cur }; }
    const out = this.t(rec.orig);
    rec.shown = out;
    if (out !== cur) el.setAttribute(attr, out);
  },

  translateTree(root) {
    if (!root) return;
    if (root.nodeType === Node.TEXT_NODE) { this._textNode(root); return; }
    if (root.nodeType !== Node.ELEMENT_NODE) return;
    if (this._skipped(root)) return;
    for (const a of this.ATTRS) this._attr(root, a);
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walker.nextNode())) {
      if (n.nodeType === Node.TEXT_NODE) this._textNode(n);
      else for (const a of this.ATTRS) this._attr(n, a);
    }
  },

  start() {
    if (this._observer || !document.body) return;
    this._observer = new MutationObserver((mutations) => {
      for (const m of mutations) {
        if (m.type === 'childList') m.addedNodes.forEach(n => this.translateTree(n));
        else if (m.type === 'characterData') this._textNode(m.target);
        else if (m.type === 'attributes') this._attr(m.target, m.attributeName);
      }
    });
    this._observer.observe(document.body, {
      subtree: true, childList: true, characterData: true,
      attributes: true, attributeFilter: this.ATTRS
    });
  }
};

function t(text) { return I18N.t(text); }

I18N.start();
