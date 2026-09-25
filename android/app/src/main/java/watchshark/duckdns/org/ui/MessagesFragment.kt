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
import watchshark.duckdns.org.data.Friend

class MessagesFragment : Fragment() {
    private lateinit var adapter: FriendAdapter

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
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val ctx = requireContext()
                DmCrypto.ensureUploaded(ctx)
                val res = ApiClient.api.friends()
                if (!isAdded) return@launch
                val list = res.friends.orEmpty()
                adapter.setItems(list)
                v.findViewById<View>(R.id.dm_empty).visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    class FriendAdapter(
        private val items: MutableList<Friend>,
        private val onTap: (Friend) -> Unit
    ) : RecyclerView.Adapter<FriendAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: WebmAvatarView = v.findViewById(R.id.f_avatar)
            val fallback: TextView = v.findViewById(R.id.f_fallback)
            val name: TextView = v.findViewById(R.id.f_name)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
            return Holder(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: Holder, position: Int) {
            val f = items[position]
            h.name.text = "@${f.username}"
            if (f.avatar != null) {
                h.avatar.visibility = View.VISIBLE
                h.fallback.visibility = View.GONE
                h.avatar.setAvatar(f.avatar, R.drawable.ic_person)
            } else {
                h.avatar.visibility = View.GONE
                h.fallback.visibility = View.VISIBLE
                h.fallback.text = f.username.firstOrNull()?.uppercase() ?: "?"
            }
            h.itemView.setOnClickListener { onTap(f) }
        }
        fun setItems(list: List<Friend>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
    }
}
