<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';

  onMount(() => {
    const user = ($page.url.searchParams.get('user') ?? '').trim();
    if (user) goto(`/channel/${encodeURIComponent(user)}`, { replaceState: true });
    else goto('/', { replaceState: true });
  });
</script>

<p class="muted center">Redirecting…</p>
