import { h as head, p as pop, a as push } from "../../../chunks/index.js";
function _page($$payload, $$props) {
  push();
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Messages • WatchShark</title>`;
  });
  $$payload.out += `<h1>Messages</h1> `;
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
