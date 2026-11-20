package com.bloom.feature.route

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloom.core.designsystem.UiState
import com.bloom.core.designsystem.component.BloomChip
import com.bloom.core.designsystem.component.BloomTopBar
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.component.ErrorState
import com.bloom.core.designsystem.component.SkeletonList
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.designsystem.toUserMessage
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.RouteSortType
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import com.bloom.data.fake.SamplePlaces
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** S-02 경로 후보 목록 (상태: 4.1 S-02-1~4). */
@Composable
fun RouteCandidatesScreen(
    onBack: () -> Unit,
    onCandidateClick: (RouteCandidate) -> Unit,
    viewModel: RouteCandidatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    RouteCandidatesContent(
        destinationName = viewModel.destinationName,
        uiState = uiState,
        sort = sort,
        onBack = onBack,
        onSortChange = viewModel::onSortChange,
        onRetry = viewModel::load,
        onCandidateClick = onCandidateClick,
    )
}

/** 상태만 받아 그리는 부분 — Preview·UI 테스트는 이 함수로 한다. */
@Composable
internal fun RouteCandidatesContent(
    destinationName: String,
    uiState: UiState<List<RouteCandidate>>,
    sort: RouteSortType,
    onBack: () -> Unit,
    onSortChange: (RouteSortType) -> Unit,
    onRetry: () -> Unit,
    onCandidateClick: (RouteCandidate) -> Unit,
) {
    Scaffold(
        topBar = { BloomTopBar(title = "경로 후보", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = BloomDimens.ScreenHorizontal)) {
            Text("출발  현재 위치", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
            Text("도착  $destinationName", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteSortType.entries.forEach { type ->
                    BloomChip(label = type.label, selected = type == sort, onClick = { onSortChange(type) })
                }
            }
            when (uiState) {
                UiState.Loading -> SkeletonList(rows = 4)
                UiState.Empty -> EmptyState(
                    title = "검색 결과가 없습니다",
                    description = "도착지 이름을 바꿔 다시 검색해 보세요",
                )
                is UiState.Error -> ErrorState(
                    message = "경로를 불러올 수 없습니다",
                    description = uiState.error.toUserMessage(),
                    onAction = onRetry,
                    secondaryActionLabel = "카카오맵 길찾기",
                    onSecondaryAction = { /* TODO(트랙 A): 카카오맵 앱 연결 */ },
                )
                is UiState.Success -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(BloomDimens.CardGap),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(uiState.data, key = { it.id }) { candidate ->
                        RouteCandidateCard(candidate, onClick = { onCandidateClick(candidate) })
                    }
                }
            }
        }
    }
}

/** 후보 카드: 소요 시간(가장 크게) → 구간 막대 → 환승·도보 거리 (5.4 근접성). */
@Composable
private fun RouteCandidateCard(candidate: RouteCandidate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(BloomDimens.CardRadius),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${candidate.totalMinutes}분", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text("${candidate.arrivalTime.format(TimeFormat)} 도착", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
            }
            SegmentBar(candidate.segments)
            Text(
                "환승 ${candidate.transferCount}회 · 도보 ${candidate.walkMeters}m",
                style = MaterialTheme.typography.bodySmall,
                color = BloomTheme.colors.textSecondary,
            )
        }
    }
}

/** 구간 막대. 색 + 글자('도보 6', '7016 12', '2호선 6')로 구분한다(6.4). */
@Composable
internal fun SegmentBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { seg ->
            val (bg, fg) = when (seg.mode) {
                TransitMode.WALK -> BloomTheme.colors.segmentWalk to BloomTheme.colors.onSegmentWalk
                TransitMode.BUS -> BloomTheme.colors.segmentBus to BloomTheme.colors.onSegmentBus
                TransitMode.SUBWAY -> BloomTheme.colors.segmentSubway to BloomTheme.colors.onSegmentSubway
            }
            Box(
                Modifier.weight(seg.durationMinutes.coerceAtLeast(1).toFloat()).fillMaxSize().background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${seg.label} ${seg.durationMinutes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteCandidatesPreview() {
    val walk = Segment(TransitMode.WALK, 6, "도보", "", "이대역", distanceMeters = 380)
    val subway = Segment(TransitMode.SUBWAY, 6, "2호선", "이대역", "홍대입구역", stopCount = 2)
    val candidate = RouteCandidate("1", SamplePlaces.HongdaeStreet, 18, LocalTime.of(14, 20), 0, 780, listOf(walk, subway, walk))
    BloomTheme {
        RouteCandidatesContent(
            destinationName = "홍대 걷고싶은거리",
            uiState = UiState.Success(listOf(candidate, candidate.copy(id = "2", totalMinutes = 21))),
            sort = RouteSortType.FASTEST,
            onBack = {}, onSortChange = {}, onRetry = {}, onCandidateClick = {},
        )
    }
}
