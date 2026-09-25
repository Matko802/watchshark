async function me() {
  try {
    const r = await fetch('/api/me', { credentials: 'include' });
    return (await r.json()).user;
  } catch { return null; }
}
function isAdminView(u) {
  return !!u && !!u.admin && localStorage.getItem('ws_adminview') !== '0';
}
async function refreshAuth() {
  const u = await me();
  document.querySelectorAll('[data-auth]').forEach((el) => {
    const mode = el.dataset.auth;
    el.style.display = (mode === 'in' ? !!u : (mode === 'out' ? !u : true)) ? '' : 'none';
  });
  document.querySelectorAll('[data-admin]').forEach((el) => {
    el.style.display = isAdminView(u) ? '' : 'none';
  });
  const lbl = document.getElementById('whoami');
  if (lbl) lbl.innerHTML = u ? `<a class="who" href="/channel?user=${esc(u.username)}">${avatarHtml(u.avatar, 'pfp-sm')}<span>@${esc(u.username)}</span></a>` : '';
  checkBanStatus(u);
  return u;
}
function avatarHtml(file, cls) {
  if (!file) return `<md-icon class="${cls}">account_circle</md-icon>`;
  if (file.endsWith('.webm')) return `<video class="${cls}" src="${file}" autoplay loop muted playsinline></video>`;
  return `<img class="${cls}" src="${file}" alt="">`;
}
/** Online presence dot (green = active in last 5 min, grey = offline). */
function statusDot(online) {
  return `<span class="onlinedot${online ? ' on' : ''}"></span>`;
}
/** Avatar with presence dot overlay. */
function avatarStatusHtml(avatar, cls, online) {
  return `<span class="avwrap">${avatarHtml(avatar, cls)}${statusDot(online)}</span>`;
}
async function doLogout() {
  await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
  location.reload();
}
function openAuth(tab = 'login') {
  const d = document.getElementById('authDialog');
  if (!d) return;
  d.show();
  switchAuth(tab);
}
function currentMode() {
  return document.getElementById('loginPane').style.display === 'none' ? 'signup' : 'login';
}
function switchAuth(tab) {
  const login = tab === 'login';
  document.getElementById('loginPane').style.display = login ? '' : 'none';
  document.getElementById('signupPane').style.display = login ? 'none' : '';
  document.getElementById('auth-switch').textContent = login ? 'Create account' : 'Log in';
  document.getElementById('auth-go').textContent = login ? 'Log in' : 'Create account';
}
function toggleAuth() {
  switchAuth(currentMode() === 'login' ? 'signup' : 'login');
}
function submitForm(id) {
  const f = document.getElementById(id);
  if (!f) return false;
  if (typeof f.requestSubmit === 'function') { f.requestSubmit(); return true; }
  f.dispatchEvent(new Event('submit', { cancelable: true }));
  return true;
}
function authGo() {
  if (currentMode() === 'login') submitForm('loginPane'); else submitForm('signupPane');
}
function closeAuth() { document.getElementById('authDialog').close(); }
let authBusy = false;
async function storeCred(id, pw) {
  try {
    if (window.PasswordCredential && navigator.credentials && navigator.credentials.store) {
      await navigator.credentials.store(new PasswordCredential({ id: String(id), password: String(pw) }));
    }
  } catch { }
}
function submitLogin(e) { if (e) e.preventDefault(); doLogin(); return false; }
function submitSignup(e) { if (e) e.preventDefault(); doSignup(); return false; }
function submitChangePw(e) { if (e) e.preventDefault(); doChangePw(); return false; }
async function doLogin() {
  if (authBusy) return;
  authBusy = true;
  const body = {
    login: document.getElementById('login-id').value,
    password: document.getElementById('login-pw').value,
  };
  const r = await fetch('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify(body),
  });
  const j = await r.json();
  if (!r.ok) { document.getElementById('auth-err').textContent = j.error || 'Login failed'; authBusy = false; return; }
  await storeCred(body.login, body.password);
  location.reload();
}
async function doSignup() {
  if (authBusy) return;
  authBusy = true;
  const body = {
    username: document.getElementById('su-name').value,
    email: document.getElementById('su-email').value,
    password: document.getElementById('su-pw').value,
  };
  const r = await fetch('/api/auth/signup', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify(body),
  });
  const j = await r.json();
  if (!r.ok) { document.getElementById('auth-err').textContent = j.error || 'Signup failed'; authBusy = false; return; }
  await storeCred(body.username, body.password);
  location.reload();
}
const Pjax = {
  cleanups: [],
  on(t, ev, fn, opt) {
    t.addEventListener(ev, fn, opt);
    this.cleanups.push(() => { try { t.removeEventListener(ev, fn, opt); } catch {} });
  },
  timeout(fn, ms) {
    const id = setTimeout(fn, ms);
    this.cleanups.push(() => clearTimeout(id));
    return id;
  },
  cleanup() {
    while (this.cleanups.length) {
      try { this.cleanups.pop()(); } catch {}
    }
  }
};
let pjaxBusy = false;
function pjaxAppJs(doc) {
  const s = doc.querySelector('script[src*="/app.js"]');
  return s ? s.getAttribute('src') : '';
}
function currentAppJs() {
  const s = document.querySelector('script[src*="/app.js"]');
  return s ? s.getAttribute('src') : '';
}
async function pjaxHead(doc) {
  const loads = [];
  doc.querySelectorAll('head script[src]').forEach((s) => {
    const src = s.getAttribute('src');
    if (src && !document.querySelector('head script[src="' + src + '"]')) {
      loads.push(new Promise((res) => {
        const el = document.createElement('script');
        el.src = src;
        el.async = false;
        el.onload = res;
        el.onerror = res;
        document.head.appendChild(el);
      }));
    }
  });
  doc.querySelectorAll('head link[rel="stylesheet"]').forEach((l) => {
    const href = l.getAttribute('href');
    if (href && !document.querySelector('head link[href="' + href + '"]')) {
      const el = document.createElement('link');
      el.rel = 'stylesheet';
      el.href = href;
      loads.push(new Promise((res) => {
        el.onload = res;
        el.onerror = res;
      }));
      document.head.appendChild(el);
    }
  });
  await Promise.all(loads);
}
async function pjaxSwap(url, push) {
  if (pjaxBusy) { location.href = url; return false; }
  pjaxBusy = true;
  pjaxBar(true);
  try {
    let html;
    try {
      const r = await fetch(url, { headers: { 'X-Requested-With': 'fetch' } });
      const ct = r.headers.get('Content-Type') || '';
      if (!r.ok || !ct.includes('text/html')) throw 0;
      html = await r.text();
    } catch { location.href = url; return false; }
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const codes = [];
    doc.querySelectorAll('body script:not([src])').forEach((s) => codes.push(s.textContent));
    if (doc.body.getAttribute('data-pjax') !== '1' || !codes.length || pjaxAppJs(doc) !== currentAppJs()) {
      location.href = url;
      return false;
    }
    try { history.replaceState({ url: location.href, scroll: window.scrollY }, ''); } catch {}
    if (push) {
      try { history.pushState({ url, scroll: 0 }, '', url); } catch { location.href = url; return false; }
      window.scrollTo(0, 0);
    }
    try { if (typeof window.__destroy === 'function') window.__destroy(); } catch {}
    Pjax.cleanup();
    await pjaxHead(doc);
    try {
      document.querySelectorAll('video').forEach((v) => { try { v.pause(); v.removeAttribute('src'); v.load(); } catch {} });
    } catch {}
    delete window.__boot;
    delete window.__destroy;
    document.title = doc.title;
    document.body.className = doc.body.className;
    document.body.innerHTML = doc.body.innerHTML;
    hideBoot();
    applyBlurPref();
    ensureSidebar();
    await refreshAuth();
    initBell();
    initDmButton();
    for (const code of codes) {
      const el = document.createElement('script');
      el.textContent = code;
      document.body.appendChild(el);
      el.remove();
    }
    if (typeof window.__boot === 'function') window.__boot();
    return true;
  } finally {
    pjaxBusy = false;
    pjaxBar(false);
  }
}
function hideBoot() {
  const b = document.getElementById('bootloader');
  if (b) b.remove();
}
/** Blur effects toggle (translucent bars); default on. */
function applyBlurPref() {
  try {
    document.body.classList.toggle('no-blur', localStorage.getItem('ws_blur') === '0');
  } catch {}
}
applyBlurPref();
document.addEventListener('DOMContentLoaded', () => setTimeout(hideBoot, 1500));
/** Desktop left nav like YouTube (injected, desktop widths only). */
function ensureSidebar() {
  if (window.innerWidth < 1000) return;
  const bare = ['/forgot', '/reset', '/verify'].some((p) => location.pathname.startsWith(p));
  if (bare) return;
  if (document.getElementById('sidebar')) {
    markSidebar();
    return;
  }
  const aside = document.createElement('aside');
  aside.id = 'sidebar';
  aside.innerHTML = `<nav>
    <a href="/" data-side="home"><md-icon>home</md-icon><span>Home</span></a>
    <a href="/wheels" data-side="wheels"><md-icon>movie</md-icon><span>Wheels</span></a>
    <a href="/music" data-side="music"><md-icon>music_note</md-icon><span>Music</span></a>
  </nav>`;
  document.body.appendChild(aside);
  document.body.classList.add('has-sidebar');
  markSidebar();
  if (typeof refreshAuth === 'function') refreshAuth();
}
function markSidebar() {
  const path = location.pathname;
  const tab = path === '/' ? 'home'
    : path === '/wheels' ? 'wheels'
    : path === '/music' ? 'music'
    : (path === '/settings' || path === '/admin') ? 'account' : '';
  document.querySelectorAll('#sidebar [data-side]').forEach((a) => {
    a.classList.toggle('active', a.dataset.side === tab);
  });
}
document.addEventListener('DOMContentLoaded', () => { ensureSidebar(); applyBlurPref(); });
function pjaxBar(show) {  let bar = document.getElementById('pjaxbar');
  if (show) {
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'pjaxbar';
      document.body.appendChild(bar);
    }
    bar.style.display = '';
  } else if (bar) {
    bar.style.display = 'none';
  }
}
async function pjaxGo(url) {
  await pjaxSwap(url, true);
}
document.addEventListener('click', (e) => {
  if (e.defaultPrevented || e.button !== 0) return;
  if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
  const t = e.target && e.target.closest ? e.target.closest('a[href],md-text-button[href],md-filled-button[href],md-outlined-button[href],md-filled-tonal-button[href],md-icon-button[href],md-assist-chip[href]') : null;
  if (!t) return;
  const href = t.getAttribute('href');
  if (!href) return;
  let url;
  try { url = new URL(href, location.href); } catch { return; }
  if (url.origin !== location.origin) return;
  if (url.pathname.startsWith('/api/')) return;
  e.preventDefault();
  if (url.href === location.href) {
    pjaxSwap(url.href, false);
    return;
  }
  pjaxGo(url.href);
});
window.addEventListener('popstate', async (e) => {
  const url = (e.state && e.state.url) || location.href;
  if (await pjaxSwap(url, false)) window.scrollTo(0, (e.state && e.state.scroll) || 0);
});
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}
/** Server timestamps are UTC ("YYYY-MM-DD HH:MM:SS") — anchor them so the client converts to local time. */
function utcMs(s) {
  const t = String(s).replace(' ', 'T').replace(/Z$/, '') + 'Z';
  const ms = Date.parse(t);
  return isNaN(ms) ? NaN : ms;
}
function fmtAge(s) {
  const t = utcMs(s);
  if (isNaN(t)) return String(s);
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000));
  if (sec < 60) return sec <= 1 ? '1 second ago' : sec + ' seconds ago';
  const m = Math.floor(sec / 60);
  if (m < 60) return m === 1 ? '1 minute ago' : m + ' minutes ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h === 1 ? '1 hour ago' : h + ' hours ago';
  const d = Math.floor(h / 24);
  if (d < 7) return d === 1 ? '1 day ago' : d + ' days ago';
  if (d < 30) {
    const w = Math.floor(d / 7);
    return w === 1 ? '1 week ago' : w + ' weeks ago';
  }
  const mo = Math.floor(d / 30);
  if (mo < 12) return mo === 1 ? '1 month ago' : mo + ' months ago';
  const y = Math.floor(mo / 12);
  return y === 1 ? '1 year ago' : y + ' years ago';
}
function fmtDate(s) {
  const t = utcMs(s);
  if (isNaN(t)) return String(s);
  return new Date(t).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}
