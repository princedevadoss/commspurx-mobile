package com.commspurx.mobile.network

import com.commspurx.mobile.data.api.AuthApi
import com.commspurx.mobile.data.local.SessionStore
import com.commspurx.mobile.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenRefresher(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val tokenProvider: TokenProvider,
) {
    private val mutex = Mutex()

    suspend fun refreshAccessToken(): String? = mutex.withLock {
        val storedRefresh = sessionStore.getRefreshToken() ?: return null
        val currentAccess = tokenProvider.getAccessToken()
        val storedAccess = sessionStore.getAccessToken()

        // Another thread may have refreshed already.
        if (storedAccess != null && storedAccess != currentAccess) {
            tokenProvider.setAccessToken(storedAccess)
            return storedAccess
        }

        return try {
            val response = authApi.refresh(RefreshRequest(storedRefresh))
            sessionStore.updateTokens(response.accessToken, response.refreshToken)
            tokenProvider.setAccessToken(response.accessToken)
            response.accessToken
        } catch (_: Exception) {
            sessionStore.clear()
            tokenProvider.setAccessToken(null)
            null
        }
    }
}

class TokenAuthenticator(
    private val tokenRefresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.contains("/auth/")) return null

        val newToken = runBlocking { tokenRefresher.refreshAccessToken() } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
