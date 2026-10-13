<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { api } from '$lib/api';

  let state: 'working' | 'ok' | 'bad' = 'working';
  let msg = '';

  onMount(async () => {
    const token = $page.url.searchParams.get('token') ?? '';
    if (!token) {
      state = 'bad';
      msg = 'Missing token.';
      return;
    }
    try {
      await api.verify(token);
      state = 'ok';
    } catch (e) {
      state = 'bad';
      msg = e instanceof Error ? e.message : 'Network error';
    }
  });
</script>

<svelte:head>
  <title>Verify • WatchShark</title>
</svelte:head>

<h1>Verify account</h1>

{#if state === 'working'}
  <p class="muted">Verifying…</p>
{:else if state === 'ok'}
  <p>Account confirmed.</p>
  <p><a class="linkbtn" href="/auth">Log in</a></p>
{:else}
  <p class="error">{msg || 'Invalid or expired link.'}</p>
{/if}
