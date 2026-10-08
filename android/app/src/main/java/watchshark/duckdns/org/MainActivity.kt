package watchshark.duckdns.org

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.CrashLog
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.ui.AdminFragment
import watchshark.duckdns.org.ui.AuthFragment
import watchshark.duckdns.org.ui.ChannelFragment
import watchshark.duckdns.org.ui.ChatFragment
import watchshark.duckdns.org.ui.MessagesFragment
import watchshark.duckdns.org.ui.NotificationsFragment
import watchshark.duckdns.org.ui.SettingsFragment
import watchshark.duckdns.org.ui.UploadFragment
import watchshark.duckdns.org.ui.WatchFragment
import watchshark.duckdns.org.ui.compose.AppNavigator
import watchshark.duckdns.org.ui.compose.WatchSharkApp

/**
 * M3 Expressive Compose shell (YouTube-proper navigation).
 *
 * Legacy Views fragments are still hosted via AndroidFragment interop,
 * so [openDetail]/[openChannel] are kept as a bridge: they translate
 * old Fragment pushes into Compose routes.
 */
class MainActivity : AppCompatActivity() {

    @Volatile
    var currentUsername: String? = null
        private set

    /** Appearance choice (Settings → App). Recomposed instantly; Views side follows via delegate. */
    private var themeMode by mutableIntStateOf(ThemePrefs.MODE_SYSTEM)

    /** Called from Settings when the user picks System / Light / Dark / AMOLED. */
    fun setThemeMode(mode: Int) {
        ThemePrefs.setMode(this, mode)
        themeMode = mode
        AppCompatDelegate.setDefaultNightMode(ThemePrefs.toNightMode(mode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeMode = ThemePrefs.getMode(this)
        AppCompatDelegate.setDefaultNightMode(ThemePrefs.toNightMode(themeMode))
        CrashLog.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        Updater.init(this)
        CrashLog.showNow(this)

        val loggedIn = !ApiClient.sessionToken().isNullOrEmpty()
        refreshUsername()

        setContent {
            WatchSharkApp(
                startLoggedIn = loggedIn,
                currentUsername = { currentUsername },
                darkThemeOverride = ThemePrefs.toDarkOverride(themeMode),
                amoled = themeMode == ThemePrefs.MODE_AMOLED,
            )
        }

        if (savedInstanceState == null) {
            handleShortcut(intent)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: android.content.Intent?): Boolean {
        when (intent?.action) {
            "watchshark.duckdns.org.action.HOME" -> {
                AppNavigator.goTab("home")
            }
            "watchshark.duckdns.org.action.WHEELS" -> {
                AppNavigator.goTab("wheels")
            }
            "watchshark.duckdns.org.action.MESSAGES" -> {
                AppNavigator.goTab("messages")
            }
            "watchshark.duckdns.org.action.UPLOAD" -> {
                AppNavigator.navigate("upload")
            }
            "watchshark.duckdns.org.action.DM" -> {
                val name = intent.getStringExtra("username") ?: ""
                if (name.isNotEmpty()) AppNavigator.navigate("chat/$name")
                else AppNavigator.goTab("messages")
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
                if (vid > 0) AppNavigator.navigate("watch/$vid")
                else AppNavigator.goTab("home")
            }
            else -> return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshUsername()
        if (!ApiClient.sessionToken().isNullOrEmpty()) {
            UploadAlerts.ensureScheduled(this)
        }
    }

    private fun refreshUsername() {
        lifecycleScope.launch {
            try {
                currentUsername = ApiClient.api.me().user?.username
            } catch (_: Exception) {
            }
        }
    }

    fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Legacy bridge (called by old Fragments hosted via interop) ----

    fun showMain() = showHome()
    fun showHome() = AppNavigator.goTab("home")
    fun showWheels() = AppNavigator.goTab("wheels")
    fun showMessages() {
        if (ApiClient.sessionToken().isNullOrEmpty()) showAuth()
        else AppNavigator.goTab("messages")
    }

    fun showAuth() = AppNavigator.navigate("auth")

    fun restartToAuth() {
        ApiClient.clearSession()
        UploadAlerts.cancel(this)
        currentUsername = null
        AppNavigator.navigate("auth")
    }

    fun openAdmin() = AppNavigator.navigate("admin")

    fun openChannel(name: String) {
        AppNavigator.navigate("channel/$name")
    }

    fun openDetail(fragment: Fragment) {
        val args = fragment.arguments
        when (fragment) {
            is WatchFragment -> {
                val id = args?.getLong("id", 0) ?: 0
                if (id > 0) AppNavigator.navigate("watch/$id")
            }
            is ChannelFragment -> {
                val name = args?.getString("user", "").orEmpty()
                if (name.isNotEmpty()) AppNavigator.navigate("channel/$name")
            }
            is ChatFragment -> {
                val name = args?.getString("username", "").orEmpty()
                if (name.isNotEmpty()) AppNavigator.navigate("chat/$name")
                else AppNavigator.goTab("messages")
            }
            is UploadFragment -> AppNavigator.navigate("upload")
            is SettingsFragment -> AppNavigator.navigate("settings")
            is AdminFragment -> AppNavigator.navigate("admin")
            is NotificationsFragment -> AppNavigator.navigate("notifications")
            is AuthFragment -> AppNavigator.navigate("auth")
            is MessagesFragment -> AppNavigator.goTab("messages")
            else -> {
                // Fallback: unknown detail -> home.
                AppNavigator.goTab("home")
            }
        }
    }
}
