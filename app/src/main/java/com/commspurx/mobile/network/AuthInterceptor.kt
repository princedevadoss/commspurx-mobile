package com.commspurx.mobile.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.getAccessToken()
        val authenticated = if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Commspurx-Client", "mobile")
                .build()
        } else {
            request.newBuilder()
                .header("X-Commspurx-Client", "mobile")
                .build()
        }
        return chain.proceed(authenticated)
    }
}
