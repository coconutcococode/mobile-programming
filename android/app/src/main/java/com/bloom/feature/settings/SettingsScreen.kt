package com.bloom.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.datastore.AppSettings
import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomTextButton
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.SignInError
import com.bloom.core.model.UserAccount
import com.bloom.data.repository.AuthRepository
import com.bloom.feature.guide.GuideSystemSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** 로그인 진행 중. 버튼을 '로그인 중…'으로 바꾸고 다시 누르지 못하게 함 */
    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    /** 로그인 실패, 설정 저장 실패 안내. null 이면 배너 없음 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        if (!settingsDataStore.setDarkMode(enabled)) _message.value = SAVE_FAILED_MESSAGE
    }

    fun setAlightVibration(enabled: Boolean) = viewModelScope.launch {
        if (!settingsDataStore.setAlightVibration(enabled)) _message.value = SAVE_FAILED_MESSAGE
    }

    fun signIn(activityContext: Context) {
        if (_signingIn.value) return
        viewModelScope.launch {
            _signingIn.value = true
            _message.value = null
            authRepository.signInWithGoogle(activityContext).onFailure { _message.value = signInMessage(it) }
            _signingIn.value = false
        }
    }

    fun signOut() = viewModelScope.launch { authRepository.signOut() }

    fun dismissMessage() {
        _message.value = null
    }
}

private const val SAVE_FAILED_MESSAGE = "설정을 저장하지 못했어요. 다시 시도해 주세요"

/** 로그인 실패 안내 문구. 사용자가 직접 닫은 경우(Cancelled)는 안내하지 않음(null) */
internal fun signInMessage(error: Throwable): String? = when (error) {
    SignInError.Cancelled -> null
    SignInError.NoAccount -> "기기에 Google 계정이 없어요. 기기 설정에서 계정을 추가한 뒤 다시 시도해 주세요"
    SignInError.Network -> "인터넷 연결을 확인한 뒤 다시 시도해 주세요"
    else -> "로그인하지 못했어요. 다시 시도해 주세요"
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
    val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    // 시스템 설정에서 권한을 바꾸고 돌아오면 바로 반영되도록 화면이 다시 보일 때마다 권한 상태를 새로 읽음
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeCount++ }
    val permissions = remember(resumeCount) { PermissionStatus.read(context) }

    Scaffold(
        topBar = { BloomTopBar(title = "설정") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BloomDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
        ) {
            message?.let { NoticeBanner(it, actionLabel = "닫기", onAction = viewModel::dismissMessage) }

            SectionTitle("계정")
            val account = user
            if (account == null) {
                Text(
                    "로그인하면 즐겨찾기를 저장하고 맞춤 추천순을 볼 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = BloomTheme.colors.textSecondary,
                )
                BloomPrimaryButton(
                    if (signingIn) "로그인 중…" else "Google로 로그인",
                    onClick = { viewModel.signIn(context) },
                    enabled = !signingIn,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName ?: account.email, style = MaterialTheme.typography.titleMedium)
                        Text("Google 로그인 · ${account.email}", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
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
            SectionTitle("권한")
            StatusRow("위치", permissions.location, onOpen = { openAppDetailsSettings(context) })
            StatusRow(
                "알림",
                if (permissions.notificationsOn) "켬" else "꺼짐 · 잠금화면에 길안내가 보이지 않아요",
                onOpen = { GuideSystemSettings.openAppNotificationSettings(context) },
            )
            StatusRow(
                "잠금화면 알림",
                if (permissions.lockScreenHidden) "숨김 · 잠금을 풀어야 길안내가 보여요" else "보임",
                onOpen = { GuideSystemSettings.openLockScreenNotificationSettings(context) },
            )
            HorizontalDivider()
            SectionTitle("정보")
            Text(
                "데이터 출처",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onDataSourceClick).padding(vertical = 12.dp),
            )
        }
    }
}

/** 권한 상태 한 번 읽은 값. 위치는 정확한 위치까지 허용해야 길안내 가능(NFR-08) */
private data class PermissionStatus(val location: String, val notificationsOn: Boolean, val lockScreenHidden: Boolean) {
    companion object {
        fun read(context: Context): PermissionStatus {
            fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            val location = when {
                granted(Manifest.permission.ACCESS_FINE_LOCATION) -> "정확한 위치 허용"
                granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> "대략적인 위치만 허용 · 길안내에는 정확한 위치 필요"
                else -> "허용 안 함"
            }
            return PermissionStatus(
                location = location,
                notificationsOn = GuideSystemSettings.notificationsEnabled(context),
                lockScreenHidden = GuideSystemSettings.isLockScreenHidden(context),
            )
        }
    }
}

private fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = BloomTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
}

/** 권한 한 줄: 이름, 지금 상태, 시스템 설정 열기 */
@Composable
private fun StatusRow(label: String, status: String, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
        }
        BloomTextButton("설정 열기", onClick = onOpen)
    }
}

/**
 * 토글 옆에 '켬/끔' 글자를 함께 둔다(6.4 색 외 구분 수단).
 * 행 전체를 하나의 스위치로 묶어 TalkBack 이 설정 이름과 상태를 함께 읽게 한다.
 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(if (checked) "켬" else "끔", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = null) // 상태 변경은 행에서 한 번만 처리
    }
}
