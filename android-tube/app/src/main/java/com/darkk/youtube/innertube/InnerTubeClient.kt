package com.darkk.youtube.innertube

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.util.Locale

/**
 * InnerTube HTTP client – mirrors AirBeats' InnerTube.kt approach.
 * Uses the YouTube DATA/InnerTube API (v1) directly.
 */
object InnerTubeClient {

    private const val BASE_URL = "https://www.youtube.com/youtubei/v1/"
    
    enum class ClientType(
        val clientName: String,
        val clientVersion: String,
        val clientId: String,
        val userAgent: String,
        val osVersion: String? = null
    ) {
        WEB(
            "WEB", 
            "2.20250312.04.00", 
            "1", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        ),
        WEB_REMIX(
            "WEB_REMIX",
            "1.20250106.01.00",
            "86",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        ),
        IOS(
            "IOS",
            "20.10.4",
            "5",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            "18.3.2.22D82"
        ),
        ANDROID_VR(
            "ANDROID_VR",
            "1.61.48",
            "28",
            "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3)",
            null
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }
        defaultRequest {
            url(BASE_URL)
        }
    }

    fun buildContext(client: ClientType = ClientType.WEB, visitorData: String? = null): JsonObject = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("gl", Locale.getDefault().country)
            put("hl", Locale.getDefault().toLanguageTag())
            if (visitorData != null) {
                put("visitorData", visitorData)
            }
            client.osVersion?.let { put("osVersion", it) }
        })
    }

    fun HttpRequestBuilder.applyYtHeaders(client: ClientType = ClientType.WEB) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", "https://www.youtube.com")
            append("Referer", "https://www.youtube.com/")
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }
}
