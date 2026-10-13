import { h as head, p as pop, a as push } from "../../../chunks/index.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "clsx";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
function _page($$payload, $$props) {
  push();
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Verify • WatchShark</title>`;
  });
  $$payload.out += `<h1>Verify account</h1> `;
  {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted">Verifying…</p>`;
  }
  $$payload.out += `<!--]-->`;
  pop();
}
export {
  _page as default
};
