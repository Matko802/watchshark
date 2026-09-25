package watchshark.duckdns.org.data
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import watchshark.duckdns.org.BuildConfig
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
    /** Cookie header for the current session, if logged in. */
    fun authCookie(): String? {
        val token = sessionToken() ?: return null
        return "ws_token=$token"
    }
    /**
     * ExoPlayer with the session cookie attached to media requests.
     * The server requires auth for /v/ /t/ streams, and plain
     * MediaItem.fromUri sends no cookies — without this, shorts and
     * music fail with 403 and nothing plays.
     */
    fun buildPlayer(ctx: Context): ExoPlayer {
        val props = mutableMapOf<String, String>()
        authCookie()?.let { props["Cookie"] = it }
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(props)
        try {
            val meter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
                .getSingletonInstance(ctx)
            dataSource.setTransferListener(meter)
            AutoQuality.bind(meter)
        } catch (_: Exception) {
        }
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
    }
    fun mediaItem(url: String): MediaItem = MediaItem.fromUri(url)
}