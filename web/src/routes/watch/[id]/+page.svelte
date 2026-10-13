<script lang="ts">
  import { page } from '$app/stores';
  import { api, fmtAge, fmtNum, type CommentItem, type Video } from '$lib/api';
  import { me, refreshSession } from '$lib/session';

  $: id = Number($page.params.id);
  let video: Video | null = null;
  let comments: CommentItem[] = [];
  let loading = true;
  let error: string | null = null;
  let draft = '';
  let posting = false;
  let replyTo: CommentItem | null = null;
  let expanded = false;

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await api.videoDetail(id);
      video = res.video;
      comments = res.comments ?? [];
      if (!video) error = 'Video not found';
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      loading = false;
    }
  }

  $: if (id) {
    replyTo = null;
    expanded = false;
    void load();
  }

  async function toggleLike() {
    if (!video) return;
    const cur = video;
    video = { ...cur, liked: !cur.liked, likes: Math.max(0, cur.likes + (cur.liked ? -1 : 1)) };
    try {
      await api.like(cur.id);
    } catch {
      video = cur;
    }
  }

  async function toggleFollow() {
    if (!video || !video.user_id) return;
    const cur = video;
    video = {
      ...cur,
      following: !cur.following,
      followers: Math.max(0, cur.followers + (cur.following ? -1 : 1))
    };
    try {
      await api.follow(cur.user_id);
      await refreshSession();
    } catch {
      video = cur;
    }
  }

  async function post() {
    const body = draft.trim();
    if (!body || posting || !video) return;
    posting = true;
    try {
      await api.comment(video.id, body, replyTo?.id);
      draft = '';
      replyTo = null;
      const res = await api.videoDetail(video.id);
      comments = res.comments ?? [];
    } catch (e) {
      error = e instanceof Error ? e.message : 'Network error';
    } finally {
      posting = false;
    }
  }

  function share() {
    if (!video) return;
    const url = `${location.origin}/watch/${video.id}`;
    if (navigator.share) {
      navigator.share({ title: video.title, url }).catch(() => {});
    } else {
      void navigator.clipboard?.writeText(`${video.title} ${url}`);
    }
  }

  $: tops = comments.filter((c) => c.parent_id == null);
  $: repliesOf = (pid: number) => comments.filter((c) => c.parent_id === pid);
</script>

<svelte:head>
  <title>{video ? `${video.title} • WatchShark` : 'WatchShark'}</title>
</svelte:head>

{#if loading}
  <p class="muted center">Loading…</p>
{:else if error && !video}
  <div class="center">
    <p>{error}</p>
    <p><button class="btn" on:click={load}>Retry</button></p>
  </div>
{:else if video}
  {@const v = video}
  <video class="player" src={v.src} poster={v.thumbnail ?? undefined} controls playsinline preload="metadata" />
  <h2 style="margin: 12px 0 4px;">{v.title}</h2>
  <p class="muted" style="margin: 0 0 12px;">
    {fmtNum(v.views)} views • {fmtAge(v.created_at)} • {fmtNum(v.likes)} likes
  </p>

  <div class="row" style="flex-wrap: wrap;">
    <a class="row grow" href={`/channel/${v.username}`}>
      {#if v.avatar}<img class="avatar" src={v.avatar} alt={v.username} />{/if}
      <strong>@{v.username}</strong>
      <span class="muted">• {fmtNum(v.followers)} followers</span>
    </a>
    {#if $me && $me.id !== v.user_id}
      <button class="btn ghost" on:click={toggleFollow}>
        {v.following ? 'Following' : 'Follow'}
      </button>
    {/if}
  </div>

  <div class="row" style="margin: 12px 0; flex-wrap: wrap;">
    <button class="btn ghost" on:click={toggleLike} aria-pressed={v.liked}>
      <span class="material-symbols" class:filled={v.liked}>favorite</span>
      {fmtNum(v.likes)}
    </button>
    <button class="btn ghost" on:click={share}>
      <span class="material-symbols">share</span> Share
    </button>
  </div>

  <button class="linkbtn" on:click={() => (expanded = !expanded)}>
    {expanded ? 'Show less' : 'Show description'}
  </button>
  {#if expanded}
    <p class="muted" style="white-space: pre-wrap;">{v.description || 'No description yet.'}</p>
  {/if}

  <h3>Comments ({comments.length})</h3>
  {#if !$me}
    <p class="muted"><a class="linkbtn" href="/auth">Log in to comment</a></p>
  {:else}
    {#if replyTo}
      <p>
        Replying to @{replyTo.username}
        <button class="linkbtn" on:click={() => (replyTo = null)}>cancel</button>
      </p>
    {/if}
    <div class="replybox">
      <input
        placeholder={replyTo ? `Reply to @${replyTo.username}…` : 'Add a comment…'}
        bind:value={draft}
        on:keydown={(e) => e.key === 'Enter' && post()}
      />
      <button class="btn" on:click={post} disabled={posting || !draft.trim()}>Post</button>
    </div>
  {/if}
  <div class="thread">
    {#each tops as c (c.id)}
      <div class="comment">
        {#if c.avatar}<img class="avatar" src={c.avatar} alt={c.username} />{/if}
        <div class="grow">
          <div class="muted" style="font-size: 12px;">@{c.username} • {fmtAge(c.created_at)}</div>
          <p class="body">{c.body}</p>
          {#if $me}
            <button class="linkbtn" on:click={() => (replyTo = c)}>Reply</button>
          {/if}
          {#each repliesOf(c.id) as r (r.id)}
            <div class="comment nested">
              {#if r.avatar}<img class="avatar" src={r.avatar} alt={r.username} />{/if}
              <div class="grow">
                <div class="muted" style="font-size: 12px;">@{r.username} • {fmtAge(r.created_at)}</div>
                <p class="body">{r.body}</p>
              </div>
            </div>
          {/each}
        </div>
      </div>
    {/each}
  </div>
{/if}
