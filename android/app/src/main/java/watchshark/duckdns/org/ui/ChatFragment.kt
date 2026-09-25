package watchshark.duckdns.org.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.DmCrypto
import watchshark.duckdns.org.data.DmMessage
import watchshark.duckdns.org.data.DmOutbox

class ChatFragment : Fragment() {
    private var userId: Long = 0
    private var username: String = ""
    private var peerKey: String? = null
    private var maxId: Long = 0
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollTask: Runnable? = null

    companion object {
        fun newInstance(userId: Long, username: String) = ChatFragment().apply {
            arguments = Bundle().apply {
                putLong("user_id", userId)
                putString("username", username)
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        userId = arguments?.getLong("user_id", 0) ?: 0
        username = arguments?.getString("username", "") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        view.findViewById<TextView>(R.id.chat_title).text = "@$username"
        val input: EditText = view.findViewById(R.id.chat_input)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else false
        }
        view.findViewById<View>(R.id.chat_send).setOnClickListener { send() }
        flushQueue()
        load(true)
    }

    override fun onResume() {
        super.onResume()
        pollTask = object : Runnable {
            override fun run() {
                if (isAdded) {
                    flushQueue()
                    load(false)
                    pollHandler.postDelayed(this, 3000)
                }
            }
        }
        pollHandler.postDelayed(pollTask!!, 3000)
    }

    override fun onPause() {
        pollTask?.let { pollHandler.removeCallbacks(it) }
        pollTask = null
        super.onPause()
    }

    private fun bubble(text: String, mine: Boolean) {
        val v = view ?: return
        val list: LinearLayout = v.findViewById(R.id.chat_list)
        val row = TextView(requireContext()).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, (8 * resources.displayMetrics.density).toInt(), pad, (8 * resources.displayMetrics.density).toInt())
            background = resources.getDrawable(R.drawable.search_bg, null)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = if (mine) android.view.Gravity.END else android.view.Gravity.START
            lp.topMargin = (4 * resources.displayMetrics.density).toInt()
            lp.bottomMargin = (4 * resources.displayMetrics.density).toInt()
            layoutParams = lp
        }
        list.addView(row)
        v.findViewById<ScrollView>(R.id.chat_scroll).post {
            v.findViewById<ScrollView>(R.id.chat_scroll).fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun load(initial: Boolean) {
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                if (!DmCrypto.ensureUploaded(ctx)) {
                    if (initial && isAdded) v.snack("Could not set up encryption")
                    return@launch
                }
                if (peerKey == null) {
                    try {
                        peerKey = ApiClient.api.dmGetKey(username).get("pubkey")?.asString
                    } catch (_: Exception) {
                        peerKey = null
                    }
                }
                val key = peerKey
                if (key.isNullOrEmpty()) {
                    if (initial && isAdded) v.snack("@$username has not opened messages yet")
                    return@launch
                }
                val res = ApiClient.api.dmThread(username, if (initial) 0 else maxId, 50)
                if (!isAdded) return@launch
                val fresh = res.messages.orEmpty().filter { it.id > maxId }.sortedBy { it.id }
                for (m in fresh) {
                    val text = DmCrypto.decrypt(ctx, key, m.nonce, m.body) ?: continue
                    appendMessage(m, text)
                }
            } catch (e: Exception) {
                if (initial && isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    private fun appendMessage(m: DmMessage, text: String) {
        if (m.id > maxId) maxId = m.id
        bubble(text, m.senderId != userId)
    }

    private fun send() {
        val v = view ?: return
        val input: EditText = v.findViewById(R.id.chat_input)
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                var key = peerKey
                if (key == null) {
                    try {
                        key = ApiClient.api.dmGetKey(username).get("pubkey")?.asString
                        peerKey = key
                    } catch (ex: Exception) {
                        if (ex is java.io.IOException) {
                            DmOutbox.add(ctx, username, text)
                            if (isAdded) v.snack("No connection — will send when online")
                            return@launch
                        }
                    }
                }
                if (key == null) {
                    if (isAdded) v.snack("@$username has not opened messages yet")
                    return@launch
                }
                val (nonce, body) = DmCrypto.encrypt(ctx, key, text) ?: run {
                    if (isAdded) v.snack("Encrypt failed")
                    return@launch
                }
                val res = ApiClient.api.dmSend(mapOf("to" to username, "nonce" to nonce, "body" to body))
                if (!isAdded) return@launch
                val id = try {
                    res.get("id")?.asLong ?: 0
                } catch (_: Exception) {
                    0
                }
                appendMessage(
                    DmMessage(id = id, senderId = 0, recipientId = userId, nonce = nonce, body = body),
                    text
                )
                if (isAdded) input.text.clear()
            } catch (e: Exception) {
                if (!isAdded) return@launch
                if (e is java.io.IOException) {
                    DmOutbox.add(requireContext(), username, text)
                    v.snack("No connection — will send when online")
                } else {
                    v.snack(httpErrorMessage(e))
                }
            }
        }
    }
    private fun flushQueue() {
        val peer = username
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                for (e in DmOutbox.forPeer(ctx, peer)) {
                    val id = try {
                        DmOutbox.flushEntry(ctx, e)
                    } catch (_: java.io.IOException) {
                        break
                    } ?: break
                    DmOutbox.remove(ctx, e.ts)
                    if (id > maxId) maxId = id
                }
            } catch (_: Exception) {
            }
        }
    }
}
