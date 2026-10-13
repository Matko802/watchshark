<script lang="ts">
  import { page } from '$app/stores';
  import { api, fmtAge, fmtNum, type ChannelUser, type Video } from '$lib/api';
  import { me } from '$lib/session';

  $: name = $page.params.name ?? '';
  let user: ChannelUser | null = null;
  let videos: Video[] = [];
  let loading = true;
  let error: string | null = null;
  let tab: 0 | 1 = 0;

  async function load() {
    if (!name) return;
    loading = true;
    error = null;
    try {
      const res = await api.channel(name);
      user = res.user;
      videos = res.videos ?? [];
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
    }
  }

  $: if (name) void load();

  async function toggleFollow() {
    if (!user) return;
    const cur = user;
    user = {
      ...cur,
      following: !cur.following,
      followers: Math.max(0, cur.followers + (cur.following ? -1 : 1))
    };
    try {
      await api.follow(cur.id);
    } catch {
      user = cur;
    }
  }

  $: shown = tab === 0 ? videos.filter((v) => v.kind !== 'wheel') : videos.filter((v) => v.kind === 'wheel');
</script>

<svelte:head>
  <title>{user ? `@${user.username} • WatchShark` : 'Channel • WatchShark'}</title>
</svelte:head>

{#if loading}
  <p class="muted center">Loading…</p>
{:else if error || !user}
  <div class="center">
    <p>{error ?? 'Channel not found'}</p>
    <p><button class="btn" on:click={load}>Retry</button></p>
  </div>
{:else}
  <div class="row" style="padding: 16px 4px;">
    {#if user.avatar}<img class="avatar" style="width:64px;height:64px;" src={user.avatar} alt={user.username} />{/if}
    <div class="grow">
      <h2 style="margin: 0;">@{user.username}</h2>
      <p class="muted" style="margin: 4px 0 0;">
        {fmtNum(user.followers)} followers • {fmtNum(user.videos)} videos • {fmtNum(user.views)} views
      </p>
    </div>
    {#if $me && $me.id !== user.id}
      <button class="btn ghost" on:click={toggleFollow}>
        {user.following ? 'Following' : 'Follow'}
      </button>
    {/if}
  </div>

  <div class="segtabs" style="position: static;">
    <button class="segbtn" class:on={tab === 0} on:click={() => (tab = 0)}>
      <span class="material-symbols">play_circle</span>Videos
    </button>
    <button class="segbtn" class:on={tab === 1} on:click={() => (tab = 1)}>
      <span class="material-symbols">movie</span>Wheels
    </button>
  </div>

  {#if shown.length === 0}
    <p class="muted center">Nothing here yet.</p>
  {:else}
    <div class="grid">
      {#each shown as v (v.id)}
        <a class="card" href={`/watch/${v.id}`}>
          <div class="thumb">
            {#if v.thumbnail}<img src={v.thumbnail} alt={v.title} loading="lazy" />{/if}
          </div>
          <div class="meta">
            <div class="grow">
              <h3>{v.title}</h3>
              <p class="sub">{fmtNum(v.views)} views • {fmtAge(v.created_at)}</p>
            </div>
          </div>
        </a>
      {/each}
    </div>
  {/if}
{/if}
