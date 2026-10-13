import { s as store_get, h as head, b as attr, e as escape_html, u as unsubscribe_stores, p as pop, a as push } from "../../../chunks/index.js";
import { p as page } from "../../../chunks/stores.js";
function _page($$payload, $$props) {
  push();
  var $$store_subs;
  let token;
  let password = "";
  let busy = false;
  token = store_get($$store_subs ??= {}, "$page", page).url.searchParams.get("token") ?? "";
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>Reset password • WatchShark</title>`;
  });
  $$payload.out += `<h1>Reset password</h1> `;
  if (!token) {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="error">Missing token. Use the link from your email.</p>`;
  } else {
    $$payload.out += "<!--[!-->";
    $$payload.out += `<div class="field"><label for="rs-pw">New password (6+ chars)</label> <input id="rs-pw" type="password"${attr("value", password)} autocomplete="new-password"></div> `;
    {
      $$payload.out += "<!--[!-->";
    }
    $$payload.out += `<!--]--> <p><button class="btn"${attr("disabled", busy, true)}>${escape_html("Save new password")}</button></p>`;
  }
  $$payload.out += `<!--]-->`;
  if ($$store_subs) unsubscribe_stores($$store_subs);
  pop();
}
export {
  _page as default
};
