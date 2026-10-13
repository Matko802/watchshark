<script lang="ts">
  import { goto } from '$app/navigation';
  import { api } from '$lib/api';

  let kind: 'video' | 'wheel' = 'video';
  let title = '';
  let desc = '';
  let file: File | null = null;
  let thumb: File | null = null;
  let busy = false;
  let msg = '';

  function pick(e: Event, which: 'file' | 'thumb') {
    const input = e.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (which === 'file') file = f;
    else thumb = f;
  }

  async function upload() {
    msg = '';
    if (!file) {
      msg = 'Choose a file first';
      return;
    }
    if (!title.trim()) {
      msg = 'Title is required';
      return;
    }
    if (file.size > 500 * 1024 * 1024) {
      msg = 'File too big (max 500MB)';
      return;
    }
    busy = true;
    msg = 'Uploading…';
    try {
      const data = new FormData();
      data.set('title', title.trim());
      data.set('description', desc);
      data.set('kind', kind);
      data.set('file', file);
      if (thumb) {
        if (thumb.size > 10 * 1024 * 1024) throw new Error('Thumbnail too big (max 10MB)');
        data.set('thumb', thumb);
      }
      const res = await api.upload(data);
      msg = 'Uploaded!';
      goto(`/watch/${res.id}`);
    } catch (e) {
      msg = e instanceof Error ? e.message : 'Upload failed';
    } finally {
      busy = false;
    }
  }
</script>

<svelte:head>
  <title>Upload • WatchShark</title>
</svelte:head>

<h1>Upload</h1>

<div class="segtabs" style="position: static;">
  <button class="segbtn" class:on={kind === 'video'} on:click={() => (kind = 'video')}>Video</button>
  <button class="segbtn" class:on={kind === 'wheel'} on:click={() => (kind = 'wheel')}>Wheel</button>
</div>

<div class="field">
  <label for="up-title">Title</label>
  <input id="up-title" bind:value={title} maxlength={120} />
</div>
<div class="field">
  <label for="up-desc">Description</label>
  <textarea id="up-desc" rows="3" bind:value={desc}></textarea>
</div>
<div class="field">
  <label for="up-file">{kind === 'video' ? 'Video file' : 'Wheel file'}</label>
  <input id="up-file" type="file" accept="video/*" on:change={(e) => pick(e, 'file')} />
  {#if file}<span class="muted">{file.name}</span>{/if}
</div>
<div class="field">
  <label for="up-thumb">Thumbnail (optional)</label>
  <input id="up-thumb" type="file" accept="image/*" on:change={(e) => pick(e, 'thumb')} />
  {#if thumb}<span class="muted">{thumb.name}</span>{/if}
</div>

<p>
  <button class="btn" on:click={upload} disabled={busy}>{busy ? 'Uploading…' : 'Upload'}</button>
</p>

{#if msg}
  <p class="muted">{msg}</p>
{/if}
