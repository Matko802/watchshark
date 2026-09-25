package watchshark.duckdns.org.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.DmCrypto
import watchshark.duckdns.org.data.DmRepo
import watchshark.duckdns.org.data.Friend

class MessagesFragment : Fragment() {
    private lateinit var adapter: FriendAdapter
    private var myId: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = FriendAdapter(mutableListOf()) { f ->
            (activity as? MainActivity)?.openDetail(ChatFragment.newInstance(f.id, f.username))
        }
        view.findViewById<RecyclerView>(R.id.friends_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MessagesFragment.adapter
        }
        reload()
    }

    private fun reload() {
        val ctx = context ?: return
        lifecycleScope.launch {
            val list = DmRepo.friends(ctx)
            if (!isAdded) return@launch
            adapter.setItems(list)
            view?.findViewById<View>(R.id.dm_empty)?.visibility =
                if (list.isEmpty()) View.VISIBLE else View.GONE
            if (list.isNotEmpty()) loadPreviews(list)
        }
    }

    private fun loadPreviews(list: List<Friend>) {
        lifecycleScope.launch {
            try {
                myId = ApiClient.api.me().user?.id ?: 0
            } catch (_: Exception) {
            }
            val recent = try {
                ApiClient.api.dmRecent(30).messages.orEmpty()
            } catch (_: Exception) {
                return@launch
            }
            if (!isAdded) return@launch
            fillPreviews(list, recent)
        }
    }

    private suspend fun fillPreviews(
        list: List<Friend>,
        recent: List<watchshark.duckdns.org.data.DmMessage>
    ) {
        val ctx = requireContext()
        val byId = list.associateBy { it.id }
        val seen = mutableSetOf<Long>()
        for (m in recent.sortedByDescending { it.id }) {
            val peerId = if (m.senderId == myId) m.recipientId else m.senderId
            if (!byId.containsKey(peerId) || !seen.add(peerId)) continue
            val peer = byId[peerId] ?: continue
            var preview: String? = null
            var fail = false
            try {
                val key = DmRepo.peerKey(peer.username)
                preview = if (key != null) {
                    DmCrypto.decrypt(ctx.applicationContext, key, m.nonce, m.body)
                } else {
                    null
                }
                if (preview == null) fail = true
            } catch (_: Exception) {
                fail = true
            }
            val text = when {
                preview != null -> preview.take(60)
                fail && m.senderId != myId -> "New message"
                else -> null
            }
            if (text != null && isAdded) {
                adapter.setPreview(peer.username, text, fmtAge(m.createdAt))
            }
            if (seen.size >= list.size) break
        }
    }

    class FriendAdapter(
        private val items: MutableList<Row>,
        private val onTap: (Friend) -> Unit
    ) : RecyclerView.Adapter<FriendAdapter.Holder>() {
        data class Row(val friend: Friend, var preview: String? = null, var time: String? = null)

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: WebmAvatarView = v.findViewById(R.id.f_avatar)
            val fallback: TextView = v.findViewById(R.id.f_fallback)
            val name: TextView = v.findViewById(R.id.f_name)
            val preview: TextView = v.findViewById(R.id.f_preview)
            val time: TextView = v.findViewById(R.id.f_time)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val r = items[position]
            h.name.text = "@${r.friend.username}"
            if (r.friend.avatar != null) {
                h.avatar.visibility = View.VISIBLE
                h.fallback.visibility = View.GONE
                h.avatar.setAvatar(r.friend.avatar, R.drawable.ic_person)
            } else {
                h.avatar.visibility = View.GONE
                h.fallback.visibility = View.VISIBLE
                h.fallback.text = r.friend.username.firstOrNull()?.uppercase() ?: "?"
            }
            if (r.preview != null) {
                h.preview.visibility = View.VISIBLE
                h.preview.text = r.preview
            } else {
                h.preview.visibility = View.GONE
            }
            if (r.time != null) {
                h.time.visibility = View.VISIBLE
                h.time.text = r.time
            } else {
                h.time.visibility = View.GONE
            }
            h.itemView.setOnClickListener { onTap(r.friend) }
        }

        fun setItems(list: List<Friend>) {
            items.clear()
            items.addAll(list.map { Row(it) })
            notifyDataSetChanged()
        }

        fun setPreview(username: String, text: String, time: String) {
            val i = items.indexOfFirst { it.friend.username == username }
            if (i < 0) return
            items[i] = items[i].copy(preview = text, time = time)
            notifyItemChanged(i)
        }
    }
}
