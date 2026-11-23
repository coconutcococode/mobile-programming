package com.bloom

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloom.core.datastore.AppSettings
import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.navigation.BloomApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

// 3버튼 내비게이션일 때 바 뒤에 깔리는 반투명 막 (androidx.activity 기본값과 같은 값)
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    /** 안내 알림을 누를 때마다 1씩 오른다 → BloomApp 이 안내 진행(S-04)으로 이동 */
    private val openGuideRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent) // 화면 회전 등으로 다시 만들어질 때는 다시 이동하지 않는다
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = settings.darkMode ?: isSystemInDarkTheme()

            // 시스템 바 아이콘 색을 시스템 설정이 아니라 앱 테마(다크 모드 설정)에 맞춘다.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { darkTheme },
                )
                onDispose {}
            }

            val openGuideRequest by openGuideRequests.collectAsStateWithLifecycle()
            BloomTheme(darkTheme = darkTheme) {
                BloomApp(openGuideRequest = openGuideRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_GUIDE) openGuideRequests.value++
    }

    companion object {
        const val ACTION_OPEN_GUIDE = "com.bloom.OPEN_GUIDE"
    }
}
