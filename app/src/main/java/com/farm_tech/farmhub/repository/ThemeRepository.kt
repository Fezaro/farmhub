package com.farm_tech.farmhub.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore instance per application, tied to the application context.
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "farmhub_settings")

class ThemeRepository(private val context: Context) {

    companion object {
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    /** Emits the persisted dark-theme preference. Defaults to false (light theme) if unset. */
    val isDarkTheme: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_DARK_THEME] ?: false
    }

    /** Persists [isDark] so the preference survives process death. */
    suspend fun setDarkTheme(isDark: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = isDark
        }
    }
}
