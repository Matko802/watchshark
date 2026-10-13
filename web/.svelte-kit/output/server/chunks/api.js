async function req(path, init) {
  const res = await fetch(path, {
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    ...init
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const j = await res.json();
      if (j && typeof j.error === "string") msg = j.error;
    } catch {
    }
    throw new Error(msg);
  }
  return await res.json();
}
const api = {
  me: () => req("/api/me"),
  login: (login, password) => req("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ login, password })
  }),
  signup: (username, email, password) => req("/api/auth/signup", {
    method: "POST",
    body: JSON.stringify({ username, email, password })
  }),
  logout: () => req("/api/auth/logout", { method: "POST" }),
  verify: (token) => req(`/api/auth/verify?token=${encodeURIComponent(token)}`),
  forgot: (email) => req("/api/auth/forgot", {
    method: "POST",
    body: JSON.stringify({ email })
  }),
  reset: (token, password) => req("/api/auth/reset", {
    method: "POST",
    body: JSON.stringify({ token, password })
  }),
  rename: (username) => req("/api/auth/username", {
    method: "POST",
    body: JSON.stringify({ username })
  }),
  changePw: (current, password) => req("/api/auth/change", {
    method: "POST",
    body: JSON.stringify({ current, password })
  }),
  videos: (q = {}) => {
    const p = new URLSearchParams();
    if (q.q) p.set("q", q.q);
    if (q.sort) p.set("sort", q.sort);
    p.set("page", String(q.page ?? 1));
    p.set("limit", String(q.limit ?? 24));
    if (q.mine) p.set("mine", "1");
    if (q.kind) p.set("kind", q.kind);
    return req(`/api/videos?${p}`);
  },
  wheels: (seen = [], limit = 5) => {
    const p = new URLSearchParams();
    if (seen.length) p.set("seen", seen.join(","));
    p.set("limit", String(limit));
    return req(`/api/wheels?${p}`);
  },
  videoDetail: (id) => req(`/api/videos/${id}`),
  like: (id) => req(`/api/videos/${id}/like`, { method: "POST" }),
  comment: (id, body, parent_id) => req(`/api/videos/${id}/comments`, {
    method: "POST",
    body: JSON.stringify(parent_id ? { body, parent_id } : { body })
  }),
  editVideo: (id, data) => fetch(`/api/videos/${id}/edit`, { method: "POST", credentials: "include", body: data }).then(async (r) => {
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.error ?? `HTTP ${r.status}`);
    return r.json();
  }),
  deleteVideo: (id, reason = "") => req(`/api/videos/${id}`, {
    method: "DELETE",
    body: JSON.stringify({ reason })
  }),
  upload: (data) => fetch("/api/videos", { method: "POST", credentials: "include", body: data }).then(async (r) => {
    const j = await r.json().catch(() => null);
    if (!r.ok) throw new Error(j?.error ?? `HTTP ${r.status}`);
    return j;
  }),
  follow: (id) => req(`/api/follow/${id}`, { method: "POST" }),
  channel: (name) => req(`/api/channel/${encodeURIComponent(name)}`),
  notifications: () => req("/api/notifications"),
  notifRead: (id) => req("/api/notifications/read", {
    method: "POST",
    body: JSON.stringify(id ? { id } : {})
  }),
  notifSet: (uploads) => req("/api/settings/notifications", {
    method: "POST",
    body: JSON.stringify({ uploads })
  }),
  dmConversations: () => req("/api/dm/conversations"),
  dmThread: (user, limit = 50) => req(
    `/api/dm/thread?user=${encodeURIComponent(user)}&limit=${limit}`
  ),
  dmSend: (to, body) => req("/api/dm/send", {
    method: "POST",
    body: JSON.stringify({ to, body })
  }),
  dmRead: (user) => req("/api/dm/read", {
    method: "POST",
    body: JSON.stringify({ user })
  }),
  dmUnread: () => req("/api/dm/unread"),
  dmUserSearch: (q) => req(
    `/api/users/search?q=${encodeURIComponent(q)}`
  ),
  adminUsers: () => req("/api/admin/users"),
  adminApprove: (id) => req("/api/admin/approve", { method: "POST", body: JSON.stringify({ id }) }),
  adminBan: (id, days, reason) => req("/api/admin/ban", {
    method: "POST",
    body: JSON.stringify({ id, days, hours: 0, reason })
  }),
  adminUnban: (id) => req("/api/admin/unban", { method: "POST", body: JSON.stringify({ id }) }),
  adminSoftDelete: (id, reason) => req("/api/admin/soft-delete", {
    method: "POST",
    body: JSON.stringify({ id, reason })
  }),
  adminRestore: (id) => req("/api/admin/restore", { method: "POST", body: JSON.stringify({ id }) }),
  adminDelUser: (id) => req(`/api/admin/users/${id}`, { method: "DELETE" }),
  appLatest: () => req("/api/app/latest")
};
function fmtNum(n) {
  if (n < 1e3) return String(n);
  const units = [
    [1e9, "B"],
    [1e6, "M"],
    [1e3, "K"]
  ];
  for (const [v, s] of units) {
    if (n >= v) {
      const x = n / v;
      return (x >= 100 ? String(Math.round(x)) : x.toFixed(1).replace(/\.0$/, "")) + s;
    }
  }
  return String(n);
}
function fmtAge(s) {
  if (!s) return "";
  const t = Date.parse(s.replace(" ", "T") + "Z");
  if (Number.isNaN(t)) return s;
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1e3));
  if (sec < 60) return `${sec} seconds ago`;
  if (sec < 3600) return `${Math.floor(sec / 60)} minutes ago`;
  if (sec < 86400) return `${Math.floor(sec / 3600)} hours ago`;
  if (sec < 86400 * 30) return `${Math.floor(sec / 86400)} days ago`;
  return `${Math.floor(sec / (86400 * 30))} months ago`;
}
export {
  api as a,
  fmtAge as b,
  fmtNum as f
};
