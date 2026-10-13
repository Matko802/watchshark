import { f as ensure_array_like, h as head, b as attr, d as attr_class, e as escape_html, p as pop, a as push } from "../../../chunks/index.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
function _page($$payload, $$props) {
  push();
  let shown;
  let users = [];
  let query = "";
  let filter = "all";
  let days = {};
  let reasons = {};
  function status(u) {
    if (u.deleted) return "deleted";
    if (u.banned) return "banned";
    if (u.verified) return "active";
    return "pending";
  }
  shown = users.filter((u) => {
    const q = query.trim().toLowerCase();
    return (!q || u.username.toLowerCase().includes(q)) && filter === "all";
  });
  const each_array = ensure_array_like([
    "all",
    "pending",
    "active",
    "banned",
    "deleted"
  ]);
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Admin • WatchShark</title>`;
  });
  $$payload.out += `<h1>Admin</h1> <div class="field"><label for="adm-q">Search users</label> <input id="adm-q"${attr("value", query)} class="svelte-9q3bk7"></div> <div class="segtabs" style="position: static;"><!--[-->`;
  for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
    let f = each_array[$$index];
    $$payload.out += `<button${attr_class("segbtn", void 0, { "on": filter === f })}>${escape_html(f)}</button>`;
  }
  $$payload.out += `<!--]--></div> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--> `;
  if (users.length === 0) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted center">Loading…</p>`;
  } else {
    $$payload.out += "<!--[!-->";
    const each_array_1 = ensure_array_like(shown);
    $$payload.out += `<!--[-->`;
    for (let $$index_1 = 0, $$length = each_array_1.length; $$index_1 < $$length; $$index_1++) {
      let u = each_array_1[$$index_1];
      $$payload.out += `<div style="border: 1px solid var(--outline); border-radius: 16px; padding: 12px 14px; margin: 8px 0;"><div class="row">`;
      if (u.avatar) {
        $$payload.out += "<!--[-->";
        $$payload.out += `<img class="avatar"${attr("src", u.avatar)}${attr("alt", u.username)}>`;
      } else {
        $$payload.out += "<!--[!-->";
      }
      $$payload.out += `<!--]--> <div class="grow"><div><strong>${escape_html(u.username)}</strong>${escape_html(u.role === "admin" ? " ⭐" : "")}</div> <div class="muted" style="font-size: 13px;">${escape_html(status(u))}${escape_html(u.banned ? ` • ${u.ban_days_left < 0 ? "permanent" : `${u.ban_days_left.toFixed(1)}d left`}${u.ban_reason ? ` • ${u.ban_reason}` : ""}` : "")}${escape_html(u.deleted && u.deleted_reason ? ` • ${u.deleted_reason}` : "")}</div></div></div> `;
      if (u.role !== "admin") {
        $$payload.out += "<!--[-->";
        if (!u.deleted && !u.banned) {
          $$payload.out += "<!--[-->";
          $$payload.out += `<div class="row" style="margin-top: 8px;"><input placeholder="Days"${attr("value", days[u.id])} style="width: 90px;" class="svelte-9q3bk7"> <input placeholder="Reason"${attr("value", reasons[u.id])} class="grow svelte-9q3bk7"> <button class="btn">Ban</button></div>`;
        } else {
          $$payload.out += "<!--[!-->";
        }
        $$payload.out += `<!--]--> <div class="row" style="margin-top: 8px; flex-wrap: wrap;">`;
        if (!u.verified && !u.deleted) {
          $$payload.out += "<!--[-->";
          $$payload.out += `<button class="btn ghost">Approve</button>`;
        } else {
          $$payload.out += "<!--[!-->";
        }
        $$payload.out += `<!--]--> `;
        if (u.banned && !u.deleted) {
          $$payload.out += "<!--[-->";
          $$payload.out += `<button class="btn ghost">Unban</button>`;
        } else {
          $$payload.out += "<!--[!-->";
        }
        $$payload.out += `<!--]--> `;
        if (u.deleted) {
          $$payload.out += "<!--[-->";
          $$payload.out += `<button class="btn ghost">Restore</button>`;
        } else {
          $$payload.out += "<!--[!-->";
          $$payload.out += `<button class="btn ghost">Delete</button>`;
        }
        $$payload.out += `<!--]--> <button class="btn ghost">Remove</button></div>`;
      } else {
        $$payload.out += "<!--[!-->";
      }
      $$payload.out += `<!--]--></div>`;
    }
    $$payload.out += `<!--]-->`;
  }
  $$payload.out += `<!--]-->`;
  pop();
}
export {
  _page as default
};
