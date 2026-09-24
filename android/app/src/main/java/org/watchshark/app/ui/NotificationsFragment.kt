package org.watchshark.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.watchshark.app.MainActivity
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient
import org.watchshark.app.data.Notif

class NotificationsFragment : Fragment() {
    private lateinit var adapter: NotifAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = NotifAdapter(mutableListOf()) { n ->
            lifecycleScope.launch {
                try {
                    ApiClient.api.notifRead(mapOf("id" to n.id))
                } catch (_: Exception) {
                }
                if (n.videoId != null && n.videoId > 0) {
                    (activity as? MainActivity)?.openDetail(WatchFragment.newInstance(n.videoId))
                } else {
                    reload()
                }
            }
        }
        view.findViewById<RecyclerView>(R.id.notif_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NotificationsFragment.adapter
        }
        view.findViewById<Button>(R.id.mark_read).setOnClickListener {
            lifecycleScope.launch {
                try {
                    ApiClient.api.notifRead(mapOf("id" to null))
                } catch (_: Exception) {
                }
                reload()
            }
        }
        reload()
    }

    private fun reload() {
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.notifications()
                if (!isAdded) return@launch
                adapter.setItems(res.notifications.orEmpty())
            } catch (e: Exception) {
                if (isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    class NotifAdapter(
        private val items: MutableList<Notif>,
        private val onTap: (Notif) -> Unit,
    ) : RecyclerView.Adapter<NotifAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.n_title)
            val body: TextView = v.findViewById(R.id.n_body)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notif, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val n = items[position]
            if (n.kind == "delete") {
                h.title.text = "Video removed: ${n.title}"
                h.body.text = n.text
            } else {
                h.title.text = "@${n.username} uploaded: ${n.title}"
                h.body.text = fmtAge(n.created_at)
            }
            h.title.alpha = if (n.read) 0.6f else 1.0f
            h.itemView.setOnClickListener { onTap(n) }
        }

        fun setItems(list: List<Notif>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
    }
}
