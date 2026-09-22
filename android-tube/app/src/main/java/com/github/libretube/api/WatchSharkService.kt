package com.github.libretube.api

import com.github.libretube.helpers.PreferenceHelper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MediaType.Companion.toMediaType

/**
 * WatchShark backend: Retrofit service plus session handling.
 * Replaces the Piped backend; the rest of the app is untouched.
 */
interface WatchSharkService {
    @POST("api/auth/login")
    suspend fun login(@Body body: JsonObject): JsonObject

    @POST("api/auth/signup")
    suspend fun signup(@Body body: JsonObject): JsonObject

    @POST("api/auth/logout")
    suspend fun logout(): JsonObject

    @GET("api/me")
    suspend fun me(): JsonObject

    @GET("api/videos")
    suspend fun videos(
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Long = 1,
        @Query("limit") limit: Long = 12,
        @Query("kind") kind: String? = null,
    ): JsonObject

    @GET("api/videos/{id}")
    suspend fun videoDetail(@Path("id") id: Long): JsonObject

    @POST("api/videos/{id}/like")
    suspend fun like(@Path("id") id: Long): JsonObject

    @POST("api/videos/{id}/comments")
    suspend fun comment(@Path("id") id: Long, @Body body: JsonObject): JsonObject

    @POST("api/follow/{id}")
    suspend fun follow(@Path("id") id: Long): JsonObject

    @GET("api/channel/{name}")
    suspend fun channel(@Path("name") name: String): JsonObject

    @GET("api/wheels")
    suspend fun wheels(@Query("seen") seen: String? = null): JsonObject

    @GET("api/notifications")
    suspend fun notifications(): JsonObject

    @POST("api/notifications/read")
    suspend fun notifRead(@Body body: JsonObject): JsonObject
}

object WatchSharkBackend {
    const val APP_URL = "https://watchshark.duckdns.org"
    private const val TOKEN_KEY = "ws_session_token"

    fun sessionToken(): String? =
        PreferenceHelper.getString(TOKEN_KEY, "").takeIf { it.isNotEmpty() }

    fun saveToken(token: String) = PreferenceHelper.putString(TOKEN_KEY, token)
    fun clearSession() = PreferenceHelper.putString(TOKEN_KEY, "")

    private val cookieSaver = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        response.headers("Set-Cookie").forEach { header ->
            header.split(";").firstOrNull { it.trim().startsWith("ws_token=") }?.let {
                saveToken(it.trim().removePrefix("ws_token="))
            }
        }
        response
    }

    private val cookieSender = Interceptor { chain ->
        val token = sessionToken()
        val request = if (!token.isNullOrEmpty()) {
            chain.request().newBuilder().header("Cookie", "ws_token=$token").build()
        } else chain.request()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(cookieSender)
        .addInterceptor(cookieSaver)
        .build()

    val api: WatchSharkService by lazy {
        Retrofit.Builder()
            .baseUrl("$APP_URL/")
            .client(client)
            .addConverterFactory(JsonHelper.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WatchSharkService::class.java)
    }

    fun fullUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http")) return path
        return APP_URL + path
    }

    fun loginBody(login: String, password: String): JsonObject = buildJsonObject {
        put("login", login)
        put("password", password)
    }

    fun signupBody(username: String, email: String, password: String): JsonObject = buildJsonObject {
        put("username", username)
        put("email", email)
        put("password", password)
    }

    fun idBody(id: Long): JsonObject = buildJsonObject { put("id", id) }
}
