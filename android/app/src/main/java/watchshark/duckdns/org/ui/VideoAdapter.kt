package watchshark.duckdns.org.ui
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Video
class VideoAdapter(
    private val videos: MutableList<Video>,
    private val onOpen: (Video) -> Unit = {},
    private val layoutRes: Int = R.layout.item_video,
) : RecyclerView.Adapter<VideoAdapter.Holder>() {
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val avatar: WebmAvatarView = v.findViewById(R.id.avatar)
        val title: TextView = v.findViewById(R.id.title)
        val meta: TextView = v.findViewById(R.id.meta)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return Holder(v)
    }
    override fun getItemCount(): Int = videos.size
    override fun onBindViewHolder(h: Holder, position: Int) {
        val v = videos[position]
        h.thumb.loadMedia(v.thumbnail)
        h.avatar.setAvatar(v.avatar, R.drawable.ic_person)
        h.title.text = if (v.status != "ready") "⏳ ${v.title}" else v.title
        h.meta.text = "@${v.username} • ${fmtNum(v.views)} views • ${fmtAge(v.created_at)}"
        h.itemView.setOnClickListener {
            onOpen(v)
            (h.itemView.context as? MainActivity)?.openDetail(WatchFragment.newInstance(v.id))
        }
    }
    override fun onViewRecycled(h: Holder) {
        h.avatar.release()
        super.onViewRecycled(h)
    }
    fun setItems(items: List<Video>) {
        videos.clear()
        videos.addAll(items)
        notifyDataSetChanged()
    }
    fun append(items: List<Video>) {
        val start = videos.size
        videos.addAll(items)
        notifyItemRangeInserted(start, items.size)
    }
}