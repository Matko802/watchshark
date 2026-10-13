<script lang="ts">
  import { goto } from '$app/navigation';
  import { api } from '$lib/api';
  import { refreshSession } from '$lib/session';

  let mode: 'login' | 'signup' = 'login';
  let login = '';
  let password = '';
  let username = '';
  let email = '';
  let showPw = false;
  let busy = false;
  let err = '';

  function validEmail(v: string) {
    return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(v.trim());
  }

  async function submit() {
    if (busy) return;
    err = '';
    if (mode === 'login') {
      if (!login.trim() || !password) {
        err = 'Enter email and password';
        return;
      }
      busy = true;
      try {
        const res = await api.login(login.trim(), password);
        if (res.error) {
          err = String(res.error);
          return;
        }
        await refreshSession();
        goto('/');
      } catch (e) {
        err = e instanceof Error ? e.message : 'Network error';
      } finally {
        busy = false;
      }
    } else {
      if (!/^[A-Za-z0-9_]{3,30}$/.test(username.trim())) {
        err = 'Handle needs 3-30 letters, numbers or _';
        return;
      }
      if (!validEmail(email)) {
        err = 'Enter a valid email';
        return;
      }
      if (password.length < 6) {
        err = 'Password needs 6+ characters';
        return;
      }
      busy = true;
      try {
        const res = await api.signup(username.trim(), email.trim().toLowerCase(), password);
        if (res.error) {
          err = String(res.error);
          return;
        }
        await refreshSession();
        goto('/');
      } catch (e) {
        err = e instanceof Error ? e.message : 'Network error';
      } finally {
        busy = false;
      }
    }
  }
</script>

<svelte:head>
  <title>{mode === 'login' ? 'Log in' : 'Create account'} • WatchShark</title>
</svelte:head>

<h1>{mode === 'login' ? 'Log in' : 'Create account'}</h1>

{#if mode === 'signup'}
  <div class="field">
    <label for="su-user">Handle (letters, numbers, _)</label>
    <input id="su-user" bind:value={username} autocomplete="username" />
  </div>
  <div class="field">
    <label for="su-email">Email</label>
    <input id="su-email" type="email" bind:value={email} autocomplete="email" />
  </div>
{/if}

<div class="field">
  <label for="auth-login">{mode === 'login' ? 'Email or handle' : 'Password (6+ chars)'}</label>
  {#if mode === 'login'}
    <input id="auth-login" bind:value={login} autocomplete="username" />
  {:else}
    <input
      id="auth-login"
      type={showPw ? 'text' : 'password'}
      bind:value={password}
      autocomplete="new-password"
    />
  {/if}
</div>

{#if mode === 'login'}
  <div class="field">
    <label for="auth-pw">Password</label>
    <input
      id="auth-pw"
      type={showPw ? 'text' : 'password'}
      bind:value={password}
      autocomplete="current-password"
    />
  </div>
{/if}

<div class="field">
  <label>
    <input type="checkbox" bind:checked={showPw} /> Show password
  </label>
</div>

{#if err}
  <p class="error">{err}</p>
{/if}

<p>
  <button class="btn" on:click={submit} disabled={busy}>
    {busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
  </button>
</p>
<p>
  <button
    class="linkbtn"
    on:click={() => {
      mode = mode === 'login' ? 'signup' : 'login';
      err = '';
    }}
  >
    {mode === 'login' ? 'Create account' : 'Log in'}
  </button>
</p>
{#if mode === 'login'}
  <p><a class="linkbtn" href="/forgot">Forgot password?</a></p>
{/if}
