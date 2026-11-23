package com.bloom.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
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
        // 파일 읽기 실패 시 기본값을 먼저 보여 주고, 잠시 뒤 다시 읽어 복구되면 실제 설정으로 바꾼다.
        .retryWhen { e, _ ->
            if (e !is IOException) return@retryWhen false
            emit(emptyPreferences())
            delay(READ_RETRY_DELAY_MILLIS)
            true
        }
        .map { prefs ->
            AppSettings(
                darkMode = prefs[Keys.DARK_MODE],
                alightVibration = prefs[Keys.ALIGHT_VIBRATION] ?: true,
            )
        }

    /** 잠금화면 알림 표시 설정 안내(S-04-S1)를 이미 보여 줬는지. 한 번만 보여 준다. */
    val lockScreenNoticeShown: Flow<Boolean> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.LOCK_SCREEN_NOTICE_SHOWN] ?: false }

    suspend fun setLockScreenNoticeShown() = write { it[Keys.LOCK_SCREEN_NOTICE_SHOWN] = true }

    suspend fun setDarkMode(enabled: Boolean) = write { it[Keys.DARK_MODE] = enabled }

    suspend fun setAlightVibration(enabled: Boolean) = write { it[Keys.ALIGHT_VIBRATION] = enabled }

    /** 저장 실패(IOException)로 앱이 꺼지지 않게 한다. 실패하면 기존 설정이 그대로 남아 토글도 원래 값으로 보인다. */
    private suspend fun write(block: (MutablePreferences) -> Unit) {
        try {
            store.edit(block)
        } catch (_: IOException) {
            // TODO: 저장 실패 안내가 필요해지면 여기서 결과를 돌려준다.
        }
    }

    private companion object {
        const val READ_RETRY_DELAY_MILLIS = 1_000L
    }

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ALIGHT_VIBRATION = booleanPreferencesKey("alight_vibration")
        val LOCK_SCREEN_NOTICE_SHOWN = booleanPreferencesKey("lock_screen_notice_shown")
    }
}
