import { h as head, b as attr, e as escape_html, p as pop, a as push } from "../../../chunks/index.js";
function _page($$payload, $$props) {
  push();
  let email = "";
  let busy = false;
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Forgot password • WatchShark</title>`;
  });
  $$payload.out += `<h1>Forgot password?</h1> `;
  {
    $$payload.out += "<!--[!-->";
    $$payload.out += `<div class="field"><label for="fg-email">Email</label> <input id="fg-email" type="email"${attr("value", email)} autocomplete="email"></div> `;
    {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--> <p><button class="btn"${attr("disabled", busy, true)}>${escape_html("Send reset link")}</button></p>`;
  }
  $$payload.out += `<!--]-->`;
  pop();
}
export {
  _page as default
};
