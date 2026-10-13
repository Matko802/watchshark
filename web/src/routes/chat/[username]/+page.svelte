<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { api, type DmMessage } from '$lib/api';

  $: username = $page.params.username ?? '';
  let messages: DmMessage[] = [];
  let meId = 0;
  let draft = '';
  let loading = true;
  let sending = false;
  let sendError: string | null = null;

  async function reload(markRead = true) {
    if (!username) return;
    try {
      const res = await api.dmThread(username, 50);
      messages = res.messages ?? [];
      meId = res.me;
      if (markRead && messages.length) {
        try {
          await api.dmRead(username);
        } catch {
          /* ignore */
        }
      }
    } finally {
      loading = false;
    }
  }

  async function send() {
    const body = draft.trim();
    if (!body || sending) return;
    draft = '';
    sendError = null;
    sending = true;
    try {
      await api.dmSend(username, body);
      await reload(false);
    } catch (e) {
      sendError = e instanceof Error ? e.message : 'Network error';
      draft = body;
    } finally {
      sending = false;
    }
  }

  onMount(() => {
    void reload();
    const t = setInterval(() => void reload(false), 10000);
    return () => clearInterval(t);
  });
</script>

<svelte:head>
  <title>@{username} • WatchShark</title>
</svelte:head>

<h1 style="font-size: 20px;">@{username}</h1>

{#if loading}
  <p class="muted center">Loading…</p>
{:else}
  <div>
    {#each [...messages].reverse() as m (m.id)}
      {@const mine = m.sender_id === meId}
      <div class="row" style={mine ? 'justify-content: flex-end;' : 'justify-content: flex-start;'}>
        <div
          style="max-width: 75%; padding: 10px 14px; border-radius: 16px; background: {mine
            ? '#fff; color: #0f0f0f'
            : 'var(--surface-highest);'};"
        >
          {m.body}
        </div>
      </div>
    {/each}
  </div>
{/if}

{#if sendError}
  <p class="error">{sendError}</p>
{/if}
<div class="replybox">
  <input
    placeholder="Message @{username}"
    bind:value={draft}
    on:keydown={(e) => e.key === 'Enter' && send()}
  />
  <button class="btn" on:click={send} disabled={sending || !draft.trim()}>Send</button>
</div>

<style>
  .row {
    margin: 6px 0;
  }
</style>
