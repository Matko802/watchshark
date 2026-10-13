<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from '$lib/api';
  import { me } from '$lib/session';
  import { goto } from '$app/navigation';

  interface Row {
    id: number;
    username: string;
    verified: boolean;
    role: string;
    created_at: string;
    banned: boolean;
    ban_days_left: number;
    ban_reason: string;
    deleted: boolean;
    deleted_reason: string;
    avatar: string | null;
  }

  let users: Row[] = [];
  let loading = true;
  let error: string | null = null;
  let query = '';
  let filter: 'all' | 'pending' | 'active' | 'banned' | 'deleted' = 'all';
  let days: Record<number, string> = {};
  let reasons: Record<number, string> = {};

  function status(u: Row): 'pending' | 'active' | 'banned' | 'deleted' {
    if (u.deleted) return 'deleted';
    if (u.banned) return 'banned';
    if (u.verified) return 'active';
    return 'pending';
  }

  async function reload() {
    loading = true;
    try {
      const res = await api.adminUsers();
      users = (res.users ?? []) as unknown as Row[];
      error = null;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    if ($me && !$me.admin) goto('/settings');
    void reload();
  });

  $: shown = users.filter((u) => {
    const q = query.trim().toLowerCase();
    return (
      (!q || u.username.toLowerCase().includes(q)) &&
      (filter === 'all' || status(u) === filter)
    );
  });

  async function act(fn: () => Promise<unknown>) {
    try {
      await fn();
      await reload();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    }
  }

  function ban(u: Row) {
    const d = Number(days[u.id] ?? 0);
    if (!(d > 0)) {
      error = 'Give ban days';
      return;
    }
    void act(() => api.adminBan(u.id, d, reasons[u.id]?.trim() || 'Removed by admin'));
  }
</script>

<svelte:head>
  <title>Admin • WatchShark</title>
</svelte:head>

<h1>Admin</h1>

<div class="field">
  <label for="adm-q">Search users</label>
  <input id="adm-q" bind:value={query} />
</div>

<div class="segtabs" style="position: static;">
  {#each ['all', 'pending', 'active', 'banned', 'deleted'] as f}
    <button class="segbtn" class:on={filter === f} on:click={() => (filter = f as typeof filter)}>
      {f}
    </button>
  {/each}
</div>

{#if error}
  <p class="error">{error}</p>
{/if}

{#if loading && users.length === 0}
  <p class="muted center">Loading…</p>
{:else}
  {#each shown as u (u.id)}
    <div style="border: 1px solid var(--outline); border-radius: 16px; padding: 12px 14px; margin: 8px 0;">
      <div class="row">
        {#if u.avatar}<img class="avatar" src={u.avatar} alt={u.username} />{/if}
        <div class="grow">
          <div><strong>{u.username}</strong>{u.role === 'admin' ? ' ⭐' : ''}</div>
          <div class="muted" style="font-size: 13px;">
            {status(u)}{u.banned
              ? ` • ${u.ban_days_left < 0 ? 'permanent' : `${u.ban_days_left.toFixed(1)}d left`}${u.ban_reason ? ` • ${u.ban_reason}` : ''}`
              : ''}{u.deleted && u.deleted_reason ? ` • ${u.deleted_reason}` : ''}
          </div>
        </div>
      </div>
      {#if u.role !== 'admin'}
        {#if !u.deleted && !u.banned}
          <div class="row" style="margin-top: 8px;">
            <input placeholder="Days" bind:value={days[u.id]} style="width: 90px;" />
            <input placeholder="Reason" bind:value={reasons[u.id]} class="grow" />
            <button class="btn" on:click={() => ban(u)}>Ban</button>
          </div>
        {/if}
        <div class="row" style="margin-top: 8px; flex-wrap: wrap;">
          {#if !u.verified && !u.deleted}
            <button class="btn ghost" on:click={() => act(() => api.adminApprove(u.id))}>Approve</button>
          {/if}
          {#if u.banned && !u.deleted}
            <button class="btn ghost" on:click={() => act(() => api.adminUnban(u.id))}>Unban</button>
          {/if}
          {#if u.deleted}
            <button class="btn ghost" on:click={() => act(() => api.adminRestore(u.id))}>Restore</button>
          {:else}
            <button
              class="btn ghost"
              on:click={() => act(() => api.adminSoftDelete(u.id, reasons[u.id]?.trim() || 'Removed by admin'))}
            >
              Delete
            </button>
          {/if}
          <button class="btn ghost" on:click={() => act(() => api.adminDelUser(u.id))}>Remove</button>
        </div>
      {/if}
    </div>
  {/each}
{/if}

<style>
  input {
    background: var(--surface-high);
    border: 1px solid var(--outline);
    color: var(--on-surface);
    border-radius: 12px;
    padding: 10px 12px;
    font-size: 14px;
    outline: none;
  }
</style>
