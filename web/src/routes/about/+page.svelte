<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from '$lib/api';

  let version = '';
  let apkUrl = 'https://github.com/Matko802/watchshark/releases/latest';
  let size = '';

  function fmtSize(n: number): string {
    if (!n) return '';
    return n >= 1048576 ? `${Math.round(n / 1048576)} MB` : `${Math.round(n / 1024)} KB`;
  }

  onMount(async () => {
    try {
      const j = await api.appLatest();
      if (j.version) version = j.version;
      if (j.apk_url) apkUrl = j.apk_url;
      if (j.size) size = fmtSize(j.size);
    } catch {
      /* fall back to releases page */
    }
  });
</script>

<svelte:head>
  <title>About • WatchShark</title>
</svelte:head>

<div class="center">
  <img src="/watchshark.webp" alt="WatchShark" width="96" height="96" style="border-radius: 24px;" />
  <h1>WatchShark</h1>
  {#if version}
    <p class="muted">Android v{version}{size ? ` • ${size}` : ''}</p>
  {/if}
  <p>
    <a class="btn" href={apkUrl}>Download for Android</a>
  </p>
  <p class="muted">Native Kotlin app, Material 3 UI, same backend as this site.</p>
</div>
