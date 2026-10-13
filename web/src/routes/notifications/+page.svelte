<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Notif } from '$lib/api';
  import { unread } from '$lib/session';
  import { goto } from '$app/navigation';

  let items: Notif[] = [];
  let loading = true;
  let error: string | null = null;

  async function load() {
    loading = true;
    try {
      const res = await api.notifications();
      items = res.notifications ?? [];
      unread.set(res.unread ?? 0);
      error = null;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
    }
  }

  async function markAll() {
    items = items.map((n) => ({ ...n, read: true }));
    try {
      await api.notifRead();
      unread.set(0);
    } catch {
      /* ignore */
    }
  }

  async function open(n: Notif) {
    items = items.map((x) => (x.id === n.id ? { ...x, read: true } : x));
    try {
      await api.notifRead(n.id);
    } catch {
      /* ignore */
    }
    if (n.video_id) goto(`/watch/${n.video_id}`);
  }

  onMount(() => void load());
</script>

<svelte:head>
  <title>Notifications • WatchShark</title>
</svelte:head>

<div class="row">
  <h1 class="grow">Notifications</h1>
  {#if items.length > 0}
    <button class="btn ghost" on:click={markAll}>Mark all read</button>
  {/if}
</div>

{#if loading}
  <p class="muted center">Loading…</p>
{:else if error}
  <div class="center">
    <p>{error}</p>
    <p><button class="btn" on:click={load}>Retry</button></p>
  </div>
{:else if items.length === 0}
  <div class="center">
    <p>All caught up</p>
    <p class="muted">New likes, follows and uploads land here.</p>
  </div>
{:else}
  {#each items as n (n.id)}
    <button
      on:click={() => open(n)}
      style="display:block;width:100%;text-align:left;background:{n.read
        ? 'var(--surface-high)'
        : 'rgba(255,255,255,0.12)'};border:none;color:inherit;border-radius:16px;padding:14px 16px;margin:6px 0;cursor:pointer;"
    >
      <div><strong>{n.title}</strong></div>
      <div class="muted" style="font-size: 13px;">@{n.username}</div>
    </button>
  {/each}
{/if}
