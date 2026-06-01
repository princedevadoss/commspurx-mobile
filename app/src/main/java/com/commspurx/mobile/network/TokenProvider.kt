package com.commspurx.mobile.network

import com.commspurx.mobile.data.local.SessionStore
import java.util.concurrent.atomic.AtomicReference

class TokenProvider(private val sessionStore: SessionStore) {
    private val accessToken = AtomicReference(sessionStore.getAccessToken())

    fun getAccessToken(): String? = accessToken.get()

    fun setAccessToken(token: String?) {
        accessToken.set(token)
    }

    fun syncFromStore() {
        accessToken.set(sessionStore.getAccessToken())
    }
}
