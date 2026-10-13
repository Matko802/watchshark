<script lang="ts">
  import { page } from '$app/stores';
  import { api } from '$lib/api';

  $: token = $page.url.searchParams.get('token') ?? '';
  let password = '';
  let busy = false;
  let done = false;
  let err = '';

  async function submit() {
    if (busy) return;
    if (password.length < 6) {
      err = 'Password must be 6+ chars';
      return;
    }
    busy = true;
    err = '';
    try {
      await api.reset(token, password);
      done = true;
    } catch (e) {
      err = e instanceof Error ? e.message : 'Network error';
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head>
  <title>Reset password • WatchShark</title>
</svelte:head>

<h1>Reset password</h1>

{#if !token}
  <p class="error">Missing token. Use the link from your email.</p>
{:else if done}
  <p>Password changed.</p>
  <p><a class="linkbtn" href="/auth">Log in</a></p>
{:else}
  <div class="field">
    <label for="rs-pw">New password (6+ chars)</label>
    <input id="rs-pw" type="password" bind:value={password} autocomplete="new-password" />
  </div>
  {#if err}
    <p class="error">{err}</p>
  {/if}
  <p>
    <button class="btn" on:click={submit} disabled={busy}>
      {busy ? 'Saving…' : 'Save new password'}
    </button>
  </p>
{/if}
