import { writable } from 'svelte/store';
import { api, type MeUser } from './api';

export const me = writable<MeUser | null>(null);
export const unread = writable(0);

export async function refreshSession() {
  try {
    const j = await api.me();
    me.set(j.user);
  } catch {
    me.set(null);
  }
  try {
    const n = await api.notifications();
    unread.set(n.unread ?? 0);
  } catch {
    unread.set(0);
  }
}
