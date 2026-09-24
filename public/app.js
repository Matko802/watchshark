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
    await refreshAuth();
    initBell();
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
document.addEventListener('DOMContentLoaded', () => setTimeout(hideBoot, 1500));
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
function fmtAge(s) {
  const t = Date.parse(String(s).replace(' ', 'T').replace(/Z$/, ''));
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
  const t = Date.parse(String(s).replace(' ', 'T').replace(/Z$/, ''));
  if (isNaN(t)) return String(s);
  return new Date(t).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}
function fmtAgeDual(s) {
  const t = Date.parse(String(s).replace(' ', 'T').replace(/Z$/, ''));
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
  setInterval(refreshBellBadge, 60000);
});
