package com.commspurx.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.SessionSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "commspurx_session",
)

class SessionStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.applicationContext.sessionDataStore

    fun saveSession(
        accessToken: String,
        refreshToken: String,
        user: AuthUser,
        account: AuthAccount?,
    ) = runBlocking {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER] = json.encodeToString(user)
            if (account != null) {
                prefs[KEY_ACCOUNT] = json.encodeToString(account)
            } else {
                prefs.remove(KEY_ACCOUNT)
            }
        }
    }

    fun updateTokens(accessToken: String, refreshToken: String) = runBlocking {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    fun getAccessToken(): String? = runBlocking {
        dataStore.data.map { it[KEY_ACCESS_TOKEN] }.first()
    }

    fun getRefreshToken(): String? = runBlocking {
        dataStore.data.map { it[KEY_REFRESH_TOKEN] }.first()
    }

    fun getSessionSnapshot(): SessionSnapshot? = runBlocking {
        val prefs = dataStore.data.first()
        val accessToken = prefs[KEY_ACCESS_TOKEN] ?: return@runBlocking null
        val refreshToken = prefs[KEY_REFRESH_TOKEN] ?: return@runBlocking null
        val userJson = prefs[KEY_USER] ?: return@runBlocking null
        val user = runCatching { json.decodeFromString<AuthUser>(userJson) }.getOrNull()
            ?: return@runBlocking null
        val accountJson = prefs[KEY_ACCOUNT]
        val account = accountJson?.let {
            runCatching { json.decodeFromString<AuthAccount>(it) }.getOrNull()
        }
        SessionSnapshot(accessToken, refreshToken, user, account)
    }

    fun clear() = runBlocking {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER = stringPreferencesKey("user")
        private val KEY_ACCOUNT = stringPreferencesKey("account")
    }
}
