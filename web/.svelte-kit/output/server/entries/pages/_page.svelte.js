import { s as store_get, h as head, d as attr_class, u as unsubscribe_stores, p as pop, a as push, e as escape_html, f as ensure_array_like, b as attr } from "../../chunks/index.js";
import { p as page } from "../../chunks/stores.js";
import { a as api, f as fmtNum, b as fmtAge } from "../../chunks/api.js";
function _page($$payload, $$props) {
  push();
  var $$store_subs;
  let query;
  let videos = [];
  let loading = true;
  let loadingMore = false;
  let error = null;
  let sort = "new";
  let pageNum = 1;
  let pages = 1;
  async function load(reset = true) {
    if (reset) {
      loading = true;
      error = null;
      pageNum = 1;
    }
    try {
      const res = await api.videos({
        q: query || void 0,
        sort: sort === "popular" ? "popular" : "new",
        page: pageNum,
        limit: 24,
        kind: "video"
      });
      videos = reset ? res.videos ?? [] : [...videos, ...res.videos ?? []];
      pages = res.pages ?? 1;
    } catch (e) {
      error = e instanceof Error ? e.message : "Network error";
    } finally {
      loading = false;
      loadingMore = false;
    }
  }
  query = store_get($$store_subs ??= {}, "$page", page).url.searchParams.get("q") ?? "";
  if (query !== void 0) {
    void load(true);
  }
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>WatchShark</title>`;
  });
  $$payload.out += `<div class="segtabs" role="tablist" aria-label="Sort videos"><button${attr_class("segbtn", void 0, { "on": sort === "new" })}><span class="material-symbols">new_releases</span>Latest</button> <button${attr_class("segbtn", void 0, { "on": sort === "popular" })}><span class="material-symbols">local_fire_department</span>Trending</button></div> `;
  if (loading) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted center">Loading…</p>`;
  } else if (error && videos.length === 0) {
    $$payload.out += "<!--[1-->";
    $$payload.out += `<div class="center"><p>Couldn't load videos</p> <p class="muted">${escape_html(error)}</p> <p><button class="btn">Retry</button></p></div>`;
  } else if (videos.length === 0) {
    $$payload.out += "<!--[2-->";
    $$payload.out += `<div class="center"><p>No videos yet</p> <p class="muted">${escape_html(query ? "Try a different search" : "Pull to refresh")}</p></div>`;
  } else {
    $$payload.out += "<!--[!-->";
    const each_array = ensure_array_like(videos);
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
      $$payload.out += `<!--]--></div> <div class="meta">`;
      if (v.avatar) {
        $$payload.out += "<!--[-->";
        $$payload.out += `<img class="avatar"${attr("src", v.avatar)}${attr("alt", v.username)} loading="lazy">`;
      } else {
        $$payload.out += "<!--[!-->";
      }
      $$payload.out += `<!--]--> <div class="grow"><h3>${escape_html(v.title)}</h3> <p class="sub">@${escape_html(v.username)} • ${escape_html(fmtNum(v.views))} views • ${escape_html(fmtAge(v.created_at))}</p></div></div></a>`;
    }
    $$payload.out += `<!--]--></div> <div style="height: 8px"></div> `;
    if (loadingMore) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<p class="muted center">Loading more…</p>`;
    } else {
      $$payload.out += "<!--[!-->";
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
