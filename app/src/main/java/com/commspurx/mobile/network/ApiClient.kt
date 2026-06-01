package com.commspurx.mobile.network

import com.commspurx.mobile.BuildConfig
import com.commspurx.mobile.data.api.ApprovalsApi
import com.commspurx.mobile.data.api.AuthApi
import com.commspurx.mobile.data.api.BulkImportApi
import com.commspurx.mobile.data.api.ContractsApi
import com.commspurx.mobile.data.api.DeliveriesApi
import com.commspurx.mobile.data.api.HealthApi
import com.commspurx.mobile.data.api.NotificationsApi
import com.commspurx.mobile.data.local.SessionStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class ApiClient(sessionStore: SessionStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val tokenProvider = TokenProvider(sessionStore)

    fun getTokenProvider(): TokenProvider = tokenProvider

    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("X-Commspurx-Client", "mobile")
                    .header("Content-Type", "application/json")
                    .build(),
            )
        }
        .build()

    private val refreshRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
        .client(refreshClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val tokenRefresher = TokenRefresher(
        authApi = refreshRetrofit.create(AuthApi::class.java),
        sessionStore = sessionStore,
        tokenProvider = tokenProvider,
    )

    private val authenticatedClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenProvider))
        .authenticator(TokenAuthenticator(tokenRefresher))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    },
                )
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
        .client(authenticatedClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val publicRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
        .client(refreshClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val healthApi: HealthApi = publicRetrofit.create(HealthApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val refreshAuthApi: AuthApi = refreshRetrofit.create(AuthApi::class.java)
    val notificationsApi: NotificationsApi = retrofit.create(NotificationsApi::class.java)
    val approvalsApi: ApprovalsApi = retrofit.create(ApprovalsApi::class.java)
    val bulkImportApi: BulkImportApi = retrofit.create(BulkImportApi::class.java)
    val deliveriesApi: DeliveriesApi = retrofit.create(DeliveriesApi::class.java)
    val contractsApi: ContractsApi = retrofit.create(ContractsApi::class.java)

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
