import { s as store_get, h as head, b as attr, u as unsubscribe_stores, p as pop, a as push } from "../../../chunks/index.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
import { m as me } from "../../../chunks/session.js";
function _page($$payload, $$props) {
  push();
  var $$store_subs;
  let name = "";
  let curPw = "";
  let newPw = "";
  let notifOn = false;
  if (store_get($$store_subs ??= {}, "$me", me)) {
    notifOn = store_get($$store_subs ??= {}, "$me", me).notify_uploads;
  }
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Settings • WatchShark</title>`;
  });
  $$payload.out += `<h1>Settings</h1> `;
  if (!store_get($$store_subs ??= {}, "$me", me)) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted">You are browsing as a guest. <a class="linkbtn" href="/auth">Log in</a></p>`;
  } else {
    $$payload.out += "<!--[!-->";
    $$payload.out += `<h2>Account</h2> <div class="field"><label for="set-name">New handle</label> <input id="set-name"${attr("value", name)}></div> <p><button class="btn">Save name</button></p> <h2>Password</h2> <div class="field"><label for="set-cur">Current password</label> <input id="set-cur" type="password"${attr("value", curPw)} autocomplete="current-password"></div> <div class="field"><label for="set-new">New password (6+ chars)</label> <input id="set-new" type="password"${attr("value", newPw)} autocomplete="new-password"></div> <p><button class="btn">Save password</button></p> <h2>Notifications</h2> <label><input type="checkbox"${attr("checked", notifOn, true)}> Upload alerts from followed channels</label> `;
    if (store_get($$store_subs ??= {}, "$me", me).admin) {
      $$payload.out += "<!--[-->";
      $$payload.out += `<h2>Admin</h2> <p><a class="btn ghost" href="/admin">Open admin</a></p>`;
    } else {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--> <p><button class="btn ghost">Sign out</button></p> `;
    {
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
