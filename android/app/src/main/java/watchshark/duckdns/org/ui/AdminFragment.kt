package watchshark.duckdns.org.ui
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
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.AdminUser
import watchshark.duckdns.org.data.ApiClient
class AdminFragment : Fragment() {
    private lateinit var adapter: AdminAdapter
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_admin, container, false)
    }
    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = AdminAdapter(mutableListOf(), lifecycleScope, { reload() }) { name ->
            (activity as? watchshark.duckdns.org.MainActivity)?.openChannel(name)
        }
        view.findViewById<RecyclerView>(R.id.admin_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AdminFragment.adapter
            clearBottomBar()
        }
        reload()
    }
    private fun reload() {
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.adminUsers()
                if (!isAdded) return@launch
                adapter.setItems(res.users.orEmpty())
            } catch (e: Exception) {
                if (isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }
    class AdminAdapter(
        private val items: MutableList<AdminUser>,
        private val scope: kotlinx.coroutines.CoroutineScope,
        private val onChanged: () -> Unit,
        private val onOpen: (String) -> Unit = {},
    ) : RecyclerView.Adapter<AdminAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: WebmAvatarView = v.findViewById(R.id.u_avatar)
            val row: View = v.findViewById(R.id.u_row)
            val name: TextView = v.findViewById(R.id.u_name)
            val status: TextView = v.findViewById(R.id.u_status)
            val banRow: View = v.findViewById(R.id.u_banrow)
            val days: TextInputEditText = v.findViewById(R.id.u_days)
            val reason: TextInputEditText = v.findViewById(R.id.u_reason)
            val ban: Button = v.findViewById(R.id.u_ban)
            val approve: Button = v.findViewById(R.id.u_approve)
            val unban: Button = v.findViewById(R.id.u_unban)
            val restore: Button = v.findViewById(R.id.u_restore)
            val delete: Button = v.findViewById(R.id.u_delete)
            val remove: Button = v.findViewById(R.id.u_remove)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_user, parent, false)
            return Holder(v)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: Holder, position: Int) {
            val u = items[position]
            val isAdmin = u.role == "admin"
            h.avatar.setAvatar(u.avatar, R.drawable.ic_person)
            h.avatar.setOnline(u.online)
            h.row.setOnClickListener { onOpen(u.username) }
            h.name.text = u.username + if (isAdmin) " ⭐" else ""
            val status = when {
                u.deleted -> "deleted"
                u.banned -> "banned"
                u.verified -> "active"
                else -> "pending"
            }
            var extra = ""
            if (u.deleted && u.deleted_reason.isNotEmpty()) extra = " • " + u.deleted_reason
            if (u.banned) {
                val dl = if (u.ban_days_left < 0) "permanent" else "%.1fd left".format(u.ban_days_left)
                extra = " • $dl" + if (u.ban_reason.isNotEmpty()) " • ${u.ban_reason}" else ""
            }
            h.status.text = "$status • ${fmtDateTime(u.created_at)}$extra"
            if (isAdmin) {
                h.banRow.visibility = View.GONE
                h.approve.visibility = View.GONE
                h.unban.visibility = View.GONE
                h.restore.visibility = View.GONE
                h.delete.visibility = View.GONE
                h.remove.visibility = View.GONE
                return
            }
            h.banRow.visibility = if (!u.deleted && !u.banned) View.VISIBLE else View.GONE
            h.approve.visibility = if (!u.verified && !u.deleted) View.VISIBLE else View.GONE
            h.unban.visibility = if (u.banned && !u.deleted) View.VISIBLE else View.GONE
            h.restore.visibility = if (u.deleted) View.VISIBLE else View.GONE
            h.delete.visibility = if (!u.deleted) View.VISIBLE else View.GONE
            h.remove.visibility = View.VISIBLE
            val ctx = h.itemView.context
            h.ban.setOnClickListener {
                val days = h.days.text.toString().toDoubleOrNull() ?: 0.0
                if (days <= 0) {
                    ctx.toast("Give ban days")
                    return@setOnClickListener
                }
                scope.launch {
                    try {
                        ApiClient.api.adminBan(
                            mapOf(
                                "id" to u.id,
                                "days" to days,
                                "hours" to 0.0,
                                "reason" to h.reason.text.toString(),
                            ),
                        )
                        onChanged()
                    } catch (e: Exception) {
                        ctx.toast(httpErrorMessage(e))
                    }
                }
            }
            h.unban.setOnClickListener {
                scope.launch {
                    try {
                        ApiClient.api.adminUnban(mapOf("id" to u.id))
                        onChanged()
                    } catch (e: Exception) {
                        ctx.toast(httpErrorMessage(e))
                    }
                }
            }
            h.approve.setOnClickListener {
                scope.launch {
                    try {
                        ApiClient.api.adminApprove(mapOf("id" to u.id))
                        onChanged()
                    } catch (e: Exception) {
                        ctx.toast(httpErrorMessage(e))
                    }
                }
            }
            h.restore.setOnClickListener {
                scope.launch {
                    try {
                        ApiClient.api.adminRestore(mapOf("id" to u.id))
                        onChanged()
                    } catch (e: Exception) {
                        ctx.toast(httpErrorMessage(e))
                    }
                }
            }
            h.delete.setOnClickListener {
                scope.launch {
                    try {
                        ApiClient.api.adminSoftDelete(mapOf("id" to u.id, "reason" to "Removed by admin"))
                        onChanged()
                    } catch (e: Exception) {
                        ctx.toast(httpErrorMessage(e))
                    }
                }
            }
            h.remove.setOnClickListener {
                scope.launch {
                    try {
                        ApiClient.api.adminDelUser(u.id)
                        onChanged()
                    } catch (e: Exception) {
                        ctx.toast(httpErrorMessage(e))
                    }
                }
            }
        }
        fun setItems(list: List<AdminUser>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
        override fun onViewRecycled(h: Holder) {
            h.avatar.release()
            super.onViewRecycled(h)
        }
    }
}