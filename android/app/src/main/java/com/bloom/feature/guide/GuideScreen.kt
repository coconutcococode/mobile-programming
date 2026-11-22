package com.bloom.feature.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomTextButton
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.BannerTone
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.GuideSession
import com.bloom.data.repository.GuideSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val guideSessionRepository: GuideSessionRepository,
    private val locationTracker: LocationTracker,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    val session: StateFlow<GuideSession?> = guideSessionRepository.session

    private val _rerouting = MutableStateFlow(false)
    val rerouting: StateFlow<Boolean> = _rerouting.asStateFlow()

    /** 화면 아래 잠깐 보여 줄 안내(재탐색 실패 등). 보여 준 뒤 [messageShown]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun next() = guideSessionRepository.next()
    fun board() = guideSessionRepository.board()
    fun alight() = guideSessionRepository.alight()
    fun moveToStep(index: Int) = guideSessionRepository.moveToStep(index)
    fun correctStation(passedStops: Int) = guideSessionRepository.correctStation(passedStops)
    fun end() = guideSessionRepository.end()
    fun messageShown() { _message.value = null }

    /** S-04-B3 재탐색: 지금 위치에서 같은 목적지로 가장 빠른 경로를 다시 찾아 안내를 바꾼다(FR-06). */
    fun reroute() {
        if (_rerouting.value) return
        _rerouting.value = true
        viewModelScope.launch {
            val here = locationTracker.currentLocation()
            val replaced = here != null && guideSessionRepository.reroute(here)
            _rerouting.value = false
            if (!replaced) {
                _message.value = if (here == null) "현재 위치를 확인하지 못했어요" else "새 경로를 찾지 못했어요 · 잠시 후 다시 시도해 주세요"
            }
        }
    }

    /** S-04-S1은 한 번만: 아직 안 보여 줬으면 true 를 돌려주고 보여 준 것으로 기록한다. */
    suspend fun takeLockScreenNotice(): Boolean {
        if (settingsDataStore.lockScreenNoticeShown.first()) return false
        settingsDataStore.setLockScreenNoticeShown()
        return true
    }
}

