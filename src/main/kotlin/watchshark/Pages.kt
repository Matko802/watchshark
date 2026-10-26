package watchshark

import io.javalin.http.Context
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlinx.html.stream.createHTML

object Pages {
    private const val CSS_V = "43"
    private const val APPJS_V = "29"

    private const val THEME_SCRIPT =
        "try{var t=localStorage.getItem('ws_theme');if(t!=='dark'&&t!=='light'){t=(window.matchMedia&&matchMedia('(prefers-color-scheme: light)').matches)?'light':'dark'}document.documentElement.dataset.theme=t}catch(e){}"

    private const val BOOTLOADER = """<div id="bootloader"><div class="boots"></div></div>"""

    private const val brand = """<a class="brand" href="/"><img src="/watchshark.webp?v=2" alt="WatchShark logo"><span>WatchShark</span></a>"""
    private const val bell = """<span id="bellSlot" data-auth="in" style="display:none"></span>"""
    private const val settingsBtn =
        """<md-icon-button data-auth="in" href="/settings" aria-label="Settings" style="display:none"><md-icon>settings</md-icon></md-icon-button>"""
    private const val adminBtn = """<md-text-button data-admin href="/admin" style="display:none">Admin</md-text-button>"""
    private const val createBtn =
        """<a class="createcircle" data-auth="in" href="/upload" style="display:none" aria-label="Create"><md-icon>add</md-icon></a>"""
    private const val whoami = """<span id="whoami"></span>"""
    private const val signinBtn =
        """<md-filled-button data-auth="out" onclick="openAuth('login')" style="display:none">Sign in</md-filled-button>"""
    private const val searchbox = """<div class="searchbox collapsed" id="searchbox">
    <md-icon-button id="searchToggle" aria-label="Search" onclick="toggleSearch()"><md-icon id="searchToggleIcon">search</md-icon></md-icon-button>
    <md-outlined-text-field id="q" placeholder="Search" class="search">
      <md-icon slot="leading-icon">search</md-icon>
    </md-outlined-text-field>
  </div>"""
    private const val spacer = """<span class="spacer"></span>"""

    private const val bottomnav = """<nav id="bottomnav">
  <a href="/" data-tab="home"><md-icon>home</md-icon><span>Home</span></a>
  <a href="/wheels" data-tab="wheels"><md-icon>movie</md-icon><span>Wheels</span></a>
  <a href="/upload" class="createbtn" aria-label="Create"><md-icon>add</md-icon></a>
  <a href="/messages" data-tab="messages"><md-icon>chat</md-icon><span>Messages</span></a>
  <a href="/settings" data-tab="account" id="navAccount"><span id="navAvatar"><md-icon>person</md-icon></span><span>You</span></a>
</nav>"""

