package org.watchshark.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.watchshark.app.MainActivity
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient

class AuthFragment : Fragment() {
    private var modeLogin = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_auth, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        val go: Button = view.findViewById(R.id.auth_go)
        go.setOnClickListener { if (modeLogin) doLogin() else doSignup() }
        view.findViewById<Button>(R.id.auth_switch).setOnClickListener { toggle() }
    }

    private fun toggle() {
        val v = requireView()
        modeLogin = !modeLogin
        v.findViewById<View>(R.id.su_name_wrap).visibility = if (modeLogin) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.su_email_wrap).visibility = if (modeLogin) View.GONE else View.VISIBLE
        v.findViewById<Button>(R.id.auth_switch).text = if (modeLogin) "Create account" else "Log in"
        v.findViewById<Button>(R.id.auth_go).text = if (modeLogin) "Log in" else "Create account"
    }

    private fun err(msg: String) {
        view?.findViewById<TextView>(R.id.auth_err)?.text = msg
    }

    private fun doLogin() {
        val v = requireView()
        val login = v.findViewById<TextInputEditText>(R.id.login_id).text.toString()
        val pw = v.findViewById<TextInputEditText>(R.id.login_pw).text.toString()
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.login(mapOf("login" to login, "password" to pw))
                if (!isAdded) return@launch
                if (res.has("error")) {
                    err(res.get("error").asString)
                    return@launch
                }
                afterAuth()
            } catch (e: Exception) {
                if (isAdded) err(httpErrorMessage(e))
            }
        }
    }

    private fun doSignup() {
        val v = requireView()
        val body = mapOf(
            "username" to v.findViewById<TextInputEditText>(R.id.su_name).text.toString(),
            "email" to v.findViewById<TextInputEditText>(R.id.su_email).text.toString(),
            "password" to v.findViewById<TextInputEditText>(R.id.login_pw).text.toString(),
        )
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.signup(body)
                if (!isAdded) return@launch
                if (res.has("error")) {
                    err(res.get("error").asString)
                    return@launch
                }
                if (res.has("verify")) {
                    err("Account created — waiting for admin approval.")
                    toggle()
                } else {
                    afterAuth()
                }
            } catch (e: Exception) {
                if (isAdded) err(httpErrorMessage(e))
            }
        }
    }

    private fun afterAuth() {
        // verify session + ban state, then enter
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user
                if (!isAdded) return@launch
                if (me == null) {
                    err("Login failed")
                    return@launch
                }
                if (me.deleted) {
                    err("Your account has been deleted." + (me.deleted_reason?.let { "\nReason: $it" } ?: ""))
                    ApiClient.clearSession()
                    return@launch
                }
                if (me.banned) {
                    val days = if (me.ban_days_left < 0) "permanently" else "for %.1f days".format(me.ban_days_left)
                    err("You have been banned $days." + (me.ban_reason?.let { "\nReason: $it" } ?: ""))
                    return@launch
                }
                (activity as? MainActivity)?.showMain()
            } catch (e: Exception) {
                if (isAdded) err(httpErrorMessage(e))
            }
        }
    }
}
