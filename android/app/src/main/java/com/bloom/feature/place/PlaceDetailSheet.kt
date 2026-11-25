package com.bloom.feature.place

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bloom.core.designsystem.UiState
import com.bloom.core.designsystem.component.BannerTone
import com.bloom.core.designsystem.component.BloomPrimaryButton
import com.bloom.core.designsystem.component.BloomSecondaryButton
import com.bloom.core.designsystem.component.BloomTextButton
import com.bloom.core.designsystem.component.ErrorState
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.component.SkeletonBlock
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.designsystem.toUserMessage
import com.bloom.core.model.Place

/**
 * S-09 장소 상세(시트). S-01 지도 핀 / S-06 지도 핀·목록 행에서 열린다.
 * TODO(트랙 A): 열 때마다 실시간 재조회(S-09-2 로딩 / S-09-4 오류), 카카오맵 '자세히 보기' 링크, 추천 근거 칩.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailSheet(
    state: UiState<Place>,
    isLoggedIn: Boolean,
    onDirections: (Place) -> Unit,
    onSave: (Place) -> Unit,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
) {
    var showLoginDialog by remember { mutableStateOf(false) }

    // 내용이 짧아 반만 펼칠 필요가 없음: 처음부터 전부 펼쳐서 길찾기 · 저장 버튼이 화면 밖으로 밀리지 않게
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            // 큰 글자 설정이나 가로 화면에서 내용이 시트 높이를 넘어도 아래 버튼까지 닿도록 스크롤
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = BloomDimens.ScreenHorizontal).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                UiState.Loading -> PlaceSkeleton()
                // 장소를 못 찾음(Empty)도 사용자에게는 같은 '불러오지 못함'으로 안내
                UiState.Empty -> ErrorState(message = "장소 정보를 불러오지 못했어요", onAction = onRetry)
                is UiState.Error -> ErrorState(message = state.error.toUserMessage(), onAction = onRetry)
                is UiState.Success -> PlaceContent(
                    place = state.data,
                    onDirections = onDirections,
                    onSave = { if (isLoggedIn) onSave(it) else showLoginDialog = true },
                )
            }
        }
    }

    // S-07-D3 로그인 안내
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("로그인이 필요해요") },
            text = { Text("즐겨찾기는 Google 계정에 저장돼요.") },
            confirmButton = {
                TextButton(onClick = { showLoginDialog = false; onLoginClick() }) { Text("로그인하러 가기") }
            },
            dismissButton = { TextButton(onClick = { showLoginDialog = false }) { Text("취소") } },
        )
    }
}

/** S-09-2 불러오는 중: 이름, 정보 두 줄, 버튼 자리. TalkBack 에는 한 문장으로 읽힘 */
@Composable
private fun PlaceSkeleton() {
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "장소 정보를 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBlock(Modifier.width(180.dp).height(28.dp))
        SkeletonBlock(Modifier.width(140.dp).height(16.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(20.dp))
        SkeletonBlock(Modifier.padding(top = 12.dp).fillMaxWidth().height(BloomDimens.PrimaryButtonHeight))
    }
}

@Composable
private fun PlaceContent(place: Place, onDirections: (Place) -> Unit, onSave: (Place) -> Unit) {
    val context = LocalContext.current
    var linkFailed by remember(place.id) { mutableStateOf(false) }

    Text(place.name, style = MaterialTheme.typography.titleLarge)
    val meta = listOfNotNull(
        place.category?.label,
        place.distanceMeters?.let { "${it}m" },
        place.walkMinutes?.let { "도보 ${it}분" },
    ).joinToString(" · ")
    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
    // 추천 이유가 장소 데이터에 있을 때만 표시(추천 기준 · 이유 생성 방식은 #28 에서 결정)
    place.recommendReason?.let { RecommendReasonChip(it) }
    Text("주소  ${place.address}", style = MaterialTheme.typography.bodyMedium)
    place.phone?.let { Text("전화  $it", style = MaterialTheme.typography.bodyMedium) }
    place.placeUrl?.let { url ->
        BloomTextButton("카카오맵에서 자세히 보기", onClick = { linkFailed = !openPlacePage(context, url) })
    }
    if (linkFailed) NoticeBanner("카카오맵 장소 페이지를 열 수 없어요", tone = BannerTone.Warning)
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // '길찾기'(212dp)를 '저장'(100dp)보다 크게 — 5.2 피츠의 법칙
        BloomPrimaryButton("길찾기", onClick = { onDirections(place) }, modifier = Modifier.width(212.dp))
        BloomSecondaryButton("☆ 저장", onClick = { onSave(place) }, modifier = Modifier.width(100.dp))
    }
}

/** 추천 근거 칩. 누르는 기능이 없는 표시용이라 버튼이 아닌 글자 칸 */
@Composable
private fun RecommendReasonChip(reason: String) {
    Surface(
        shape = RoundedCornerShape(BloomDimens.ChipRadius),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.semantics { contentDescription = "추천 이유: $reason" },
    ) {
        Text(
            "추천 · $reason",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * 카카오맵 장소 페이지 열기(place_url 은 저장 허용 값). 받을 앱(브라우저 등)은 Android 가 고름.
 * 위치 같은 민감한 정보가 없는 공개 주소라 받을 앱을 지정하지 않음. 열 곳이 없으면 false
 */
private fun openPlacePage(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
