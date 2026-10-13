<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { api, fmtAge, fmtNum, type Video } from '$lib/api';

  let videos: Video[] = [];
  let loading = true;
  let loadingMore = false;
  let error: string | null = null;
  let sort: 'new' | 'popular' = 'new';
  let pageNum = 1;
  let pages = 1;
  let sentinel: HTMLDivElement | null = null;

  $: query = $page.url.searchParams.get('q') ?? '';

  async function load(reset = true) {
    if (reset) {
      loading = true;
      error = null;
      pageNum = 1;
    }
    try {
      const res = await api.videos({
        q: query || undefined,
        sort: sort === 'popular' ? 'popular' : 'new',
        page: pageNum,
        limit: 24,
        kind: 'video'
      });
      videos = reset ? (res.videos ?? []) : [...videos, ...(res.videos ?? [])];
      pages = res.pages ?? 1;
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
      loadingMore = false;
    }
  }

  async function loadMore() {
    if (loading || loadingMore || pageNum >= pages) return;
    loadingMore = true;
    pageNum += 1;
    try {
      const res = await api.videos({
        q: query || undefined,
        sort: sort === 'popular' ? 'popular' : 'new',
        page: pageNum,
        limit: 24,
        kind: 'video'
      });
      videos = [...videos, ...(res.videos ?? [])];
      pages = res.pages ?? pageNum;
    } catch {
      pageNum -= 1;
    } finally {
      loadingMore = false;
    }
  }

  $: if (query !== undefined) {
    void load(true);
  }

  onMount(() => {
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) void loadMore();
      },
      { rootMargin: '600px' }
    );
    const check = setInterval(() => {
      if (sentinel) {
        io.observe(sentinel);
        clearInterval(check);
      }
    }, 100);
    return () => {
      clearInterval(check);
      io.disconnect();
    };
  });

  function setSort(s: 'new' | 'popular') {
    if (sort !== s) {
      sort = s;
      void load(true);
    }
  }
</script>

<svelte:head>
  <title>WatchShark</title>
</svelte:head>

<div class="segtabs" role="tablist" aria-label="Sort videos">
  <button class="segbtn" class:on={sort === 'new'} on:click={() => setSort('new')}>
    <span class="material-symbols">new_releases</span>Latest
  </button>
  <button class="segbtn" class:on={sort === 'popular'} on:click={() => setSort('popular')}>
    <span class="material-symbols">local_fire_department</span>Trending
  </button>
</div>

{#if loading}
  <p class="muted center">Loading…</p>
{:else if error && videos.length === 0}
  <div class="center">
    <p>Couldn't load videos</p>
    <p class="muted">{error}</p>
    <p><button class="btn" on:click={() => load(true)}>Retry</button></p>
  </div>
{:else if videos.length === 0}
  <div class="center">
    <p>No videos yet</p>
    <p class="muted">{query ? 'Try a different search' : 'Pull to refresh'}</p>
  </div>
{:else}
  <div class="grid">
    {#each videos as v (v.id)}
      <a class="card" href={`/watch/${v.id}`}>
        <div class="thumb">
          {#if v.thumbnail}
            <img src={v.thumbnail} alt={v.title} loading="lazy" />
          {/if}
        </div>
        <div class="meta">
          {#if v.avatar}
            <img class="avatar" src={v.avatar} alt={v.username} loading="lazy" />
          {/if}
          <div class="grow">
            <h3>{v.title}</h3>
            <p class="sub">@{v.username} • {fmtNum(v.views)} views • {fmtAge(v.created_at)}</p>
          </div>
        </div>
      </a>
    {/each}
  </div>
  <div bind:this={sentinel} style="height: 8px"></div>
  {#if loadingMore}
    <p class="muted center">Loading more…</p>
  {/if}
{/if}
