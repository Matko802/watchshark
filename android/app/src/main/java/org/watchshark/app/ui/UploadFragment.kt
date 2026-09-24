package org.watchshark.app.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.watchshark.app.MainActivity
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient

class UploadFragment : Fragment() {
    private var kind = "video"
    private var fileUri: Uri? = null
    private var thumbUri: Uri? = null
    private var busy = false

    companion object {
        fun newInstance(kind: String) = UploadFragment().apply {
            arguments = Bundle().apply { putString("kind", kind) }
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fileUri = uri
            view?.findViewById<TextView>(R.id.file_name)?.text = displayName(uri)
        }
    }
    private val pickThumb = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        thumbUri = uri
        if (uri != null) view?.findViewById<TextView>(R.id.file_name)?.append("\nThumb: " + displayName(uri))
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        kind = arguments?.getString("kind", "video") ?: "video"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        view.clearBottomBar()
        val tabs: TabLayout = view.findViewById(R.id.kind_tabs)
        val kinds = arrayOf("video", "wheel", "music")
        tabs.getTabAt(kinds.indexOf(kind).coerceAtLeast(0))?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                kind = kinds[tab.position]
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        view.findViewById<Button>(R.id.pick_file).setOnClickListener {
            pickFile.launch(if (kind == "music") "audio/*" else "video/*")
        }
        view.findViewById<Button>(R.id.pick_thumb).setOnClickListener {
            pickThumb.launch("image/*")
        }
        view.findViewById<Button>(R.id.up_go).setOnClickListener { upload() }
    }

    private fun displayName(uri: Uri): String {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else uri.lastPathSegment ?: "file"
            } ?: (uri.lastPathSegment ?: "file")
        } catch (e: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    private fun upload() {
        val v = view ?: return
        if (busy) return
        val uri = fileUri
        val title = v.findViewById<TextInputEditText>(R.id.up_title).text.toString().trim()
        val msg: TextView = v.findViewById(R.id.up_msg)
        if (uri == null) {
            msg.text = "Choose a file first"
            return
        }
        if (title.isEmpty()) {
            msg.text = "Title is required"
            return
        }
        busy = true
        v.findViewById<View>(R.id.up_bar).visibility = View.VISIBLE
        msg.text = "Uploading..."
        lifecycleScope.launch {
            try {
                val cr = requireContext().contentResolver
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val bytes = withContext(Dispatchers.IO) {
                    cr.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Cannot read file")
                }
                val filePart = MultipartBody.Part.createFormData(
                    "file", displayName(uri),
                    bytes.toRequestBody(mime.toMediaType()),
                )
                var thumbPart: MultipartBody.Part? = null
                thumbUri?.let { tu ->
                    val tm = cr.getType(tu) ?: "image/jpeg"
                    val tb = withContext(Dispatchers.IO) {
                        cr.openInputStream(tu)?.use { it.readBytes() }
                    }
                    if (tb != null) {
                        thumbPart = MultipartBody.Part.createFormData(
                            "thumb", displayName(tu), tb.toRequestBody(tm.toMediaType()),
                        )
                    }
                }
                val res = ApiClient.api.upload(
                    title.toRequestBody("text/plain".toMediaType()),
                    v.findViewById<TextInputEditText>(R.id.up_desc).text.toString()
                        .toRequestBody("text/plain".toMediaType()),
                    kind.toRequestBody("text/plain".toMediaType()),
                    filePart, thumbPart,
                )
                if (!isAdded) return@launch
                if (res.has("error") || !res.has("id")) {
                    msg.text = res.get("error")?.asString ?: "Upload failed"
                    return@launch
                }
                val id = res.get("id").asLong
                val rkind = res.get("kind")?.asString ?: kind
                msg.text = "Uploaded!"
                if (rkind == "music") {
                    (activity as? MainActivity)?.showMusic()
                } else {
                    (activity as? MainActivity)?.openDetail(WatchFragment.newInstance(id))
                }
            } catch (e: Exception) {
                if (isAdded) msg.text = httpErrorMessage(e)
            } finally {
                busy = false
                if (isAdded) v.findViewById<View>(R.id.up_bar).visibility = View.GONE
            }
        }
    }
}
