import { h as head, d as attr_class, b as attr, e as escape_html, p as pop, a as push } from "../../../chunks/index.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
function _page($$payload, $$props) {
  push();
  let kind = "video";
  let title = "";
  let desc = "";
  let busy = false;
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Upload • WatchShark</title>`;
  });
  $$payload.out += `<h1>Upload</h1> <div class="segtabs" style="position: static;"><button${attr_class("segbtn", void 0, { "on": kind === "video" })}>Video</button> <button${attr_class("segbtn", void 0, { "on": kind === "wheel" })}>Wheel</button></div> <div class="field"><label for="up-title">Title</label> <input id="up-title"${attr("value", title)}${attr("maxlength", 120)}></div> <div class="field"><label for="up-desc">Description</label> <textarea id="up-desc" rows="3">`;
  const $$body = escape_html(desc);
  if ($$body) {
    $$payload.out += `${$$body}`;
  }
  $$payload.out += `</textarea></div> <div class="field"><label for="up-file">${escape_html("Video file")}</label> <input id="up-file" type="file" accept="video/*"> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--></div> <div class="field"><label for="up-thumb">Thumbnail (optional)</label> <input id="up-thumb" type="file" accept="image/*"> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--></div> <p><button class="btn"${attr("disabled", busy, true)}>${escape_html("Upload")}</button></p> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]-->`;
  pop();
}
export {
  _page as default
};
