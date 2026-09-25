package watchshark.duckdns.org
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.ui.AdminFragment
import watchshark.duckdns.org.ui.AuthFragment
import watchshark.duckdns.org.ui.ChannelFragment
import watchshark.duckdns.org.ui.HomeFragment
import watchshark.duckdns.org.ui.MusicFragment
import watchshark.duckdns.org.ui.NotificationsFragment
import watchshark.duckdns.org.ui.SettingsFragment
import watchshark.duckdns.org.ui.UploadFragment
import watchshark.duckdns.org.ui.WheelsFragment
import watchshark.duckdns.org.ui.loadMedia
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        watchshark.duckdns.org.data.CrashLog.install(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setTheme(R.style.Theme_WatchShark)
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        watchshark.duckdns.org.data.Updater.init(this)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge()
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0 && currentTab == "you") {
                val tag = supportFragmentManager.findFragmentById(R.id.container)?.tag
                if (tag == "home" || tag == "wheels" || tag == "music") {
                    currentTab = tag
                    markNav()
                }
            }
            val onDetail = supportFragmentManager.backStackEntryCount > 0
            findViewById<View>(R.id.top_search_btn)?.visibility =
                if (onDetail) View.GONE else View.VISIBLE
            if (onDetail && searchExpanded) collapseSearch(clear = false)
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
        findViewById<View>(R.id.nav_music).setOnClickListener { showMusic() }
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
            "watchshark.duckdns.org.action.MUSIC" -> showMusic()
            "watchshark.duckdns.org.action.UPLOAD" ->
                openDetail(UploadFragment.newInstance("video"))
            else -> return false
        }
        return true
    }
    override fun onResume() {
        super.onResume()
        if (findViewById<View>(R.id.topbar).visibility == View.VISIBLE) {
            refreshTopbar()
        }
        if (!ApiClient.sessionToken().isNullOrEmpty()) {
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
     * padding) instead of padding the root. That way the #111111 bars
     * themselves extend behind the status + gesture bars — no black
     * strips above the topbar or below the bottom nav.
     */
    private fun applyEdgeToEdge() {
        val density = resources.displayMetrics.density
        val root: View = findViewById(R.id.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            findViewById<View>(R.id.topbar)?.setPadding(
                (8 * density).toInt(),
                (8 * density).toInt() + bars.top,
                (8 * density).toInt(),
                (8 * density).toInt()
            )
            findViewById<View>(R.id.nav_row)?.setPadding(
                0,
                (9 * density).toInt(),
                0,
                (7 * density).toInt() + bars.bottom
            )
            val navH = findViewById<View>(R.id.bottomnav)?.height ?: 0
            val imePx = (ime.bottom - navH).coerceAtLeast(0)
            imeBottomPx = imePx
            findViewById<View>(R.id.bottom_search_bar)?.let { strip ->
                if (searchExpanded && !searchAnimating) strip.translationY = -imePx.toFloat()
            }
            insets
        }
    }
    fun refreshTopbar() {
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user
                findViewById<View>(R.id.topbar).visibility = View.VISIBLE
                findViewById<View>(R.id.bottomnav).visibility = View.VISIBLE
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
                findViewById<View>(R.id.top_settings).visibility = View.VISIBLE
                findViewById<View>(R.id.top_admin).visibility =
                    if (me.admin) View.VISIBLE else View.GONE
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
    fun showMusic() = showRoot(MusicFragment(), "music")
    fun restartToAuth() {
        ApiClient.clearSession()
        showAuth()
    }
    private fun showRoot(fragment: Fragment, tag: String) = goRoot(fragment, tag, true)
    private fun rootFragment(tag: String): Fragment = when (tag) {
        "wheels" -> WheelsFragment()
        "music" -> MusicFragment()
        else -> HomeFragment()
    }
    /** Root-tab history for back navigation (Music back to Home, etc.). */
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
        val order = listOf("home", "wheels", "music")
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
        refreshTopbar()
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
            Tab(R.id.nav_music_icon, R.id.nav_music_label, "music"),
        )
        val icons = mapOf(
            "home" to R.drawable.ic_home,
            "wheels" to R.drawable.ic_movie,
            "music" to R.drawable.ic_music_note,
        )
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