    private fun authDialog(headline: String): String = """<md-dialog id="authDialog">
  <div slot="headline">$headline</div>
  <div slot="content">
    <form id="loginPane" method="post" action="/api/auth/login" onsubmit="return submitLogin(event)">
      <div class="nfield">
        <label for="login-id">Email</label>
        <input placeholder=" " id="login-id" name="username" type="text" autocomplete="username" autocapitalize="none" autocorrect="off" spellcheck="false" required>
      </div>
      <div class="nfield">
        <label for="login-pw">Password</label>
        <input placeholder=" " id="login-pw" name="password" type="password" autocomplete="current-password" required>
      </div>
      <div class="auth-row"><md-text-button onclick="location.href='/forgot'">Forgot password?</md-text-button></div>
      <button type="submit" tabindex="-1" aria-hidden="true" style="position:fixed;top:0;left:0;width:8px;height:8px;opacity:.01;pointer-events:none;border:0;padding:0"></button>
    </form>
    <form id="signupPane" method="post" action="/api/auth/signup" onsubmit="return submitSignup(event)" style="display:none">
      <div class="nfield">
        <label for="su-name">Handle (a-z, 0-9, _)</label>
        <input placeholder=" " id="su-name" name="username" type="text" autocomplete="username" autocapitalize="none" autocorrect="off" spellcheck="false" minlength="3" maxlength="30" required>
      </div>
      <div class="nfield">
        <label for="su-email">Email</label>
        <input placeholder=" " id="su-email" name="email" type="email" autocomplete="email" autocapitalize="none" autocorrect="off" spellcheck="false" required>
      </div>
      <div class="nfield">
        <label for="su-pw">Password (6+ chars)</label>
        <input placeholder=" " id="su-pw" name="password" type="password" autocomplete="new-password" minlength="6" required>
      </div>
      <button type="submit" tabindex="-1" aria-hidden="true" style="position:fixed;top:0;left:0;width:8px;height:8px;opacity:.01;pointer-events:none;border:0;padding:0"></button>
    </form>
    <p id="auth-err" style="color:var(--md-sys-color-error)"></p>
  </div>
  <div slot="actions">
    <md-text-button id="auth-switch" onclick="toggleAuth()">Create account</md-text-button>
    <md-filled-button id="auth-go" onclick="authGo()">Log in</md-filled-button>
    <md-text-button onclick="closeAuth()">Close</md-text-button>
  </div>
</md-dialog>"""

    private fun topbarHtml(vararg parts: String): String =
        """<header class="topbar">
  """ + parts.joinToString("\n  ") + "\n</header>"

    private val topHome by lazy {
        topbarHtml(brand, searchbox, spacer, bell, settingsBtn, adminBtn, createBtn, whoami, signinBtn)
    }
    private val topFull by lazy {
        topbarHtml(brand, spacer, bell, settingsBtn, adminBtn, createBtn, whoami, signinBtn)
    }
    private val topAdminFirst by lazy {
        topbarHtml(brand, spacer, bell, adminBtn, settingsBtn, createBtn, whoami, signinBtn)
    }
    private val topSettings by lazy {
        topbarHtml(brand, spacer, bell, settingsBtn, adminBtn, whoami)
    }
    private val topUpload by lazy {
        topbarHtml(brand, spacer, bell, settingsBtn, adminBtn)
    }
    private val topAdmin by lazy {
        topbarHtml(brand, spacer, bell, settingsBtn, whoami)
    }
    private val topWheels by lazy {
        topbarHtml(brand, spacer, bell, whoami)
    }
    private val topBrand by lazy {
        topbarHtml(brand)
    }

    private class Body(
        val main: String,
        val navScript: String? = null,
        val tail: String? = null,
        val css: String? = null,
    )

    private class Spec(
        val title: String,
        val ogTitle: String? = null,
        val ogDescription: String? = null,
        val topbar: String,
        val bodyClass: String? = null,
        val mainClass: String? = null,
        val headline: String? = null,
        val nav: Boolean = false,
        val appJs: Boolean = true,
        val swiper: Boolean = false,
        val extraDialogs: Boolean = false,
        val crawlerMeta: Boolean = false,
    )

    private val bodies = mapOf(
        "index" to Body(PageBodies.INDEX_MAIN, PageBodies.INDEX_NAVSCRIPT, PageBodies.INDEX_TAIL),
        "about" to Body(PageBodies.ABOUT_MAIN, PageBodies.ABOUT_NAVSCRIPT, PageBodies.ABOUT_TAIL, css = PageBodies.ABOUT_CSS),
        "forgot" to Body(PageBodies.FORGOT_MAIN),
        "verify" to Body(PageBodies.VERIFY_MAIN, tail = PageBodies.VERIFY_TAIL),
        "reset" to Body(PageBodies.RESET_MAIN, tail = PageBodies.RESET_TAIL),
        "upload" to Body(PageBodies.UPLOAD_MAIN, PageBodies.UPLOAD_NAVSCRIPT, PageBodies.UPLOAD_TAIL),
        "settings" to Body(PageBodies.SETTINGS_MAIN, PageBodies.SETTINGS_NAVSCRIPT, PageBodies.SETTINGS_TAIL),
        "admin" to Body(PageBodies.ADMIN_MAIN, PageBodies.ADMIN_NAVSCRIPT, PageBodies.ADMIN_TAIL),
        "channel" to Body(PageBodies.CHANNEL_MAIN, PageBodies.CHANNEL_NAVSCRIPT, PageBodies.CHANNEL_TAIL),
        "watch" to Body(PageBodies.WATCH_MAIN, tail = PageBodies.WATCH_TAIL),
        "wheels" to Body(PageBodies.WHEELS_MAIN, PageBodies.WHEELS_NAVSCRIPT, PageBodies.WHEELS_TAIL),
        "messages" to Body(PageBodies.MESSAGES_MAIN, PageBodies.MESSAGES_NAVSCRIPT, PageBodies.MESSAGES_TAIL, css = PageBodies.MESSAGES_CSS),
    )

