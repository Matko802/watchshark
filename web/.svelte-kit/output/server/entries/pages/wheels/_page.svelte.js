import { h as head, p as pop, a as push } from "../../../chunks/index.js";
import "clsx";
function _page($$payload, $$props) {
  push();
  let videos = [];
  let current = 0;
  let els = [];
  function syncPlayback() {
    els.forEach((el, i) => {
      if (!el) return;
      if (i === current) {
        el.play().catch(() => {
        });
      } else {
        el.pause();
      }
    });
  }
  if (videos.length && els.length) {
    queueMicrotask(syncPlayback);
  }
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Wheels • WatchShark</title>`;
  });
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
