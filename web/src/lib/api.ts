export interface Video {
  id: number;
  title: string;
  username: string;
  user_id: number;
  src: string;
  thumbnail: string | null;
  views: number;
  likes: number;
  liked: boolean;
  followers: number;
  following: boolean;
  created_at: string;
  avatar: string | null;
  kind: string;
  status: string;
  description: string | null;
  renditions: Record<string, string> | null;
  orientation: string | null;
  mimetype?: string;
}

export interface MeUser {
  id: number;
  username: string;
  avatar: string | null;
  admin: boolean;
  notify_uploads: boolean;
  banned: boolean;
  ban_days_left: number;
  ban_reason: string | null;
  deleted: boolean;
  deleted_reason: string | null;
  online: boolean;
}

export interface ChannelUser {
  id: number;
  username: string;
  avatar: string | null;
  created_at: string;
  followers: number;
  videos: number;
  views: number;
  following: boolean;
  online: boolean;
  counts: Record<string, number>;
}

export interface CommentItem {
  id: number;
  body: string;
  created_at: string;
  username: string;
  avatar: string | null;
  parent_id: number | null;
  parent_username: string | null;
  online: boolean;
}

export interface Notif {
  id: number;
  video_id: number | null;
  created_at: string;
  read: boolean;
  title: string;
  username: string;
  kind: string;
  text: string;
}

export interface DmConversation {
  user_id: number;
  username: string;
  avatar: string | null;
  online: boolean;
  last_message: string;
  last_message_id: number;
  last_at: string;
  last_sender_id: number;
  unread: number;
}

export interface DmMessage {
  id: number;
  sender_id: number;
  recipient_id: number;
  body: string;
  created_at: string;
  read: boolean;
}

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...init
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const j = await res.json();
      if (j && typeof j.error === 'string') msg = j.error;
    } catch {
      /* keep default */
    }
    throw new Error(msg);
  }
  return (await res.json()) as T;
}

