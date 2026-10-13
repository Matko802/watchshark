<script lang="ts">
  import { api } from '$lib/api';

  let email = '';
  let busy = false;
  let done = false;
  let err = '';

  async function submit() {
    if (busy || !email.trim()) return;
    busy = true;
    err = '';
    try {
      await api.forgot(email.trim());
      done = true;
    } catch (e) {
      err = e instanceof Error ? e.message : 'Network error';
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head>
  <title>Forgot password • WatchShark</title>
</svelte:head>

<h1>Forgot password?</h1>

{#if done}
  <p>If that email has an account, a reset link is on its way (valid 1 hour).</p>
  <p><a class="linkbtn" href="/auth">Back to log in</a></p>
{:else}
  <div class="field">
    <label for="fg-email">Email</label>
    <input id="fg-email" type="email" bind:value={email} autocomplete="email" />
  </div>
  {#if err}
    <p class="error">{err}</p>
  {/if}
  <p>
    <button class="btn" on:click={submit} disabled={busy}>
      {busy ? 'Sending…' : 'Send reset link'}
    </button>
  </p>
{/if}
