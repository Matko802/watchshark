<script lang="ts">
  import { onMount } from 'svelte';
  import { api, fmtAge, type DmConversation } from '$lib/api';

  let items: DmConversation[] = [];
  let loading = true;
  let error: string | null = null;

  async function load() {
    loading = true;
    try {
      const res = await api.dmConversations();
      items = res.conversations ?? [];
      error = null;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void load();
    const t = setInterval(() => void load(), 15000);
    return () => clearInterval(t);
  });
</script>

<svelte:head>
  <title>Messages • WatchShark</title>
</svelte:head>

<h1>Messages</h1>

{#if loading}
  <p class="muted center">Loading…</p>
{:else if error}
  <div class="center">
    <p>{error}</p>
    <p><button class="btn" on:click={load}>Retry</button></p>
  </div>
{:else if items.length === 0}
  <div class="center">
    <p>No conversations yet</p>
    <p class="muted">Follow each other with someone to start chatting.</p>
  </div>
{:else}
  {#each items as c (c.user_id)}
    <a class="row" href={`/chat/${c.username}`} style="padding: 10px 4px; border-bottom: 1px solid var(--outline);">
      {#if c.avatar}<img class="avatar" src={c.avatar} alt={c.username} />{/if}
      <div class="grow">
        <div><strong>@{c.username}</strong></div>
        <div class="muted" style="font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
          {c.last_message}
        </div>
      </div>
      <div class="muted" style="font-size: 12px;">{fmtAge(c.last_at)}</div>
      {#if c.unread > 0}<span class="badge" style="position: static;">{c.unread > 99 ? '99+' : c.unread}</span>{/if}
    </a>
  {/each}
{/if}