export const api = {
  me: () => req<{ user: MeUser | null }>('/api/me'),
  login: (login: string, password: string) =>
    req<Record<string, unknown>>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ login, password })
    }),
  signup: (username: string, email: string, password: string) =>
    req<Record<string, unknown>>('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ username, email, password })
    }),
  logout: () => req<Record<string, unknown>>('/api/auth/logout', { method: 'POST' }),
  rename: (username: string) =>
    req<Record<string, unknown>>('/api/auth/username', {
      method: 'POST',
      body: JSON.stringify({ username })
    }),
  changePw: (current: string, password: string) =>
    req<Record<string, unknown>>('/api/auth/change', {
      method: 'POST',
      body: JSON.stringify({ current, password })
    }),

  videos: (q: { q?: string; sort?: string; page?: number; limit?: number; mine?: boolean; kind?: string } = {}) => {
    const p = new URLSearchParams();
    if (q.q) p.set('q', q.q);
    if (q.sort) p.set('sort', q.sort);
    p.set('page', String(q.page ?? 1));
    p.set('limit', String(q.limit ?? 24));
    if (q.mine) p.set('mine', '1');
    if (q.kind) p.set('kind', q.kind);
    return req<{ videos: Video[]; page: number; pages: number; total: number }>(`/api/videos?${p}`);
  },
  wheels: (seen: number[] = [], limit = 5) => {
    const p = new URLSearchParams();
    if (seen.length) p.set('seen', seen.join(','));
    p.set('limit', String(limit));
    return req<{ videos?: Video[]; video?: Video }>(`/api/wheels?${p}`);
  },
  videoDetail: (id: number) =>
    req<{ video: Video | null; comments: CommentItem[] }>(`/api/videos/${id}`),
  like: (id: number) =>
    req<{ ok: boolean; liked: boolean; likes: number }>(`/api/videos/${id}/like`, { method: 'POST' }),
  comment: (id: number, body: string, parent_id?: number) =>
    req<{ ok: boolean; id: number }>(`/api/videos/${id}/comments`, {
      method: 'POST',
      body: JSON.stringify(parent_id ? { body, parent_id } : { body })
    }),
  editVideo: (id: number, data: FormData) =>
    fetch(`/api/videos/${id}/edit`, { method: 'POST', credentials: 'include', body: data }).then(async (r) => {
      if (!r.ok) throw new Error((await r.json().catch(() => null))?.error ?? `HTTP ${r.status}`);
      return r.json();
    }),
  deleteVideo: (id: number, reason = '') =>
    req<Record<string, unknown>>(`/api/videos/${id}`, {
      method: 'DELETE',
      body: JSON.stringify({ reason })
    }),
  upload: (data: FormData) =>
    fetch('/api/videos', { method: 'POST', credentials: 'include', body: data }).then(async (r) => {
      const j = await r.json().catch(() => null);
      if (!r.ok) throw new Error(j?.error ?? `HTTP ${r.status}`);
      return j as { ok: boolean; id: number; kind: string };
    }),

  follow: (id: number) =>
    req<{ ok: boolean; following: boolean; followers: number }>(`/api/follow/${id}`, { method: 'POST' }),
  channel: (name: string) =>
    req<{ user: ChannelUser; videos: Video[] }>(`/api/channel/${encodeURIComponent(name)}`),

  notifications: () => req<{ notifications: Notif[]; unread: number }>('/api/notifications'),
  notifRead: (id?: number) =>
    req<Record<string, unknown>>('/api/notifications/read', {
      method: 'POST',
      body: JSON.stringify(id ? { id } : {})
    }),
  notifSet: (uploads: boolean) =>
    req<Record<string, unknown>>('/api/settings/notifications', {
      method: 'POST',
      body: JSON.stringify({ uploads })
    }),

  dmConversations: () => req<{ conversations: DmConversation[] }>('/api/dm/conversations'),
  dmThread: (user: string, limit = 50) =>
    req<{ messages: DmMessage[]; peer: { user_id: number; username: string; avatar: string | null; online: boolean }; me: number }>(
      `/api/dm/thread?user=${encodeURIComponent(user)}&limit=${limit}`
    ),
  dmSend: (to: string, body: string) =>
    req<Record<string, unknown>>('/api/dm/send', {
      method: 'POST',
      body: JSON.stringify({ to, body })
    }),
  dmRead: (user: string) =>
    req<Record<string, unknown>>('/api/dm/read', {
      method: 'POST',
      body: JSON.stringify({ user })
    }),
  dmUnread: () => req<{ unread: number }>('/api/dm/unread'),
  dmUserSearch: (q: string) =>
    req<{ users: { user_id: number; username: string; avatar: string | null; online: boolean }[] }>(
      `/api/users/search?q=${encodeURIComponent(q)}`
    ),

  adminUsers: () => req<{ users: Record<string, unknown>[] }>('/api/admin/users'),
  adminApprove: (id: number) =>
    req<Record<string, unknown>>('/api/admin/approve', { method: 'POST', body: JSON.stringify({ id }) }),
  adminBan: (id: number, days: number, reason: string) =>
    req<Record<string, unknown>>('/api/admin/ban', {
      method: 'POST',
      body: JSON.stringify({ id, days, hours: 0, reason })
    }),
  adminUnban: (id: number) =>
    req<Record<string, unknown>>('/api/admin/unban', { method: 'POST', body: JSON.stringify({ id }) }),
  adminSoftDelete: (id: number, reason: string) =>
    req<Record<string, unknown>>('/api/admin/soft-delete', {
      method: 'POST',
      body: JSON.stringify({ id, reason })
    }),
  adminRestore: (id: number) =>
    req<Record<string, unknown>>('/api/admin/restore', { method: 'POST', body: JSON.stringify({ id }) }),
  adminDelUser: (id: number) => req<Record<string, unknown>>(`/api/admin/users/${id}`, { method: 'DELETE' }),

  appLatest: () =>
    req<{ tag: string; version: string; apk_url: string; size: number; html_url: string }>('/api/app/latest')
};

export function fmtNum(n: number): string {
  if (n < 1000) return String(n);
  const units: Array<[number, string]> = [
    [1_000_000_000, 'B'],
    [1_000_000, 'M'],
    [1_000, 'K']
  ];
  for (const [v, s] of units) {
    if (n >= v) {
      const x = n / v;
      return (x >= 100 ? String(Math.round(x)) : x.toFixed(1).replace(/\.0$/, '')) + s;
    }
  }
  return String(n);
}

export function fmtAge(s: string | null | undefined): string {
  if (!s) return '';
  const t = Date.parse(s.replace(' ', 'T') + 'Z');
  if (Number.isNaN(t)) return s;
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000));
  if (sec < 60) return `${sec} seconds ago`;
  if (sec < 3600) return `${Math.floor(sec / 60)} minutes ago`;
  if (sec < 86400) return `${Math.floor(sec / 3600)} hours ago`;
  if (sec < 86400 * 30) return `${Math.floor(sec / 86400)} days ago`;
  return `${Math.floor(sec / (86400 * 30))} months ago`;
}
