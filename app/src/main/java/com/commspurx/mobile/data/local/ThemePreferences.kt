package com.commspurx.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore("theme_prefs")

class ThemePreferences(context: Context) {
    private val dataStore = context.themeDataStore

    val darkTheme: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DARK_THEME] ?: false
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[DARK_THEME] = enabled }
    }

    companion object {
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}
