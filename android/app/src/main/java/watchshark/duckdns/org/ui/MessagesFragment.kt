package watchshark.duckdns.org.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.DmConversation
import watchshark.duckdns.org.data.DmUserEntry

class MessagesFragment : Fragment() {
    private lateinit var convAdapter: ConvAdapter
    private lateinit var searchAdapter: SearchAdapter
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollTask: Runnable? = null
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        convAdapter = ConvAdapter(mutableListOf()) { c ->
            (activity as? MainActivity)?.openDetail(ChatFragment.newInstance(c.userId, c.username))
        }
        searchAdapter = SearchAdapter(mutableListOf()) { u ->
            (activity as? MainActivity)?.openDetail(ChatFragment.newInstance(u.userId, u.username))
        }
        view.findViewById<RecyclerView>(R.id.conv_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = convAdapter
            clearBottomBar()
        }
        view.findViewById<RecyclerView>(R.id.dm_search_results).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
        view.findViewById<EditText>(R.id.dm_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()
                searchJob?.cancel()
                if (q.isEmpty()) {
                    searchAdapter.setItems(emptyList())
                    return
                }
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(350)
                    if (!isAdded) return@launch
                    try {
                        val res = ApiClient.api.dmUserSearch(q)
                        if (!isAdded) return@launch
                        searchAdapter.setItems(res.users.orEmpty())
                    } catch (_: Exception) {}
                }
            }
        })
        reload()
    }

    override fun onResume() {
        super.onResume()
        pollTask = object : Runnable {
            override fun run() {
                if (isAdded) {
                    reload()
                    pollHandler.postDelayed(this, 5000)
                }
            }
        }
        pollHandler.postDelayed(pollTask!!, 5000)
    }

    override fun onPause() {
        pollTask?.let { pollHandler.removeCallbacks(it) }
        pollTask = null
        super.onPause()
    }

    private fun reload() {
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.dmConversations()
                if (!isAdded) return@launch
                val list = res.conversations.orEmpty()
                convAdapter.setItems(list)
                v.findViewById<View>(R.id.dm_empty).visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (isAdded && convAdapter.itemCount == 0) v.snack(httpErrorMessage(e))
            }
        }
    }

    class ConvAdapter(
        private val items: MutableList<DmConversation>,
        private val onTap: (DmConversation) -> Unit
    ) : RecyclerView.Adapter<ConvAdapter.Holder>() {
        private var lastSig = ""
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: WebmAvatarView = v.findViewById(R.id.c_avatar)
            val name: TextView = v.findViewById(R.id.c_name)
            val preview: TextView = v.findViewById(R.id.c_preview)
            val time: TextView = v.findViewById(R.id.c_time)
            val unread: TextView = v.findViewById(R.id.c_unread)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val c = items[position]
            h.name.text = "@${c.username}"
            h.avatar.setAvatar(c.avatar, R.drawable.ic_person)
            h.avatar.setOnline(c.online)
            if (c.lastMessage.isNotEmpty()) {
                h.preview.visibility = View.VISIBLE
                h.preview.text = c.lastMessage
            } else {
                h.preview.visibility = View.GONE
            }
            h.time.text = fmtAge(c.lastAt)
            if (c.unread > 0) {
                h.unread.visibility = View.VISIBLE
                h.unread.text = if (c.unread > 9) "9+" else c.unread.toString()
            } else {
                h.unread.visibility = View.GONE
            }
            h.itemView.setOnClickListener { onTap(c) }
        }

        fun setItems(list: List<DmConversation>) {
            val sig = list.joinToString("|") { "${it.userId}:${it.lastMessageId}:${it.unread}:${it.lastMessage}" }
            if (sig == lastSig && items.size == list.size) return
            lastSig = sig
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
    }

    class SearchAdapter(
        private val items: MutableList<DmUserEntry>,
        private val onTap: (DmUserEntry) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: WebmAvatarView = v.findViewById(R.id.c_avatar)
            val name: TextView = v.findViewById(R.id.c_name)
            val preview: TextView = v.findViewById(R.id.c_preview)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val u = items[position]
            h.name.text = "@${u.username}"
            h.avatar.setAvatar(u.avatar, R.drawable.ic_person)
            h.avatar.setOnline(u.online)
            h.preview.visibility = View.GONE
            h.itemView.findViewById<View>(R.id.c_time).visibility = View.GONE
            h.itemView.findViewById<View>(R.id.c_unread).visibility = View.GONE
            h.itemView.setOnClickListener { onTap(u) }
        }

        fun setItems(list: List<DmUserEntry>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
    }
}
