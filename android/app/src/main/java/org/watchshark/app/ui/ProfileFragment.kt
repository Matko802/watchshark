package org.watchshark.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.watchshark.app.MainActivity
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient

class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        val act = activity as? MainActivity
        view.findViewById<Button>(R.id.p_channel).setOnClickListener {
            val name = view.findViewById<TextView>(R.id.p_name).tag as? String ?: return@setOnClickListener
            act?.openDetail(ChannelFragment.newInstance(name))
        }
        view.findViewById<Button>(R.id.p_upload).setOnClickListener {
            act?.openDetail(UploadFragment.newInstance("video"))
        }
        view.findViewById<Button>(R.id.p_notif).setOnClickListener {
            act?.openDetail(NotificationsFragment())
        }
        view.findViewById<Button>(R.id.p_settings).setOnClickListener {
            act?.openDetail(SettingsFragment())
        }
        view.findViewById<Button>(R.id.p_admin).setOnClickListener {
            act?.openDetail(AdminFragment())
        }
        view.findViewById<Button>(R.id.p_signin).setOnClickListener { act?.showAuth() }
        view.findViewById<Button>(R.id.p_signout).setOnClickListener { act?.restartToAuth() }
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user
                if (!isAdded) return@launch
                if (me == null) {
                    showSignedOut()
                    return@launch
                }
                view.findViewById<TextView>(R.id.p_name).text = "@${me.username}"
                view.findViewById<TextView>(R.id.p_name).tag = me.username
                view.findViewById<TextView>(R.id.p_info).text = "Member since ${me.since}"
                view.findViewById<Button>(R.id.p_signout).visibility = View.VISIBLE
                if (me.admin) view.findViewById<Button>(R.id.p_admin).visibility = View.VISIBLE
            } catch (e: Exception) {
                if (isAdded) showSignedOut()
            }
        }
    }

    private fun showSignedOut() {
        val v = view ?: return
        v.findViewById<TextView>(R.id.p_name).text = "Not signed in"
        v.findViewById<TextView>(R.id.p_info).text = "Sign in to upload, like and follow."
        v.findViewById<Button>(R.id.p_signin).visibility = View.VISIBLE
        v.findViewById<Button>(R.id.p_channel).visibility = View.GONE
        v.findViewById<Button>(R.id.p_upload).visibility = View.GONE
        v.findViewById<Button>(R.id.p_notif).visibility = View.GONE
        v.findViewById<Button>(R.id.p_settings).visibility = View.GONE
    }
}
