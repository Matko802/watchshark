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
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
        setTheme(R.style.Theme_WatchShark)
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.brand_icon).setOnClickListener { showHome() }
        findViewById<View>(R.id.brand_text).setOnClickListener { showHome() }
        findViewById<MaterialButton>(R.id.top_wheels).setOnClickListener { showWheels() }
        findViewById<MaterialButton>(R.id.top_music).setOnClickListener { showMusic() }
        findViewById<ImageButton>(R.id.bell_btn).setOnClickListener { openDetail(NotificationsFragment()) }
        findViewById<ImageButton>(R.id.top_settings).setOnClickListener { openDetail(SettingsFragment()) }
        findViewById<ImageView>(R.id.whoami).setOnClickListener { v ->
            (v.tag as? String)?.let { openChannel(it) }
        }
        findViewById<MaterialButton>(R.id.top_admin).setOnClickListener { openAdmin() }
        findViewById<MaterialButton>(R.id.signin_btn).setOnClickListener { showAuth() }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            openDetail(UploadFragment.newInstance("video"))
        }
        if (savedInstanceState == null) {
            if (ApiClient.sessionToken().isNullOrEmpty()) {
                showAuth()
            } else {
                showHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (findViewById<View>(R.id.topbar).visibility == View.VISIBLE) {
            refreshTopbar()
        }
    }

    fun refreshTopbar() {
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user
                findViewById<View>(R.id.topbar).visibility = View.VISIBLE
                if (me == null) {
                    findViewById<View>(R.id.bell_wrap).visibility = View.GONE
                    findViewById<View>(R.id.top_settings).visibility = View.GONE
                    findViewById<View>(R.id.top_admin).visibility = View.GONE
                    findViewById<View>(R.id.whoami).visibility = View.GONE
                    findViewById<View>(R.id.fab).visibility = View.GONE
                    findViewById<View>(R.id.signin_btn).visibility = View.VISIBLE
                    return@launch
                }
                findViewById<View>(R.id.bell_wrap).visibility = View.VISIBLE
                findViewById<View>(R.id.top_settings).visibility = View.VISIBLE
                findViewById<ImageView>(R.id.whoami).apply {
                    visibility = View.VISIBLE
                    tag = me.username
                    loadMedia(me.avatar, R.drawable.ic_person)
                    clipToOutline = true
                    outlineProvider = circleOutline()
                }
                findViewById<View>(R.id.top_admin).visibility =
                    if (me.admin) View.VISIBLE else View.GONE
                findViewById<View>(R.id.fab).visibility = View.VISIBLE
                findViewById<View>(R.id.signin_btn).visibility = View.GONE
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

    private fun circleOutline() = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    fun showAuth() {
        findViewById<View>(R.id.topbar).visibility = View.GONE
        findViewById<View>(R.id.fab).visibility = View.GONE
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
        findViewById<View>(R.id.topbar).visibility = View.VISIBLE
        refreshTopbar()
        supportFragmentManager.popBackStackImmediate(null, 1)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, tag)
            .commit()
    }

    /** Push a detail screen (watch, channel, upload, settings, admin, notifications). */
    fun openDetail(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun openAdmin() = openDetail(AdminFragment())
    fun openChannel(name: String) = openDetail(ChannelFragment.newInstance(name))
}
