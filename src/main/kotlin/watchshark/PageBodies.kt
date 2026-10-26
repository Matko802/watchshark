package watchshark

object PageBodies {
    val ABOUT_MAIN = """
  <div class="about-hero">
    <img src="/watchshark.webp?v=2" alt="WatchShark logo">
    <div>
      <h2>WatchShark</h2>
      <p class="muted" style="margin:4px 0 0">Your own video platform. No ads, no tracking, your server, your rules.</p>
    </div>
  </div>
  <div class="about-grid">
    <md-elevated-card class="about-card">
      <h3><md-icon>movie</md-icon>What is it</h3>
      <p>WatchShark is a self-hosted place to watch and share video: full-length <b>videos</b> and vertical <b>wheels</b> you swipe through.</p>
      <p>Follow creators, get upload alerts, and chat over <b>messages</b> — DMs only ever open between mutual friends.</p>
      <p class="muted">Everything runs on this server. Your account, uploads and messages stay here.</p>
    </md-elevated-card>
    <md-elevated-card class="about-card">
      <h3><md-icon>smartphone</md-icon>Android app</h3>
      <p>Get the native app for the smoothest watching, with built-in update checks.</p>
      <div class="dlrow">
        <md-filled-button id="apkBtn" href="https://github.com/Matko802/watchshark/releases/latest" disabled><md-icon slot="icon">download</md-icon><span id="apkLbl">Finding latest version…</span></md-filled-button>
      </div>
      <p class="muted" id="apkNote" style="font-size:13px"></p>
    </md-elevated-card>
    <md-elevated-card class="about-card">
      <h3><md-icon>code</md-icon>Source code</h3>
      <p>WatchShark is open source. The server, the web UI and the Android app all live in one repo.</p>
      <div class="dlrow">
        <md-filled-tonal-button href="https://github.com/Matko802/watchshark"><svg class="ghicon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/></svg>GitHub repo</md-filled-tonal-button>
      </div>
      <p class="muted" style="font-size:13px">github.com/Matko802/watchshark</p>
    </md-elevated-card>
  </div>"""
    val ABOUT_CSS = """
.about-hero{display:flex;align-items:center;gap:16px;margin:8px 0 20px}
.about-hero img{width:72px;height:72px;border-radius:20px}
.about-hero h2{margin:0}
.about-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px;margin-top:8px}
.about-card{padding:20px}
.about-card h3{margin:0 0 8px;display:flex;align-items:center;gap:8px}
.about-card p{margin:8px 0}
.ghicon{width:22px;height:22px;fill:currentColor;flex:none}
.dlrow{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-top:12px}"""
    val ABOUT_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val ABOUT_TAIL = """
(function() {
  const btn = document.getElementById('apkBtn');
  const lbl = document.getElementById('apkLbl');
  const note = document.getElementById('apkNote');
  function fmtSize(n) {
    if (!n || n <= 0) return '';
    return n >= 1048576 ? Math.round(n / 1048576) + ' MB' : Math.round(n / 1024) + ' KB';
  }
  fetch('/api/app/latest').then((r) => r.json()).then((j) => {
    if (j && j.apk_url) {
      btn.href = j.apk_url;
      btn.removeAttribute('disabled');
      lbl.textContent = 'Download for Android' + (j.version ? ' • v' + j.version : '') + (j.size ? ' • ' + fmtSize(j.size) : '');
      note.textContent = 'Direct APK from the latest release' + (j.tag ? ' (' + j.tag + ')' : '') + '.';
    } else {
      throw 0;
    }
  }).catch(() => {
    btn.removeAttribute('disabled');
    lbl.textContent = 'Download for Android';
    note.textContent = 'Grab the newest APK from the releases page.';
  });
})();
hideBoot();"""
    val ADMIN_MAIN = """
  <div id="gate"><md-linear-progress indeterminate></md-linear-progress></div>
  <div id="body" style="display:none">
    <h2>All accounts <span id="ucount" class="muted"></span></h2>
    <md-list id="users"></md-list>
  </div>"""
    val ADMIN_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
      const u = new URLSearchParams(location.search).get('user');
      if (path.startsWith('/channel') && u === j.user.username) mark('account');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val ADMIN_TAIL = """
async function loadAdmin() {
  const u = await refreshAuth();
  if (!u || !u.admin) {
    document.getElementById('gate').innerHTML = '<p class="muted">Admins only.</p>';
    return;
  }
  document.getElementById('gate').style.display = 'none';
  document.getElementById('body').style.display = '';
  const us = await (await fetch('/api/admin/users', { credentials: 'include' })).json();
  document.getElementById('ucount').textContent = us.users.length ? `(${'$'}{us.users.length})` : '';
  document.getElementById('users').innerHTML = us.users.map((x) => {
    let status = x.deleted ? 'deleted' : (x.banned ? 'banned' : (x.verified ? 'active' : 'pending'));
    let extra = '';
    if (x.deleted && x.deleted_reason) extra = ' • ' + esc(x.deleted_reason);
    else if (x.banned) {
      const dl = x.ban_days_left < 0 ? 'permanent' : (Number(x.ban_days_left).toFixed(1) + 'd left');
      extra = ' • ' + dl + (x.ban_reason ? ' • ' + esc(x.ban_reason) : '');
    }
    const isDel = !!x.deleted;
    const isBan = !!x.banned && !isDel;
    const isPending = !x.verified && !isDel;
    const banForm = (isDel || isBan) ? '' : `
          <div class="nfield">
            <label for="bd-${'$'}{x.id}">Ban days</label>
            <input placeholder=" " id="bd-${'$'}{x.id}" type="number" min="0" step="0.5" value="7">
          </div>
          <div class="nfield">
            <label for="bh-${'$'}{x.id}">Ban hours</label>
            <input placeholder=" " id="bh-${'$'}{x.id}" type="number" min="0" max="23" step="1" value="0">
          </div>
          <div class="nfield">
            <label for="br-${'$'}{x.id}">Reason</label>
            <input placeholder=" " id="br-${'$'}{x.id}" type="text">
          </div>
          <label class="perm"><input id="bp-${'$'}{x.id}" type="checkbox"> permanent ban</label>`;
    let actions = `<md-text-button onclick="document.getElementById('adlg-${'$'}{x.id}').close()">Close</md-text-button>`;
    if (isDel) {
      actions = `<md-text-button onclick="restoreUser(${'$'}{x.id})">Restore</md-text-button>`
        + actions
        + `<md-text-button onclick="delUser(${'$'}{x.id})">Remove</md-text-button>`;
    } else if (isBan) {
      actions = `<md-text-button onclick="unbanUser(${'$'}{x.id})">Unban</md-text-button>`
        + actions
        + `<md-text-button onclick="softDelUser(${'$'}{x.id})">Delete</md-text-button>`
        + `<md-text-button onclick="delUser(${'$'}{x.id})">Remove</md-text-button>`;
    } else {
      if (isPending) actions = `<md-filled-button onclick="approveUser(${'$'}{x.id})">Approve</md-filled-button>` + actions;
      actions = actions
        + `<md-text-button onclick="softDelUser(${'$'}{x.id})">Delete</md-text-button>`
        + `<md-text-button onclick="delUser(${'$'}{x.id})">Remove</md-text-button>`
        + `<md-filled-button onclick="banUser(${'$'}{x.id})">Ban</md-filled-button>`;
    }
    const controls = x.role === 'admin' ? '' : `
      <md-icon-button aria-label="Manage user" onclick="document.getElementById('adlg-${'$'}{x.id}').show()"><md-icon>settings</md-icon></md-icon-button>
      <md-dialog id="adlg-${'$'}{x.id}">
        <div slot="headline">Manage @${'$'}{esc(x.username)}</div>
        <div slot="content">
          <p class="muted">${'$'}{status}${'$'}{extra ? ' • ' + extra : ''}</p>
          ${'$'}{banForm}
        </div>
        <div slot="actions">
          ${'$'}{actions}
        </div>
      </md-dialog>`;
    return `<md-list-item><div slot="start">${'$'}{avatarStatusHtml(x.avatar, 'pfp-sm', x.online)}</div><div slot="headline"><a class="ulink" href="/channel?user=${'$'}{encodeURIComponent(x.username)}">${'$'}{esc(x.username)}</a>${'$'}{x.role === 'admin' ? ' ⭐' : ''}</div>`
      + `<div slot="supporting-text">${'$'}{status} • ${'$'}{esc(x.created_at)}${'$'}{extra}</div>`
      + `<div slot="end">${'$'}{controls}</div></md-list-item>`;
  }).join('');
}
async function banUser(id) {
  const days = parseFloat((document.getElementById('bd-' + id) || {}).value || '0');
  const hours = parseFloat((document.getElementById('bh-' + id) || {}).value || '0');
  const reason = ((document.getElementById('br-' + id) || {}).value || '').trim();
  const permanent = !!((document.getElementById('bp-' + id) || {}).checked);
  if (!permanent && !(days > 0 || hours > 0)) { alert('Give days/hours or tick perm'); return; }
  if (!reason) { if (!confirm('Ban without reason?')) return; }
  const r = await fetch('/api/admin/ban', { method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id, days, hours, reason, permanent }) });
  if (!r.ok) alert((await r.json()).error || 'Ban failed');
  loadAdmin();
}
async function unbanUser(id) {
  await fetch('/api/admin/unban', { method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }) });
  loadAdmin();
}
async function approveUser(id) {
  const r = await fetch('/api/admin/approve', { method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }) });
  if (!r.ok) alert((await r.json()).error || 'Approve failed');
  loadAdmin();
}
async function softDelUser(id) {
  const reason = prompt('Delete reason (shown to user):') || '';
  if (!confirm('Delete this account (user will see "account deleted" warning, videos removed)?')) return;
  const r = await fetch('/api/admin/soft-delete', { method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id, reason }) });
  if (!r.ok) alert((await r.json()).error || 'Delete failed');
  loadAdmin();
}
async function restoreUser(id) {
  await fetch('/api/admin/restore', { method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }) });
  loadAdmin();
}
async function delUser(id) {
  if (!confirm('Permanently remove this account and all its videos?')) return;
  const r = await fetch('/api/admin/users/' + id, { method: 'DELETE', credentials: 'include' });
  if (!r.ok) alert((await r.json()).error);
  loadAdmin();
}
loadAdmin();"""
    val CHANNEL_MAIN = """
  <div id="gate"><md-linear-progress indeterminate></md-linear-progress></div>
  <div id="body" style="display:none">
    <div class="row">
      <span id="cpic"></span>
      <div>
        <h2 id="cname" style="margin:0"></h2>
        <p class="muted" id="csub" style="margin:4px 0"></p>
        <p class="muted" id="cmore" style="display:none;margin:4px 0"></p>
        <md-text-button id="cmoreBtn" onclick="toggleMore()">...more</md-text-button>
      </div>
      <span class="spacer"></span>
      <md-outlined-button id="cmessage" onclick="messageChannel()" style="display:none"><md-icon slot="icon">chat</md-icon>Message</md-outlined-button>
      <md-filled-tonal-button id="cfollow" onclick="toggleChanFollow()" style="display:none"></md-filled-tonal-button>
    </div>
    <div class="segtabs" id="chanTabs" role="tablist" aria-label="Channel sections">
      <span class="segind"></span>
      <button class="segbtn on" data-kind="video"><md-icon>play_circle</md-icon>Videos</button>
      <button class="segbtn" data-kind="wheel"><md-icon>movie</md-icon>Wheels</button>
    </div>
    <div id="grid" class="grid"></div>
  </div>"""
    val CHANNEL_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
      const u = new URLSearchParams(location.search).get('user');
      if (path.startsWith('/channel') && u === j.user.username) mark('account');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val CHANNEL_TAIL = """
{
window.__boot = function() {
let CHAN = null;
let CHANVIDS = [];
let chanTab = 'video';
const chanSeg = segTabs(document.getElementById('chanTabs'), (btn) => {
  chanTab = btn.dataset.kind || 'video';
  renderChanGrid();
});
function chanCard(v) {
  const media = v.thumbnail
    ? `<img class="media" src="${'$'}{v.thumbnail}" loading="lazy" alt="">`
    : `<video class="media" src="${'$'}{v.src}#t=0.5" preload="metadata" muted></video>`;
  const a = document.createElement('a');
  a.className = 'cardlink'; a.href = '/watch?id=' + v.id;
  a.innerHTML = `<article class="m3card">${'$'}{media}<div class="meta"><div class="metatext"><h3>${'$'}{esc(v.title)}</h3>`
    + `<small>${'$'}{fmtNum(v.views)} views • <span title="${'$'}{esc(fmtDate(v.created_at))}">${'$'}{fmtAge(v.created_at)}</span></small></div></div></article>`;
  return a;
}
function wheelCard(v) {
  const media = v.thumbnail
    ? `<img class="wmedia" src="${'$'}{v.thumbnail}" loading="lazy" alt="">`
    : `<video class="wmedia" src="${'$'}{v.src}#t=0.5" preload="metadata" muted></video>`;
  const a = document.createElement('a');
  a.className = 'cardlink'; a.href = '/watch?id=' + v.id;
  a.innerHTML = `<article class="m3card wcard">${'$'}{media}<div class="wmeta"><h3>${'$'}{esc(v.title)}</h3>`
    + `<small>${'$'}{fmtNum(v.views)} views</small></div></article>`;
  return a;
}
function chanKind(v) {
  return v.kind || 'video';
}
function renderChanGrid() {
  const g = document.getElementById('grid');
  g.innerHTML = '';
  g.className = chanTab === 'wheel' ? 'grid wgrid' : 'grid';
  const list = CHANVIDS.filter((v) => chanKind(v) === chanTab);
  if (!list.length) {
    g.innerHTML = chanTab === 'wheel' ? '<p class="muted">No wheels yet.</p>' : '<p class="muted">No videos yet.</p>';
    return;
  }
  for (const v of list) g.appendChild(chanTab === 'wheel' ? wheelCard(v) : chanCard(v));
}
async function initChannel() {
  const name = new URLSearchParams(location.search).get('user') || '';
  await refreshAuth();
  if (!name) {
    document.getElementById('gate').innerHTML = '<p class="muted">No channel given.</p>';
    return;
  }
  document.title = '@' + name + ' — WatchShark';
  const r = await fetch('/api/channel/' + encodeURIComponent(name));
  if (!r.ok) {
    document.getElementById('gate').innerHTML = '<p class="muted">Channel not found.</p>';
    return;
  }
  const j = await r.json();
  CHAN = j.user;
  document.getElementById('gate').style.display = 'none';
  document.getElementById('body').style.display = '';
  document.getElementById('cpic').innerHTML = avatarStatusHtml(CHAN.avatar, 'pfp-lg', CHAN.online);
  document.getElementById('cname').textContent = CHAN.username;
  document.getElementById('csub').textContent = '@' + CHAN.username + ' • ' + fmtNum(CHAN.followers) + ' followers • ' + CHAN.videos + ' videos';
  document.getElementById('cmore').textContent = fmtFull(CHAN.views) + ' total views • joined ' + CHAN.created_at;
  renderChanFollow();
  CHANVIDS = j.videos || [];
  const counts = CHAN.counts || { video: 1, wheel: 1 };
  const wheelBtn = document.querySelector('#chanTabs [data-kind="wheel"]');
  const videoBtn = document.querySelector('#chanTabs [data-kind="video"]');
  if (wheelBtn) wheelBtn.style.display = counts.wheel ? '' : 'none';
  if (!counts.video && counts.wheel && wheelBtn) { chanTab = 'wheel'; if (chanSeg) chanSeg.pick(wheelBtn); }
  else { chanTab = 'video'; if (chanSeg && videoBtn) chanSeg.pick(videoBtn, true); renderChanGrid(); }
}
function messageChannel() {
  if (typeof CHAN !== "undefined" && CHAN) location.href = '/messages?user=' + encodeURIComponent(CHAN.username);
}
async function renderChanFollow() {
  const b = document.getElementById('cfollow');
  const mb = document.getElementById('cmessage');
  const u = await me();
  if (!u || (CHAN && u.id === CHAN.id)) { b.style.display = 'none'; if (mb) mb.style.display = 'none'; return; }
  b.style.display = '';
  if (mb) mb.style.display = '';
  b.innerHTML = `<md-icon slot="icon">${'$'}{CHAN.following ? 'how_to_reg' : 'person_add'}</md-icon>${'$'}{CHAN.following ? 'Following' : 'Follow'}`;
}
async function toggleChanFollow() {
  const r = await fetch('/api/follow/' + CHAN.id, { method: 'POST', credentials: 'include' });
  const j = await r.json();
  if (!r.ok) return;
  CHAN.following = j.following;
  CHAN.followers = j.followers;
  renderChanFollow();
}
function toggleMore() {
  const m = document.getElementById('cmore');
  const open = m.style.display === 'none';
  m.style.display = open ? '' : 'none';
  document.getElementById('cmoreBtn').textContent = open ? 'Show less' : '...more';
}
initChannel();
Object.assign(window, { toggleChanFollow, toggleMore, messageChannel });
};
window.__boot();
hideBoot();

Object.assign(window, { toggleMore, messageChannel, toggleChanFollow });
}"""
    val FORGOT_MAIN = """
  <h2>Trouble signing in?</h2>
  <p class="muted">Enter your account email. We send a reset link, or a fresh confirmation link if you never confirmed.</p>
  <form method="post" action="/api/auth/forgot" onsubmit="return submitForgot(event)">
    <div class="nfield">
      <label for="forgot-email">Email</label>
      <input placeholder=" " id="forgot-email" name="email" type="email" autocomplete="email" autocapitalize="none" autocorrect="off" spellcheck="false" required>
    </div>
    <button type="submit" tabindex="-1" aria-hidden="true" style="position:fixed;top:0;left:0;width:8px;height:8px;opacity:.01;pointer-events:none;border:0;padding:0"></button>
  </form>
  <div class="row">
    <md-filled-button onclick="doForgot()"><md-icon slot="icon">mail</md-icon>Send reset link</md-filled-button>
    <md-outlined-button onclick="doResendEmail()">Resend confirmation</md-outlined-button>
  </div>
  <p id="forgot-msg" class="muted"></p>"""
    val INDEX_MAIN = """
  <div class="segtabs" id="sortTabs" role="tablist" aria-label="Sort videos">
    <span class="segind"></span>
    <button class="segbtn on" data-sort="new"><md-icon>new_releases</md-icon>Latest</button>
    <button class="segbtn" data-sort="popular"><md-icon>local_fire_department</md-icon>Trending</button>
  </div>
  <div id="grid" class="grid stripe"></div>
  <div id="sentinel" aria-hidden="true" style="min-height:4px"></div>"""
    val INDEX_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
      const u = new URLSearchParams(location.search).get('user');
      if (path.startsWith('/channel') && u === j.user.username) mark('account');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val INDEX_TAIL = """
{
window.__boot = function() {
let page = 1, sort = 'new';
segTabs(document.getElementById('sortTabs'), (btn) => {
  sort = btn.dataset.sort || 'new';
  load(1);
});
document.getElementById('q').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') load(1);
  if (e.key === 'Escape') toggleSearch(false);
});
function toggleSearch(force) {
  const box = document.getElementById('searchbox');
  const open = force !== undefined ? force : box.classList.contains('collapsed');
  box.classList.toggle('collapsed', !open);
  box.classList.toggle('expanded', open);
  document.getElementById('searchToggleIcon').textContent = open ? 'close' : 'search';
  if (open) setTimeout(() => document.getElementById('q').focus(), 120);
  else document.getElementById('q').blur();
}
document.addEventListener('click', (e) => {
  const box = document.getElementById('searchbox');
  if (!box || !box.classList.contains('expanded')) return;
  if (box.contains(e.target)) return;
  toggleSearch(false);
});
let searchTimer = null;
let searchAbort = null;
let loading = false, hasMore = true;
document.getElementById('q').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => load(1), 250); });
async function load(p = 1) {
  if (p === 1) {
    page = 1;
    hasMore = true;
    const g0 = document.getElementById('grid');
    g0.innerHTML = '';
    for (let i = 0; i < 6; i++) {
      const d = document.createElement('div');
      d.innerHTML = `<div class="skel skel-media"></div><div class="skel skel-line"></div><div class="skel skel-line short"></div>`;
      g0.appendChild(d);
    }
  } else if (loading || !hasMore) {
    return;
  }
  loading = true;
  // abort superseded requests so slow responses can't overwrite fresh results
  if (searchAbort) searchAbort.abort();
  searchAbort = new AbortController();
  const q = document.getElementById('q').value || '';
  let r;
  try {
    r = await fetch(`/api/videos?q=${'$'}{encodeURIComponent(q)}&sort=${'$'}{sort}&page=${'$'}{p}&limit=12`, { signal: searchAbort.signal });
  } catch (e) {
    loading = false;
    if (e && e.name === 'AbortError') return;
    throw e;
  }
  const j = await r.json();
  loading = false;
  if (!document.getElementById('grid')) return;
  page = p;
  hasMore = p < j.pages;
  const g = document.getElementById('grid');
  if (p === 1) g.innerHTML = '';
  for (const v of j.videos) {
    const media = v.thumbnail
      ? `<img class="media" src="${'$'}{v.thumbnail}" loading="lazy" alt="">`
      : `<video class="media" src="${'$'}{v.src}#t=0.5" preload="metadata" muted></video>`;
    const a = document.createElement('a');
    a.className = 'cardlink'; a.href = '/watch?id=' + v.id;
    const badge = v.status && v.status !== 'ready' ? '⏳ Processing… ' : '';
    a.innerHTML = `<article class="m3card">${'$'}{media}<div class="meta">${'$'}{avatarHtml(v.avatar, 'pfp-sm')}<div class="metatext"><h3>${'$'}{esc(v.title)}</h3>`
      + `<small>${'$'}{badge}${'$'}{esc(v.username)} • ${'$'}{fmtNum(v.views)} views • <span title="${'$'}{esc(fmtDate(v.created_at))}">${'$'}{fmtAge(v.created_at)}</span></small></div></div></article>`;
    g.appendChild(a);
  }
}
if (window.__moreObs) window.__moreObs.disconnect();
window.__moreObs = new IntersectionObserver((es) => {
  if (es.some((e) => e.isIntersecting)) load(page + 1);
}, { rootMargin: '700px' });
window.__moreObs.observe(document.getElementById('sentinel'));
window.__destroy = function() { if (window.__moreObs) { window.__moreObs.disconnect(); window.__moreObs = null; } };
load(1);
Object.assign(window, { load, toggleSearch });
};
window.__boot();
hideBoot();
}"""
    val MESSAGES_MAIN = """
  <div class="dmwrap chatting" id="dmWrap">
    <div id="dmThread">
      <div id="dmMenu" style="display:none"></div>
      <div id="dmEmpty" style="display:none"></div>
      <div id="dmChatErr" style="display:none"><div class="dmgate"><h2>Couldn't load chat</h2><p class="muted" id="dmChatErrMsg">Check your connection and try again.</p><md-filled-button id="dmChatRetry">Retry</md-filled-button></div></div>
      <div id="dmChat" style="display:none">
        <div class="row" style="align-items:center">
          <md-icon-button id="dmBack" aria-label="Back to conversations"><md-icon>arrow_back</md-icon></md-icon-button>
          <span id="dmTitleAv"></span>
          <h3 id="dmTitle" style="margin:0 4px 4px 0"></h3>
          <span class="spacer"></span>
          <select id="dmPeerSel" class="themesel" style="display:none;max-width:38vw" aria-label="Conversation"></select>
        </div>
        <div id="dmMsgs"></div>
        <md-filled-button id="dmPill" style="display:none">↓ New messages</md-filled-button>
        <div class="dmform" id="dmForm">
          <md-outlined-text-field id="dmInput" label="Message" class="field" style="flex:1" maxlength="2000"></md-outlined-text-field>
          <md-filled-button id="dmSend"><md-icon slot="icon">send</md-icon>Send</md-filled-button>
        </div>
      </div>
      <p class="muted" id="dm-msg"></p>
    </div>
  </div>"""
    val MESSAGES_CSS = """
.dmwrap{display:flex;gap:16px;align-items:stretch;height:calc(100dvh - 220px);min-height:420px}
.dmempty{padding:18px 14px;text-align:center;color:var(--md-sys-color-on-surface-variant);font-size:14px;line-height:1.5}
.dmempty .dmtitle{font-size:15px;font-weight:700;color:var(--md-sys-color-on-surface);margin-bottom:6px}
.dmgate{margin:auto;text-align:center;max-width:340px;padding:24px 16px}
.dmgate h2{margin:0 0 8px}
#dmThread{flex:1;min-width:0;display:flex;flex-direction:column;min-height:0;position:relative}
#dmPill{position:absolute;left:50%;transform:translateX(-50%);bottom:76px;z-index:5}
#dmOlder{text-align:center;padding:4px}
#dmChat{flex:1;min-height:0;display:flex;flex-direction:column}
#dmPeerSel{flex:none}
#dmMsgs{flex:1;display:flex;flex-direction:column;gap:2px;padding:12px 4px;overflow-y:auto;min-height:0}
.msgrow{display:flex;flex-direction:column;max-width:78%}
.msgrow.mine{align-self:flex-end;align-items:flex-end}
.msgrow.theirs{align-self:flex-start;align-items:flex-start}
.bubble{padding:10px 14px;border-radius:18px;background:#1e1e1e;color:#fff;font-size:15px;overflow-wrap:anywhere;white-space:pre-wrap}
.msgrow.mine .bubble{background:#f5f5f5;color:#000;border-bottom-right-radius:6px}
.msgrow.theirs .bubble{border-bottom-left-radius:6px}
.msgtime{font-size:11px;color:var(--md-sys-color-on-surface-variant);margin:2px 6px 6px}
.dmform{display:flex;gap:8px;margin-top:8px;align-items:center}
#dmEmpty{margin:auto;text-align:center}
#dmMenu{flex:1;min-height:0;overflow-y:auto;display:flex;flex-direction:column;gap:2px;padding:4px 2px}
.dmmenuhead h2{margin:8px 4px}
.dmrow{display:flex;align-items:center;gap:12px;padding:10px 8px;border:0;background:transparent;color:inherit;font:inherit;text-align:left;border-radius:12px;cursor:pointer;width:100%}
.dmrow:hover{background:rgba(128,128,128,.12)}
.dmmeta{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.dmname{font-weight:700;font-size:14px;display:flex;align-items:center;gap:6px}
.dmprev{font-size:13px;color:var(--md-sys-color-on-surface-variant);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dmprev:empty{display:none}
.dmtime{font-size:11px;color:var(--md-sys-color-on-surface-variant);flex:none}
.dmunread{background:var(--md-sys-color-primary);color:var(--md-sys-color-on-primary);border-radius:999px;font-size:11px;font-weight:700;padding:1px 7px}
#dmBack{flex:none}
@media (max-width:720px){
  .dmwrap{flex-direction:column;height:calc(100dvh - 240px)}
}"""
    val MESSAGES_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/messages') mark('messages');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val MESSAGES_TAIL = """
{
const M = {
  me: null, myId: 0,
  view: 'menu', peer: null, peerId: 0,
  friends: [],
  maxId: 0, minId: 0, historyDone: false,
  convo: 0, cur: null,
  chatTimer: 0, listTimer: 0,
  chatWait: 0, listWait: 0,
  sending: false, loadingOlder: false,
  boots: 0,
};
window.__destroy = function () {
  M.convo++;
  if (M.cur) { try { M.cur.abort(); } catch {} M.cur = null; }
  if (M.chatTimer) clearInterval(M.chatTimer);
  if (M.listTimer) clearInterval(M.listTimer);
  M.chatTimer = 0; M.listTimer = 0;
  document.removeEventListener('visibilitychange', onVisible);
};
const seenLog = {};
function clog(ev, detail) {
  try {
    const k = ev + '|' + (detail || '');
    const now = Date.now();
    if (seenLog[k] && now - seenLog[k] < 60000) return;
    seenLog[k] = now;
    fetch('/api/clientlog', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      credentials: 'include', body: JSON.stringify({ ev, detail: detail || '' }),
    }).catch(() => {});
  } catch {}
}
function note(t) { const el = document.getElementById('dm-msg'); if (el) el.textContent = t || ''; }
function fmtClock(s) {
  const t = utcMs(s);
  if (isNaN(t)) return '';
  return new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
function timeoutSignal(ms, outer) {
  let t = null;
  try {
    t = AbortSignal.timeout(ms);
  } catch {
    const c = new AbortController();
    setTimeout(() => { try { c.abort(); } catch {} }, ms);
    t = c.signal;
  }
  if (!outer) return t;
  try {
    if (typeof AbortSignal.any === 'function') return AbortSignal.any([outer, t]);
  } catch {}
  return outer;
}
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
async function api(path, opts) {
  const o = opts || {};
  const r = await fetch(path, {
    method: o.method || 'GET',
    headers: o.body ? { 'Content-Type': 'application/json' } : undefined,
    credentials: 'include',
    body: o.body ? JSON.stringify(o.body) : undefined,
    signal: timeoutSignal(o.timeout || 20000, o.signal),
  });
  if (!r.ok) {
    const e = new Error('http ' + r.status);
    e.status = r.status;
    try { e.data = await r.json(); } catch {}
    throw e;
  }
  try {
    return await r.json();
  } catch {
    return {};
  }
}
function syncPeerSelect() {
  const sel = document.getElementById('dmPeerSel');
  if (!sel) return;
  const cur = M.peer || '';
  sel.innerHTML = M.friends.map((c) =>
    `<option value="${'$'}{esc(c.username)}"${'$'}{c.username === cur ? ' selected' : ''}>@${'$'}{esc(c.username)}${'$'}{c.unread ? ' (' + c.unread + ')' : ''}</option>`
  ).join('');
  sel.style.display = M.friends.length > 1 ? '' : 'none';
  sel.value = cur;
}
async function refreshList(autoOpen) {
  let j = null;
  for (let i = 0; i < 3; i++) {
    try {
      j = await api('/api/dm/sync');
      break;
    } catch (e) {
      if (e && e.status === 401) { openAuth('login'); return; }
      await sleep(700 * (i + 1));
    }
  }
  if (!j || !Array.isArray(j.friends)) {
    M.listWait = Date.now() + 30000;
    clog('msgListFail', '');
    if (!M.peer) showChatError('Could not load messages. Check your connection and try again.');
    return;
  }
  M.listWait = 0;
  M.friends = j.friends;
  syncPeerSelect();
  if (M.view === 'menu') renderMenu();
}
function renderMenu() {
  const menu = document.getElementById('dmMenu');
  if (!menu || M.view !== 'menu') return;
  if (!M.friends.length) {
    menu.innerHTML = `<div class="dmgate"><h2>Messages</h2>` +
      `<p class="muted">No conversations yet. Once you and someone follow each other, you can chat here.</p></div>`;
    return;
  }
  menu.innerHTML = `<div class="dmmenuhead"><h2>Messages</h2></div>` + M.friends.map((c) =>
    `<button class="dmrow" data-u="${'$'}{esc(c.username)}">` +
    `${'$'}{avatarStatusHtml(c.avatar, 'pfp-sm', c.online)}` +
    `<span class="dmmeta"><span class="dmname">@${'$'}{esc(c.username)}${'$'}{c.unread ? ` <span class="dmunread">${'$'}{c.unread > 9 ? '9+' : c.unread}</span>` : ''}</span>` +
    `<span class="dmprev">${'$'}{esc(c.last_message || '')}</span></span>` +
    `<span class="dmtime">${'$'}{esc(c.last_at ? fmtAge(c.last_at) : '')}</span></button>`
  ).join('');
  menu.querySelectorAll('[data-u]').forEach((b) => b.addEventListener('click', () => openChat(b.dataset.u)));
}
function showMenu() {
  M.convo++;
  if (M.cur) { try { M.cur.abort(); } catch {} M.cur = null; }
  M.peer = null;
  M.view = 'menu';
  M.peerId = 0; M.maxId = 0; M.minId = 0;
  M.historyDone = false; M.loadingOlder = false;
  hidePill();
  note('');
  hideChatError();
  document.getElementById('dmChatErr').style.display = 'none';
  document.getElementById('dmChat').style.display = 'none';
  document.getElementById('dmEmpty').style.display = 'none';
  document.getElementById('dmMenu').style.display = '';
  syncPeerSelect();
  try { history.replaceState({}, '', '/messages'); } catch {}
  renderMenu();
}
function showGate(username) {
  clog('msgGate', username);
  M.peer = null;
  M.view = 'gate';
  document.getElementById('dmWrap').classList.add('chatting');
  document.getElementById('dmChat').style.display = 'none';
  document.getElementById('dmMenu').style.display = 'none';
  hideChatError();
  const empty = document.getElementById('dmEmpty');
  empty.style.display = '';
  empty.innerHTML = `<div class="dmgate"><h2>Friends only</h2>` +
    `<p class="muted">You and <b>@${'$'}{esc(username)}</b> need to follow each other before you can chat.</p>` +
    `<md-filled-button onclick="location.href='/channel?user=${'$'}{encodeURIComponent(username)}'">View profile</md-filled-button></div>`;
}
function showChatError(msg) {
  document.getElementById('dmChat').style.display = 'none';
  document.getElementById('dmEmpty').style.display = 'none';
  document.getElementById('dmMenu').style.display = 'none';
  document.getElementById('dmChatErrMsg').textContent = msg;
  document.getElementById('dmChatErr').style.display = '';
}
function hideChatError() {
  document.getElementById('dmChatErr').style.display = 'none';
}
function chatSkeletons() {
  const box = document.getElementById('dmMsgs');
  box.innerHTML = '';
  for (let i = 0; i < 4; i++) {
    const d = document.createElement('div');
    d.className = 'msgrow' + (i % 2 ? ' mine' : ' theirs');
    d.innerHTML = '<div class="skel skel-line" style="width:220px;max-width:70%"></div>';
    box.appendChild(d);
  }
}
function bubbleEl(text, mine, at) {
  const row = document.createElement('div');
  row.className = 'msgrow' + (mine ? ' mine' : ' theirs');
  const d = document.createElement('div');
  d.className = 'bubble';
  d.textContent = text;
  row.appendChild(d);
  const t = at ? fmtClock(at) : '';
  if (t) {
    const s = document.createElement('div');
    s.className = 'msgtime';
    s.title = at;
    s.textContent = t;
    row.appendChild(s);
  }
  return row;
}
function addBubble(text, mine, at) {
  document.getElementById('dmMsgs').appendChild(bubbleEl(text, mine, at));
}
function showPill() {
  const p = document.getElementById('dmPill');
  if (p) p.style.display = '';
}
function hidePill() {
  const p = document.getElementById('dmPill');
  if (p) p.style.display = 'none';
}
function olderButton(count) {
  const box = document.getElementById('dmMsgs');
  if (M.historyDone) {
    const gone = document.getElementById('dmOlder');
    if (gone) gone.remove();
    return;
  }
  if (count < 30) {
    M.historyDone = true;
    return;
  }
  if (document.getElementById('dmOlder')) return;
  const wrap = document.createElement('div');
  wrap.id = 'dmOlder';
  wrap.innerHTML = '<md-text-button id="dmOlderBtn">Load older messages</md-text-button>';
  box.prepend(wrap);
  wrap.querySelector('#dmOlderBtn').addEventListener('click', loadOlder);
}
async function confirmedFriend(peer) {
  const s = await api('/api/dm/sync').catch(() => null);
  if (!s || !Array.isArray(s.friends)) return null;
  M.friends = s.friends;
  syncPeerSelect();
  return s.friends.some((c) => c.username === peer);
}
async function openChat(username) {
  clog('msgOpen', username);
  M.convo++;
  if (M.cur) { try { M.cur.abort(); } catch {} M.cur = null; }
  M.peer = username;
  M.view = 'chat';
  M.peerId = 0; M.maxId = 0; M.minId = 0;
  M.historyDone = false; M.loadingOlder = false;
  M.chatWait = 0;
  hidePill();
  note('');
  hideChatError();
  const oldBtn = document.getElementById('dmOlder');
  if (oldBtn) oldBtn.remove();
  chatSkeletons();
  document.getElementById('dmWrap').classList.add('chatting');
  document.getElementById('dmMenu').style.display = 'none';
  document.getElementById('dmEmpty').style.display = 'none';
  document.getElementById('dmChat').style.display = '';
  document.getElementById('dmTitle').textContent = '@' + username;
  document.getElementById('dmTitleAv').innerHTML = '';
  syncPeerSelect();
  try { history.replaceState({}, '', '/messages?user=' + encodeURIComponent(username)); } catch {}
  for (let i = 0; i < 3; i++) {
    try {
      const res = await loadInitial();
      if (res !== 'retry') return;
    } catch (e) {
      clog('msgThreadFail', String((e && e.status) || 'net'));
      if (i >= 2) break;
    }
    await sleep(700 * (i + 1));
  }
  if (InboxStillHere(username)) showChatError('Could not load chat. Check your connection and try again.');
}
function InboxStillHere(username) {
  return M.peer === username && M.view === 'chat';
}
async function loadInitial() {
  const mark = M.convo;
  const peer = M.peer;
  if (!peer) return 'dropped';
  const ctl = new AbortController();
  M.cur = ctl;
  try {
    const url = '/api/dm/thread?user=' + encodeURIComponent(peer) + '&limit=30';
    let r;
    try {
      r = await fetch(url, { credentials: 'include', signal: timeoutSignal(20000, ctl.signal) });
    } catch (e) {
      if (e && e.name === 'AbortError') return 'dropped';
      throw e;
    } finally {
      if (M.cur === ctl) M.cur = null;
    }
    if (mark !== M.convo || peer !== M.peer) return 'dropped';
    if (r.status === 401) {
      openAuth('login');
      return 'dropped';
    }
    if (r.status === 403) {
      const ok = await confirmedFriend(peer).catch(() => null);
      if (mark !== M.convo || peer !== M.peer) return 'dropped';
      if (ok === true) return 'retry';
      if (ok === false) {
        showGate(peer);
        return 'gated';
      }
      throw new Error('confirm failed');
    }
    if (!r.ok) {
      const e = new Error('http ' + r.status);
      e.status = r.status;
      throw e;
    }
    const j = await r.json();
    if (mark !== M.convo || peer !== M.peer) return 'dropped';
    document.getElementById('dmMsgs').innerHTML = '';
    if (j.peer) {
      document.getElementById('dmTitleAv').innerHTML = avatarStatusHtml(j.peer.avatar, 'pfp-sm', j.peer.online);
      M.peerId = j.peer.user_id || 0;
    }
    if (j.me) M.myId = j.me;
    const box = document.getElementById('dmMsgs');
    let inbound = false;
    for (const m of (j.messages || [])) {
      if (M.maxId && m.id <= M.maxId) continue;
      if (m.id > M.maxId) M.maxId = m.id;
      if (!M.minId || m.id < M.minId) M.minId = m.id;
      if (m.sender_id !== M.myId) inbound = true;
      addBubble(m.body || '', m.sender_id === M.myId, m.created_at);
    }
    box.scrollTop = box.scrollHeight;
    olderButton((j.messages || []).length);
    hideChatError();
    clog('msgThreadOk', peer + ' ' + (j.messages || []).length + ' msgs');
    if (!(j.messages || []).length) clog('msgEmptyThread', peer);
    if (inbound) {
      markRead().then(() => refreshList()).catch(() => {});
    }
    return 'ok';
  } catch (e) {
    if (e && e.name === 'AbortError') return 'dropped';
    if (mark !== M.convo || peer !== M.peer) return 'dropped';
    throw e;
  } finally {
    if (M.cur === ctl) M.cur = null;
  }
}
async function pollOnce() {
  const mark = M.convo;
  const peer = M.peer;
  if (!peer) return;
  try {
    const url = '/api/dm/thread?user=' + encodeURIComponent(peer) + (M.maxId ? '&after_id=' + M.maxId : '&limit=30');
    const j = await api(url);
    if (mark !== M.convo || peer !== M.peer) return;
    const box = document.getElementById('dmMsgs');
    const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 150;
    let added = false;
    let inbound = false;
    for (const m of (j.messages || [])) {
      if (M.maxId && m.id <= M.maxId) continue;
      if (m.id > M.maxId) M.maxId = m.id;
      if (!M.minId || m.id < M.minId) M.minId = m.id;
      if (m.sender_id !== M.myId) inbound = true;
      addBubble(m.body || '', m.sender_id === M.myId, m.created_at);
      added = true;
    }
    if (added && nearBottom) {
      box.scrollTop = box.scrollHeight;
      hidePill();
    } else if (added) {
      showPill();
    }
    if (inbound) {
      markRead().then(() => refreshList()).catch(() => {});
    }
    M.chatWait = 0;
  } catch (e) {
    if (mark !== M.convo || peer !== M.peer) return;
    if (e && e.status === 401) {
      openAuth('login');
      return;
    }
    if (e && e.status === 403) {
      const ok = await confirmedFriend(peer).catch(() => null);
      if (mark !== M.convo || peer !== M.peer) return;
      if (ok === false) showGate(peer);
      return;
    }
    M.chatWait = Date.now() + 8000;
  }
}
async function loadOlder() {
  if (!M.peer || M.loadingOlder || M.historyDone) return;
  const mark = M.convo;
  const peer = M.peer;
  M.loadingOlder = true;
  try {
    const box = document.getElementById('dmMsgs');
    const btn = document.getElementById('dmOlderBtn');
    if (btn) btn.textContent = 'Loading…';
    const j = await api('/api/dm/thread?user=' + encodeURIComponent(peer) + '&before_id=' + M.minId + '&limit=30', { timeout: 20000 });
    if (mark !== M.convo || peer !== M.peer) return;
    const msgs = (j.messages || []).slice().sort((a, b) => a.id - b.id);
    if (msgs.length < 30) M.historyDone = true;
    if (!msgs.length) {
      const gone = document.getElementById('dmOlder');
      if (gone) gone.remove();
      return;
    }
    const oldH = box.scrollHeight;
    const frag = document.createDocumentFragment();
    for (const m of msgs) {
      if (M.minId && m.id >= M.minId) continue;
      if (!M.minId || m.id < M.minId) M.minId = m.id;
      frag.appendChild(bubbleEl(m.body || '', m.sender_id === M.myId, m.created_at));
    }
    const older = document.getElementById('dmOlder');
    box.insertBefore(frag, older ? older.nextSibling : box.firstChild);
    box.scrollTop += box.scrollHeight - oldH;
    const btn2 = document.getElementById('dmOlderBtn');
    if (M.historyDone) {
      const gone = document.getElementById('dmOlder');
      if (gone) gone.remove();
    } else if (btn2) {
      btn2.textContent = 'Load older messages';
    }
  } catch {
    const btn = document.getElementById('dmOlderBtn');
    if (btn) btn.textContent = 'Load older messages';
  } finally {
    M.loadingOlder = false;
  }
}
async function markRead() {
  const peer = M.peer;
  if (!peer) return;
  try {
    await api('/api/dm/read', { method: 'POST', body: { user: peer }, timeout: 10000 });
  } catch {}
}
async function sendDm() {
  const inp = document.getElementById('dmInput');
  const text = (inp.value || '').trim();
  const peer = M.peer;
  if (!text || !peer || M.sending || peer !== M.peer) return;
  if (text.length > 2000) { note('Message too long (max 2000).'); return; }
  M.sending = true;
  note('');
  try {
    const j = await api('/api/dm/send', { method: 'POST', body: { to: peer, body: text }, timeout: 20000 });
    if (peer !== M.peer) return;
    if (j.id && j.id > M.maxId) M.maxId = j.id;
    inp.value = '';
    addBubble(text, true, j.created_at || new Date().toISOString());
    const box = document.getElementById('dmMsgs');
    box.scrollTop = box.scrollHeight;
    refreshList();
  } catch (e) {
    if (peer !== M.peer) return;
    note(((e && e.data && e.data.error) || 'Send failed.'));
    if (e && e.status === 401) {
      openAuth('login');
      return;
    }
    if (e && e.status === 403) {
      const ok = await confirmedFriend(peer).catch(() => null);
      if (ok === false && peer === M.peer) showGate(peer);
    }
  } finally {
    M.sending = false;
  }
}
function onVisible() {
  if (document.hidden) return;
  refreshList(false);
  if (M.view === 'chat' && M.peer) pollOnce();
}
async function boot() {
  let u = null;
  try {
    u = await Promise.race([
      refreshAuth().catch(() => null),
      new Promise((r) => setTimeout(() => r(null), 15000)),
    ]);
  } catch {
    u = null;
  }
  if (!u) { openAuth('login'); note('Sign in to use messages.'); return; }
  M.me = u.username;
  M.myId = u.id || 0;
  const send = document.getElementById('dmSend');
  if (send) send.addEventListener('click', sendDm);
  const cr = document.getElementById('dmChatRetry');
  if (cr) cr.addEventListener('click', async () => { if (M.peer) openChat(M.peer); else { await refreshList(false); if (!M.peer && M.listWait === 0) showMenu(); } });
  const back = document.getElementById('dmBack');
  if (back) back.addEventListener('click', showMenu);
  const sel = document.getElementById('dmPeerSel');
  if (sel) sel.addEventListener('change', () => { if (sel.value) openChat(sel.value); });
  const pill = document.getElementById('dmPill');
  if (pill) pill.addEventListener('click', () => {
    const box = document.getElementById('dmMsgs');
    box.scrollTop = box.scrollHeight;
    hidePill();
  });
  const input = document.getElementById('dmInput');
  if (input) input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendDm(); }
  });
  document.addEventListener('visibilitychange', onVisible);
  M.listTimer = setInterval(() => {
    if (document.hidden || Date.now() < M.listWait) return;
    refreshList(false);
  }, 10000);
  M.chatTimer = setInterval(() => {
    if (M.view !== 'chat' || !M.peer || document.hidden || Date.now() < M.chatWait) return;
    pollOnce();
  }, 3000);
  const q = new URLSearchParams(location.search).get('user') || '';
  await refreshList(false);
  if (M.peer) {
    // already chatting (e.g. restored by a poll) — nothing to do
  } else if (q) {
    openChat(q);
  } else if (M.friends.length || M.listWait === 0) {
    showMenu();
  }
  clog('msgBootOk', M.friends.length + ' friends');
}
let bootTries = 0;
async function bootSafe() {
  bootTries++;
  try {
    if (typeof refreshAuth !== 'function' || typeof esc !== 'function' ||
        typeof avatarStatusHtml !== 'function' || typeof fmtAge !== 'function' ||
        typeof utcMs !== 'function' || typeof hideBoot !== 'function' ||
        typeof openAuth !== 'function') {
      throw new Error('loader');
    }
    await boot();
    return;
  } catch {}
  if (!document.getElementById('dmThread')) return;
  if (bootTries < 3) {
    note('Loading…');
    await new Promise((r) => setTimeout(r, 1500));
    if (!document.getElementById('dmThread')) return;
    return bootSafe();
  }
  showChatError('Messages failed to start. Reload the page to try again.');
  clog('msgBootFail', '');
}
bootSafe();
hideBoot();
}"""
    val RESET_MAIN = """
  <h2>Set a new password</h2>
  <form id="form" method="post" action="/api/auth/reset" onsubmit="return submitReset(event)">
    <md-outlined-text-field id="pw1" name="password" type="password" autocomplete="new-password" label="New password (6+ chars)" class="field"></md-outlined-text-field>
    <md-outlined-text-field id="pw2" label="Repeat new password" type="password" class="field"></md-outlined-text-field>
    <input type="password" id="pw1-n" name="password" autocomplete="new-password" tabindex="-1" aria-hidden="true" style="position:fixed;top:0;left:0;width:8px;height:8px;opacity:.01;pointer-events:none;border:0;padding:0">
    <button type="submit" tabindex="-1" aria-hidden="true" style="position:fixed;top:0;left:0;width:8px;height:8px;opacity:.01;pointer-events:none;border:0;padding:0"></button>
    <md-filled-button onclick="doReset()"><md-icon slot="icon">lock_reset</md-icon>Save password</md-filled-button>
  </form>
  <p id="msg" class="muted"></p>"""
    val RESET_TAIL = """
{
var token = new URLSearchParams(location.search).get('token') || '';
var resetBusy = false;
var linkOk = false;
function submitReset(e) { if (e) e.preventDefault(); doReset(); return false; }
async function checkLink() {
  const msg = document.getElementById('msg');
  if (!token) {
    msg.textContent = 'Missing reset link.';
    document.getElementById('form').style.display = 'none';
    return;
  }
  try {
    const r = await fetch('/api/auth/reset?token=' + encodeURIComponent(token));
    if (!r.ok) throw 0;
    linkOk = true;
    msg.textContent = 'Link confirmed — enter your new password below.';
  } catch {
    msg.textContent = 'This link is invalid or expired. Ask for a new one on the forgot-password page.';
    document.getElementById('form').style.display = 'none';
  }
}
async function doReset() {
  if (resetBusy) return;
  if (!linkOk) {
    document.getElementById('msg').textContent = 'This link is invalid or expired.';
    return;
  }
  resetBusy = true;
  const a = document.getElementById('pw1').value, b = document.getElementById('pw2').value;
  const msg = document.getElementById('msg');
  if (!token) { msg.textContent = 'Missing reset link.'; resetBusy = false; return; }
  if (a.length < 6) { msg.textContent = 'Password must be 6+ chars.'; resetBusy = false; return; }
  if (a !== b) { msg.textContent = 'Passwords do not match.'; resetBusy = false; return; }
  const r = await fetch('/api/auth/reset', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, password: a }),
  });
  const j = await r.json();
  if (!r.ok) { msg.textContent = j.error; resetBusy = false; linkOk = false; return; }
  document.getElementById('form').style.display = 'none';
  msg.innerHTML = 'Password saved! <a href="/" style="color:var(--md-sys-color-primary)">Sign in on the home page.</a>';
}
checkLink();
for (const id of ['pw1', 'pw2']) {
  document.getElementById(id).addEventListener('keydown', (e) => { if (e.key === 'Enter') doReset(); });
}
document.getElementById('pw1').addEventListener('input', () => {
  const n = document.getElementById('pw1-n');
  if (n && n.value !== document.getElementById('pw1').value) n.value = document.getElementById('pw1').value;
});
document.getElementById('pw1-n').addEventListener('input', (e) => {
  document.getElementById('pw1').value = e.target.value;
});

