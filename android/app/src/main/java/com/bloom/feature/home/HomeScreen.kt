package com.bloom.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloom.core.designsystem.component.BloomTextButton
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.model.Place
import com.bloom.data.fake.SamplePlaces
import com.bloom.feature.place.PlaceDetailSheet

/**
 * S-01 홈(지도). 지도(배경) 위에 상단 검색창 + 하단 시트(즐겨찾기·최근 목적지)를 겹친다.
 * TODO(트랙 A): Kakao 지도 연결, 검색 상태(자동완성 10개·0.3초), 최근 목적지 20개, 바텀 시트 3단, 상태 S-01-0~5.
 */
@Composable
fun HomeScreen(
    onPlaceSelected: (Place) -> Unit,
    onGuideBannerClick: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onLoginRequired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val guideSession by viewModel.guideSession.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        MapPlaceholder(
            modifier = Modifier.fillMaxSize(),
            onPinClick = { viewModel.onMapPlaceClick(SamplePlaces.BrewingLab.id) },
        )

        Column(
            Modifier.statusBarsPadding().padding(horizontal = BloomDimens.ScreenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBarStub(onClick = { onPlaceSelected(SamplePlaces.HongdaeStreet) })
            guideSession?.let { session ->
                NoticeBanner(
                    message = "안내 중 · ${session.currentStep.title}",
                    actionLabel = "안내 보기",
                    onAction = onGuideBannerClick,
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(BloomDimens.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("즐겨찾기", style = MaterialTheme.typography.titleMedium)
                if (isLoggedIn) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        favorites.forEach { fav ->
                            AssistChip(
                                onClick = { SamplePlaces.all.firstOrNull { it.id == fav.placeId }?.let(onPlaceSelected) },
                                label = { Text(fav.alias) },
                                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            )
                        }
                    }
                } else {
                    Text("로그인하면 즐겨찾기를 여기서 바로 쓸 수 있어요", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
                }
                Text("최근 목적지", style = MaterialTheme.typography.titleMedium)
                Text("TODO: 최근 목적지 최대 20개", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
                BloomTextButton("(예시) 위치 권한 거절 → S-08-P1", onLocationPermissionDenied)
            }
        }
    }

    selectedPlace?.let { place ->
        PlaceDetailSheet(
            place = place,
            isLoggedIn = isLoggedIn,
            onDirections = { viewModel.dismissPlace(); onPlaceSelected(it) },
            onSave = { viewModel.save(it); viewModel.dismissPlace() },
            onLoginClick = { viewModel.dismissPlace(); onLoginRequired() },
            onDismiss = viewModel::dismissPlace,
        )
    }
}

@Composable
private fun SearchBarStub(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(BloomDimens.CardRadius),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = BloomTheme.colors.textSecondary)
            Text(
                "장소·주소·역 검색  (예시: 탭하면 S-02)",
                style = MaterialTheme.typography.bodyMedium,
                color = BloomTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** TODO(트랙 A): Kakao Maps SDK(MapView)로 교체. 핀 터치 영역 48×48dp. */
@Composable
private fun MapPlaceholder(modifier: Modifier, onPinClick: () -> Unit) {
    Box(modifier.background(BloomTheme.colors.skeleton), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("지도 영역 (Kakao Maps SDK)", style = MaterialTheme.typography.bodyMedium, color = BloomTheme.colors.textSecondary)
            BloomTextButton("(예시) 지도 핀 탭 → S-09", onPinClick)
        }
    }
}
