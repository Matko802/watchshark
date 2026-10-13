import { h as head, b as attr, p as pop, a as push } from "../../../chunks/index.js";
function _page($$payload, $$props) {
  push();
  let apkUrl = "https://github.com/Matko802/watchshark/releases/latest";
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>About • WatchShark</title>`;
  });
  $$payload.out += `<div class="center"><img src="/watchshark.webp" alt="WatchShark" width="96" height="96" style="border-radius: 24px;"> <h1>WatchShark</h1> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--> <p><a class="btn"${attr("href", apkUrl)}>Download for Android</a></p> <p class="muted">Native Kotlin app, Material 3 UI, same backend as this site.</p></div>`;
  pop();
}
export {
  _page as default
};