    private val specs = mapOf(
        "index" to Spec(
            title = "WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topHome,
            nav = true,
            headline = "Welcome to WatchShark",
        ),
        "about" to Spec(
            title = "About — WatchShark",
            ogTitle = "About — WatchShark",
            ogDescription = "What WatchShark is, where the code lives, and where to get the Android app.",
            topbar = topFull,
            nav = true,
        ),
        "forgot" to Spec(
            title = "Forgot password — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topBrand,
        ),
        "verify" to Spec(
            title = "Confirm email — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topBrand,
            appJs = false,
        ),
        "reset" to Spec(
            title = "New password — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topBrand,
            appJs = false,
        ),
        "upload" to Spec(
            title = "Upload — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topUpload,
            nav = true,
        ),
        "settings" to Spec(
            title = "Settings — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topSettings,
            nav = true,
        ),
        "admin" to Spec(
            title = "Admin — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topAdmin,
            nav = true,
        ),
        "channel" to Spec(
            title = "Channel — WatchShark",
            topbar = topAdminFirst,
            nav = true,
            headline = "Welcome to WatchShark",
            crawlerMeta = true,
        ),
        "watch" to Spec(
            title = "Watch — WatchShark",
            topbar = topFull,
            mainClass = "watchmain",
            headline = "Login required",
            extraDialogs = true,
            crawlerMeta = true,
        ),
        "wheels" to Spec(
            title = "Wheels — WatchShark",
            ogTitle = "WatchShark",
            ogDescription = "WatchShark — watch and share videos and wheels.",
            topbar = topWheels,
            bodyClass = "reelbody",
            mainClass = "reelmain",
            nav = true,
            swiper = true,
        ),
        "messages" to Spec(
            title = "Messages — WatchShark",
            ogTitle = "Messages — WatchShark",
            ogDescription = "WatchShark direct messages.",
            topbar = topFull,
            nav = true,
            headline = "Welcome to WatchShark",
        ),
    )

    private fun ogBlock(spec: Spec): String =
        if (spec.ogTitle == null) {
            """<meta property="og:site_name" content="WatchShark">
<meta name="twitter:card" content="summary_large_image">
"""
        } else {
            """<meta property="og:site_name" content="WatchShark">
<meta property="og:type" content="website">
<meta name="twitter:card" content="summary_large_image">
<meta property="og:title" content="${spec.ogTitle}">
<meta property="og:description" content="${spec.ogDescription}">
<meta property="og:image" content="${Config.siteBase()}/watchshark.webp">
"""
        }

    private fun contentBlock(spec: Spec, main: String): String {
        val sb = StringBuilder()
        sb.append("<main")
        if (spec.mainClass != null) sb.append(' ').append("class=\"").append(spec.mainClass).append('"')
        sb.append(">\n").append(main).append("\n</main>\n")
        spec.headline?.let { sb.append(authDialog(it)).append('\n') }
        if (spec.extraDialogs) sb.append(PageBodies.WATCH_EXTRADIALOGS).append('\n')
        return sb.toString()
    }

