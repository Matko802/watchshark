import "clsx";
import { p as pop, a as push } from "../../../chunks/index.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
function _page($$payload, $$props) {
  push();
  $$payload.out += `<p class="muted center">Redirecting…</p>`;
  pop();
}
export {
  _page as default
};
