import { h as head, e as escape_html, b as attr, p as pop, a as push } from "../../../chunks/index.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils2.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/root.js";
import "../../../chunks/state.svelte.js";
function _page($$payload, $$props) {
  push();
  let login = "";
  let password = "";
  let showPw = false;
  let busy = false;
  head($$payload, ($$payload2) => {
    $$payload2.title = `<title>${escape_html("Log in")} • WatchShark</title>`;
  });
  $$payload.out += `<h1>${escape_html("Log in")}</h1> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--> <div class="field"><label for="auth-login">${escape_html("Email or handle")}</label> `;
  {
    $$payload.out += "<!--[-->";
    $$payload.out += `<input id="auth-login"${attr("value", login)} autocomplete="username">`;
  }
  $$payload.out += `<!--]--></div> `;
  {
    $$payload.out += "<!--[-->";
    $$payload.out += `<div class="field"><label for="auth-pw">Password</label> <input id="auth-pw"${attr("type", "password")}${attr("value", password)} autocomplete="current-password"></div>`;
  }
  $$payload.out += `<!--]--> <div class="field"><label><input type="checkbox"${attr("checked", showPw, true)}> Show password</label></div> `;
  {
    $$payload.out += "<!--[!-->";
  }
  $$payload.out += `<!--]--> <p><button class="btn"${attr("disabled", busy, true)}>${escape_html("Log in")}</button></p> <p><button class="linkbtn">${escape_html("Create account")}</button></p> `;
  {
    $$payload.out += "<!--[-->";
    $$payload.out += `<p><a class="linkbtn" href="/forgot">Forgot password?</a></p>`;
  }
  $$payload.out += `<!--]-->`;
  pop();
}
export {
  _page as default
};