function fmtAgeDual(s) {
  const t = utcMs(s);
  if (isNaN(t)) return String(s);
  return fmtAge(s) + ' · ' + fmtDate(s);
}
function fmtNum(n) {
  n = Number(n) || 0;
  if (n < 1000) return String(n);
  const units = [[1e9, 'B'], [1e6, 'M'], [1e3, 'K']];
  for (const [v, s] of units) {
    if (n >= v) {
      const x = n / v;
      return (x >= 100 ? String(Math.round(x)) : x.toFixed(1).replace(/\.0$/, '')) + s;
    }
  }
  return String(n);
}
function fmtFull(n) {
  return (Number(n) || 0).toLocaleString('en-US');
}
function submitForgot(e) { if (e) e.preventDefault(); doForgot(); return false; }
async function doForgot() {
  const email = document.getElementById('forgot-email').value;
  const msg = document.getElementById('forgot-msg');
  const r = await fetch('/api/auth/forgot', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  });
  await r.json();
  msg.textContent = 'If that email has an account, a reset link is on its way.';
}
async function doResendEmail() {
  const email = document.getElementById('forgot-email').value;
  const msg = document.getElementById('forgot-msg');
  const r = await fetch('/api/auth/resend', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  });
  await r.json();
  msg.textContent = 'If that email has an unconfirmed account, a fresh link is on its way.';
}
function submitForgot(e) { if (e) e.preventDefault(); doForgot(); return false; }
async function doChangePw() {
  const cur = document.getElementById('cp-cur').value;
  const password = document.getElementById('cp-new').value;
  const msg = document.getElementById('cp-msg');
  const r = await fetch('/api/auth/change', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ current: cur, password }),
  });
  const j = await r.json();
  msg.textContent = r.ok ? 'Password changed.' : (j.error || 'Failed');
}
async function doChangePw() {
  const cur = document.getElementById('cp-cur').value;
  const password = document.getElementById('cp-new').value;
  const msg = document.getElementById('cp-msg');
  const r = await fetch('/api/auth/change', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ current: cur, password }),
  });
  const j = await r.json();
  msg.textContent = r.ok ? 'Password changed.' : (j.error || 'Failed');
}
let BELL = { unread: 0, items: [] };
async function initBell() {
  const slot = document.getElementById('bellSlot');
  if (!slot) return;
  slot.innerHTML = `
    <span class="bellwrap">
      <md-icon-button id="bellBtn" aria-label="Notifications"><md-icon id="bellIcon">notifications</md-icon></md-icon-button>
      <span id="bellBadge" class="badge" style="display:none"></span>
    </span>
    <md-menu id="bellMenu" anchor="bellBtn" positioning="popover">
      <div id="paneUploads" class="notifpane"></div>
    </md-menu>`;
  document.getElementById('bellBtn').addEventListener('click', async () => {
    await refreshBellData();
    renderBell();
    document.getElementById('bellMenu').show();
  });
  refreshBellBadge();
}
async function refreshBellData() {
  try {
    const r = await fetch('/api/notifications', { credentials: 'include' });
    if (!r.ok) return;
    const j = await r.json();
    BELL.unread = j.unread;
    BELL.items = j.notifications;
  } catch { return; }
}
async function refreshBellBadge() {
  const badge = document.getElementById('bellBadge');
  if (!badge) return;
  await refreshBellData();
  const n = BELL.unread;
  badge.style.display = n ? '' : 'none';
  badge.textContent = n > 9 ? '9+' : String(n);
  document.getElementById('bellIcon').textContent = n ? 'notifications_active' : 'notifications';
}
function renderBell() {
  const badge = document.getElementById('bellBadge');
  const n = BELL.unread;
  badge.style.display = n ? '' : 'none';
  badge.textContent = n > 9 ? '9+' : String(n);
  const pu = document.getElementById('paneUploads');
  pu.innerHTML = BELL.items.length
    ? '<div class="pad"><md-text-button onclick="markAllRead()">Mark all read</md-text-button></div><md-list>' + BELL.items.map((x) =>
      x.kind === 'delete'
        ? `<md-list-item class="${x.read ? '' : 'unread'}" onclick="openNotif(${x.id},0)">`
          + `<div slot="headline">Video removed: ${esc(x.title)}</div>`
          + `<div slot="supporting-text">${esc(x.text)}</div></md-list-item>`
        : `<md-list-item class="${x.read ? '' : 'unread'}" onclick="openNotif(${x.id},${x.video_id})">`
          + `<div slot="headline">${esc(x.username)} uploaded: ${esc(x.title)}</div>`
          + `<div slot="supporting-text">${esc(x.created_at)}</div></md-list-item>`).join('') + '</md-list>'
    : '<p class="muted pad">No notifications yet. Follow channels to get upload alerts.</p>';
}
async function openNotif(id, vid) {
  await fetch('/api/notifications/read', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ id }),
  });
  if (vid) {
    location.href = '/watch?id=' + vid;
    return;
  }
  await refreshBellData();
  renderBell();
}
async function markAllRead() {
  await fetch('/api/notifications/read', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({}),
  });
  await refreshBellData();
  renderBell();
}
async function adminApprove(id) {
  await fetch('/api/admin/approve', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ id }),
  });
  await refreshBellData();
  renderBell();
  if (typeof loadAdmin === 'function') loadAdmin();
}
async function adminReject(id) {
  if (!confirm('Reject this account?')) return;
  await fetch('/api/admin/reject', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ id }),
  });
  await refreshBellData();
  renderBell();
  if (typeof loadAdmin === 'function') loadAdmin();
}
function b64enc(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}
function b64dec(b64) {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}
function cmpBytes(a, b) {
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    const d = a[i] - b[i];
    if (d !== 0) return d;
  }
  return a.length - b.length;
}
async function dmKeypair() {
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem('ws_dm_priv') || 'null'); } catch {}
  if (saved && saved.priv && saved.pub) {
    const priv = await crypto.subtle.importKey(
      'jwk', saved.priv, { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
    return { priv, pubRaw: b64dec(saved.pub) };
  }
  const pair = await crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
  const pubRaw = new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey));
  try {
    localStorage.setItem('ws_dm_priv', JSON.stringify({
      priv: await crypto.subtle.exportKey('jwk', pair.privateKey),
      pub: b64enc(pubRaw),
    }));
  } catch {}
  return { priv: pair.privateKey, pubRaw };
}
async function dmSharedKey(priv, myRaw, peerB64) {
  const peerRaw = b64dec(peerB64);
  const peerKey = await crypto.subtle.importKey(
    'raw', peerRaw, { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const z = new Uint8Array(await crypto.subtle.deriveBits(
    { name: 'ECDH', public: peerKey }, priv, 256));
  const sorted = [myRaw, peerRaw].sort(cmpBytes);
  const info = new Uint8Array(8 + 65 + 65);
  info.set(new TextEncoder().encode('ws-dm-v1'), 0);
  info.set(sorted[0], 8);
  info.set(sorted[1], 73);
  const hkdfKey = await crypto.subtle.importKey('raw', z, 'HKDF', false, ['deriveBits']);
  const okm = await crypto.subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info }, hkdfKey, 256);
  return crypto.subtle.importKey('raw', okm, 'AES-GCM', false, ['encrypt', 'decrypt']);
}
async function dmEncrypt(peerB64, text) {
  const { priv, pubRaw } = await dmKeypair();
  const key = await dmSharedKey(priv, pubRaw, peerB64);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const body = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce }, key, new TextEncoder().encode(text)));
  return { nonce: b64enc(nonce), body: b64enc(body) };
}
async function dmDecrypt(peerB64, nonceB64, bodyB64) {
  const { priv, pubRaw } = await dmKeypair();
  const key = await dmSharedKey(priv, pubRaw, peerB64);
  const pt = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: b64dec(nonceB64) }, key, b64dec(bodyB64));
  return new TextDecoder().decode(pt);
}
async function dmEnsureUploaded() {
  try {
    if (localStorage.getItem('ws_dm_up') === '1') return true;
    const { pubRaw } = await dmKeypair();
    const r = await fetch('/api/dm/key', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      credentials: 'include', body: JSON.stringify({ pubkey: b64enc(pubRaw) }),
    });
    if (!r.ok) return false;
    localStorage.setItem('ws_dm_up', '1');
    return true;
  } catch { return false; }
}
async function initDmButton() {
  const slot = document.getElementById('bellSlot');
  if (!slot || document.getElementById('dmBtn')) return;
  let u = null;
  try { u = await me(); } catch {}
  if (!u) return;
  const a = document.createElement('md-icon-button');
  a.id = 'dmBtn';
  a.setAttribute('href', '/messages');
  a.setAttribute('aria-label', 'Messages');
  a.innerHTML = '<md-icon>forum</md-icon>';
  slot.before(a);
}
function banMessage(u) {
  if (!u) return null;
  if (u.deleted) {
    let t = 'Your account has been deleted.';
    if (u.deleted_reason) t += '\nReason: ' + u.deleted_reason;
    return t;
  }
  if (u.banned) {
    const r = u.ban_reason ? '\nReason: ' + u.ban_reason : '';
    if (u.ban_days_left != null && u.ban_days_left < 0) return 'You have been banned permanently.' + r;
    let d = Number(u.ban_days_left);
    if (!isFinite(d)) d = 0;
    const ds = d < 1 ? d.toFixed(2) : d.toFixed(1);
    return 'You have been banned for ' + ds + ' days.' + r;
  }
  return null;
}
function checkBanStatus(u) {
  const msg = banMessage(u);
  let ov = document.getElementById('banOverlay');
  if (!msg) {
    if (ov) ov.remove();
    return;
  }
  if (!ov) {
    ov = document.createElement('div');
    ov.id = 'banOverlay';
    ov.innerHTML = `<div class="ban-card">
      <md-icon class="ban-icon">block</md-icon>
      <h2 id="banTitle">Account restricted</h2>
      <p id="banText"></p>
      <md-filled-button id="banClose">Close</md-filled-button>
    </div>`;
    document.body.appendChild(ov);
    document.getElementById('banClose').addEventListener('click', () => {
      ov.style.display = 'none';
    });
    ov.addEventListener('click', (e) => {
      if (e.target === ov) ov.style.display = 'none';
    });
  } else {
    ov.style.display = '';
  }
  const txt = document.getElementById('banText');
  if (txt) txt.innerText = msg;
}
function isBlockedUser(u) {
  return !!(u && (u.deleted || u.banned));
}
document.addEventListener('DOMContentLoaded', () => {
  try {
    if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
    history.replaceState({ url: location.href, scroll: 0 }, '');
  } catch {}
  refreshAuth();
  initBell();
  initDmButton();
  setInterval(refreshBellBadge, 60000);
});
