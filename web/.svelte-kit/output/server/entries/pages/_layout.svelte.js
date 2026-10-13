import { s as store_get, b as attr, e as escape_html, c as slot, d as attr_class, u as unsubscribe_stores, p as pop, a as push } from "../../chunks/index.js";
import { p as page } from "../../chunks/stores.js";
import { u as unread } from "../../chunks/session.js";
function _layout($$payload, $$props) {
  push();
  var $$store_subs;
  let tab;
  let query = "";
  tab = store_get($$store_subs ??= {}, "$page", page).url.pathname === "/" ? "home" : store_get($$store_subs ??= {}, "$page", page).url.pathname.startsWith("/wheels") ? "wheels" : store_get($$store_subs ??= {}, "$page", page).url.pathname.startsWith("/messages") || store_get($$store_subs ??= {}, "$page", page).url.pathname.startsWith("/chat") ? "messages" : "you";
  $$payload.out += `<div class="app"><header class="topbar"><a class="brand" href="/"><img src="/watchshark.webp" alt="WatchShark"> <span>WatchShark</span></a> <form class="search"><input placeholder="Search"${attr("value", query)} aria-label="Search"></form> <a class="iconbtn" href="/notifications" aria-label="Notifications"><span class="material-symbols">notifications</span> `;
  if (store_get($$store_subs ??= {}, "$unread", unread) > 0) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<span class="badge">${escape_html(store_get($$store_subs ??= {}, "$unread", unread) > 9 ? "9+" : store_get($$store_subs ??= {}, "$unread", unread))}</span>`;
  } else {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--></a> <a class="iconbtn" href="/settings" aria-label="Settings"><span class="material-symbols">settings</span></a></header> <main><!---->`;
  slot($$payload, $$props, "default", {});
  $$payload.out += `<!----></main> <nav class="pillnav" aria-label="Sections"><a href="/"${attr_class("", void 0, { "active": tab === "home" })}><span class="ico"><span class="material-symbols">home</span></span> <span>Home</span></a> <a href="/wheels"${attr_class("", void 0, { "active": tab === "wheels" })}><span class="ico"><span class="material-symbols">movie</span></span> <span>Wheels</span></a> <a href="/upload" class="create" aria-label="Create"><span class="ico"><span class="material-symbols">add</span></span></a> <a href="/messages"${attr_class("", void 0, { "active": tab === "messages" })}><span class="ico"><span class="material-symbols">chat</span></span> <span>Messages</span></a> <a href="/settings"${attr_class("", void 0, { "active": tab === "you" })}><span class="ico"><span class="material-symbols">person</span></span> <span>You</span></a></nav></div>`;
  if ($$store_subs) unsubscribe_stores($$store_subs);
  pop();
}
export {
  _layout as default
};
