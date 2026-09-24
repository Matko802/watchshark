package org.watchshark.app

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.watchshark.app.data.ApiClient
import org.watchshark.app.ui.AdminFragment
import org.watchshark.app.ui.AuthFragment
import org.watchshark.app.ui.ChannelFragment
import org.watchshark.app.ui.HomeFragment
import org.watchshark.app.ui.MusicFragment
import org.watchshark.app.ui.NotificationsFragment
import org.watchshark.app.ui.SettingsFragment
import org.watchshark.app.ui.UploadFragment
import org.watchshark.app.ui.WheelsFragment
import org.watchshark.app.ui.loadMedia

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        org.watchshark.app.data.CrashLog.install(this)
        // let the app handle insets (root layout has fitsSystemWindows).
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
        org.watchshark.app.data.Updater.init(this)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge()
        org.watchshark.app.ui.BlurBarView.blurEnabled =
            getSharedPreferences("watchshark_ui", MODE_PRIVATE).getBoolean("blur", true)
        val container: View = findViewById(R.id.container)
        (findViewById<View>(R.id.bottomnav) as? org.watchshark.app.ui.BlurBarView)?.target = container
        (findViewById<View>(R.id.topbar) as? org.watchshark.app.ui.BlurBarView)?.target = container
        // If the last session crashed, show the report right away so the
        // user can copy + send it instead of just seeing "app stopped".
        org.watchshark.app.data.CrashLog.showNow(this)

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
            "org.watchshark.app.action.HOME" -> showHome()
            "org.watchshark.app.action.WHEELS" -> showWheels()
            "org.watchshark.app.action.MUSIC" -> showMusic()
            "org.watchshark.app.action.UPLOAD" ->
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
        // Automatic update check when coming back (throttled to once a day).
        if (!ApiClient.sessionToken().isNullOrEmpty()) {
            supportFragmentManager.findFragmentById(R.id.container)?.let { frag ->
                if (frag.isAdded) org.watchshark.app.data.Updater.checkSilent(frag)
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
                val avatar = findViewById<org.watchshark.app.ui.WebmAvatarView>(R.id.nav_avatar)
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
        findViewById<org.watchshark.app.ui.WebmAvatarView>(R.id.nav_avatar)?.release()
        super.onDestroy()
    }

    private fun circleOutline() = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    fun showAuth() {
        findViewById<View>(R.id.topbar).visibility = View.GONE
        findViewById<View>(R.id.bottomnav).visibility = View.GONE
        supportFragmentManager.popBackStackImmediate(null, 1)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, AuthFragment())
            .commit()
    }

    fun showMain() = showHome()

    fun showHome() = showRoot(HomeFragment(), "home")
    fun showWheels() = showRoot(WheelsFragment(), "wheels")
    fun showMusic() = showRoot(MusicFragment(), "music")

    fun restartToAuth() {
        ApiClient.clearSession()
        showAuth()
    }

    private fun showRoot(fragment: Fragment, tag: String) {
        val order = listOf("home", "wheels", "music")
        val oldIdx = order.indexOf(currentTab)
        val newIdx = order.indexOf(tag)
        currentTab = tag
        findViewById<View>(R.id.topbar).visibility = View.VISIBLE
        findViewById<View>(R.id.bottomnav).visibility = View.VISIBLE
        markNav()
        refreshTopbar()
        supportFragmentManager.popBackStackImmediate(null, 1)
        val tx = supportFragmentManager.beginTransaction()
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
                org.watchshark.app.data.Updater.checkSilent(it)
            }
        }
    }


    private fun markNav() {
        // Match the website bottom nav exactly: icons always stay outlined
        // sharp; the active tab is only brighter (white vs #A8A8A8).
        // The You tab highlights too when it's the current tab.
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
        // You tab: person/avatar icon + label highlight when selected.
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