    private fun headRest(spec: Spec, css: String?): String {
        val sb = StringBuilder()
        sb.append("""<link rel="icon" type="image/png" href="/favicon.png?v=1">
<script>""").append(THEME_SCRIPT).append("</script>\n")
        sb.append("""<link rel="stylesheet" href="/styles.css?v=""").append(CSS_V).append("\">\n")
        sb.append("""<link rel="preload" href="/vendor/fonts/f1.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/vendor/fonts/f0.woff2" as="font" type="font/woff2" crossorigin>
<link href="/vendor/fonts.css" rel="stylesheet">
""")
        if (spec.swiper) sb.append("""<link rel="stylesheet" href="/swiper-bundle.min.css?v=1">
""")
        sb.append("""
<link rel="modulepreload" href="/vendor/material-web.js">
<script type="module">import '/vendor/material-web.js';</script>
""")
        if (spec.swiper) sb.append("""<script src="/swiper-bundle.min.js?v=1"></script>
""")
        sb.append(ogBlock(spec))
        if (css != null) sb.append("""<style>
""").append(css).append("""
</style>
""")
        return sb.toString()
    }

    private fun bodyBlock(spec: Spec, bd: Body): String {
        val sb = StringBuilder(BOOTLOADER)
        sb.append('\n').append(spec.topbar).append('\n')
        sb.append(contentBlock(spec, bd.main))
        if (spec.nav) sb.append(bottomnav).append('\n')
        bd.navScript?.let { sb.append("<script>").append(it).append("</script>\n") }
        if (spec.appJs) sb.append("""<script src="/app.js?v=""").append(APPJS_V).append("\"></script>\n")
        bd.tail?.let { sb.append("<script>").append(it).append("</script>\n") }
        return sb.toString()
    }

    private fun render(key: String, spec: Spec, bd: Body): String {
        val page = createHTML(prettyPrint = false).html {
            attributes["lang"] = "en"
            head {
                meta { attributes["charset"] = "utf-8" }
                meta { attributes["name"] = "viewport"; attributes["content"] = "width=device-width,initial-scale=1" }
                title { +spec.title }
                unsafe { raw(headRest(spec, bd.css)) }
            }
            body {
                if (spec.bodyClass != null) attributes["class"] = spec.bodyClass
                attributes["data-pjax"] = "1"
                unsafe { raw(bodyBlock(spec, bd)) }
            }
        }
        return "<!DOCTYPE html>\n" + page
    }

    private val rendered: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        for ((key, spec) in specs) {
            val html = render(key, spec, bodies.getValue(key))
            out[key] = html
            out["$key.html"] = html
        }
        out
    }

    private fun respond(ctx: Context, html: String) {
        ctx.header("Content-Type", "text/html; charset=utf-8")
        ctx.header("X-Content-Type-Options", "nosniff")
        ctx.header("Cache-Control", "no-cache")
        ctx.status(200)
        if (ctx.method().name != "HEAD") ctx.result(html)
    }

    fun serve(ctx: Context, page: String): Boolean {
        val key = page.removeSuffix(".html")
        val spec = specs[key] ?: return false
        val html = rendered["$key.html"] ?: return false
        if (spec.crawlerMeta && Static.isCrawler(ctx)) {
            val extra = when (key) {
                "watch" -> {
                    val (id, rest, ok) = HttpUtil.parseId(ctx.queryParam("id") ?: "")
                    if (ok && rest == "") Static.watchMeta(id) else ""
                }
                else -> Static.channelMeta(ctx.queryParam("user") ?: "")
            }
            if (extra.isNotEmpty()) {
                val i = html.indexOf("</head>")
                if (i >= 0) {
                    respond(ctx, html.substring(0, i) + extra + html.substring(i))
                    return true
                }
            }
        }
        respond(ctx, html)
        return true
    }

    fun has(page: String): Boolean {
        val key = page.removeSuffix(".html")
        return key in specs
    }
}
