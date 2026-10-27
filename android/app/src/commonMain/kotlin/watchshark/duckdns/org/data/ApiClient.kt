package watchshark.duckdns.org.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import watchshark.duckdns.org.ui.Context
import watchshark.duckdns.org.ui.Uikit

object ApiClient {
    val BASE_URL: String
        get() = Uikit.baseUrl

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
    }

    internal val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(ApiClient.json)
        }
        HttpResponseValidator {
            validateResponse { response ->
                val status = response.status
                if (!status.isSuccess()) {
                    val text = try {
                        response.bodyAsText()
                    } catch (_: Exception) {
                        ""
                    }
                    val serverMsg = runCatching {
                        Json.parseToJsonElement(text).jsonObject["error"]
                            ?.jsonPrimitive?.content
                    }.getOrNull()
                    throw ApiException(
                        status.value,
                        text,
                        serverMsg ?: "HTTP ${status.value}",
                    )
                }
            }
        }
    }

    lateinit var api: ApiService
        private set

    fun init(ctx: Context) {
        if (!this::api.isInitialized) {
            api = ApiService()
        }
    }

    fun sessionToken(): String? = Uikit.getSessionToken()

    fun clearSession() {
        Uikit.setSessionToken(null)
    }

    fun fullUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    }

    fun authCookie(): String? {
        val token = sessionToken() ?: return null
        return "ws_token=$token"
    }
}

internal fun bodyObj(m: Map<String, *>): JsonObject {
    val out = kotlinx.serialization.json.buildJsonObject {
        m.forEach { (k, v) ->
            put(k, when (v) {
                null -> JsonNull
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is JsonElement -> v
                else -> JsonPrimitive(v.toString())
            })
        }
    }
    return out
}
