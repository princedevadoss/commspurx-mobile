package com.commspurx.mobile.data.repository

import com.commspurx.mobile.data.api.AuthApi
import com.commspurx.mobile.data.local.SessionStore
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.LoginRequest
import com.commspurx.mobile.data.model.LogoutRequest
import com.commspurx.mobile.data.model.RefreshRequest
import com.commspurx.mobile.data.model.SessionSnapshot
import com.commspurx.mobile.data.model.UserRole
import android.content.Context
import com.commspurx.mobile.notifications.FcmTokenRegistrar
import com.commspurx.mobile.network.TokenProvider
import retrofit2.HttpException

class AuthRepository(
    private val appContext: Context,
    private val authApi: AuthApi,
    private val refreshAuthApi: AuthApi,
    private val sessionStore: SessionStore,
    private val tokenProvider: TokenProvider,
) {
    fun getStoredSession(): SessionSnapshot? = sessionStore.getSessionSnapshot()

    suspend fun restoreSession(): SessionSnapshot? {
        val snapshot = sessionStore.getSessionSnapshot() ?: return null
        tokenProvider.setAccessToken(snapshot.accessToken)
        return try {
            val refreshed = refreshAuthApi.refresh(RefreshRequest(snapshot.refreshToken))
            val user = refreshed.user.copy(
                name = refreshed.user.name.ifBlank { refreshed.user.email },
            )
            sessionStore.saveSession(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                user = user,
                account = refreshed.account,
            )
            tokenProvider.setAccessToken(refreshed.accessToken)
            SessionSnapshot(refreshed.accessToken, refreshed.refreshToken, user, refreshed.account)
        } catch (_: Exception) {
            // Stay signed in offline; token refresh will run again when connectivity returns.
            snapshot
        }
    }

    suspend fun login(
        accountCode: String,
        email: String,
        password: String,
    ): Result<SessionSnapshot> {
        return try {
            val response = authApi.login(
                body = LoginRequest(
                    accountCode = accountCode.trim().lowercase(),
                    email = email.trim(),
                    password = password,
                ),
            )
            val refreshToken = response.refreshToken
                ?: return Result.failure(IllegalStateException("Missing refresh token from server"))
            val role = UserRole.from(response.user.role)
                ?: return Result.failure(IllegalStateException("This app is for admins and managers only"))
            if (role == UserRole.Trader) {
                return Result.failure(IllegalStateException("This app is for admins and managers only"))
            }
            val user = response.user.copy(
                name = response.user.name.ifBlank { response.user.email },
            )
            sessionStore.saveSession(
                accessToken = response.accessToken,
                refreshToken = refreshToken,
                user = user,
                account = response.account,
            )
            tokenProvider.setAccessToken(response.accessToken)
            Result.success(
                SessionSnapshot(response.accessToken, refreshToken, user, response.account),
            )
        } catch (e: HttpException) {
            Result.failure(mapHttpError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        FcmTokenRegistrar.unregisterCurrentToken(appContext)
        val refreshToken = sessionStore.getRefreshToken()
        try {
            if (refreshToken != null) {
                authApi.logout(LogoutRequest(refreshToken))
            }
        } catch (_: Exception) {
            // Best effort logout
        } finally {
            sessionStore.clear()
            tokenProvider.setAccessToken(null)
        }
    }

    private fun mapHttpError(e: HttpException): Exception {
        return when (e.code()) {
            401 -> IllegalStateException("Invalid account code, email, or password")
            429 -> IllegalStateException("Too many attempts. Please wait and try again.")
            else -> IllegalStateException("Login failed. Please try again.")
        }
    }
}
