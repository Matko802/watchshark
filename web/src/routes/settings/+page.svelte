<script lang="ts">
  import { goto } from '$app/navigation';
  import { api } from '$lib/api';
  import { me, refreshSession } from '$lib/session';

  let name = '';
  let curPw = '';
  let newPw = '';
  let notifOn = false;
  let msg = '';

  $: if ($me) {
    notifOn = $me.notify_uploads;
  }

  async function saveName() {
    if (!name.trim()) return;
    try {
      await api.rename(name.trim());
      msg = 'Name saved!';
      await refreshSession();
    } catch (e) {
      msg = e instanceof Error ? e.message : 'Network error';
    }
  }

  async function savePw() {
    try {
      await api.changePw(curPw, newPw);
      curPw = '';
      newPw = '';
      msg = 'Password changed.';
    } catch (e) {
      msg = e instanceof Error ? e.message : 'Network error';
    }
  }

  async function saveNotif() {
    try {
      await api.notifSet(notifOn);
      msg = 'Saved!';
    } catch (e) {
      msg = e instanceof Error ? e.message : 'Network error';
    }
  }

  async function logout() {
    try {
      await api.logout();
    } catch {
      /* ignore */
    }
    await refreshSession();
    goto('/auth');
  }
</script>

<svelte:head>
  <title>Settings • WatchShark</title>
</svelte:head>

<h1>Settings</h1>

{#if !$me}
  <p class="muted">You are browsing as a guest. <a class="linkbtn" href="/auth">Log in</a></p>
{:else}
  <h2>Account</h2>
  <div class="field">
    <label for="set-name">New handle</label>
    <input id="set-name" bind:value={name} />
  </div>
  <p><button class="btn" on:click={saveName}>Save name</button></p>

  <h2>Password</h2>
  <div class="field">
    <label for="set-cur">Current password</label>
    <input id="set-cur" type="password" bind:value={curPw} autocomplete="current-password" />
  </div>
  <div class="field">
    <label for="set-new">New password (6+ chars)</label>
    <input id="set-new" type="password" bind:value={newPw} autocomplete="new-password" />
  </div>
  <p><button class="btn" on:click={savePw}>Save password</button></p>

  <h2>Notifications</h2>
  <label>
    <input type="checkbox" bind:checked={notifOn} on:change={saveNotif} /> Upload alerts from followed channels
  </label>

  {#if $me.admin}
    <h2>Admin</h2>
    <p><a class="btn ghost" href="/admin">Open admin</a></p>
  {/if}

  <p><button class="btn ghost" on:click={logout}>Sign out</button></p>

  {#if msg}
    <p class="muted">{msg}</p>
  {/if}
{/if}
