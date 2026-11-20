package com.bloom.feature.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.datastore.AppSettings
import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomTextButton
import com.bloom.core.designsystem.component.ScreenStub
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.UserAccount
import com.bloom.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val user: StateFlow<UserAccount?> = authRepository.currentUser

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDarkMode(enabled) }
    fun setAlightVibration(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setAlightVibration(enabled) }
    fun signIn(activityContext: Context) = viewModelScope.launch { authRepository.signInWithGoogle(activityContext) }
    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}

/**
 * S-08 설정: 계정 · 화면 · 권한 · 추천 · 정보 순.
 * TODO(트랙 A): 실제 Google 로그인(Credential Manager + Firebase), 위치·알림 권한 상태, 잠금화면 알림 표시 안내,
 *  추천 기록 관리(S-08-D1), 동기화 시각 표시.
 */
@Composable
fun SettingsScreen(
    onDataSourceClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    ScreenStub(screenId = "S-08", title = "설정", description = "로그인은 지금 가짜 저장소에 연결되어 있다(누르면 바로 로그인).") {
        SectionTitle("계정")
        val account = user
        if (account == null) {
            BloomPrimaryButton("Google로 로그인", onClick = { viewModel.signIn(context) })
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.email, style = MaterialTheme.typography.titleMedium)
                    Text("Google 로그인", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
                }
                BloomTextButton("로그아웃", onClick = { viewModel.signOut() })
            }
        }
        HorizontalDivider()
        SectionTitle("화면")
        SwitchRow("다크 모드", checked = settings.darkMode ?: systemDark, onCheckedChange = { viewModel.setDarkMode(it) })
        HorizontalDivider()
        SectionTitle("알림")
        SwitchRow("하차 전 진동 알림", checked = settings.alightVibration, onCheckedChange = { viewModel.setAlightVibration(it) })
        HorizontalDivider()
        SectionTitle("정보")
        Text(
            "데이터 출처",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onDataSourceClick).padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = BloomTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
}

/** 토글 옆에 '켬/끔' 글자를 함께 둔다(6.4 색 외 구분 수단). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(if (checked) "켬" else "끔", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
