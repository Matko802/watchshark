package watchshark.duckdns.org.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.DmMessage

class ChatFragment : Fragment() {
    private var userId: Long = 0
    private var username: String = ""
    private var myId: Long = 0
    private var maxId: Long = 0
    private var minId: Long = 0
    private var historyDone = false
    private var loadingOlder = false
    private lateinit var adapter: MsgAdapter
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollTask: Runnable? = null
    private var loading = false

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
        adapter = MsgAdapter(mutableListOf())
        val list: RecyclerView = view.findViewById(R.id.chat_list)
        list.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        list.adapter = adapter
        list.clearBottomBar()
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= adapter.itemCount - 1) hideNewPill()
                if (!historyDone && !loadingOlder && lm.findFirstVisibleItemPosition() <= 2) loadOlder()
            }
        })
        view.findViewById<View>(R.id.chat_newpill).setOnClickListener { scrollToEnd(); hideNewPill() }
        view.findViewById<TextView>(R.id.chat_title).text = "@$username"
        view.findViewById<View>(R.id.chat_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        val input: EditText = view.findViewById(R.id.chat_input)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else false
        }
        view.findViewById<View>(R.id.chat_send).setOnClickListener { send() }
        load(true)
    }

    override fun onResume() {
        super.onResume()
        pollTask = object : Runnable {
            override fun run() {
                if (isAdded) {
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

    override fun onDestroyView() {
        view?.findViewById<WebmAvatarView>(R.id.chat_avatar)?.release()
        super.onDestroyView()
    }

    private fun load(initial: Boolean) {
        if (loading || username.isEmpty()) return
        loading = true
        lifecycleScope.launch {
            try {
                val res = if (maxId > 0 && !initial) {
                    ApiClient.api.dmThread(username, maxId, null, 50)
                } else {
                    ApiClient.api.dmThread(username, null, null, 30)
                }
                if (!isAdded) return@launch
                myId = res.me
                adapter.myId = myId
                res.peer?.let { p ->
                    view?.findViewById<WebmAvatarView>(R.id.chat_avatar)?.setAvatar(p.avatar, R.drawable.ic_person)
                    val on = view?.findViewById<TextView>(R.id.chat_online)
                    if (p.online) {
                        on?.visibility = View.VISIBLE
                        on?.text = "Online"
                    } else {
                        on?.visibility = View.GONE
                    }
                }
                val msgs = res.messages.orEmpty()
                if (msgs.isNotEmpty()) {
                    for (m in msgs) {
                        if (m.id > maxId) maxId = m.id
                        if (minId == 0L || m.id < minId) minId = m.id
                    }
                    if (initial) {
                        historyDone = msgs.size < 30
                        adapter.setItems(msgs)
                        scrollToEnd()
                    } else {
                        val added = adapter.appendNew(msgs)
                        if (added && nearBottom()) scrollToEnd() else if (added) showNewPill()
                    }
                    markRead()
                } else if (initial) {
                    historyDone = true
                    try {
                        val me = ApiClient.api.me().user
                        if (me != null) {
                            myId = me.id
                            adapter.myId = myId
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                if (isAdded && initial) view?.snack(httpErrorMessage(e))
            } finally {
                loading = false
            }
        }
    }

    private fun scrollToEnd() {
        val list: RecyclerView? = view?.findViewById(R.id.chat_list)
        if (adapter.itemCount > 0) list?.scrollToPosition(adapter.itemCount - 1)
    }

    private fun nearBottom(): Boolean {
        val list: RecyclerView = view?.findViewById(R.id.chat_list) ?: return true
        val lm = list.layoutManager as? LinearLayoutManager ?: return true
        return lm.findLastVisibleItemPosition() >= adapter.itemCount - 3
    }

    private fun showNewPill() {
        view?.findViewById<View>(R.id.chat_newpill)?.visibility = View.VISIBLE
    }

    private fun hideNewPill() {
        view?.findViewById<View>(R.id.chat_newpill)?.visibility = View.GONE
    }

    private fun loadOlder() {
        if (loadingOlder || historyDone || username.isEmpty() || minId == 0L) return
        loadingOlder = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.dmThread(username, null, minId, 30)
                if (!isAdded) return@launch
                val msgs = res.messages.orEmpty()
                if (msgs.size < 30) historyDone = true
                if (msgs.isNotEmpty()) {
                    for (m in msgs) if (minId == 0L || m.id < minId) minId = m.id
                    adapter.prepend(msgs)
                }
            } catch (_: Exception) {
            } finally {
                loadingOlder = false
            }
        }
    }

    private fun markRead() {
        lifecycleScope.launch {
            try {
                ApiClient.api.dmRead(mapOf("user" to username))
            } catch (_: Exception) {}
        }
    }

    private var sending = false

    private fun send() {
        val v = view ?: return
        val input: EditText = v.findViewById(R.id.chat_input)
        val text = input.text.toString().trim()
        if (text.isEmpty() || sending) return
        if (text.length > 2000) {
            v.snack("Message too long (max 2000)")
            return
        }
        sending = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.dmSend(mapOf("to" to username, "body" to text))
                if (!isAdded) return@launch
                val id = res.get("id")?.asLong ?: 0L
                val sentAt = try { res.get("created_at")?.asString ?: "" } catch (_: Exception) { "" }
                if (id > maxId) maxId = id
                if (minId == 0L && id > 0) minId = id
                input.text.clear()
                val optimistic = DmMessage(id = if (id > 0) id else (maxId + 1), senderId = myId, recipientId = userId, body = text, createdAt = sentAt, read = false)
                adapter.appendNew(listOf(optimistic))
                scrollToEnd()
                load(false)
            } catch (e: Exception) {
                if (isAdded) view?.snack(httpErrorMessage(e))
            } finally {
                sending = false
            }
        }
    }

    class MsgAdapter(private val items: MutableList<DmMessage>) :
        RecyclerView.Adapter<MsgAdapter.Holder>() {
        var myId: Long = 0

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val row: View = v.findViewById(R.id.msg_row)
            val bubble: TextView = v.findViewById(R.id.msg_bubble)
            val time: TextView = v.findViewById(R.id.msg_time)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val m = items[position]
            val mine = m.senderId != 0L && m.senderId == myId
            val bubbleLp = h.bubble.layoutParams as? android.widget.LinearLayout.LayoutParams
            val timeLp = h.time.layoutParams as? android.widget.LinearLayout.LayoutParams
            if (mine) {
                h.bubble.layoutParams = (bubbleLp ?: android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )).apply { gravity = Gravity.END }
                h.time.layoutParams = (timeLp ?: android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )).apply { gravity = Gravity.END }
                h.bubble.setBackgroundResource(R.drawable.circle_white)
                h.bubble.setTextColor(android.graphics.Color.BLACK)
            } else {
                h.bubble.layoutParams = (bubbleLp ?: android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )).apply { gravity = Gravity.START }
                h.time.layoutParams = (timeLp ?: android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )).apply { gravity = Gravity.START }
                h.bubble.setBackgroundResource(R.drawable.search_bg)
                h.bubble.setTextColor(android.graphics.Color.WHITE)
            }
            h.bubble.text = m.body
            h.time.text = fmtAge(m.createdAt.ifEmpty { null })
            h.time.visibility = if (m.createdAt.isEmpty()) View.GONE else View.VISIBLE
        }

        fun setItems(list: List<DmMessage>) {
            items.clear()
            items.addAll(list.sortedBy { it.id })
            notifyDataSetChanged()
        }

        fun prepend(list: List<DmMessage>) {
            val ids = items.map { it.id }.toHashSet()
            val fresh = list.sortedBy { it.id }.filter { it.id != 0L && !ids.contains(it.id) }
            if (fresh.isEmpty()) return
            items.addAll(0, fresh)
            notifyItemRangeInserted(0, fresh.size)
        }

        fun appendNew(list: List<DmMessage>): Boolean {
            val ids = items.map { it.id }.toHashSet()
            var added = false
            for (m in list.sortedBy { it.id }) {
                if (m.id != 0L && ids.contains(m.id)) continue
                items.add(m)
                added = true
            }
            if (added) {
                items.sortBy { it.id }
                notifyDataSetChanged()
            }
            return added
        }
    }

}
