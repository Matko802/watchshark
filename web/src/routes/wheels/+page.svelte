<script lang="ts">
  import { onMount } from 'svelte';
  import { api, fmtNum, type Video } from '$lib/api';
  import { me, refreshSession } from '$lib/session';

  let videos: Video[] = [];
  let loading = true;
  let error: string | null = null;
  let seen: number[] = [];
  let current = 0;
  let container: HTMLDivElement | null = null;
  let els: HTMLVideoElement[] = [];

  async function loadMore() {
    try {
      const res = await api.wheels(seen, 5);
      const batch = res.videos ?? (res.video ? [res.video] : []);
      if (!batch.length) return;
      videos = [...videos, ...batch];
      seen = [...seen, ...batch.map((v) => v.id)];
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
    }
  }

  function onScroll() {
    if (!container) return;
    const i = Math.round(container.scrollTop / container.clientHeight);
    if (i !== current) {
      current = Math.max(0, Math.min(i, videos.length - 1));
      syncPlayback();
    }
    if (current >= videos.length - 3) void loadMore();
  }

  function syncPlayback() {
    els.forEach((el, i) => {
      if (!el) return;
      if (i === current) {
        el.play().catch(() => {});
      } else {
        el.pause();
      }
    });
  }

  async function toggleLike(v: Video, i: number) {
    videos[i] = {
      ...v,
      liked: !v.liked,
      likes: Math.max(0, v.likes + (v.liked ? -1 : 1))
    };
    try {
      await api.like(v.id);
    } catch {
      videos[i] = v;
    }
  }

  async function toggleFollow(v: Video, i: number) {
    if (!v.user_id) return;
    videos[i] = {
      ...v,
      following: !v.following,
      followers: Math.max(0, v.followers + (v.following ? -1 : 1))
    };
    try {
      await api.follow(v.user_id);
      await refreshSession();
    } catch {
      videos[i] = v;
    }
  }

  function togglePlay(i: number) {
    const el = els[i];
    if (!el) return;
    if (el.paused) el.play().catch(() => {});
    else el.pause();
  }

  onMount(() => {
    refreshSession();
    void loadMore();
  });

  $: if (videos.length && els.length) {
    // keep playback in sync after list updates
    queueMicrotask(syncPlayback);
  }
</script>

<svelte:head>
  <title>Wheels • WatchShark</title>
</svelte:head>

{#if loading}
  <p class="muted center">Loading…</p>
{:else if error && videos.length === 0}
  <div class="center">
    <p>{error}</p>
    <p><button class="btn" on:click={() => loadMore()}>Retry</button></p>
  </div>
{:else}
  <div
    class="reels"
    bind:this={container}
    on:scroll={onScroll}
  >
    {#each videos as v, i (v.id)}
      <section class="reel">
        <video
          bind:this={els[i]}
          src={v.src}
          poster={v.thumbnail ?? undefined}
          loop
          muted
          playsinline
          preload="metadata"
          on:click={() => togglePlay(i)}
        ></video>
        <div class="reel-side">
          <button class="iconbtn" on:click={() => toggleLike(v, i)} aria-label="Like">
            <span class="material-symbols" class:filled={v.liked}>favorite</span>
          </button>
          <span class="count">{fmtNum(v.likes)}</span>
          {#if $me && $me.id !== v.user_id}
            <button class="iconbtn" on:click={() => toggleFollow(v, i)} aria-label="Follow">
              <span class="material-symbols">{v.following ? 'how_to_reg' : 'person_add'}</span>
            </button>
          {/if}
          <a class="iconbtn" href={`/watch/${v.id}`} aria-label="Comments">
            <span class="material-symbols">chat</span>
          </a>
          <a class="reel-user" href={`/channel/${v.username}`}>@{v.username}</a>
        </div>
        <div class="reel-cap">
          <strong>{v.title}</strong>
        </div>
      </section>
    {/each}
  </div>
{/if}

<style>
  .reels {
    height: calc(100dvh - 160px);
    min-height: 420px;
    overflow-y: scroll;
    scroll-snap-type: y mandatory;
    border-radius: 16px;
  }
  .reel {
    position: relative;
    height: 100%;
    scroll-snap-align: start;
    background: #000;
    overflow: hidden;
  }
  .reel video {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
  }
  .reel-side {
    position: absolute;
    right: 6px;
    bottom: 12px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }
  .reel-side .iconbtn {
    background: rgba(20, 20, 20, 0.55);
  }
  .reel-side .count {
    font-size: 12px;
    color: #fff;
    margin-bottom: 6px;
  }
  .reel-user {
    margin-top: 6px;
    font-size: 12px;
    color: #fff;
    background: rgba(20, 20, 20, 0.55);
    border-radius: 999px;
    padding: 4px 10px;
    max-width: 92px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .reel-cap {
    position: absolute;
    left: 12px;
    bottom: 12px;
    right: 96px;
    color: #fff;
    font-size: 14px;
    text-shadow: 0 1px 6px rgba(0, 0, 0, 0.8);
  }
</style>
