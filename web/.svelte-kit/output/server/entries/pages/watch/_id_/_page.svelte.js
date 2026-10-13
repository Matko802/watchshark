import { s as store_get, h as head, u as unsubscribe_stores, p as pop, a as push, e as escape_html, f as ensure_array_like, b as attr, d as attr_class } from "../../../../chunks/index.js";
import { p as page } from "../../../../chunks/stores.js";
import { a as api, f as fmtNum, b as fmtAge } from "../../../../chunks/api.js";
import { m as me } from "../../../../chunks/session.js";
function _page($$payload, $$props) {
  push();
  var $$store_subs;
  let id, tops, repliesOf;
  let video = null;
  let comments = [];
  let loading = true;
  let error = null;
  let draft = "";
  let replyTo = null;
  let expanded = false;
  async function load() {
    loading = true;
    error = null;
    try {
      const res = await api.videoDetail(id);
      video = res.video;
      comments = res.comments ?? [];
      if (!video) error = "Video not found";
    } catch (e) {
      error = e instanceof Error ? e.message : "Network error";
    } finally {
      loading = false;
    }
  }
  id = Number(store_get($$store_subs ??= {}, "$page", page).params.id);
  if (id) {
    replyTo = null;
    expanded = false;
    void load();
  }
  tops = comments.filter((c) => c.parent_id == null);
  repliesOf = (pid) => comments.filter((c) => c.parent_id === pid);
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>${escape_html(video ? `${video.title} • WatchShark` : "WatchShark")}</title>`;
  });
  if (loading) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted center">Loading…</p>`;
  } else if (error && !video) {
    $$payload.out += "<!--[1-->";
    $$payload.out += `<div class="center"><p>${escape_html(error)}</p> <p><button class="btn">Retry</button></p></div>`;
  } else if (video) {
    $$payload.out += "<!--[2-->";
    const v = video;
    const each_array = ensure_array_like(tops);
    $$payload.out += `<video class="player"${attr("src", v.src)}${attr("poster", v.thumbnail ?? void 0)} controls playsinline preload="metadata"></video> <h2 style="margin: 12px 0 4px;">${escape_html(v.title)}</h2> <p class="muted" style="margin: 0 0 12px;">${escape_html(fmtNum(v.views))} views • ${escape_html(fmtAge(v.created_at))} • ${escape_html(fmtNum(v.likes))} likes</p> <div class="row" style="flex-wrap: wrap;"><a class="row grow"${attr("href", `/channel/${v.username}`)}>`;
    if (v.avatar) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<img class="avatar"${attr("src", v.avatar)}${attr("alt", v.username)}>`;
    } else {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--> <strong>@${escape_html(v.username)}</strong> <span class="muted">• ${escape_html(fmtNum(v.followers))} followers</span></a> `;
    if (store_get($$store_subs ??= {}, "$me", me) && store_get($$store_subs ??= {}, "$me", me).id !== v.user_id) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<button class="btn ghost">${escape_html(v.following ? "Following" : "Follow")}</button>`;
    } else {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--></div> <div class="row" style="margin: 12px 0; flex-wrap: wrap;"><button class="btn ghost"${attr("aria-pressed", v.liked)}><span${attr_class("material-symbols", void 0, { "filled": v.liked })}>favorite</span> ${escape_html(fmtNum(v.likes))}</button> <button class="btn ghost"><span class="material-symbols">share</span> Share</button></div> <button class="linkbtn">${escape_html(expanded ? "Show less" : "Show description")}</button> `;
    if (expanded) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<p class="muted" style="white-space: pre-wrap;">${escape_html(v.description || "No description yet.")}</p>`;
    } else {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--> <h3>Comments (${escape_html(comments.length)})</h3> `;
    if (!store_get($$store_subs ??= {}, "$me", me)) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<p class="muted"><a class="linkbtn" href="/auth">Log in to comment</a></p>`;
    } else {
      $$payload.out += "<!--[!-->";
      if (replyTo) {
        $$payload.out += "<!--[-->";
        $$payload.out += `<p>Replying to @${escape_html(replyTo.username)} <button class="linkbtn">cancel</button></p>`;
      } else {
        $$payload.out += "<!--[!-->";
      }
      $$payload.out += `<!--]--> <div class="replybox"><input${attr("placeholder", replyTo ? `Reply to @${replyTo.username}…` : "Add a comment…")}${attr("value", draft)}> <button class="btn"${attr("disabled", !draft.trim(), true)}>Post</button></div>`;
    }
    $$payload.out += `<!--]--> <div class="thread"><!--[-->`;
    for (let $$index_1 = 0, $$length = each_array.length; $$index_1 < $$length; $$index_1++) {
      let c = each_array[$$index_1];
      const each_array_1 = ensure_array_like(repliesOf(c.id));
      $$payload.out += `<div class="comment">`;
      if (c.avatar) {
        $$payload.out += "<!--[-->";
        $$payload.out += `<img class="avatar"${attr("src", c.avatar)}${attr("alt", c.username)}>`;
      } else {
        $$payload.out += "<!--[!-->";
      }
      $$payload.out += `<!--]--> <div class="grow"><div class="muted" style="font-size: 12px;">@${escape_html(c.username)} • ${escape_html(fmtAge(c.created_at))}</div> <p class="body">${escape_html(c.body)}</p> `;
      if (store_get($$store_subs ??= {}, "$me", me)) {
        $$payload.out += "<!--[-->";
        $$payload.out += `<button class="linkbtn">Reply</button>`;
      } else {
        $$payload.out += "<!--[!-->";
      }
      $$payload.out += `<!--]--> <!--[-->`;
      for (let $$index = 0, $$length2 = each_array_1.length; $$index < $$length2; $$index++) {
        let r = each_array_1[$$index];
        $$payload.out += `<div class="comment nested">`;
        if (r.avatar) {
          $$payload.out += "<!--[-->";
          $$payload.out += `<img class="avatar"${attr("src", r.avatar)}${attr("alt", r.username)}>`;
        } else {
          $$payload.out += "<!--[!-->";
        }
        $$payload.out += `<!--]--> <div class="grow"><div class="muted" style="font-size: 12px;">@${escape_html(r.username)} • ${escape_html(fmtAge(r.created_at))}</div> <p class="body">${escape_html(r.body)}</p></div></div>`;
      }
      $$payload.out += `<!--]--></div></div>`;
    }
    $$payload.out += `<!--]--></div>`;
  } else {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]-->`;
  if ($$store_subs) unsubscribe_stores($$store_subs);
  pop();
}
export {
  _page as default
};
