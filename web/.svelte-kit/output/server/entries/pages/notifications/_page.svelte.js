import { h as head, p as pop, a as push } from "../../../chunks/index.js";
import "clsx";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
function _page($$payload, $$props) {
  push();
  let items = [];
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Notifications • WatchShark</title>`;
  });
  $$payload.out += `<div class="row"><h1 class="grow">Notifications</h1> `;
  if (items.length > 0) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<button class="btn ghost">Mark all read</button>`;
  } else {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--></div> `;
  {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted center">Loading…</p>`;
  }
  $$payload.out += `<!--]-->`;
  pop();
}
export {
  _page as default
};
