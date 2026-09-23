package org.watchshark.app.data

import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/me")
    suspend fun me(): MeResponse

    @POST("api/auth/login")
    suspend fun login(@Body b: Map<String, String>): JsonObject

    @POST("api/auth/signup")
    suspend fun signup(@Body b: Map<String, String>): JsonObject

    @POST("api/auth/logout")
    suspend fun logout(): JsonObject

    @GET("api/videos")
    suspend fun videos(
        @Query("q") q: String?,
        @Query("sort") sort: String?,
        @Query("page") page: Long,
        @Query("limit") limit: Int,
        @Query("kind") kind: String?
    ): VideosResponse

    @GET("api/wheels")
    suspend fun wheels(@Query("seen") seen: String?): JsonObject

    @GET("api/videos/{id}")
    suspend fun videoDetail(@Path("id") id: Long): VideoDetailResponse

    @GET("api/channel/{name}")
    suspend fun channel(@Path("name") name: String): ChannelResponse

    @POST("api/follow/{id}")
    suspend fun follow(@Path("id") id: Long): JsonObject

    @POST("api/videos/{id}/like")
    suspend fun like(@Path("id") id: Long): JsonObject

    @POST("api/videos/{id}/comments")
    suspend fun comment(@Path("id") id: Long, @Body b: Map<String, String>): JsonObject

    @Multipart
    @POST("api/videos")
    suspend fun upload(
        @Part("title") title: RequestBody,
        @Part("description") desc: RequestBody,
        @Part("kind") kind: RequestBody,
        @Part file: MultipartBody.Part,
        @Part thumb: MultipartBody.Part?
    ): JsonObject

    @Multipart
    @POST("api/videos/{id}/edit")
    suspend fun editVideo(
        @Path("id") id: Long,
        @Part("title") title: RequestBody,
        @Part("description") desc: RequestBody
    ): JsonObject

    @HTTP(method = "DELETE", path = "api/videos/{id}", hasBody = true)
    suspend fun deleteVideo(@Path("id") id: Long, @Body b: Map<String, String>): JsonObject

    @GET("api/admin/users")
    suspend fun adminUsers(): AdminUsersResponse

    @POST("api/admin/ban")
    suspend fun adminBan(@Body b: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @POST("api/admin/unban")
    suspend fun adminUnban(@Body b: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @POST("api/admin/approve")
    suspend fun adminApprove(@Body b: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @POST("api/admin/restore")
    suspend fun adminRestore(@Body b: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @POST("api/admin/soft-delete")
    suspend fun adminSoftDelete(@Body b: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @DELETE("api/admin/users/{id}")
    suspend fun adminDelUser(@Path("id") id: Long): JsonObject

    @GET("api/notifications")
    suspend fun notifications(): NotificationsResponse

    @POST("api/notifications/read")
    suspend fun notifRead(@Body b: Map<String, @JvmSuppressWildcards Any?>): JsonObject

    @POST("api/settings/notifications")
    suspend fun notifSet(@Body b: Map<String, Boolean>): JsonObject

    @POST("api/auth/username")
    suspend fun rename(@Body b: Map<String, String>): JsonObject

    @POST("api/auth/change")
    suspend fun changePw(@Body b: Map<String, String>): JsonObject
}
