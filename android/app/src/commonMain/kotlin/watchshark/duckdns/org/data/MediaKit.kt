package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context

expect open class PickedFile(name: String, mime: String, size: Long) {
    val name: String
    val mime: String
    val size: Long
}

expect object MediaKit {
    fun pick(ctx: Context, mime: String, onPicked: (PickedFile?) -> Unit)
    fun readBytes(file: PickedFile, maxBytes: Long): ByteArray?
}
