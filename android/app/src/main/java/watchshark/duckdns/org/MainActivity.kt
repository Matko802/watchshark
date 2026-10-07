package watchshark.duckdns.org
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.ui.applyBarClearance
import watchshark.duckdns.org.ui.AdminFragment
import watchshark.duckdns.org.ui.AuthFragment
import watchshark.duckdns.org.ui.ChannelFragment
import watchshark.duckdns.org.ui.ChatFragment
import watchshark.duckdns.org.ui.HomeFragment
import watchshark.duckdns.org.ui.MessagesFragment
import watchshark.duckdns.org.ui.NotificationsFragment
import watchshark.duckdns.org.ui.SettingsFragment
import watchshark.duckdns.org.ui.UploadFragment
import watchshark.duckdns.org.ui.WatchFragment
import watchshark.duckdns.org.ui.WheelsFragment
import watchshark.duckdns.org.ui.loadMedia
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Always follow the system light/dark theme.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        watchshark.duckdns.org.data.CrashLog.install(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Draw into the display cutout instead of letterboxing: without this
        // landscape gets a black bar next to the camera cutout.
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            // Follow the system theme: dark icons on light backgrounds and vice versa.
            val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        setTheme(R.style.Theme_WatchShark)
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        watchshark.duckdns.org.data.Updater.init(this)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge()
        layoutNavForOrientation()
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0 && currentTab == "you") {
                val tag = supportFragmentManager.findFragmentById(R.id.container)?.tag
                if (tag == "home" || tag == "wheels" || tag == "messages") {
                    currentTab = tag
                    markNav()
                }
            }
            val onDetail = supportFragmentManager.backStackEntryCount > 0
            findViewById<View>(R.id.top_search_btn)?.visibility =
                if (onDetail) View.GONE else View.VISIBLE
            if (onDetail && searchExpanded) collapseSearch(clear = false)
            syncBars()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }
                if (searchExpanded) {
                    collapseSearch(clear = true)
                    return
                }
                if (tabHistory.size > 1) {
                    tabHistory.removeLast()
                    goRoot(rootFragment(tabHistory.last()), tabHistory.last(), false)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        watchshark.duckdns.org.data.CrashLog.showNow(this)
        setupTopSearch()
        findViewById<View>(R.id.brand_icon).setOnClickListener { showHome() }
        findViewById<View>(R.id.brand_text).setOnClickListener { showHome() }
        findViewById<ImageButton>(R.id.bell_btn).setOnClickListener { openDetail(NotificationsFragment()) }
        findViewById<ImageButton>(R.id.top_settings).setOnClickListener { openDetail(SettingsFragment()) }
        findViewById<MaterialButton>(R.id.top_admin).setOnClickListener { openAdmin() }
        findViewById<MaterialButton>(R.id.signin_btn).setOnClickListener { showAuth() }
        findViewById<View>(R.id.nav_home).setOnClickListener { showHome() }
        findViewById<View>(R.id.nav_wheels).setOnClickListener { showWheels() }
        findViewById<View>(R.id.nav_create).setOnClickListener {
            openDetail(UploadFragment.newInstance("video"))
        }
        findViewById<View>(R.id.nav_messages).setOnClickListener { showMessages() }
        findViewById<View>(R.id.nav_you).setOnClickListener {
            val name = currentUsername
            if (name != null) openChannel(name) else showAuth()
        }
        if (savedInstanceState == null) {
            if (!handleShortcut(intent)) {
                if (ApiClient.sessionToken().isNullOrEmpty()) {
                    showAuth()
                } else {
                    showHome()
                }
            }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }
    /** Launcher shortcut routing (long-press app icon). Returns true if handled. */
    private fun handleShortcut(intent: android.content.Intent?): Boolean {
        when (intent?.action) {
            "watchshark.duckdns.org.action.HOME" -> showHome()
            "watchshark.duckdns.org.action.WHEELS" -> showWheels()
            "watchshark.duckdns.org.action.MESSAGES" -> showMessages()
            "watchshark.duckdns.org.action.UPLOAD" ->
                openDetail(UploadFragment.newInstance("video"))
            "watchshark.duckdns.org.action.DM" -> {
                val uid = intent.getLongExtra("user_id", 0)
                val name = intent.getStringExtra("username") ?: ""
                if (uid > 0 && name.isNotEmpty()) openDetail(ChatFragment.newInstance(uid, name))
                else openDetail(MessagesFragment())
            }
            "watchshark.duckdns.org.action.WATCH" -> {
                val vid = intent.getLongExtra("video_id", 0)
                val nid = intent.getLongExtra("notif_id", 0)
                if (nid > 0) lifecycleScope.launch {
                    try {
                        ApiClient.api.notifRead(mapOf("id" to nid))
                    } catch (_: Exception) {
                    }
                }
                if (vid > 0) openDetail(WatchFragment.newInstance(vid)) else showHome()
            }
            else -> return false
        }
        return true
    }
    override fun onResume() {
        super.onResume()
        layoutNavForOrientation()
        syncBars()
        if (!ApiClient.sessionToken().isNullOrEmpty()) {
            watchshark.duckdns.org.data.UploadAlerts.ensureScheduled(this)
            supportFragmentManager.findFragmentById(R.id.container)?.let { frag ->
                if (frag.isAdded) watchshark.duckdns.org.data.Updater.checkSilent(frag)
            }
        }
    }
    private var currentUsername: String? = null
    private var currentTab = "home"
    private var updateChecked = false
    /**
     * Pushes the system-bar insets INTO the top/bottom bars (as extra
     * padding) instead of padding the root. That way the bars themselves
     * extend behind the status + gesture bars — no black strips above the
     * topbar or below the bottom nav. Cutout insets are folded in so the
     * camera hole never gets a letterbox bar in landscape.
     */
    private fun applyEdgeToEdge() {
        val density = resources.displayMetrics.density
        val root: View = findViewById(R.id.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            val cut = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val left = maxOf(bars.left, cut.left)
            val right = maxOf(bars.right, cut.right)
            findViewById<View>(R.id.topbar)?.setPadding(
                (8 * density).toInt() + left,
                (8 * density).toInt() + maxOf(bars.top, cut.top),
                (8 * density).toInt() + right,
                (8 * density).toInt()
            )
            // Floating pill nav: inset padding goes on the outer container so
            // the pill itself keeps its shape on every screen size. In
            // landscape the pill is a right-side rail, so the inset goes
            // on the end instead of the bottom.
            if (isLandscape()) {
                findViewById<View>(R.id.bottomnav)?.setPadding(
                    0,
                    (12 * density).toInt(),
                    0,
                    (12 * density).toInt()
                )
                findViewById<View>(R.id.nav_row)?.setPadding(
                    (8 * density).toInt(),
                    (9 * density).toInt(),
                    (8 * density).toInt() + right,
                    (7 * density).toInt()
                )
            } else {
                findViewById<View>(R.id.bottomnav)?.setPadding(
                    (16 * density).toInt() + left,
                    0,
                    (16 * density).toInt() + right,
                    (12 * density).toInt() + maxOf(bars.bottom, cut.bottom)
                )
                // Restore the pill's own padding (landscape adds the
                // cutout inset to its end).
                findViewById<View>(R.id.nav_row)?.setPadding(
                    (8 * density).toInt(),
                    (9 * density).toInt(),
                    (8 * density).toInt(),
                    (7 * density).toInt()
                )
            }
            val navH = findViewById<View>(R.id.bottomnav)?.height ?: 0
            val imePx = (ime.bottom - navH).coerceAtLeast(0)
            imeBottomPx = imePx
            findViewById<View>(R.id.bottom_search_bar)?.let { strip ->
                if (searchExpanded && !searchAnimating) strip.translationY = -imePx.toFloat()
            }
            insets
        }
    }
    private fun isLandscape(): Boolean {
        return resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }
    /**
     * Landscape turns the bottom pill into a right-side rail (YouTube
     * style): the container docks to the end, the pill stacks vertically
     * with icon-only buttons, and content clears it via container padding.
     * Called on resume + rotation; guarded so steady state costs nothing.
     */
    private var navLandscape: Boolean? = null
    fun layoutNavForOrientation() {
        val landscape = isLandscape()
        if (navLandscape != landscape) {
            navLandscape = landscape
            val density = resources.displayMetrics.density
            val bottomnav = findViewById<View>(R.id.bottomnav)
            val row = findViewById<android.widget.LinearLayout>(R.id.nav_row)
            if (landscape) {
                (bottomnav?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                    it.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                    it.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    it.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    bottomnav.layoutParams = it
                }
                row?.orientation = android.widget.LinearLayout.VERTICAL
                findViewById<View>(R.id.nav_create)?.let { cb ->
                    (cb.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                        val m = (6 * density).toInt()
                        lp.setMargins(0, m, 0, m)
                        cb.layoutParams = lp
                    }
                }
                // Compact icon-only rail: short landscape screens fit it.
                listOf(
                    R.id.nav_home_label, R.id.nav_wheels_label,
                    R.id.nav_messages_label, R.id.nav_you_label
                ).forEach { findViewById<View>(it)?.visibility = View.GONE }
            } else {
                (bottomnav?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
                    it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    it.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    it.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    bottomnav.layoutParams = it
                }
                row?.orientation = android.widget.LinearLayout.HORIZONTAL
                findViewById<View>(R.id.nav_create)?.let { cb ->
                    (cb.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                        val m = (6 * density).toInt()
                        lp.setMargins(m, 0, m, 0)
                        cb.layoutParams = lp
                    }
                }
                listOf(
                    R.id.nav_home_label, R.id.nav_wheels_label,
                    R.id.nav_messages_label, R.id.nav_you_label
                ).forEach { findViewById<View>(it)?.visibility = View.VISIBLE }
            }
            // Padding depends on orientation: re-dispatch insets so the
            // listener above re-runs with fresh geometry.
            findViewById<View>(R.id.root)?.let {
                androidx.core.view.ViewCompat.requestApplyInsets(it)
            }
        }
        updateContainerForRail()
        reapplyBarClearance()
        (supportFragmentManager.findFragmentById(R.id.container) as? WheelsFragment)
            ?.refreshClearance()
    }
    /** In landscape the content ends left of the rail; in portrait full width. */
    private fun updateContainerForRail() {
        val container = findViewById<View>(R.id.container) ?: return
        if (!isLandscape()) {
            if (container.paddingRight != 0) container.setPadding(0, 0, 0, 0)
            return
        }
        val rail = findViewById<View>(R.id.nav_row) ?: return
        if (rail.width <= 0) {
            rail.doOnLayout { updateContainerForRail() }
            return
        }
        val want = rail.width + (8 * resources.displayMetrics.density).toInt()
        if (container.paddingRight != want) container.setPadding(0, 0, want, 0)
    }
    /** Re-runs bar clearance on every tagged scroll view (see Ui.kt) so a
     *  rotation swaps bottom spacer for none without recreating views. */
    private fun reapplyBarClearance() {
        val container = findViewById<android.view.ViewGroup>(R.id.container) ?: return
        fun walk(group: android.view.ViewGroup) {
            for (i in 0 until group.childCount) {
                val v = group.getChildAt(i)
                if (v.getTag(R.id.tag_bar_clear) != null) v.applyBarClearance()
                if (v is android.view.ViewGroup) walk(v)
            }
        }
        walk(container)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutNavForOrientation()
    }
    /**
     * Single source of truth for top/bottom bar visibility. Bars are hidden
     * only on the auth screen; every other screen shows them. Called on
     * resume (covers process-death restore, back navigation, shortcut
     * entries) so they can never get stuck hidden.
     */
    fun syncBars() {
        val frag = supportFragmentManager.findFragmentById(R.id.container)
        val onAuth = frag is AuthFragment && supportFragmentManager.backStackEntryCount == 0
        // Open DM threads go full-screen: no bottom bar while chatting.
        // The conversation list keeps it so you can still navigate.
        val inChat = frag is ChatFragment
        // Wheels and watch are full-bleed pages, so the bar goes
        // transparent there and crossfades back to opaque black elsewhere.
        val onWheels = frag is WheelsFragment && supportFragmentManager.backStackEntryCount == 0
        setNavTransparent(onWheels || frag is WatchFragment)
        findViewById<View>(R.id.topbar).visibility = if (onAuth) View.GONE else View.VISIBLE
        findViewById<View>(R.id.bottomnav).visibility = if (onAuth || inChat) View.GONE else View.VISIBLE
        // Hide the gear synchronously while in Settings so it never flashes
        // before the (async) topbar refresh below confirms it.
        if (frag is SettingsFragment) findViewById<View>(R.id.top_settings).visibility = View.GONE
        if (!onAuth) refreshTopbar()
    }
    /** Current nav pill state; null until first applied. */
    private var navTransparent: Boolean? = null
    /** Crossfades the bottom nav pill between opaque black and transparent
     *  so switching to/from wheels glides instead of popping. */
    fun setNavTransparent(transparent: Boolean) {
        if (navTransparent == transparent) return
        navTransparent = transparent
        val row = findViewById<View>(R.id.nav_row) ?: return
        val from = if (transparent) R.drawable.nav_pill else R.drawable.nav_pill_clear
        val to = if (transparent) R.drawable.nav_pill_clear else R.drawable.nav_pill
        val cross = TransitionDrawable(arrayOf(getDrawable(from), getDrawable(to)))
        cross.isCrossFadeEnabled = true
        row.background = cross
        cross.startTransition(350)
    }
    fun refreshTopbar() {
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user
                if (me == null) {
                    currentUsername = null
                    findViewById<View>(R.id.bell_wrap).visibility = View.GONE
                    findViewById<View>(R.id.top_settings).visibility = View.GONE
                    findViewById<View>(R.id.top_admin).visibility = View.GONE
                    findViewById<View>(R.id.signin_btn).visibility = View.VISIBLE
                    findViewById<ImageView>(R.id.nav_avatar).visibility = View.GONE
                    findViewById<ImageView>(R.id.nav_person).visibility = View.VISIBLE
                    markNav()
                    return@launch
                }
                currentUsername = me.username
                findViewById<View>(R.id.bell_wrap).visibility = View.VISIBLE
                // No point showing the gear while already in Settings.
                val onSettings = supportFragmentManager.findFragmentById(R.id.container) is SettingsFragment
                findViewById<View>(R.id.top_settings).visibility =
                    if (onSettings) View.GONE else View.VISIBLE
                findViewById<View>(R.id.top_admin).visibility = View.GONE
                findViewById<View>(R.id.signin_btn).visibility = View.GONE
                val avatar = findViewById<watchshark.duckdns.org.ui.WebmAvatarView>(R.id.nav_avatar)
                val person = findViewById<ImageView>(R.id.nav_person)
                if (me.avatar != null) {
                    avatar.visibility = View.VISIBLE
                    person.visibility = View.GONE
                    avatar.setAvatar(me.avatar, R.drawable.ic_person)
                } else {
                    avatar.visibility = View.GONE
                    person.visibility = View.VISIBLE
                }
                markNav()
                try {
                    val n = ApiClient.api.notifications()
                    val badge = findViewById<TextView>(R.id.bell_badge)
                    if (n.unread > 0) {
                        badge.visibility = View.VISIBLE
                        badge.text = if (n.unread > 9) "9+" else n.unread.toString()
                    } else {
                        badge.visibility = View.GONE
                    }
                } catch (_: Exception) {
                }
            } catch (_: Exception) {
            }
        }
    }
    override fun onDestroy() {
        findViewById<watchshark.duckdns.org.ui.WebmAvatarView>(R.id.nav_avatar)?.release()
        super.onDestroy()
    }
    private fun circleOutline() = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
    fun showAuth() {
        tabHistory.clear()
        findViewById<View>(R.id.topbar).visibility = View.GONE
        findViewById<View>(R.id.bottomnav).visibility = View.GONE
        supportFragmentManager.popBackStackImmediate(null, 1)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, AuthFragment())
            .commit()
    }
    private fun setupTopSearch() {
        val btn: ImageButton = findViewById(R.id.top_search_btn)
        val input: EditText = findViewById(R.id.bottom_search)
        btn.setOnClickListener {
            if (searchExpanded) collapseSearch(clear = true) else expandSearch()
        }
        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitTopSearch(input.text.toString())
                hideKeyboard(v)
                true
            } else false
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (searchSyncing) return
                searchPending?.let { searchHandler.removeCallbacks(it) }
                searchPending = Runnable { submitTopSearch(s.toString()) }
                searchHandler.postDelayed(searchPending!!, 500)
            }
        })
    }
    /** Search input lives in a strip above the bottom nav: slides up + fades in. */
    private fun expandSearch() {
        val strip: View = findViewById(R.id.bottom_search_bar)
        val input: EditText = findViewById(R.id.bottom_search)
        val btn: ImageButton = findViewById(R.id.top_search_btn)
        searchExpanded = true
        searchAnimating = true
        btn.setImageResource(R.drawable.ic_close)
        strip.animate().cancel()
        strip.visibility = View.VISIBLE
        strip.post {
            strip.translationY = (strip.height - imeBottomPx).toFloat()
            strip.alpha = 0f
            strip.animate()
                .translationY(-imeBottomPx.toFloat())
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    searchAnimating = false
                    input.requestFocus()
                    showKeyboard(input)
                }
                .start()
        }
    }
    private fun collapseSearch(clear: Boolean) {
        val strip: View = findViewById(R.id.bottom_search_bar)
        val input: EditText = findViewById(R.id.bottom_search)
        val btn: ImageButton = findViewById(R.id.top_search_btn)
        searchExpanded = false
        searchAnimating = true
        searchPending?.let { searchHandler.removeCallbacks(it) }
        hideKeyboard(input)
        input.clearFocus()
        if (clear) {
            searchSyncing = true
            input.text.clear()
            searchSyncing = false
            submitTopSearch("")
        }
        btn.setImageResource(R.drawable.ic_search)
        strip.animate().cancel()
        strip.animate()
            .translationY((strip.height - imeBottomPx).toFloat())
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                strip.visibility = View.GONE
                strip.translationY = 0f
                strip.alpha = 1f
                searchAnimating = false
            }
            .start()
    }
    private fun submitTopSearch(q: String) {
        val home = supportFragmentManager.findFragmentByTag("home") as? HomeFragment
        if (currentTab != "home" || home == null || !home.isAdded) {
            showHome()
        }
        (supportFragmentManager.findFragmentByTag("home") as? HomeFragment)?.setQuery(q)
    }
    /** Keep the input text in sync with the visible Home feed's query. */
    private fun syncSearchInput() {
        val input: EditText = findViewById(R.id.bottom_search) ?: return
        val q = (supportFragmentManager.findFragmentByTag("home") as? HomeFragment)?.currentQuery().orEmpty()
        if (input.text.toString() != q) {
            searchSyncing = true
            input.setText(q)
            searchSyncing = false
        }
    }
    private fun showKeyboard(v: View) {
        v.post {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    private fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(v.windowToken, 0)
    }
    fun showMain() = showHome()
    fun showHome() = showRoot(HomeFragment(), "home")
    fun showWheels() = showRoot(WheelsFragment(), "wheels")
    fun showMessages() {
        if (ApiClient.sessionToken().isNullOrEmpty()) {
            showAuth()
        } else {
            showRoot(MessagesFragment(), "messages")
        }
    }
    fun restartToAuth() {
        ApiClient.clearSession()
        watchshark.duckdns.org.data.UploadAlerts.cancel(this)
        showAuth()
    }
    private fun showRoot(fragment: Fragment, tag: String) = goRoot(fragment, tag, true)
    private fun rootFragment(tag: String): Fragment = when (tag) {
        "wheels" -> WheelsFragment()
        "messages" -> MessagesFragment()
        else -> HomeFragment()
    }
    /** Root-tab history for back navigation (Messages back to Home, etc.). */
    private val tabHistory = ArrayDeque<String>()
    /** Topbar expandable search (icon next to the bell, like the website). */
    private var searchExpanded = false
    private var searchAnimating = false
    private var imeBottomPx = 0
    private var searchSyncing = false
    private val toastHandler = Handler(Looper.getMainLooper())
    private var toastHide: Runnable? = null

    fun showToast(msg: String) {
        val toast: TextView = findViewById(R.id.app_toast)
        val navH = findViewById<View>(R.id.bottomnav)?.height ?: 0
        (toast.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin =
            navH + (16 * resources.displayMetrics.density).toInt()
        toast.text = msg
        toast.visibility = View.VISIBLE
        toast.animate().cancel()
        toast.alpha = 0f
        toast.translationY = (24 * resources.displayMetrics.density)
        toast.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(150)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                toastHide?.let { toastHandler.removeCallbacks(it) }
                toastHide = Runnable { hideToast() }
                toastHandler.postDelayed(toastHide!!, 1800)
            }
            .start()
    }

    private fun hideToast() {
        val toast: TextView = findViewById(R.id.app_toast)
        toast.animate().cancel()
        toast.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction { toast.visibility = View.GONE }
            .start()
    }
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchPending: Runnable? = null
    private fun goRoot(fragment: Fragment, tag: String, push: Boolean) {
        val order = listOf("home", "wheels", "messages")
        val oldIdx = order.indexOf(currentTab)
        val newIdx = order.indexOf(tag)
        currentTab = tag
        if (push && tabHistory.lastOrNull() != tag) {
            tabHistory.addLast(tag)
            while (tabHistory.size > 25) tabHistory.removeFirst()
        }
        findViewById<View>(R.id.topbar).visibility = View.VISIBLE
        findViewById<View>(R.id.bottomnav).visibility = View.VISIBLE
        markNav()
        supportFragmentManager.popBackStackImmediate(null, 1)
        val tx = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
        if (oldIdx >= 0 && newIdx >= 0 && oldIdx != newIdx) {
            if (newIdx > oldIdx) {
                tx.setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right
                )
            } else {
                tx.setCustomAnimations(
                    R.anim.slide_in_left, R.anim.slide_out_right,
                    R.anim.slide_in_right, R.anim.slide_out_left
                )
            }
        }
        tx.replace(R.id.container, fragment, tag)
            .commit()
        supportFragmentManager.executePendingTransactions()
        // Reconcile bars with the fragment now actually showing (e.g. the
        // Messages tab hides the bottom bar; the early VISIBLE above is
        // only so the transition doesn't flash).
        syncBars()
        if (!updateChecked && ApiClient.sessionToken() != null) {
            updateChecked = true
            supportFragmentManager.findFragmentByTag(tag)?.let {
                watchshark.duckdns.org.data.Updater.checkSilent(it)
            }
        }
        syncSearchInput()
    }
    private fun markNav() {
        data class Tab(val iconId: Int, val labelId: Int, val tag: String)
        val tabs = listOf(
            Tab(R.id.nav_home_icon, R.id.nav_home_label, "home"),
            Tab(R.id.nav_wheels_icon, R.id.nav_wheels_label, "wheels"),
            Tab(R.id.nav_messages_icon, R.id.nav_messages_label, "messages"),
        )
        val icons = mapOf(
            "home" to R.drawable.ic_home,
            "wheels" to R.drawable.ic_movie,
            "messages" to R.drawable.ic_chat,
        )
        // Bottom bar is always opaque black: fixed icon colors stay readable.
        val active = android.graphics.Color.WHITE
        val idle = android.graphics.Color.parseColor("#A8A8A8")
        tabs.forEach { tab ->
            val selected = tab.tag == currentTab
            (findViewById<View>(tab.iconId) as? ImageView)?.apply {
                setImageResource(icons[tab.tag]!!)
                setColorFilter(if (selected) active else idle)
            }
            (findViewById<View>(tab.labelId) as? TextView)?.apply {
                setTextColor(if (selected) active else idle)
            }
        }
        val youSelected = currentTab == "you"
        (findViewById<View>(R.id.nav_person) as? ImageView)?.apply {
            alpha = 1.0f
            setColorFilter(if (youSelected) active else idle)
        }
        (findViewById<View>(R.id.nav_avatar))?.apply {
            alpha = if (youSelected) 1.0f else 0.85f
        }
        (findViewById<View>(R.id.nav_you_label) as? TextView)?.apply {
            setTextColor(if (youSelected) active else idle)
        }
    }
    /** Push a detail screen (watch, channel, upload, settings, admin, notifications). */
    fun openDetail(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }
    fun openAdmin() = openDetail(AdminFragment())
    fun openChannel(name: String) {
        if (name == currentUsername) {
            currentTab = "you"
            markNav()
        }
        openDetail(ChannelFragment.newInstance(name))
    }
}