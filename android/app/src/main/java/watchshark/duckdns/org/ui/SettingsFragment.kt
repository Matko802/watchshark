package watchshark.duckdns.org.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.Updater
class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    override fun onViewCreated(view: View, saved: Bundle?) {
        view.clearBottomBar()
        view.findViewById<Button>(R.id.s_rename).setOnClickListener { rename() }
        view.findViewById<Button>(R.id.s_changepw).setOnClickListener { changePw() }
        view.findViewById<SwitchMaterial>(R.id.s_notif).setOnCheckedChangeListener { _, on ->
            lifecycleScope.launch {
                try {
                    ApiClient.api.notifSet(mapOf("uploads" to on))
                    if (isAdded) msg("Saved!")
                } catch (e: Exception) {
                    if (isAdded) msg(httpErrorMessage(e))
                }
            }
        }
        view.findViewById<Button>(R.id.s_signout).setOnClickListener {
            lifecycleScope.launch {
                try {
                    ApiClient.api.logout()
                } catch (_: Exception) {
                }
                if (isAdded) (activity as? MainActivity)?.restartToAuth()
            }
        }
        view.findViewById<Button>(R.id.s_admin).setOnClickListener {
            (activity as? MainActivity)?.openAdmin()
        }
        view.findViewById<TextView>(R.id.s_version).text =
            "Version ${Updater.currentVersion(requireContext())}"
        view.findViewById<Button>(R.id.s_update).setOnClickListener {
            msg("Checking…")
            Updater.checkManual(this) { status -> if (isAdded) msg(status) }
        }
        val crashBtn = view.findViewById<Button>(R.id.s_crash)
        crashBtn.visibility =
            if (watchshark.duckdns.org.data.CrashLog.lastCrash(requireContext()) != null) View.VISIBLE else View.GONE
        crashBtn.setOnClickListener {
            watchshark.duckdns.org.data.CrashLog.showNow(requireContext())
        }
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user ?: return@launch
                if (!isAdded) return@launch
                view.findViewById<SwitchMaterial>(R.id.s_notif).isChecked = me.notify_uploads
                if (me.admin) view.findViewById<Button>(R.id.s_admin).visibility = View.VISIBLE
            } catch (_: Exception) {
            }
        }
    }
    private fun msg(s: String) {
        if (isAdded) requireContext().toast(s)
    }
    private fun rename() {
        val v = view ?: return
        val name = v.findViewById<TextInputEditText>(R.id.s_name).text.toString().trim()
        lifecycleScope.launch {
            try {
                ApiClient.api.rename(mapOf("username" to name))
                if (isAdded) msg("Name saved!")
            } catch (e: Exception) {
                if (isAdded) msg(httpErrorMessage(e))
            }
        }
    }
    private fun changePw() {
        val v = view ?: return
        val cur = v.findViewById<TextInputEditText>(R.id.s_cur).text.toString()
        val pw = v.findViewById<TextInputEditText>(R.id.s_new).text.toString()
        lifecycleScope.launch {
            try {
                ApiClient.api.changePw(mapOf("current" to cur, "password" to pw))
                if (isAdded) msg("Password changed.")
            } catch (e: Exception) {
                if (isAdded) msg(httpErrorMessage(e))
            }
        }
    }
}