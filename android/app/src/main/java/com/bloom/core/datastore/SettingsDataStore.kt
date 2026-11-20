package com.bloom.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
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

    val settings: Flow<AppSettings> = store.data
        // 파일 읽기 실패 시 흐름이 죽지 않도록 빈 설정(=기본값)으로 이어 간다.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
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
