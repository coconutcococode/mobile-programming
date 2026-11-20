package com.bloom.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 기기 설정(계정 동기화 대상 아님). */
data class AppSettings(
    /** null = 아직 직접 고르지 않음 → 시스템 설정을 따른다. */
    val darkMode: Boolean? = null,
    /** 하차 전 진동 알림(FR-07) */
    val alightVibration: Boolean = true,
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            darkMode = prefs[Keys.DARK_MODE],
            alightVibration = prefs[Keys.ALIGHT_VIBRATION] ?: true,
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        store.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setAlightVibration(enabled: Boolean) {
        store.edit { it[Keys.ALIGHT_VIBRATION] = enabled }
    }

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ALIGHT_VIBRATION = booleanPreferencesKey("alight_vibration")
    }
}
