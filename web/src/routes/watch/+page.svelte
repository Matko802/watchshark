<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';

  onMount(() => {
    const id = $page.url.searchParams.get('id');
    if (id && /^\d+$/.test(id)) goto(`/watch/${id}`, { replaceState: true });
    else goto('/', { replaceState: true });
  });
</script>

<p class="muted center">Redirecting…</p>
