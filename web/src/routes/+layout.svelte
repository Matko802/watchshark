<script lang="ts">
  import '../app.css';
  import { page } from '$app/stores';
  import { onMount } from 'svelte';
  import { me, unread, refreshSession } from '$lib/session';

  let query = '';

  onMount(() => {
    refreshSession();
  });

  $: tab = $page.url.pathname === '/'
    ? 'home'
    : $page.url.pathname.startsWith('/wheels')
      ? 'wheels'
      : $page.url.pathname.startsWith('/messages') || $page.url.pathname.startsWith('/chat')
        ? 'messages'
        : 'you';

  function search(e: Event) {
    e.preventDefault();
    const q = query.trim();
    window.location.href = q ? `/?q=${encodeURIComponent(q)}` : '/';
  }
</script>

<div class="app">
  <header class="topbar">
    <a class="brand" href="/">
      <img src="/watchshark.webp" alt="WatchShark" />
      <span>WatchShark</span>
    </a>
    <form class="search" on:submit={search}>
      <input placeholder="Search" bind:value={query} aria-label="Search" />
    </form>
    <a class="iconbtn" href="/notifications" aria-label="Notifications">
      <span class="material-symbols">notifications</span>
      {#if $unread > 0}
        <span class="badge">{$unread > 9 ? '9+' : $unread}</span>
      {/if}
    </a>
    <a class="iconbtn" href="/settings" aria-label="Settings">
      <span class="material-symbols">settings</span>
    </a>
  </header>

  <main>
    <slot />
  </main>

  <nav class="pillnav" aria-label="Sections">
    <a href="/" class:active={tab === 'home'}>
      <span class="ico"><span class="material-symbols">home</span></span>
      <span>Home</span>
    </a>
    <a href="/wheels" class:active={tab === 'wheels'}>
      <span class="ico"><span class="material-symbols">movie</span></span>
      <span>Wheels</span>
    </a>
    <a href="/upload" class="create" aria-label="Create">
      <span class="ico"><span class="material-symbols">add</span></span>
    </a>
    <a href="/messages" class:active={tab === 'messages'}>
      <span class="ico"><span class="material-symbols">chat</span></span>
      <span>Messages</span>
    </a>
    <a href="/settings" class:active={tab === 'you'}>
      <span class="ico"><span class="material-symbols">person</span></span>
      <span>You</span>
    </a>
  </nav>
</div>
