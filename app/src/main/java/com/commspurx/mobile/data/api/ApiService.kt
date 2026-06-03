package com.commspurx.mobile.data.api

import com.commspurx.mobile.data.model.ApprovalsResponse
import com.commspurx.mobile.data.model.BulkImportJobResult
import com.commspurx.mobile.data.model.ContractsListResponse
import com.commspurx.mobile.data.model.DecideApprovalRequest
import com.commspurx.mobile.data.model.DeliveriesResponse
import com.commspurx.mobile.data.model.HealthResponse
import com.commspurx.mobile.data.model.LoginRequest
import com.commspurx.mobile.data.model.LoginResponse
import com.commspurx.mobile.data.model.LogoutRequest
import com.commspurx.mobile.data.model.NotificationsResponse
import com.commspurx.mobile.data.model.RefreshRequest
import com.commspurx.mobile.data.model.RefreshResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HealthApi {
    @GET("health")
    suspend fun ping(): HealthResponse
}

interface ContractsApi {
    @GET("purchase-contracts/expiring-soon")
    suspend fun listPurchaseExpiringSoon(
        @Query("days") days: Int = 7,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
    ): ContractsListResponse

    @GET("sales-contracts/expiring-soon")
    suspend fun listSalesExpiringSoon(
        @Query("days") days: Int = 7,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
    ): ContractsListResponse
}

interface MobilePushApi {
    @POST("mobile/fcm-token")
    suspend fun registerFcmToken(
        @Header("X-Commspurx-Client") client: String = "mobile",
        @Body body: com.commspurx.mobile.data.model.FcmTokenRequest,
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "mobile/fcm-token", hasBody = true)
    suspend fun unregisterFcmToken(
        @Header("X-Commspurx-Client") client: String = "mobile",
        @Body body: com.commspurx.mobile.data.model.FcmTokenRequest,
    ): Response<Unit>
}

interface AuthApi {
    @POST("auth/login")
    suspend fun login(
        @Header("X-Commspurx-Client") client: String = "mobile",
        @Body body: LoginRequest,
    ): LoginResponse

    @POST("auth/mobile/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @POST("auth/mobile/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>
}

interface NotificationsApi {
    @GET("notifications")
    suspend fun listNotifications(
        @Query("unread") unread: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
    ): NotificationsResponse

    @PATCH("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<Unit>

    @PATCH("notifications/read-all")
    suspend fun markAllRead(): Response<Unit>
}

interface ApprovalsApi {
    @GET("approvals")
    suspend fun listPending(): ApprovalsResponse

    @POST("approvals/{entityType}/{id}/decide")
    suspend fun decide(
        @Path("entityType") entityType: String,
        @Path("id") id: String,
        @Body body: DecideApprovalRequest,
    ): Response<Unit>
}

interface BulkImportApi {
    @GET("bulk-import/jobs/{jobId}")
    suspend fun getJob(@Path("jobId") jobId: String): BulkImportJobResult
}

interface DeliveriesApi {
    @GET("deliveries")
    suspend fun listDeliveries(
        @Query("tab") tab: String = "current",
        @Query("status") status: String? = "ongoing",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
    ): DeliveriesResponse
}
