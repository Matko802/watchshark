package org.watchshark.app.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.watchshark.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    val BASE_URL: String = try {
        BuildConfig.APP_URL
    } catch (_: Exception) {
        "https://watchshark.duckdns.org"
    }

    lateinit var api: ApiService
        private set

    private var appContext: Context? = null
    private const val PREFS = "watchshark_session"
    private const val KEY_COOKIE = "ws_token"

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val token = cookies.firstOrNull { it.name == "ws_token" } ?: return
                prefs.edit().putString(KEY_COOKIE, token.value).apply()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val token = prefs.getString(KEY_COOKIE, null) ?: return emptyList()
                return listOf(
                    Cookie.Builder()
                        .name("ws_token")
                        .value(token)
                        .domain(url.host)
                        .path("/")
                        .build()
                )
            }
        }
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
        val base = BASE_URL.trimEnd('/') + "/"
        api = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun sessionToken(): String? {
        val ctx = appContext ?: return null
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
    }

    fun clearSession() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_COOKIE)?.apply()
    }

    fun fullUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    }
}
