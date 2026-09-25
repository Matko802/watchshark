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
import watchshark.duckdns.org.data.DmOutbox
import watchshark.duckdns.org.data.DmRepo

class ChatFragment : Fragment() {
    private var userId: Long = 0
    private var username: String = ""
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

    private fun bubble(text: String, mine: Boolean, at: String) {
        val v = view ?: return
        val list: LinearLayout = v.findViewById(R.id.chat_list)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val lpRow = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lpRow.gravity = if (mine) android.view.Gravity.END else android.view.Gravity.START
            layoutParams = lpRow
        }
        val d = TextView(requireContext()).apply {
            this.text = text
            setTextColor(if (mine) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            textSize = 15f
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, (8 * resources.displayMetrics.density).toInt(), pad, (8 * resources.displayMetrics.density).toInt())
            background = resources.getDrawable(R.drawable.search_bg, null)
        }
        row.addView(d)
        val t = fmtAge(at)
        if (t.isNotEmpty()) {
            row.addView(TextView(requireContext()).apply {
                this.text = t
                setTextColor(android.graphics.Color.parseColor("#A8A8A8"))
                textSize = 11f
            })
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
                val lines = DmRepo.thread(ctx, username, if (initial) 0 else maxId)
                if (!isAdded) return@launch
                if (initial && lines.isEmpty()) {
                    val probe = DmRepo.peerKey(username)
                    if (probe == null) v.snack("@$username has not opened messages yet")
                }
                for (line in lines) {
                    if (line.id <= maxId) continue
                    maxId = line.id
                    bubble(line.text, line.mine, line.at)
                }
            } catch (e: Exception) {
                if (initial && isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    private fun send() {
        val v = view ?: return
        val input: EditText = v.findViewById(R.id.chat_input)
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val id = DmRepo.send(ctx, username, text)
                if (!isAdded) return@launch
                if (id == null) {
                    v.snack("@$username has not opened messages yet")
                    return@launch
                }
                if (id > maxId) maxId = id
                input.text.clear()
                bubble(text, true, "")
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
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                for (e in DmOutbox.forPeer(ctx, username)) {
                    val id = try {
                        DmRepo.send(ctx, e.uname, e.text)
                    } catch (_: java.io.IOException) {
                        break
                    } ?: break
                    DmOutbox.remove(ctx, e.ts)
                    if (id > maxId) maxId = id
                    if (isAdded) bubble(e.text, true, "")
                }
            } catch (_: Exception) {
            }
        }
    }
}
