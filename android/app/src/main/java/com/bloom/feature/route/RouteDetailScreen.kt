package com.bloom.feature.route

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.bloom.core.map.KakaoMapView
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
 * 상단 지도에 구간별 색 경로선. TODO(트랙 B): 즐겨찾기 별 — 재검색 조건(#14) 이후.
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
        if (candidate == null) {
            ErrorState(
                message = "경로 정보가 만료됐어요",
                description = "경로를 다시 검색해 주세요",
                actionLabel = "돌아가기",
                onAction = onBack,
                modifier = Modifier.padding(padding).padding(horizontal = BloomDimens.ScreenHorizontal),
            )
            return@Scaffold
        }
        // 화면이 낮으면(가로 모드·작은 창) 지도가 높이를 다 차지해 아래 정보가 안 보인다.
        // 지도는 남은 높이의 40%까지만 쓰고(지도 위를 끌면 스크롤이 아니라 지도가 움직이므로), 전체를 세로로 스크롤한다.
        // LazyColumn 은 지도가 화면 밖으로 나가면 지도를 해제했다 다시 만들어서 일반 스크롤을 쓴다(구간 수가 적어 부담 없음)
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val mapHeight = minOf(RouteMapHeight, maxHeight * RouteMapMaxHeightRatio)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                RouteMap(candidate, Modifier.fillMaxWidth().height(mapHeight))
                RouteSummary(candidate, hasActiveGuide, onOpenActiveGuide)
            }
        }
    }
}

/** 상단 지도: 구간별 색 경로선 + 출발점 + 목적지 핀, 처음 열 때 경로 전체가 보이게 맞춘다(UI 설계서 3.1 S-03). */
@Composable
private fun RouteMap(candidate: RouteCandidate, modifier: Modifier) {
    val segments = remember(candidate) { routeMapSegments(candidate) }
    val fitPoints = remember(candidate) { routeFitPoints(candidate) }
    val markers = remember(candidate) { listOf(routeDestinationMarker(candidate)) }
    KakaoMapView(
        center = remember(candidate) { routeCenter(candidate) },
        routeSegments = segments,
        fitPoints = fitPoints,
        markers = markers,
        myLocation = remember(candidate) { routeOrigin(candidate) }, // 출발 = 현재 위치(S-02 '출발 현재 위치')
        modifier = modifier,
    )
}

@Composable
private fun RouteSummary(candidate: RouteCandidate, hasActiveGuide: Boolean, onOpenActiveGuide: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = BloomDimens.ScreenHorizontal).padding(vertical = BloomDimens.CardGap),
        verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
    ) {
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            candidate.segments.forEach { seg ->
                Text("• ${seg.label} ${seg.durationMinutes}분  ${seg.startName} → ${seg.endName}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 지도 높이: 360×760dp 기준 화면 위쪽 약 1/3(UI 설계서 S-03) */
private val RouteMapHeight = 240.dp

/** 화면이 낮을 때 지도가 차지할 수 있는 최대 비율 */
private const val RouteMapMaxHeightRatio = 0.4f

/**
 * 안드로이드 13부터는 알림 권한이 있어야 잠금화면 안내(S-05)가 보인다.
 * 한 번 거절한 뒤에도 묻지만, 시스템이 더 묻지 않기로 한 상태면 창 없이 바로 결과가 와서 안내가 그대로 시작된다.
 */
private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
