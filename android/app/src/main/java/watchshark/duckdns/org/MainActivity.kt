package watchshark.duckdns.org

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.CrashLog
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.ui.compose.AppNavigator
import watchshark.duckdns.org.ui.compose.WatchSharkApp

/**
 * Pure-Compose M3 Expressive shell (YouTube-style navigation).
 * All screens are Compose; no Fragments remain.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Views-side night mode (dialogs, splash) follows the saved choice.
        AppCompatDelegate.setDefaultNightMode(
            ThemePrefs.toNightMode(ThemePrefs.getMode(this)),
        )
        CrashLog.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        Updater.init(this)
        CrashLog.showNow(this)

        val loggedIn = !ApiClient.sessionToken().isNullOrEmpty()

        setContent {
            WatchSharkApp(startLoggedIn = loggedIn)
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
        if (!ApiClient.sessionToken().isNullOrEmpty()) {
            UploadAlerts.ensureScheduled(this)
        }
    }

    fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
