import { s as store_get, h as head, e as escape_html, b as attr, u as unsubscribe_stores, p as pop, a as push, i as stringify } from "../../../../chunks/index.js";
import { p as page } from "../../../../chunks/stores.js";
function _page($$payload, $$props) {
  push();
  var $$store_subs;
  let username;
  let draft = "";
  username = store_get($$store_subs ??= {}, "$page", page).params.username ?? "";
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>@${escape_html(username)} • WatchShark</title>`;
  });
  $$payload.out += `<h1 style="font-size: 20px;">@${escape_html(username)}</h1> `;
  {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p class="muted center">Loading…</p>`;
  }
  $$payload.out += `<!--]--> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--> <div class="replybox"><input${attr("placeholder", `Message @${stringify(username)}`)}${attr("value", draft)}> <button class="btn"${attr("disabled", !draft.trim(), true)}>Send</button></div>`;
  if ($$store_subs) unsubscribe_stores($$store_subs);
  pop();
}
export {
  _page as default
};
