package com.bloom.feature.guide

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomTextButton
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.data.repository.GuideSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val guideSessionRepository: GuideSessionRepository,
) : ViewModel() {
    val session: StateFlow<GuideSession?> = guideSessionRepository.session
    fun next() = guideSessionRepository.next()
    fun board() = guideSessionRepository.board()
    fun alight() = guideSessionRepository.alight()
    fun moveToStep(index: Int) = guideSessionRepository.moveToStep(index)
    fun end() = guideSessionRepository.end()
}

/**
 * S-04 안내 진행(앱 내). 하단 탭 숨김. 뒤로가기 → 홈(안내는 계속, 홈에 '안내 중' 배너).
 * TODO(트랙 B): 실시간 도착 카드(기준 시각), 현재 역 고치기, 배너 S-04-B1~B3, 상태 S-04-1~5, 알림 표시 설정 안내 S-04-S1.
 */
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session != null) {
        if (session != null) GuideService.start(context) // 권한이 없으면 시작하지 않음 — 권한 흐름은 S-01-D1·S-03-D1
    }

    Scaffold(
        topBar = { BloomTopBar(title = session?.route?.destination?.name?.let { "${it}까지" } ?: "안내", onBack = onBack) },
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
                    Text("현재 단계", style = MaterialTheme.typography.labelMedium, color = BloomTheme.colors.textSecondary)
                    Text(current.currentStep.title, style = MaterialTheme.typography.headlineSmall)
                    Text(GuideNotification.statusText(current), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("TODO: 실시간 도착 정보 카드 (트랙 B)", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)

            Spacer(Modifier.weight(1f))

            BloomPrimaryButton(
                text = when (current.currentStep.type) {
                    GuideStepType.WAIT_BOARDING -> "승차"
                    GuideStepType.WAIT_ALIGHTING -> "하차 완료"
                    GuideStepType.ARRIVED -> "안내 종료"
                    else -> "다음 단계"
                },
                large = true,
                onClick = {
                    when {
                        current.isLastStep -> showEndDialog = true
                        current.currentStep.type == GuideStepType.WAIT_BOARDING -> viewModel.board()
                        current.currentStep.type == GuideStepType.WAIT_ALIGHTING -> viewModel.alight()
                        else -> viewModel.next()
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BloomTextButton("다음 단계로 넘기기", onClick = viewModel::next)
                BloomTextButton("안내 종료", onClick = { showEndDialog = true }) // 작게, 오른쪽(오탭 방지)
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