/**
 * S-04 안내 진행(앱 내). 하단 탭 숨김. 뒤로가기 → 홈(안내는 계속, 홈에 '안내 중' 배너).
 * 상단 배너: 경로 이탈(B3) · 위치 부정확(B2) · 알림 꺼짐(B1). 지하철 구간은 '현재 역 고치기'(UC-01 A9).
 * 첫 안내 때 잠금화면에 알림이 숨겨지는 설정이면 알림 표시 설정 안내(S-04-S1)를 한 번 보여 준다.
 * TODO(트랙 B): 실시간 도착 카드(기준 시각)와 상태 S-04-1~5 — 서버 도착 정보 연결 후.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val rerouting by viewModel.rerouting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEndDialog by remember { mutableStateOf(false) }
    var showStationDialog by remember { mutableStateOf(false) }
    var showLockScreenNotice by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // 설정 화면에서 알림을 켜고 돌아오면 배너가 바로 사라지도록, 화면에 돌아올 때마다 다시 확인한다.
    var notificationsEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = GuideSystemSettings.notificationsEnabled(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(session != null) {
        if (session == null) return@LaunchedEffect
        GuideService.start(context) // 권한이 없으면 시작하지 않음 — 권한 흐름은 S-01-D1·S-03-D1
        // 알림 자체가 꺼져 있으면 B1 배너가 먼저다. 켜져 있는데 잠금화면에서 숨겨지면 S-04-S1 (1회)
        if (GuideSystemSettings.notificationsEnabled(context) &&
            GuideSystemSettings.isLockScreenHidden(context) &&
            viewModel.takeLockScreenNotice()
        ) {
            showLockScreenNotice = true
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { BloomTopBar(title = session?.route?.destination?.name?.let { "${it}까지" } ?: "안내", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = session
        if (current == null) {
            EmptyState(
                title = "진행 중인 안내가 없어요",
                actionLabel = "홈으로",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = BloomDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
        ) {
            guideBanners(current, notificationsEnabled).forEach { banner ->
                NoticeBanner(
                    message = banner.message,
                    tone = BannerTone.Warning, // 색만이 아니라 ⚠ 아이콘·문구로도 알린다
                    actionLabel = banner.actionLabel,
                    onAction = {
                        when (banner) {
                            GuideBanner.OFF_ROUTE -> viewModel.reroute()
                            GuideBanner.LOCATION_INACCURATE -> viewModel.next()
                            GuideBanner.NOTIFICATIONS_OFF -> GuideSystemSettings.openAppNotificationSettings(context)
                        }
                    },
                )
            }
            Text(
                "도착 예정 ${current.route.arrivalTime} · ${current.currentStepIndex + 1} / ${current.steps.size}단계",
                style = MaterialTheme.typography.bodySmall,
                color = BloomTheme.colors.textSecondary,
            )
            StepTrack(current, onStepClick = viewModel::moveToStep)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(BloomDimens.CardRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "현재 단계 · ${stepTypeLabel(current.currentStep.type)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = BloomTheme.colors.textSecondary,
                    )
                    Text(current.currentStep.title, style = MaterialTheme.typography.headlineSmall)
                    Text(GuideNotification.statusText(current), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("TODO: 실시간 도착 정보 카드 (트랙 B)", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)

            if (canCorrectStation(current)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "표시된 역이 실제와 다른가요?",
                        style = MaterialTheme.typography.bodySmall,
                        color = BloomTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    BloomTextButton("현재 역 고치기", onClick = { showStationDialog = true })
                }
            }

            Spacer(Modifier.weight(1f))

            val action = primaryAction(current)
            primaryHint(current)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = BloomTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BloomPrimaryButton(
                text = if (action == GuidePrimaryAction.REROUTE && rerouting) "경로를 찾는 중…" else action.label,
                enabled = !(action == GuidePrimaryAction.REROUTE && rerouting),
                large = true,
                onClick = {
                    when (action) {
                        GuidePrimaryAction.END -> showEndDialog = true
                        GuidePrimaryAction.REROUTE -> viewModel.reroute()
                        GuidePrimaryAction.BOARD -> viewModel.board()
                        GuidePrimaryAction.ALIGHT -> viewModel.alight()
                        GuidePrimaryAction.NEXT -> viewModel.next()
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BloomTextButton("다음 단계로 넘기기", onClick = viewModel::next)
                BloomTextButton("안내 종료", onClick = { showEndDialog = true }) // 작게, 오른쪽(오탭 방지)
            }
        }
    }

    // 현재 역 고치기(UC-01 A9): 지금 있는 역을 고르면 남은 역 추정을 다시 계산한다
    val stationSession = session
    val stationSegment = stationSession?.currentStep?.segment
    if (showStationDialog && stationSession != null && stationSegment != null && canCorrectStation(stationSession)) {
        val estimated = estimatedPassedStops(stationSession)
        AlertDialog(
            onDismissRequest = { showStationDialog = false },
            title = { Text("지금 있는 역을 골라 주세요") },
            text = {
                Column {
                    stationChoices(stationSegment).forEachIndexed { passed, name ->
                        val isEstimated = passed == estimated
                        Text(
                            if (isEstimated) "$name · 지금 표시 중" else name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isEstimated) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.correctStation(passed)
                                    showStationDialog = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showStationDialog = false }) { Text("취소") } },
        )
    }

    // S-04-S1 알림 표시 설정 안내. 뒤로가기·바깥 탭은 시트만 닫고 안내는 계속(진동과 S-04 화면)
    if (showLockScreenNotice) {
        ModalBottomSheet(onDismissRequest = { showLockScreenNotice = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = BloomDimens.ScreenHorizontal).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("잠금화면에 안내가 보이지 않아요", style = MaterialTheme.typography.titleLarge)
                Text(
                    "지금 설정에서는 잠금화면에 알림이 숨겨져요. 설정에서 잠금화면 알림을 켜면 잠금 해제 없이 단계와 버튼을 볼 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "켜지 않아도 하차 진동과 이 화면으로 안내는 계속돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BloomTheme.colors.textSecondary,
                )
                BloomPrimaryButton(
                    text = "설정 열기",
                    large = true,
                    onClick = {
                        showLockScreenNotice = false
                        GuideSystemSettings.openLockScreenNotificationSettings(context)
                    },
                )
                BloomTextButton("닫기", onClick = { showLockScreenNotice = false }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    // S-04-D1 안내 종료 확인
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("안내를 종료할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    viewModel.end()
                    GuideService.stop(context)
                    onBack()
                }) { Text("종료") }
            },
            dismissButton = { TextButton(onClick = { showEndDialog = false }) { Text("계속 안내") } },
        )
    }
}

/** 가로로 미는 단계 트랙 — 현재 단계는 항상 왼쪽에서 두 번째(지난 1 · 현재 1 · 다음 1, 5.3). */
@Composable
private fun StepTrack(session: GuideSession, onStepClick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(session.currentStepIndex) {
        listState.animateScrollToItem((session.currentStepIndex - 1).coerceAtLeast(0))
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(session.steps) { index, step ->
            val isCurrent = index == session.currentStepIndex
            Card(
                onClick = { onStepClick(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.width(112.dp),
            ) {
                Text(step.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.padding(10.dp))
            }
        }
    }
}
