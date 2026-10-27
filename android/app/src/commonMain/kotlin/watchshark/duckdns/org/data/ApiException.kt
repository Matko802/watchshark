package watchshark.duckdns.org.data

class ApiException(
    val code: Int,
    val serverBody: String?,
    message: String,
) : Exception(message)