Object.assign(window, { submitReset, doReset });
}"""
    val SETTINGS_MAIN = """
  <h2 id="sname"></h2>
  <p class="muted" id="sinfo"></p>
  <div class="setwrap">
  <div class="setnav" id="setNav" role="tablist" aria-label="Settings sections">
    <button class="setnavbtn active" data-pane="paneAccount" role="tab"><md-icon>person</md-icon><span>Account</span></button>
    <button class="setnavbtn" data-pane="panePassword" role="tab"><md-icon>lock</md-icon><span>Security</span></button>
    <button class="setnavbtn" data-pane="paneNotif" role="tab"><md-icon>notifications</md-icon><span>Notifications</span></button>
    <button class="setnavbtn" data-pane="paneFx" role="tab"><md-icon>palette</md-icon><span>Appearance</span></button>
    <button class="setnavbtn" data-pane="paneAbout" role="tab"><md-icon>info</md-icon><span>About</span></button>
  </div>
  <div class="setpanes">
  <div id="paneAccount">
    <div id="spic"></div>
    <h3>Profile picture</h3>
    <p class="muted">Square picture, GIF becomes animated WebM, photos become WebP. Max 10MB.</p>
    <div class="row">
      <md-filled-tonal-button onclick="document.getElementById('pfpfile').click()"><md-icon slot="icon">folder_open</md-icon>Choose picture</md-filled-tonal-button>
      <md-filled-button onclick="uploadPfp()"><md-icon slot="icon">cloud_upload</md-icon>Upload</md-filled-button>
    </div>
    <input type="file" id="pfpfile" accept="image/*" hidden>
    <md-linear-progress id="pfpbar" indeterminate style="display:none"></md-linear-progress>
    <p id="pfpmsg" class="muted"></p>
    <h3>Handle</h3>
    <p class="muted">Letters, numbers and _ only, 3-30 characters.</p>
    <md-outlined-text-field id="nm-new" label="New handle" class="field"></md-outlined-text-field>
    <md-filled-button onclick="doRename()"><md-icon slot="icon">edit</md-icon>Save name</md-filled-button>
    <p id="nm-msg" class="muted"></p>
    <div data-realadmin style="display:none">
      <h3>Admin view</h3>
      <div class="row">
        <md-switch id="sw-adminview"></md-switch>
        <span>Show admin controls (turn off to preview as a normal user)</span>
      </div>
    </div>
    <div style="margin-top:32px">
      <h3>Sign out</h3>
      <md-outlined-button onclick="doLogout()"><md-icon slot="icon">logout</md-icon>Sign out</md-outlined-button>
    </div>
  </div>
  <div id="panePassword" style="display:none">
    <h3>Change password</h3>
    <form id="pwPane" method="post" action="/api/auth/change" onsubmit="return submitChangePw(event)">
      <div class="nfield">
        <label for="cp-cur">Current password</label>
        <input placeholder=" " id="cp-cur" name="current-password" type="password" autocomplete="current-password" required>
      </div>
      <div class="nfield">
        <label for="cp-new">New password (6+ chars)</label>
        <input placeholder=" " id="cp-new" name="new-password" type="password" autocomplete="new-password" minlength="6" required>
      </div>
    </form>
    <md-filled-button onclick="doChangePw()"><md-icon slot="icon">lock_reset</md-icon>Save new password</md-filled-button>
    <p id="cp-msg" class="muted"></p>
  </div>
  <div id="paneNotif" style="display:none">
    <h3>Upload alerts</h3>
    <div class="row">
      <md-switch id="sw-notif"></md-switch>
      <span>Notify me when channels I follow upload</span>
    </div>
    <div class="row" style="margin-top:12px">
      <md-filled-button onclick="saveNotif()"><md-icon slot="icon">save</md-icon>Save</md-filled-button>
    </div>
    <p id="nt-msg" class="muted"></p>
  </div>
  <div id="paneFx" style="display:none">
    <h3>Appearance</h3>
    <div class="row">
      <md-switch id="sw-blur"></md-switch>
      <span>Blur effects (translucent bars)</span>
    </div>
    <h3 style="margin-top:20px">Theme</h3>
    <p class="muted">Auto follows your browser. Pick one to override it.</p>
    <div class="row">
      <select id="themeSel" class="themesel" aria-label="Theme">
        <option value="auto">Auto (browser)</option>
        <option value="dark">Dark</option>
        <option value="light">White</option>
      </select>
    </div>
  </div>
  <div id="paneAbout" style="display:none">
    <h3>About</h3>
    <p class="muted">What WatchShark is, where the code lives, and where to get the Android app.</p>
    <div class="row">
      <md-filled-tonal-button href="/about"><md-icon slot="icon">info</md-icon>About WatchShark</md-filled-tonal-button>
    </div>
  </div>
  </div>
  </div>"""
    val SETTINGS_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
      const u = new URLSearchParams(location.search).get('user');
      if (path.startsWith('/channel') && u === j.user.username) mark('account');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val SETTINGS_TAIL = """
{
async function initSettings() {
  const u = await refreshAuth();
  if (!u) { location.href = '/'; return; }
  document.getElementById('sname').textContent = '@' + u.username;
  document.getElementById('sinfo').textContent = 'Member since ' + fmtDate(u.since) + ' (' + fmtAge(u.since) + ')';
  document.getElementById('spic').innerHTML = avatarHtml(u.avatar, 'pfp-lg');
  document.getElementById('sw-notif').selected = u.notify_uploads !== false;
  document.getElementById('themeSel').value = themePref();
  const tsel = document.getElementById('themeSel');
  if (!tsel.dataset.bound) {
    tsel.dataset.bound = '1';
    tsel.addEventListener('change', () => setThemePref(tsel.value));
  }
  document.getElementById('sw-blur').selected = localStorage.getItem('ws_blur') !== '0';
  const swBlur = document.getElementById('sw-blur');
  if (!swBlur.dataset.bound) {
    swBlur.dataset.bound = '1';
    swBlur.addEventListener('change', (e) => {
      localStorage.setItem('ws_blur', e.target.selected ? '1' : '0');
      applyBlurPref();
    });
  }
  if (u.admin) {
    document.querySelector('[data-realadmin]').style.display = '';
    document.getElementById('sw-adminview').selected = localStorage.getItem('ws_adminview') !== '0';
    const swAv = document.getElementById('sw-adminview');
    if (!swAv.dataset.bound) {
      swAv.dataset.bound = '1';
      swAv.addEventListener('change', (e) => {
        localStorage.setItem('ws_adminview', e.target.selected ? '1' : '0');
        location.reload();
      });
    }
  }
}
function selectSetPane(id, push) {
  document.querySelectorAll('#setNav .setnavbtn').forEach((b) => {
    b.classList.toggle('active', b.dataset.pane === id);
  });
  ['paneAccount', 'panePassword', 'paneNotif', 'paneFx', 'paneAbout'].forEach((pid) => {
    document.getElementById(pid).style.display = pid === id ? '' : 'none';
  });
  if (push) {
    try {
      const u = new URL(location.href);
      u.searchParams.set('tab', id.replace(/^pane/, '').toLowerCase());
      history.replaceState({}, '', u);
    } catch {}
  }
}
document.querySelectorAll('#setNav .setnavbtn').forEach((b) => {
  b.addEventListener('click', () => selectSetPane(b.dataset.pane, true));
});
(function() {
  const panes = ['paneAccount', 'panePassword', 'paneNotif', 'paneFx', 'paneAbout'];
  const q = (new URLSearchParams(location.search).get('tab') || '').toLowerCase();
  const match = panes.find((pid) => pid.replace(/^pane/, '').toLowerCase() === q);
  selectSetPane(match || 'paneAccount', false);
})();
async function doRename() {
  const username = document.getElementById('nm-new').value.trim();
  const msg = document.getElementById('nm-msg');
  const r = await fetch('/api/auth/username', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ username }),
  });
  const j = await r.json();
  if (!r.ok) { msg.textContent = j.error; return; }
  msg.textContent = 'Name saved!';
  initSettings();
}
async function saveNotif() {
  const on = document.getElementById('sw-notif').selected;
  const r = await fetch('/api/settings/notifications', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ uploads: on }),
  });
  document.getElementById('nt-msg').textContent = r.ok ? 'Saved!' : 'Failed.';
}
async function uploadPfp() {
  const f = document.getElementById('pfpfile').files[0];
  const msg = document.getElementById('pfpmsg');
  if (!f) { msg.textContent = 'Choose a picture first.'; return; }
  document.getElementById('pfpbar').style.display = '';
  msg.textContent = 'Uploading and converting…';
  const r = await fetch('/api/pfp', {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': f.type || 'image/png' }, body: f,
  });
  const j = await r.json();
  document.getElementById('pfpbar').style.display = 'none';
  if (!r.ok) { msg.textContent = j.error; return; }
  msg.textContent = 'Saved!';
  const u = await me();
  document.getElementById('spic').innerHTML = avatarHtml(u.avatar, 'pfp-lg');
  refreshAuth();
}
initSettings();
hideBoot();

