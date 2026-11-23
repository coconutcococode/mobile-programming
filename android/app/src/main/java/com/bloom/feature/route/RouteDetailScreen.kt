package com.bloom.feature.route

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.ErrorState
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.RouteCandidate
import com.bloom.core.navigation.RouteDetailRoute
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    routeRepository: RouteRepository,
    private val guideSessionRepository: GuideSessionRepository,
) : ViewModel() {

    /** 경로는 메모리에만 있으므로 프로세스가 재시작되면 null 일 수 있다. */
    val candidate: RouteCandidate? =
        routeRepository.findCandidate(savedStateHandle.toRoute<RouteDetailRoute>().candidateId)

    /** S-03-B1: 다른 안내가 진행 중이면 '안내 시작' 비활성 */
    val hasActiveGuide: StateFlow<Boolean> = guideSessionRepository.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, guideSessionRepository.session.value != null)

    fun startGuide(): Boolean {
        val route = candidate ?: return false
        if (guideSessionRepository.session.value != null) return false
        guideSessionRepository.start(route)
        return true
    }
}

/**
 * S-03 경로 상세. '안내 시작' 때 알림 권한이 없으면 먼저 묻는다(S-03-D1).
 * TODO(트랙 B): 지도 위 경로선(지도 부품 나온 뒤), 즐겨찾기 별.
 */
@Composable
fun RouteDetailScreen(
    onBack: () -> Unit,
    onGuideStarted: () -> Unit,
    onOpenActiveGuide: () -> Unit,
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val hasActiveGuide by viewModel.hasActiveGuide.collectAsStateWithLifecycle()
    val candidate = viewModel.candidate
    val context = LocalContext.current
    val startGuide = { if (viewModel.startGuide()) onGuideStarted() }
    // S-03-D1: 허용·거절과 상관없이 안내는 시작한다. 거절하면 안내 화면 상단에 알림 꺼짐 배너(S-04-B1)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startGuide() }

    Scaffold(
        topBar = { BloomTopBar(title = "경로 상세", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (candidate != null) {
                // 하단 고정 주 버튼 56dp (5.1 엄지 영역)
                BloomPrimaryButton(
                    text = "안내 시작",
                    enabled = !hasActiveGuide,
                    large = true,
                    onClick = {
                        if (needsNotificationPermission(context)) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startGuide()
                        }
                    },
                    modifier = Modifier.padding(BloomDimens.ScreenHorizontal),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = BloomDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
        ) {
            if (candidate == null) {
                ErrorState(message = "경로 정보가 만료됐어요", description = "경로를 다시 검색해 주세요", actionLabel = "돌아가기", onAction = onBack)
                return@Column
            }
            if (hasActiveGuide) {
                NoticeBanner("이미 진행 중인 안내가 있어요", actionLabel = "진행 중인 안내 보기", onAction = onOpenActiveGuide)
            }
            Text("${candidate.totalMinutes}분 · ${candidate.arrivalTime} 도착", style = MaterialTheme.typography.titleLarge)
            Text(
                "환승 ${candidate.transferCount}회 · 도보 ${candidate.walkMeters}m",
                style = MaterialTheme.typography.bodySmall,
                color = BloomTheme.colors.textSecondary,
            )
            SegmentBar(candidate.segments)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidate.segments) { seg ->
                    Text("• ${seg.label} ${seg.durationMinutes}분  ${seg.startName} → ${seg.endName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * 안드로이드 13부터는 알림 권한이 있어야 잠금화면 안내(S-05)가 보인다.
 * 한 번 거절한 뒤에도 묻지만, 시스템이 더 묻지 않기로 한 상태면 창 없이 바로 결과가 와서 안내가 그대로 시작된다.
 */
private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
