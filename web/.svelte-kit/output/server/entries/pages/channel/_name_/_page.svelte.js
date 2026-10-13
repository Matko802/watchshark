import { s as store_get, h as head, u as unsubscribe_stores, p as pop, a as push, e as escape_html, b as attr, d as attr_class, f as ensure_array_like } from "../../../../chunks/index.js";
import { p as page } from "../../../../chunks/stores.js";
import { a as api, f as fmtNum, b as fmtAge } from "../../../../chunks/api.js";
import { m as me } from "../../../../chunks/session.js";
function _page($$payload, $$props) {
  push();
  var $$store_subs;
  let name, shown;
  let user = null;
  let videos = [];
  let loading = true;
  let error = null;
  let tab = 0;
  async function load() {
    if (!name) return;
    loading = true;
    error = null;
    try {
      const res = await api.channel(name);
      user = res.user;
      videos = res.videos ?? [];
    } catch (e) {
      error = e instanceof Error ? e.message : "Network error";
    } finally {
      loading = false;
    }
  }
  name = store_get($$store_subs ??= {}, "$page", page).params.name ?? "";
  if (name) void load();
  shown = videos.filter((v) => v.kind !== "wheel");
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>${escape_html(user ? `@${user.username} • WatchShark` : "Channel • WatchShark")}</title>`;
  });
  if (loading) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted center">Loading…</p>`;
  } else if (error || !user) {
    $$payload.out += "<!--[1-->";
    $$payload.out += `<div class="center"><p>${escape_html(error ?? "Channel not found")}</p> <p><button class="btn">Retry</button></p></div>`;
  } else {
    $$payload.out += "<!--[!-->";
    $$payload.out += `<div class="row" style="padding: 16px 4px;">`;
    if (user.avatar) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<img class="avatar" style="width:64px;height:64px;"${attr("src", user.avatar)}${attr("alt", user.username)}>`;
    } else {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--> <div class="grow"><h2 style="margin: 0;">@${escape_html(user.username)}</h2> <p class="muted" style="margin: 4px 0 0;">${escape_html(fmtNum(user.followers))} followers • ${escape_html(fmtNum(user.videos))} videos • ${escape_html(fmtNum(user.views))} views</p></div> `;
    if (store_get($$store_subs ??= {}, "$me", me) && store_get($$store_subs ??= {}, "$me", me).id !== user.id) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<button class="btn ghost">${escape_html(user.following ? "Following" : "Follow")}</button>`;
    } else {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--></div> <div class="segtabs" style="position: static;"><button${attr_class("segbtn", void 0, { "on": tab === 0 })}><span class="material-symbols">play_circle</span>Videos</button> <button${attr_class("segbtn", void 0, { "on": tab === 1 })}><span class="material-symbols">movie</span>Wheels</button></div> `;
    if (shown.length === 0) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<p class="muted center">Nothing here yet.</p>`;
    } else {
      $$payload.out += "<!--[!-->";
      const each_array = ensure_array_like(shown);
      $$payload.out += `<div class="grid"><!--[-->`;
      for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
        let v = each_array[$$index];
        $$payload.out += `<a class="card"${attr("href", `/watch/${v.id}`)}><div class="thumb">`;
        if (v.thumbnail) {
          $$payload.out += "<!--[-->";
          $$payload.out += `<img${attr("src", v.thumbnail)}${attr("alt", v.title)} loading="lazy">`;
        } else {
          $$payload.out += "<!--[!-->";
        }
        $$payload.out += `<!--]--></div> <div class="meta"><div class="grow"><h3>${escape_html(v.title)}</h3> <p class="sub">${escape_html(fmtNum(v.views))} views • ${escape_html(fmtAge(v.created_at))}</p></div></div></a>`;
      }
      $$payload.out += `<!--]--></div>`;
    }
    $$payload.out += `<!--]-->`;
  }
  $$payload.out += `<!--]-->`;
  if ($$store_subs) unsubscribe_stores($$store_subs);
  pop();
}
export {
  _page as default
};