Object.assign(window, { uploadPfp, doRename, saveNotif });
}"""
    val UPLOAD_MAIN = """
  <h2>Upload</h2>
  <div class="segtabs" id="kindTabs" role="tablist" aria-label="Upload type">
    <span class="segind"></span>
    <button class="segbtn on" data-kind="video">Videos</button>
    <button class="segbtn" data-kind="wheel">Wheels</button>
  </div>
  <div id="drop"><p>Drag &amp; drop a file here or</p>
    <md-filled-tonal-button onclick="document.getElementById('f').click()"><md-icon slot="icon">folder_open</md-icon>Choose file</md-filled-tonal-button>
    <input type="file" id="f" accept="video/*" hidden>
  </div>
  <div class="row" style="margin-top:12px">
    <md-filled-tonal-button onclick="document.getElementById('thumbf').click()"><md-icon slot="icon">image</md-icon>Choose thumbnail</md-filled-tonal-button>
    <img id="thumbprev" style="display:none;width:128px;aspect-ratio:16/9;object-fit:cover;border-radius:8px" alt="">
  </div>
  <input type="file" id="thumbf" accept="image/*" hidden>
  <md-outlined-text-field id="title" label="Title (required)" maxlength="120" class="field"></md-outlined-text-field>
  <md-outlined-text-field id="desc" label="Description..." type="textarea" rows="3" maxlength="2000" class="field"></md-outlined-text-field>
  <md-linear-progress id="bar" value="0" style="display:none"></md-linear-progress>
  <md-filled-button onclick="up()"><md-icon slot="icon">cloud_upload</md-icon>Upload</md-filled-button>
  <p id="msg"></p>"""
    val UPLOAD_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
      const u = new URLSearchParams(location.search).get('user');
      if (path.startsWith('/channel') && u === j.user.username) mark('account');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val UPLOAD_TAIL = """
{
var file = null;
var thumbFile = null;
var upKind = 'video';
var kindManual = false;
var drop = document.getElementById('drop'), fi = document.getElementById('f');
const kindSeg = segTabs(document.getElementById('kindTabs'), (btn) => {
  upKind = btn.dataset.kind || 'video';
  kindManual = true;
});
function pickFile(f) {
  file = f;
  if (!f) return;
  drop.querySelector('p').textContent = f.name;
  if (kindManual) return;
  const url = URL.createObjectURL(f);
  const v = document.createElement('video');
  v.preload = 'metadata';
  v.onloadedmetadata = () => {
    if (!kindManual) {
      upKind = v.videoHeight > v.videoWidth ? 'wheel' : 'video';
      const b = document.querySelector(`#kindTabs [data-kind="${'$'}{upKind}"]`);
      if (kindSeg && b) kindSeg.pick(b, true);
    }
    URL.revokeObjectURL(url);
  };
  v.onerror = () => URL.revokeObjectURL(url);
  v.src = url;
}
['dragover', 'dragenter'].forEach((e) => drop.addEventListener(e, (ev) => { ev.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((e) => drop.addEventListener(e, (ev) => { ev.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', (ev) => { pickFile(ev.dataTransfer.files[0]); });
fi.addEventListener('change', () => { pickFile(fi.files[0]); });
document.getElementById('thumbf').addEventListener('change', () => {
  thumbFile = document.getElementById('thumbf').files[0] || null;
  const prev = document.getElementById('thumbprev');
  if (thumbFile) {
    prev.src = URL.createObjectURL(thumbFile);
    prev.style.display = '';
  } else {
    prev.removeAttribute('src');
    prev.style.display = 'none';
  }
});
(async () => {
  const u = await refreshAuth();
  if (!u) { alert('Login first'); openAuth('login'); return; }
  if (isBlockedUser(u)) {
    document.getElementById('msg').textContent = banMessage(u);
  }
})();
function up() {
  me().then((u) => {
    if (isBlockedUser(u)) { alert(banMessage(u)); return; }
    doUp();
  });
}
function doUp() {
  if (!file) return alert('Choose a file first');
  const title = document.getElementById('title').value.trim();
  if (!title) return alert('Title is required');
  const fd = new FormData();
  fd.append('title', title);
  fd.append('description', document.getElementById('desc').value);
  fd.append('kind', upKind);
  fd.append('file', file);
  if (thumbFile) fd.append('thumb', thumbFile);
  const bar = document.getElementById('bar');
  bar.style.display = ''; bar.indeterminate = false; bar.value = 0;
  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/videos', true); xhr.withCredentials = true;
  xhr.upload.onprogress = (ev) => { if (ev.lengthComputable) bar.value = ev.loaded / ev.total; };
  xhr.onload = () => {
    try {
      const j = JSON.parse(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300) {
        document.getElementById('msg').textContent = 'Uploaded! Redirecting...';
        location.href = '/watch?id=' + j.id;
      } else document.getElementById('msg').textContent = (j.error || 'failed');
    } catch { document.getElementById('msg').textContent = 'Upload failed'; }
  };
  xhr.onerror = () => document.getElementById('msg').textContent = 'Network error';
  xhr.send(fd);
}

Object.assign(window, { up });
}"""
    val VERIFY_MAIN = """
  <div id="box"><md-linear-progress indeterminate></md-linear-progress></div>"""
    val VERIFY_TAIL = """
(async () => {
  const token = new URLSearchParams(location.search).get('token') || '';
  const box = document.getElementById('box');
  if (!token) { box.innerHTML = '<p class="muted">Missing confirmation link.</p>'; return; }
  try {
    const r = await fetch('/api/auth/verify?token=' + encodeURIComponent(token));
    if (r.ok) {
      box.innerHTML = '<h2>Email confirmed!</h2><p class="muted">Your account is ready. Sign in to start watching and uploading.</p><md-filled-button href="/">Go to WatchShark<md-icon slot="icon">arrow_forward</md-icon></md-filled-button>';
    } else {
      const j = await r.json();
      box.innerHTML = '<h2>Link did not work</h2><p class="muted">' + j.error + '</p><md-outlined-button href="/forgot">Get a new link</md-outlined-button>';
    }
  } catch {
    box.innerHTML = '<p class="muted">Network error. Try again later.</p>';
  }
})();"""
    val WATCH_MAIN = """
  <div class="watchcol">
    <div id="wrap"><md-linear-progress indeterminate></md-linear-progress></div>
    <h3 id="chead">Comments</h3>
    <div class="row">
      <md-outlined-text-field id="cbody" placeholder="Add a comment..." class="field" style="flex:1"></md-outlined-text-field>
      <md-filled-button onclick="sendComment()">Post<md-icon slot="icon">send</md-icon></md-filled-button>
    </div>
    <div id="cpreview"></div>
    <md-text-button id="ctoggle" style="display:none">Show less</md-text-button>
    <md-list id="comments" style="display:none"></md-list>
  </div>
  <aside class="watchrail" id="rail" style="display:none">
    <div class="segtabs" id="upnextTabs" role="tablist" aria-label="More videos">
      <span class="segind"></span>
      <button class="segbtn on" data-tab="new">Latest</button>
      <button class="segbtn" data-tab="pop">Trending</button>
    </div>
    <div id="upnext"></div>
  </aside>"""
    val WATCH_EXTRADIALOGS = """
<md-dialog id="delDialog">
  <div slot="headline">Delete this video?</div>
  <div slot="content">
    <p class="muted" id="delSub"></p>
    <md-outlined-text-field id="delReason" label="Reason (sent to the owner)" class="field"></md-outlined-text-field>
    <p id="del-err" style="color:var(--md-sys-color-error)"></p>
  </div>
  <div slot="actions">
    <md-text-button onclick="closeDelDialog()">Cancel</md-text-button>
    <md-filled-button onclick="adminDel()">Delete</md-filled-button>
  </div>
</md-dialog>
<md-dialog id="editDialog">
  <div slot="headline">Edit video</div>
  <div slot="content">
    <div class="nfield">
      <label for="edTitle">Title</label>
      <input placeholder=" " id="edTitle" type="text" maxlength="120" required>
    </div>
    <div class="nfield">
      <label for="edDesc">Description</label>
      <textarea placeholder=" " id="edDesc" rows="4"></textarea>
    </div>
    <div class="row">
      <md-filled-tonal-button onclick="document.getElementById('edThumb').click()"><md-icon slot="icon">image</md-icon>Thumbnail</md-filled-tonal-button>
      <img id="edThumbPrev" style="display:none;width:128px;aspect-ratio:16/9;object-fit:cover;border-radius:8px" alt="">
    </div>
    <input type="file" id="edThumb" accept="image/*" hidden>
    <p id="edit-err" style="color:var(--md-sys-color-error)"></p>
  </div>
  <div slot="actions">
    <md-text-button onclick="closeEditDialog()">Cancel</md-text-button>
    <md-filled-button onclick="saveEdit()">Save</md-filled-button>
  </div>
</md-dialog>"""
    val WATCH_TAIL = """
{
window.__boot = function() {
const id = new URLSearchParams(location.search).get('id');
let VID = null;
function destroyPlayer() {
  try { P.vid.pause(); } catch {}
  try { P.vid.removeAttribute('src'); P.vid.load(); } catch {}
  P = null;
  stopCmtTimer();
}
async function init() {
  destroyPlayer();
  await refreshAuth();
  const r = await fetch('/api/videos/' + id);
  if (!r.ok) { document.getElementById('wrap').innerHTML = '<p>Video not found. <a href="/">Back</a></p>'; return; }
  const j = await r.json();
  VID = j.video;
  document.title = VID.title + ' — WatchShark';
  if (VID.status !== 'ready') {
    document.getElementById('wrap').innerHTML = VID.status === 'failed'
      ? `<h2>${'$'}{esc(VID.title)}</h2><p class="muted">Processing failed. This file could not be converted to a playable video.</p><div class="row"><md-outlined-button onclick="del()"><md-icon slot="icon">delete</md-icon>Delete</md-outlined-button></div>`
      : `<h2>${'$'}{esc(VID.title)}</h2><md-linear-progress indeterminate></md-linear-progress><p class="muted">Processing video… big files take a while. This page refreshes automatically.</p>`;
    if (VID.status !== 'failed') Pjax.timeout(init, 10000);
    return;
  }
  document.getElementById('wrap').innerHTML = `
    <div class="player-wrap loading" id="pw" tabindex="0">
      <video id="vid" class="player-video" playsinline preload="auto"></video>
      <md-circular-progress id="spin" class="spin" indeterminate style="display:none"></md-circular-progress>
      <div class="gesture left" id="gleft"></div>
      <div class="gesture right" id="gright"></div>
      <div class="seek-flash" id="flash" style="display:none"></div>
      <md-fab id="bigplay" class="bigplay" variant="primary" size="large" aria-label="Play"><md-icon slot="icon" id="bigIcon">play_arrow</md-icon></md-fab>
      <md-filled-tonal-button id="unmute" class="unmute" style="display:none"><md-icon slot="icon">volume_up</md-icon>Tap to unmute</md-filled-tonal-button>
      <div class="controls" id="ctrl">
        <md-slider id="seek" min="0" max="1000" step="1" value="0" aria-label="Seek"></md-slider>
        <div class="ctrl-row">
          <md-icon-button id="playBtn" aria-label="Play"><md-icon id="playIcon">play_arrow</md-icon></md-icon-button>
          <md-icon-button id="muteBtn" aria-label="Mute"><md-icon id="muteIcon">volume_up</md-icon></md-icon-button>
          <md-slider id="vol" class="vol" min="0" max="100" step="1" value="100" aria-label="Volume"></md-slider>
          <span class="time" id="time">0:00 / 0:00</span>
          <span class="spacer"></span>
          <md-text-button id="speedBtn">1x</md-text-button>
          <md-text-button id="qualBtn">Auto</md-text-button>
          <md-icon-button id="fullBtn" aria-label="Fullscreen"><md-icon id="fullIcon">fullscreen</md-icon></md-icon-button>
        </div>
      </div>
      <md-menu id="qualMenu" anchor="qualBtn" positioning="popover"></md-menu>
    </div>
    <h2>${'$'}{esc(VID.title)}</h2>
    <div class="row">
      <span id="uav"></span>
      <md-assist-chip href="/channel?user=${'$'}{esc(VID.username)}" label="${'$'}{esc(VID.username)}"></md-assist-chip>
      <md-outlined-button id="followBtn" onclick="toggleFollow()" style="display:none"></md-outlined-button>
      <md-filled-tonal-button id="likeBtn" onclick="like()"><md-icon slot="icon" class="filled" style="display:${'$'}{VID.liked ? '' : 'none'}">&#xE87D;</md-icon><md-icon slot="icon" style="display:${'$'}{VID.liked ? 'none' : ''}">&#xE87E;</md-icon><span id="likeN">${'$'}{fmtFull(VID.likes)}</span></md-filled-tonal-button>
      <span class="muted">${'$'}{fmtFull(VID.views)} views • <span title="${'$'}{esc(fmtDate(VID.created_at))}">${'$'}{fmtAge(VID.created_at)}</span></span>
      <md-outlined-button id="delBtn" style="display:none"><md-icon slot="icon">delete</md-icon><span id="delLbl">Delete</span></md-outlined-button>
      <md-outlined-button id="editBtn" style="display:none"><md-icon slot="icon">edit</md-icon><span>Edit</span></md-outlined-button>
    </div>
    <p class="muted" style="white-space:pre-wrap">${'$'}{esc(VID.description)}</p>`;
  const list = document.getElementById('comments');
  document.getElementById('uav').innerHTML = avatarHtml(VID.avatar, 'pfp-sm');
  cItems = j.comments || [];
  cIdx = 0;
  cCollapsed = true;
  renderComments();
  if (VID.thumbnail) document.getElementById('vid').poster = VID.thumbnail;
  initPlayer(VID.src);
  renderFollow();
  loadUpnext();
}
let replyParent = null;
function startReply(id, username) {
  replyParent = { id, username };
  const box = document.getElementById('cbody');
  box.placeholder = `Reply to @${'$'}{username}...`;
  box.focus();
}
function commentHTML(c) {
  const kids = (c._replies || []).map(commentHTML).join('');
  const who = c.parent_username ? `<span class="muted">↳ @${'$'}{esc(c.parent_username)} </span>` : '';
  const item = `<md-list-item><div slot="start">${'$'}{avatarStatusHtml(c.avatar, 'pfp-sm', c.online)}</div><div slot="headline"><a class="ulink" href="/channel?user=${'$'}{esc(c.username)}">${'$'}{esc(c.username)}</a></div>`
    + `<div slot="supporting-text">${'$'}{who}${'$'}{esc(c.body)}</div>`
    + `<div slot="trailing-supporting-text">${'$'}{esc(c.created_at)} <md-text-button onclick="startReply(${'$'}{c.id}, '${'$'}{esc(c.username)}')">Reply</md-text-button></div></md-list-item>`;
  return item + (kids ? `<div style="margin-left:36px">${'$'}{kids}</div>` : '');
}
function threadComments(items) {
  const byId = new Map(items.map((c) => [c.id, { ...c, _replies: [] }]));
  const top = [];
  byId.forEach((c) => {
    const p = c.parent_id != null ? byId.get(c.parent_id) : null;
    if (p) p._replies.push(c);
    else top.push(c);
  });
  return top;
}
function renderComments() {
  stopCmtTimer();
  const list = document.getElementById('comments');
  const prev = document.getElementById('cpreview');
  const tog = document.getElementById('ctoggle');
  document.getElementById('chead').textContent = cItems.length ? 'Comments (' + cItems.length + ')' : 'Comments';
  if (!cCollapsed) {
    prev.style.display = 'none';
    tog.style.display = '';
    list.style.display = '';
    list.innerHTML = threadComments(cItems).map(commentHTML).join('') || '<p class="muted">No comments yet.</p>';
    return;
  }
  list.style.display = 'none';
  tog.style.display = 'none';
  prev.style.display = '';
  if (!cItems.length) {
    prev.innerHTML = '<p class="muted">No comments yet.</p>';
    return;
  }
  const c = cItems[0];
  prev.innerHTML = `<div class="crow">${'$'}{avatarStatusHtml(c.avatar, 'pfp-sm', c.online)}<div class="metatext"><b>${'$'}{esc(c.username)}</b><p>${'$'}{esc(c.body)}</p></div></div>`
    + `<md-outlined-button id="showAllBtn">Show all ${'$'}{cItems.length} comments</md-outlined-button>`;
  document.getElementById('showAllBtn').onclick = (e) => { e.stopPropagation(); expandComments(); };
}
function nextCmt() {
  cTimer = null;
  if (cCollapsed && cItems.length > 1) {
    cIdx = (cIdx + 1) % cItems.length;
    renderComments();
  }
}
function stopCmtTimer() {
  if (cTimer) { clearTimeout(cTimer); cTimer = null; }
}
function expandComments() {
  stopCmtTimer();
  cCollapsed = false;
  renderComments();
}
function collapseComments() {
  cCollapsed = true;
  cIdx = 0;
  renderComments();
}
let upnextTab = 'new';
let upnextCache = {};
async function loadUpnext() {
  const rail = document.getElementById('rail');
  if (!rail || !VID) return;
  rail.style.display = '';
  const sort = upnextTab === 'pop' ? 'popular' : 'new';
  if (!upnextCache[sort]) {
    try {
      const r = await fetch('/api/videos?sort=' + sort + '&limit=12');
      if (!r.ok) return;
      upnextCache[sort] = (await r.json()).videos || [];
    } catch { return; }
  }
  const box = document.getElementById('upnext');
  if (!box) return;
  box.innerHTML = '';
  upnextCache[sort].filter((v) => v.id !== VID.id).slice(0, 10).forEach((v) => {
    const media = v.thumbnail
      ? `<img class="upthumb" src="${'$'}{v.thumbnail}" loading="lazy" alt="">`
      : `<video class="upthumb" src="${'$'}{v.src}#t=0.5" preload="metadata" muted></video>`;
    const a = document.createElement('a');
    a.className = 'cardlink upnext'; a.href = '/watch?id=' + v.id;
    a.innerHTML = `${'$'}{media}<div class="metatext"><h3>${'$'}{esc(v.title)}</h3>`
      + `<small>${'$'}{esc(v.username)} • ${'$'}{fmtNum(v.views)} views</small></div>`;
    box.appendChild(a);
  });
}
async function renderFollow() {
  const b = document.getElementById('followBtn');
  const u = await me();
  renderDel(u);
  if (!u || (VID && u.id === VID.user_id)) { b.style.display = 'none'; return; }
  b.style.display = '';
  b.innerHTML = `<md-icon slot="icon">${'$'}{VID.following ? 'how_to_reg' : 'person_add'}</md-icon>${'$'}{VID.following ? 'Following' : 'Follow'}`;
}
function renderDel(u) {
  const d = document.getElementById('delBtn');
  const lbl = document.getElementById('delLbl');
  const t = document.getElementById('editBtn');
  d.onclick = null;
  if (!u || !VID) { d.style.display = 'none'; t.style.display = 'none'; return; }
  if (u.id === VID.user_id) {
    d.style.display = '';
    t.style.display = '';
    lbl.textContent = 'Delete';
    d.onclick = del;
  } else if (isAdminView(u)) {
    d.style.display = '';
    t.style.display = '';
    lbl.textContent = 'Delete';
    d.onclick = openDelDialog;
  } else {
    d.style.display = 'none';
    t.style.display = 'none';
  }
  t.onclick = openEditDialog;
}
function openEditDialog() {
  document.getElementById('edTitle').value = VID.title;
  document.getElementById('edDesc').value = VID.description || '';
  document.getElementById('edit-err').textContent = '';
  document.getElementById('edThumb').value = '';
  const prev = document.getElementById('edThumbPrev');
  if (VID.thumbnail) {
    prev.src = VID.thumbnail;
    prev.style.display = '';
  } else {
    prev.removeAttribute('src');
    prev.style.display = 'none';
  }
  document.getElementById('editDialog').show();
}
function closeEditDialog() { document.getElementById('editDialog').close(); }
async function saveEdit() {
  const title = document.getElementById('edTitle').value.trim();
  const err = document.getElementById('edit-err');
  if (!title) { err.textContent = 'Title required'; return; }
  err.textContent = 'Saving…';
  const fd = new FormData();
  fd.append('title', title);
  fd.append('description', document.getElementById('edDesc').value);
  const tf = document.getElementById('edThumb').files[0];
  if (tf) fd.append('thumb', tf);
  let j = {};
  try {
    const r = await fetch('/api/videos/' + id + '/edit', { method: 'POST', credentials: 'include', body: fd });
    j = await r.json();
    if (!r.ok) { err.textContent = j.error || 'Failed'; return; }
  } catch { err.textContent = 'Failed'; return; }
  VID.title = title;
  VID.description = document.getElementById('edDesc').value;
  document.title = title + ' — WatchShark';
  document.querySelector('#wrap h2').textContent = title;
  document.querySelector('#wrap p.muted').textContent = VID.description;
  closeEditDialog();
}
function openDelDialog() {
  document.getElementById('delReason').value = '';
  document.getElementById('del-err').textContent = '';
  document.getElementById('delSub').textContent = 'Deleting "' + VID.title + '" by @' + VID.username + '. The owner will be notified.';
  document.getElementById('delDialog').show();
}
function closeDelDialog() { document.getElementById('delDialog').close(); }
async function adminDel() {
  const reason = document.getElementById('delReason').value.trim();
  if (!reason) { document.getElementById('del-err').textContent = 'Reason required'; return; }
  const r = await fetch('/api/videos/' + id, {
    method: 'DELETE', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify({ reason }),
  });
  const j = await r.json();
  if (!r.ok) { document.getElementById('del-err').textContent = j.error || 'Failed'; return; }
  location.href = '/';
}
async function toggleFollow() {
  const r = await fetch('/api/follow/' + VID.user_id, { method: 'POST', credentials: 'include' });
  const j = await r.json();
  if (!r.ok) return alert(j.error);
  VID.following = j.following;
  VID.followers = j.followers;
  renderFollow();
}
const SPEEDS = [1, 1.25, 1.5, 2, 0.5];
let P = null;
let flashTimer = null;
let cItems = [];
let cIdx = 0;
let cCollapsed = true;
let cTimer = null;
function fmt(t) {
  if (!isFinite(t) || t < 0) t = 0;
  t = Math.floor(t);
  const h = Math.floor(t / 3600), m = Math.floor(t % 3600 / 60), s = t % 60;
  return (h > 0 ? h + ':' + String(m).padStart(2, '0') + ':' : m + ':') + String(s).padStart(2, '0');
}
function initPlayer(src) {
  const vid = document.getElementById('vid');
  vid.src = src;
  P = {
    wrap: document.getElementById('pw'),
    vid: vid,
    seek: document.getElementById('seek'),
    vol: document.getElementById('vol'),
    playIcon: document.getElementById('playIcon'),
    bigIcon: document.getElementById('bigIcon'),
    bigplay: document.getElementById('bigplay'),
    unmute: document.getElementById('unmute'),
    muteIcon: document.getElementById('muteIcon'),
    time: document.getElementById('time'),
    spin: document.getElementById('spin'),
    flash: document.getElementById('flash'),
    speedBtn: document.getElementById('speedBtn'),
    qualBtn: document.getElementById('qualBtn'),
    fullIcon: document.getElementById('fullIcon'),
    speedIdx: 0,
    hideTimer: null,
    flashing: false,
    wantAuto: true,
    forceMuted: false
  };
  vid.addEventListener('play', onPlayState);
  vid.addEventListener('pause', onPlayState);
  vid.addEventListener('ended', onPlayState);
  vid.addEventListener('timeupdate', onTime);
  vid.addEventListener('loadedmetadata', onTime);
  vid.addEventListener('waiting', () => { if (P) { P.spin.style.display = ''; P.wrap.classList.add('loading'); } });
  vid.addEventListener('seeking', () => { if (P) { P.spin.style.display = ''; P.wrap.classList.add('loading'); } });
  vid.addEventListener('playing', () => { if (P) { P.spin.style.display = 'none'; P.wrap.classList.remove('loading'); } });
  vid.addEventListener('canplay', () => { if (P) { P.spin.style.display = 'none'; P.wrap.classList.remove('loading'); } });
  vid.addEventListener('click', onVideoClick);
  P.seek.addEventListener('input', () => {
    if (vid.duration) vid.currentTime = P.seek.value / 1000 * vid.duration;
  });
  P.seek.addEventListener('pointerdown', () => { P.seeking = true; });
  Pjax.on(window, 'pointerup', () => { if (P) P.seeking = false; });
  vid.addEventListener('dblclick', (e) => {
    e.preventDefault();
    if (isTouchDevice()) return;
    clearTimeout(clickTimer);
    toggleFull();
  });
  P.vol.addEventListener('input', () => {
    vid.volume = P.vol.value / 100;
    vid.muted = vid.volume === 0;
    if (!vid.muted) { P.forceMuted = false; P.unmute.style.display = 'none'; }
    syncMuteIcon();
  });
  document.getElementById('playBtn').addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
  document.getElementById('muteBtn').addEventListener('click', (e) => { e.stopPropagation(); toggleMute(); });
  document.getElementById('fullBtn').addEventListener('click', (e) => { e.stopPropagation(); toggleFull(); });
  P.speedBtn.addEventListener('click', (e) => { e.stopPropagation(); cycleSpeed(); });
  P.qualBtn.addEventListener('click', (e) => { e.stopPropagation(); openQualityMenu(); });
  applyAutoQuality();
  P.bigplay.addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
  P.unmute.addEventListener('click', (e) => { e.stopPropagation(); unmuteGesture(); });
  P.wrap.addEventListener('pointerdown', () => unmuteGesture(), { passive: true });
  P.wrap.addEventListener('mousemove', poke);
  P.wrap.addEventListener('touchstart', (e) => { tapMoved = false; poke(); }, { passive: true });
  P.wrap.addEventListener('touchmove', () => { tapMoved = true; }, { passive: true });
  P.wrap.addEventListener('keydown', onKey);
  Pjax.on(document, 'fullscreenchange', syncFullIcon);
  bindDoubleTap(document.getElementById('gleft'), -10);
  bindDoubleTap(document.getElementById('gright'), 10);
  syncMuteIcon();
  onTime();
  poke();
  autoPlay();
}
function autoPlay() {
  if (!P) return;
  P.wantAuto = true;
  P.forceMuted = false;
  P.vid.muted = false;
  syncMuteIcon();
  P.unmute.style.display = 'none';
  P.vid.play().then(() => {
    if (P) P.unmute.style.display = 'none';
  }).catch(() => {
    if (!P) return;
    P.bigIcon.textContent = 'play_arrow';
    P.bigplay.style.display = '';
  });
}
function unmuteGesture() {
  if (!P || !P.forceMuted) return;
  P.forceMuted = false;
  P.vid.muted = false;
  syncMuteIcon();
  P.unmute.style.display = 'none';
  P.vid.play().catch(() => {});
}
function isTouchDevice() {
  return matchMedia('(pointer: coarse)').matches;
}
let tapMoved = false;
let clickTimer = null;
function onVideoClick() {
  if (isTouchDevice()) {
    if (tapMoved) return;
    toggleControls();
    return;
  }
  clearTimeout(clickTimer);
  clickTimer = setTimeout(() => togglePlay(), 260);
}
function toggleControls() {
  if (!P) return;
  if (P.wrap.classList.contains('hide')) {
    poke();
  } else {
    P.wrap.classList.add('hide');
    clearTimeout(P.hideTimer);
  }
}
function togglePlay() {
  if (!P) return;
  P.wantAuto = false;
  if (P.vid.paused || P.vid.ended) {
    if (P.forceMuted) {
      P.forceMuted = false;
      P.vid.muted = false;
      syncMuteIcon();
      P.unmute.style.display = 'none';
    }
    P.vid.play().catch(() => {});
  }
  else { P.vid.pause(); }
  poke();
}
function onPlayState() {
  if (!P) return;
  const playing = !P.vid.paused && !P.vid.ended;
  P.playIcon.textContent = playing ? 'pause' : 'play_arrow';
  P.bigIcon.textContent = P.vid.ended ? 'replay' : 'play_arrow';
  if (!P.flashing) P.bigplay.style.display = playing ? 'none' : '';
  poke();
}
function onTime() {
  if (!P) return;
  const d = P.vid.duration || 0, c = P.vid.currentTime || 0;
  if (!P.seeking) P.seek.value = d > 0 ? Math.round(c / d * 1000) : 0;
  P.time.textContent = fmt(c) + ' / ' + fmt(d);
}
function poke() {
  if (!P) return;
  P.wrap.classList.remove('hide');
  clearTimeout(P.hideTimer);
  if (!P.vid.paused && !P.vid.ended) P.hideTimer = setTimeout(() => P.wrap.classList.add('hide'), 2800);
}
function toggleMute() {
  P.vid.muted = !P.vid.muted;
  if (!P.vid.muted && P.vid.volume === 0) { P.vid.volume = 0.5; P.vol.value = 50; }
  if (!P.vid.muted) { P.forceMuted = false; P.unmute.style.display = 'none'; }
  syncMuteIcon();
  poke();
}
function syncMuteIcon() {
  P.muteIcon.textContent = (P.vid.muted || P.vid.volume === 0) ? 'volume_off' : 'volume_up';
}
function cycleSpeed() {
  P.speedIdx = (P.speedIdx + 1) % SPEEDS.length;
  P.vid.playbackRate = SPEEDS[P.speedIdx];
  P.speedBtn.textContent = SPEEDS[P.speedIdx] + 'x';
  poke();
}
function dynRend(o, src) {
  const m = src && src.match(/\/v\/(.+)\.[a-z0-9]+${'$'}/i);
  if (!m) return o;
  const stem = m[1].replace(/-(720|480|360)p${'$'}/, '');
  for (const r of ['720p', '480p', '360p']) {
    if (!o.some((x) => x.label === r)) o.push({ label: r, src: `/v/${'$'}{stem}-${'$'}{r}.webm` });
  }
  return o;
}
function qualOptions() {
  const o = [{ label: 'Auto', src: null }];
  if (VID.renditions) {
    if (VID.renditions['720p']) o.push({ label: '720p', src: VID.renditions['720p'] });
    if (VID.renditions['480p']) o.push({ label: '480p', src: VID.renditions['480p'] });
    if (VID.renditions['360p']) o.push({ label: '360p', src: VID.renditions['360p'] });
  }
  dynRend(o, VID.src);
  o.push({ label: 'Source', src: VID.src });
  return o;
}
function applyAutoQuality() {
  const o = qualOptions();
  let pick = o.find((x) => x.label === '720p') || o.find((x) => x.label === '480p') || o.find((x) => x.label === '360p') || o[o.length - 1];
  pick = pick.src;
  try {
    const c = navigator.connection;
    if (c && (c.saveData || c.effectiveType === 'slow-2g' || c.effectiveType === '2g')) {
      const low = o.find((x) => x.label === '360p') || o.find((x) => x.label === '480p') || o.find((x) => x.label === '720p');
      if (low) pick = low.src;
    }
  } catch { /* keep rendition pick */ }
  setQualitySrc(pick, 'Auto');
}
function setQualitySrc(src, label) {
  if (!src || P.vid.currentSrc.endsWith(src)) {
    P.qualBtn.textContent = qualDisplay(label);
    return;
  }
  const t = P.vid.currentTime;
  const playing = !P.vid.paused;
  P.vid.src = src;
  P.vid.currentTime = t;
  if (playing) P.vid.play().catch(() => {});
  P.qualBtn.textContent = qualDisplay(label);
  poke();
}
function qualDisplay(label) {
  if (label === 'Auto' || label === 'Source') return label;
  const n = parseInt(label, 10);
  if (n >= 720) return label + ' HD';
  return label;
}
function openQualityMenu() {
  const m = document.getElementById('qualMenu');
  const o = qualOptions();
  const cur = P.qualBtn.textContent;
  m.innerHTML = '<md-list>' + o.map((opt, i) =>
    `<md-list-item onclick="applyQualityOpt(${'$'}{i})">`
    + `<div slot="headline">${'$'}{esc(qualDisplay(opt.label))}</div>`
    + (cur === qualDisplay(opt.label) ? '<md-icon slot="end">check</md-icon>' : '')
    + '</md-list-item>').join('') + '</md-list>';
  m.show();
}
function applyQualityOpt(i) {
  const o = qualOptions();
  const opt = o[i % o.length];
  document.getElementById('qualMenu').close();
  setQualitySrc(opt.src, opt.label);
}
function toggleFull() {
  if (document.fullscreenElement) { document.exitFullscreen().catch(() => {}); return; }
  if (P.wrap.requestFullscreen) P.wrap.requestFullscreen().catch(() => {});
  else if (P.vid.webkitEnterFullscreen) P.vid.webkitEnterFullscreen();
  poke();
}
function syncFullIcon() {
  if (P) P.fullIcon.textContent = document.fullscreenElement ? 'fullscreen_exit' : 'fullscreen';
}
function nudge(d) {
  P.vid.currentTime = Math.min(Math.max(0, P.vid.currentTime + d), P.vid.duration || 0);
  flash((d > 0 ? '+' : '') + d + 's');
  poke();
}
function bumpVolume(d) {
  P.vid.muted = false;
  P.vid.volume = Math.min(1, Math.max(0, P.vid.volume + d));
  P.vol.value = Math.round(P.vid.volume * 100);
  syncMuteIcon();
  poke();
}
function flash(txt) {
  P.flashing = true;
  P.bigplay.style.display = 'none';
  P.flash.textContent = txt;
  P.flash.style.display = '';
  clearTimeout(flashTimer);
  flashTimer = setTimeout(() => {
    P.flash.style.display = 'none';
    P.flashing = false;
    if (P.vid.paused || P.vid.ended) P.bigplay.style.display = '';
  }, 600);
}
function onKey(e) {
  if (!P) return;
  const tag = (e.target && e.target.tagName) || '';
  if (tag !== 'DIV' && tag !== 'VIDEO' && tag !== 'BODY') return;
  const k = e.key;
  if (k === ' ' || k === 'k') { e.preventDefault(); togglePlay(); }
  else if (k === 'ArrowRight') { e.preventDefault(); nudge(10); }
  else if (k === 'ArrowLeft') { e.preventDefault(); nudge(-10); }
  else if (k === 'ArrowUp') { e.preventDefault(); bumpVolume(0.1); }
  else if (k === 'ArrowDown') { e.preventDefault(); bumpVolume(-0.1); }
  else if (k === 'f') toggleFull();
  else if (k === 'm') toggleMute();
}
function bindDoubleTap(el, d) {
  let last = 0;
  el.addEventListener('touchend', (e) => {
    const now = Date.now();
    if (now - last < 320) { e.preventDefault(); nudge(d); }
    last = now;
  }, { passive: false });
  el.addEventListener('click', (e) => {
    e.stopPropagation();
    if (isTouchDevice()) {
      if (!tapMoved) toggleControls();
      return;
    }
    clearTimeout(clickTimer);
    clickTimer = setTimeout(() => togglePlay(), 260);
  });
  el.addEventListener('dblclick', (e) => {
    e.stopPropagation();
    if (isTouchDevice()) nudge(d);
    else { clearTimeout(clickTimer); toggleFull(); }
  });
}
async function like() {
  if (!(await me())) return openAuth('login');
  const r = await fetch(`/api/videos/${'$'}{id}/like`, { method: 'POST', credentials: 'include' });
  const j = await r.json();
  if (!r.ok) return alert(j.error);
  VID.liked = j.liked;
  VID.likes = j.likes;
  document.getElementById('likeBtn').innerHTML = `<md-icon slot="icon" class="filled" style="display:${'$'}{j.liked ? '' : 'none'}">&#xE87D;</md-icon><md-icon slot="icon" style="display:${'$'}{j.liked ? 'none' : ''}">&#xE87E;</md-icon><span id="likeN">${'$'}{fmtFull(j.likes)}</span>`;
}
async function del() {
  if (!confirm('Delete this video?')) return;
  const r = await fetch('/api/videos/' + id, { method: 'DELETE', credentials: 'include' });
  const j = await r.json();
  if (!r.ok) return alert(j.error);
  location.href = '/';
}
async function sendComment() {
  if (!(await me())) return openAuth('login');
  const body = document.getElementById('cbody').value;
  const payload = { body };
  if (replyParent) payload.parent_id = replyParent.id;
  const r = await fetch(`/api/videos/${'$'}{id}/comments`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'include', body: JSON.stringify(payload),
  });
  const j = await r.json();
  if (!r.ok) return alert(j.error);
  document.getElementById('cbody').value = '';
  document.getElementById('cbody').placeholder = 'Add a comment...';
  replyParent = null;
  init();
}
document.getElementById('cbody').addEventListener('keydown', (e) => { if (e.key === 'Enter') sendComment(); });
document.getElementById('cpreview').onclick = expandComments;
document.getElementById('ctoggle').onclick = collapseComments;
segTabs(document.getElementById('upnextTabs'), (btn) => {
  upnextTab = btn.dataset.tab || 'new';
  loadUpnext();
});
init();
window.__destroy = function() {
  destroyPlayer();
};
Object.assign(window, { like, del, toggleFollow, sendComment, startReply, openDelDialog, closeDelDialog, adminDel, applyQualityOpt, openEditDialog, closeEditDialog, saveEdit });
};
window.__boot();
hideBoot();
}"""
    val WHEELS_MAIN = """
  <div class="reel" id="reel" tabindex="0">
    <div class="swiper" id="wswiper"><div class="swiper-wrapper" id="wwrap"></div></div>
    <md-circular-progress id="rspin" class="rspinner" indeterminate style="display:none"></md-circular-progress>
    <md-fab id="rbig" class="rbig" variant="primary" size="large" aria-label="Play" style="display:none"><md-icon slot="icon" id="rbigIcon">play_arrow</md-icon></md-fab>
    <div class="reel-rail" id="rrail" style="display:none">
      <div class="railitem"><md-icon-button id="rlikeBtn" aria-label="Like"><md-icon id="rlikeOn" class="filled" style="display:none">&#xE87D;</md-icon><md-icon id="rlikeOff">&#xE87E;</md-icon></md-icon-button><span id="rlikeN" style="display:none">0</span></div>
      <div class="railitem"><md-icon-button id="rfollowBtn" aria-label="Follow"><md-icon id="rfollowIcon">person_add</md-icon></md-icon-button></div>
      <div class="railitem"><md-icon-button id="rmuteBtn" aria-label="Mute"><md-icon id="rmuteIcon">volume_up</md-icon></md-icon-button></div>
      <div class="railitem"><md-icon-button id="rprevBtn" aria-label="Previous"><md-icon>skip_previous</md-icon></md-icon-button></div>
      <div class="railitem"><md-icon-button id="rnextBtn" aria-label="Next"><md-icon>skip_next</md-icon></md-icon-button></div>
      <div class="railitem"><md-icon-button id="rfullBtn" aria-label="Fullscreen"><md-icon id="rfullIcon">fullscreen</md-icon></md-icon-button></div>
      <div class="railitem"><md-icon-button id="rsetBtn" aria-label="Quality"><md-icon>settings</md-icon></md-icon-button><span id="rqualN">Auto</span></div>
    </div>
    <md-menu id="rsetMenu" anchor="rsetBtn" positioning="popover"></md-menu>
    <div class="gesture left" id="rgleft"></div>
    <div class="gesture right" id="rgright"></div>
    <div class="seek-flash" id="rflash" style="display:none"></div>
    <div class="reel-meta" id="rmeta" style="display:none">
      <h3 id="rtitle"></h3>
      <div class="row">
        <span id="ruav"></span><b id="ruser"></b><span class="muted" id="rfollowers"></span><span class="muted" id="rviews"></span><span class="muted" id="rtime"></span>
        <md-text-button id="ropen">Comments<md-icon slot="icon">open_in_new</md-icon></md-text-button>
      </div>
    </div>
    <md-slider id="rseek" class="rseek" min="0" max="1000" step="1" value="0" aria-label="Seek"></md-slider>
    <div id="rempty" class="rempty" style="display:none">
      <h2>No videos yet</h2>
      <md-filled-button href="/">Discover</md-filled-button>
    </div>
  </div>"""
    val WHEELS_NAVSCRIPT = """
(function() {
  function mark(tab) {
    document.querySelectorAll('#bottomnav [data-tab]').forEach((a) => {
      a.classList.toggle('active', a.dataset.tab === tab);
    });
  }
  const path = location.pathname;
  if (path === '/') mark('home');
  else if (path === '/wheels') mark('wheels');
  else if (path === '/messages') mark('messages');
  else if (path === '/settings' || path === '/admin') mark('account');
  const acc = document.getElementById('navAccount');
  const navAv = document.getElementById('navAvatar');
  fetch('/api/me', { credentials: 'include' }).then((r) => r.json()).then((j) => {
    if (j && j.user) {
      acc.href = '/channel?user=' + encodeURIComponent(j.user.username);
      acc.dataset.user = j.user.username;
      if (navAv) navAv.innerHTML = avatarHtml(j.user.avatar, 'navavatar');
      const u = new URLSearchParams(location.search).get('user');
      if (path.startsWith('/channel') && u === j.user.username) mark('account');
    }
  }).catch(() => {});
  acc.addEventListener('click', (e) => {
    if (!acc.dataset.user) {
      e.preventDefault();
      if (typeof openAuth === 'function') openAuth('login');
      else location.href = '/';
    }
  });
})();"""
    val WHEELS_TAIL = """
{
window.__boot = function() {
// single-init guard: a second boot on the same DOM would create a second
// Swiper (duplicate slides, videos that can't be paused)
if (window.__wheelsAlive) return;
window.__wheelsAlive = true;
let VID = null;
let VDATA = [];
let seen = [];
let SW = null;
let mutedAll = false;
let loadingBatch = false;
let exhausted = false;
function activeVideo() {
  if (!SW) return null;
  const slides = document.getElementById('wwrap').children;
  const el = slides[SW.activeIndex];
  return el ? el.querySelector('video') : null;
}
function pauseOthers() {
  const cur = activeVideo();
  document.querySelectorAll('#wwrap video').forEach((v) => {
    if (v !== cur && !v.paused) v.pause();
  });
}
function loadSeen() {
  try {
    const a = JSON.parse(localStorage.getItem('ws_seen') || '[]');
    if (Array.isArray(a)) seen = a.filter((n) => Number.isInteger(n) && n > 0).slice(-400);
  } catch { seen = []; }
}
function saveSeen() {
  try { localStorage.setItem('ws_seen', JSON.stringify(seen.slice(-400))); } catch {}
}
async function fetchBatch(n) {
  const q = seen.length ? '?seen=' + seen.slice(-128).join(',') : '';
  const sep = q ? '&' : '?';
  try {
    const r = await fetch('/api/wheels' + q + sep + 'limit=' + n);
    if (!r.ok) return [];
    const j = await r.json();
    if (Array.isArray(j.videos)) return j.videos;
    return j.video ? [j.video] : [];
  } catch { return []; }
}
function wireVideo(v) {
  v.muted = mutedAll;
  v.addEventListener('waiting', () => {
    if (v === activeVideo()) document.getElementById('rspin').style.display = '';
  });
  v.addEventListener('playing', () => {
    if (v !== activeVideo()) return;
    pauseOthers();
    document.getElementById('rspin').style.display = 'none';
    document.getElementById('rbig').style.display = 'none';
  });
  v.addEventListener('timeupdate', () => {
    if (v === activeVideo()) onTimeReel();
  });
  v.addEventListener('loadedmetadata', () => {
    if (v === activeVideo()) onTimeReel();
  });
  v.addEventListener('error', () => {
    if (v.dataset.stepped) return;
    const wrap = v.closest('.swiper-slide');
    const sibs = wrap && wrap.parentNode ? wrap.parentNode.children : [];
    const idx = wrap ? Array.prototype.indexOf.call(sibs, wrap) : -1;
    const vid = (idx >= 0 && VDATA[idx]) || VID;
    const r = (vid && vid.renditions) || {};
    const low = r['360p'] || r['480p'] || r['720p'];
    if (!low) return;
    const cur = v.currentSrc || v.src || '';
    if (cur === low || cur.endsWith(low)) return;
    v.dataset.stepped = '1';
    try { v.src = low; v.dataset.src = low; v.load(); v.play().catch(() => {}); } catch {}
  });
}
function appendOne(j) {
  if (!SW || !j || !j.video || seen.includes(j.video.id)) return false;
  seen.push(j.video.id);
  saveSeen();
  VDATA.push(j.video);
  const d = document.createElement('div');
  const ori = j.video.orientation === 'h' ? 'h' : 'v';
  d.className = 'swiper-slide ' + ori + 'slide';
  const thumb = j.video.thumbnail || '';
  // Best rendition up front (progressive streaming shows the first frame as soon
  // as chunks arrive); sharp poster paints instantly. No blurred backdrop.
  const first = reelAutoSrc(j.video) || j.video.src;
  d.innerHTML = `<video playsinline preload="metadata" poster="${'$'}{thumb}" data-src="${'$'}{first}"></video>`;
  const v = d.querySelector('video');
  wireVideo(v);
  SW.appendSlide(d);
  return true;
}
async function loadBatch() {
  if (loadingBatch || exhausted) return false;
  loadingBatch = true;
  let added = 0;
  // single round-trip for the whole batch (was: one request per video)
  const vids = await fetchBatch(5);
  for (const vd of vids) {
    if (appendOne({ video: vd })) added++;
  }
  if (!added) exhausted = true;
  if (exhausted && !endShown) showEndCard();
  if (SW) SW.update();
  loadingBatch = false;
  return added > 0;
}
let endShown = false;
let endCardEl = null;
let lastWatchAgain = 0;
function showEndCard() {
  if (!SW || endShown) return;
  endShown = true;
  const d = document.createElement('div');
  d.className = 'swiper-slide';
  d.innerHTML = `<div class="rempty" style="position:absolute;inset:0;pointer-events:auto"><h2>You watched all wheels</h2><p class="muted">Fresh wheels, same button.</p><md-filled-button id="watchAgainBtn" style="pointer-events:auto;cursor:pointer">Watch again</md-filled-button></div>`;
  const btn = d.querySelector('#watchAgainBtn');
  const go = (e) => {
    if (e) { e.preventDefault(); e.stopPropagation(); }
    const now = Date.now();
    if (now - lastWatchAgain < 1500) return;
    lastWatchAgain = now;
    watchAgain();
  };
  btn.addEventListener('click', go);
  btn.addEventListener('touchend', go, { passive: false });
  endCardEl = d;
  SW.appendSlide(d);
  SW.update();
}
async function watchAgain() {
  pauseOthers();
  seen = [];
  try { localStorage.removeItem('ws_seen'); } catch {}
  VDATA = [];
  exhausted = false;
  endShown = false;
  if (SW) {
    SW.removeAllSlides();
    SW.update();
  }
  endCardEl = null;
  document.getElementById('rspin').style.display = '';
  await loadBatch();
  document.getElementById('rspin').style.display = 'none';
  if (SW && SW.slides.length) {
    SW.slideTo(0, 0);
    onSlide();
  }
}
function trimFarSlides() {
  if (!SW) return;
  const slides = document.getElementById('wwrap').children;
  for (let i = 0; i < slides.length; i++) {
    if (Math.abs(i - SW.activeIndex) > 2) {
      const w = slides[i].querySelector('video');
      if (w && w.src) {
        try { w.pause(); } catch {}
        w.removeAttribute('src');
        try { w.load(); } catch {}
      }
    }
  }
}
function onSlide() {
  VID = VDATA[SW.activeIndex] || null;
  if (!VID) {
    pauseOthers();
    document.getElementById('rrail').style.display = 'none';
    document.getElementById('rmeta').style.display = 'none';
    document.getElementById('rspin').style.display = 'none';
    document.getElementById('rbig').style.display = 'none';
    document.getElementById('rseek').value = 0;
    return;
  }
  renderReelMeta();
  document.getElementById('rrail').style.display = '';
  document.getElementById('rmeta').style.display = '';
  const v = activeVideo();
  if (v) {
    if (!v.src && v.dataset.src) {
      v.preload = 'auto';
      v.src = v.dataset.src;
      v.load();
    }
    v.muted = false;
    document.getElementById('rmuteIcon').textContent = 'volume_up';
    applyReelAutoQuality();
    onTimeReel();
    v.play().then(() => {
      document.getElementById('rbig').style.display = 'none';
    }).catch(() => {
      document.getElementById('rbigIcon').textContent = 'play_arrow';
      document.getElementById('rbig').style.display = '';
    });
  }
  if (SW.activeIndex >= SW.slides.length - 3) loadBatch();
  warmNeighbors();
  trimFarSlides();
}
function warmNeighbors() {
  if (!SW) return;
  const slides = document.getElementById('wwrap').children;
  [SW.activeIndex - 2, SW.activeIndex - 1, SW.activeIndex + 1, SW.activeIndex + 2].forEach((i) => {
    const s = slides[i];
    const w = s ? s.querySelector('video') : null;
    if (w && !w.src && w.dataset.src) {
      w.preload = 'auto';
      w.src = w.dataset.src;
      w.load();
    }
  });
}
const RSPEEDS = [1, 1.25, 1.5, 2, 0.5];
let rspdIdx = 0;
let rSeeking = false;
let rClickTimer = null;
let rFlashTimer = null;
let reelWheelT = 0;
let reelForced = false;
function fmtR(t) {
  if (!isFinite(t) || t < 0) t = 0;
  t = Math.floor(t);
  return Math.floor(t / 60) + ':' + String(t % 60).padStart(2, '0');
}
function isTouchDevice() {
  return matchMedia('(pointer: coarse)').matches;
}
function onTimeReel() {
  const v = activeVideo();
  const d = (v && v.duration) || 0, c = (v && v.currentTime) || 0;
  if (!rSeeking) document.getElementById('rseek').value = d > 0 ? Math.round(c / d * 1000) : 0;
  document.getElementById('rtime').textContent = fmtR(c) + ' / ' + fmtR(d);
}
function reelQualDisplay(label) {
  if (label === 'Auto' || label === 'Source') return label;
  const n = parseInt(label, 10);
  if (n >= 720) return label + ' HD';
  return label;
}
function reelQualOptions() {
  const o = [{ label: 'Auto', src: null }];
  if (VID && VID.renditions) {
    if (VID.renditions['720p']) o.push({ label: '720p', src: VID.renditions['720p'] });
    if (VID.renditions['480p']) o.push({ label: '480p', src: VID.renditions['480p'] });
    if (VID.renditions['360p']) o.push({ label: '360p', src: VID.renditions['360p'] });
  }
  if (VID) {
    const m = (VID.src || '').match(/\/v\/(.+)\.[a-z0-9]+${'$'}/i);
    if (m) {
      const stem = m[1].replace(/-(720|480|360)p${'$'}/, '');
      for (const r of ['720p', '480p', '360p']) {
        if (!o.some((x) => x.label === r)) o.push({ label: r, src: `/v/${'$'}{stem}-${'$'}{r}.webm` });
      }
    }
  }
  if (VID) o.push({ label: 'Source', src: VID.src });
  return o;
}
function setReelQualitySrc(src, label) {
  const v = activeVideo();
  document.getElementById('rqualN').textContent = reelQualDisplay(label);
  if (!v || !VID || !src) return;
  const cur = v.currentSrc || v.src || '';
  if (cur && (cur === src || cur.endsWith(src))) return;
  const t = v.currentTime;
  const playing = !v.paused;
  v.src = src;
  v.dataset.src = src;
  v.currentTime = t;
  if (playing) v.play().catch(() => {});
}
function applyReelAutoQuality() {
  setReelQualitySrc(reelAutoSrc(VID), 'Auto');
}
/** Best encoded rendition for a video object, falling back to Source. */
function reelAutoSrc(vid) {
  if (!vid) return null;
  const r = vid.renditions || {};
  try {
    const cn = navigator.connection;
    if (cn && (cn.saveData || cn.effectiveType === 'slow-2g' || cn.effectiveType === '2g')) {
      return r['360p'] || r['480p'] || r['720p'] || vid.src;
    }
  } catch {}
  return r['720p'] || r['480p'] || r['360p'] || vid.src;
}
function openRsetMenu() {
  if (!VID) return;
  const m = document.getElementById('rsetMenu');
  const o = reelQualOptions();
  const cur = document.getElementById('rqualN').textContent;
  m.innerHTML = '<div class="pad"><md-text-button id="rspdBtn">Speed: ' + RSPEEDS[rspdIdx] + 'x</md-text-button></div><md-list>'
    + o.map((opt) =>
      `<md-list-item><div slot="headline">${'$'}{esc(reelQualDisplay(opt.label))}</div>`
      + (cur === reelQualDisplay(opt.label) ? '<md-icon slot="end">check</md-icon>' : '')
      + '</md-list-item>').join('') + '</md-list>';
  m.querySelector('#rspdBtn').addEventListener('click', () => {
    cycleReelSpeed();
    m.querySelector('#rspdBtn').textContent = 'Speed: ' + RSPEEDS[rspdIdx] + 'x';
  });
  m.querySelectorAll('md-list-item').forEach((it, i) => it.addEventListener('click', () => {
    const opts = reelQualOptions();
    const opt = opts[i % opts.length];
    m.close();
    setReelQualitySrc(opt.src, opt.label);
  }));
  m.show();
}
function cycleReelSpeed() {
  rspdIdx = (rspdIdx + 1) % RSPEEDS.length;
  const v = activeVideo();
  if (v) v.playbackRate = RSPEEDS[rspdIdx];
}
function toggleReelFull() {
  const el = document.getElementById('reel');
  if (document.fullscreenElement) { document.exitFullscreen().catch(() => {}); return; }
  if (el.requestFullscreen) el.requestFullscreen().catch(() => {});
}
function syncReelFullIcon() {
  document.getElementById('rfullIcon').textContent = document.fullscreenElement ? 'fullscreen_exit' : 'fullscreen';
}
function reelFlash(txt) {
  const f = document.getElementById('rflash');
  f.textContent = txt;
  f.style.display = '';
  clearTimeout(rFlashTimer);
  rFlashTimer = setTimeout(() => { f.style.display = 'none'; }, 600);
}
function reelNudge(d) {
  const v = activeVideo();
  if (!v || !VID) return;
  v.currentTime = Math.min(Math.max(0, v.currentTime + d), v.duration || 0);
  reelFlash((d > 0 ? '+' : '') + d + 's');
}
function bindReelTap(el, d) {
  let last = 0;
  el.addEventListener('touchend', (e) => {
    const now = Date.now();
    if (now - last < 320) { e.preventDefault(); reelNudge(d); }
    last = now;
  }, { passive: false });
  el.addEventListener('click', (e) => {
    e.stopPropagation();
    if (isTouchDevice()) return;
    clearTimeout(rClickTimer);
    rClickTimer = setTimeout(() => toggleReelPlay(), 260);
  });
  el.addEventListener('dblclick', (e) => {
    e.stopPropagation();
    if (isTouchDevice()) reelNudge(d);
    else { clearTimeout(rClickTimer); toggleReelFull(); }
  });
}
async function initWheels() {
  await refreshAuth();
  loadSeen();
  if (typeof Swiper === 'undefined') {
    document.getElementById('rspin').style.display = 'none';
    document.getElementById('rempty').style.display = '';
    document.getElementById('rempty').innerHTML = '<h2>Player failed to load</h2><p class="muted">Check connection and retry.</p><md-filled-button onclick="location.reload()">Retry</md-filled-button>';
    return;
  }
  SW = new Swiper('#wswiper', {
    direction: 'vertical',
    effect: 'slide',
    speed: 220,
    resistanceRatio: 0.75,
    mousewheel: true,
  });
  SW.on('slideChange', onSlide);
  SW.on('tap', () => toggleReelPlay());
  SW.on('reachEnd', () => loadBatch());
  document.getElementById('rspin').style.display = '';
  const ok = await loadBatch();
  document.getElementById('rspin').style.display = 'none';
  if (!ok || !VDATA.length) {
    document.getElementById('rempty').style.display = '';
    return;
  }
  onSlide();
}
function syncReelLike() {
  document.getElementById('rlikeOn').style.display = VID.liked ? '' : 'none';
  document.getElementById('rlikeOff').style.display = VID.liked ? 'none' : '';
  const n = document.getElementById('rlikeN');
  n.textContent = VID.likes;
  n.style.display = VID.likes ? '' : 'none';
}
function renderReelMeta() {
  document.getElementById('rtitle').textContent = VID.title;
  document.getElementById('ruser').innerHTML = `<a class="ulink" href="/channel?user=${'$'}{esc(VID.username)}">@${'$'}{esc(VID.username)}</a>`;
  document.getElementById('rviews').textContent = fmtNum(VID.views) + ' views';
  document.getElementById('ruav').innerHTML = avatarHtml(VID.avatar, 'pfp-sm');
  syncReelLike();
  document.getElementById('rfollowIcon').textContent = VID.following ? 'how_to_reg' : 'person_add';
  document.getElementById('rfollowers').textContent = VID.followers === 1 ? '1 follower' : VID.followers + ' followers';
  document.getElementById('ropen').setAttribute('href', '/watch?id=' + VID.id);
  toggleFollowVis();
}
async function toggleFollowVis() {
  const u = await me();
  document.getElementById('rfollowBtn').style.display = (!u || (VID && u.id === VID.user_id)) ? 'none' : '';
}
function nextReel() { if (SW) SW.slideNext(); }
function prevReel() { if (SW) SW.slidePrev(); }
function toggleReelPlay() {
  const v = activeVideo();
  if (!VID || !v) return;
  if (!v.src && v.dataset.src) {
    v.src = v.dataset.src;
    v.load();
  }
  if (v.paused || v.ended) {
    pauseOthers();
    if (reelForced && v.muted) {
      reelForced = false;
      v.muted = false;
      document.getElementById('rmuteIcon').textContent = 'volume_up';
    }
    v.play().then(() => {
      document.getElementById('rbig').style.display = 'none';
    }).catch(() => {});
  } else {
    v.pause();
    document.getElementById('rbigIcon').textContent = 'play_arrow';
    document.getElementById('rbig').style.display = '';
  }
}
let reelBusy = false;
async function reelLike() {
  if (reelBusy || !VID) return;
  if (!(await me())) { location.href = '/watch?id=' + VID.id; return; }
  reelBusy = true;
  const target = VID;
  const was = target.liked;
  target.liked = !was;
  target.likes = (target.likes || 0) + (target.liked ? 1 : -1);
  if (target.likes < 0) target.likes = 0;
  if (VID === target) syncReelLike();
  try {
    const r = await fetch('/api/videos/' + target.id + '/like', { method: 'POST', credentials: 'include' });
    const j = await r.json();
    if (!r.ok) throw 0;
    target.liked = j.liked;
    target.likes = j.likes;
  } catch {
    target.liked = was;
  }
  if (VID === target) syncReelLike();
  reelBusy = false;
}
async function reelFollow() {
  if (reelBusy || !VID) return;
  if (!(await me())) { location.href = '/watch?id=' + VID.id; return; }
  reelBusy = true;
  const target = VID;
  try {
    const r = await fetch('/api/follow/' + target.user_id, { method: 'POST', credentials: 'include' });
    const j = await r.json();
    if (!r.ok) return;
    target.following = j.following;
    target.followers = j.followers;
    if (VID !== target) return;
    document.getElementById('rfollowIcon').textContent = j.following ? 'how_to_reg' : 'person_add';
    document.getElementById('rfollowers').textContent = j.followers === 1 ? '1 follower' : j.followers + ' followers';
  } finally {
    reelBusy = false;
  }
}
function reelMute() {
  const v = activeVideo();
  if (!v) return;
  reelForced = false;
  v.muted = !v.muted;
  mutedAll = v.muted;
  document.getElementById('rmuteIcon').textContent = v.muted ? 'volume_off' : 'volume_up';
}
(function wireReel() {
  Pjax.on(document, 'keydown', (e) => {
    const t = (e.target && e.target.tagName) || '';
    if (t === 'INPUT' || t === 'TEXTAREA' || t === 'BUTTON' || t.startsWith('MD-')) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); nextReel(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); prevReel(); }
    else if (e.key === ' ') { e.preventDefault(); toggleReelPlay(); }
    else if (e.key === 'ArrowRight') { e.preventDefault(); reelNudge(10); }
    else if (e.key === 'ArrowLeft') { e.preventDefault(); reelNudge(-10); }
    else if (e.key === 'f') toggleReelFull();
    else if (e.key === 'm') reelMute();
  });
  document.getElementById('rbig').addEventListener('click', toggleReelPlay);
  document.getElementById('rlikeBtn').addEventListener('click', reelLike);
  document.getElementById('rfollowBtn').addEventListener('click', reelFollow);
  document.getElementById('rmuteBtn').addEventListener('click', reelMute);
  document.getElementById('rnextBtn').addEventListener('click', nextReel);
  document.getElementById('rprevBtn').addEventListener('click', prevReel);
  document.getElementById('reel').addEventListener('wheel', (e) => {
    if (!SW || SW.animating) return;
    if (e.target.closest && e.target.closest('#wswiper')) return;
    e.preventDefault();
    const now = Date.now();
    if (now - reelWheelT < 700) return;
    reelWheelT = now;
    if (e.deltaY > 0) SW.slideNext();
    else if (e.deltaY < 0) SW.slidePrev();
  }, { passive: false });
  document.getElementById('rseek').addEventListener('input', () => {
    const v = activeVideo();
    if (v && v.duration) v.currentTime = document.getElementById('rseek').value / 1000 * v.duration;
  });
  document.getElementById('rseek').addEventListener('pointerdown', () => { rSeeking = true; });
  Pjax.on(window, 'pointerup', () => { rSeeking = false; });
  document.getElementById('rfullBtn').addEventListener('click', (e) => { e.stopPropagation(); toggleReelFull(); });
  document.getElementById('rsetBtn').addEventListener('click', (e) => { e.stopPropagation(); openRsetMenu(); });
  Pjax.on(document, 'fullscreenchange', syncReelFullIcon);
  bindReelTap(document.getElementById('rgleft'), -10);
  bindReelTap(document.getElementById('rgright'), 10);
  mutedAll = false;
  document.getElementById('rmuteIcon').textContent = 'volume_up';
  initWheels();
})();
window.__destroy = function() {
  window.__wheelsAlive = false;
  try { document.querySelectorAll('#wwrap video').forEach((v) => { try { v.pause(); } catch {} }); } catch {}
  try { if (SW) SW.destroy(true, true); } catch {}
  SW = null;
};
};
window.__boot();
hideBoot();
}"""
}
