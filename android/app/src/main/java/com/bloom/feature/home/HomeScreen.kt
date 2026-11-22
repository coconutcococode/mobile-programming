package com.bloom.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.bloom.core.designsystem.component.BannerTone
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.component.SheetStep
import com.bloom.core.designsystem.component.ThreeStepSheetScaffold
import com.bloom.core.designsystem.component.rememberThreeStepSheetState
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.map.KakaoMapView
import com.bloom.core.map.MapMarker
import com.bloom.core.model.Favorite
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.data.fake.SamplePlaces
import com.bloom.feature.place.PlaceDetailSheet
import com.bloom.feature.place.SaveFavoriteDialog

/**
 * S-01 홈(지도). 지도(배경) 위에 상단 검색창 + 하단 시트(즐겨찾기·최근 목적지)를 겹친다.
 * 상태: S-01-0 권한 전(서울시청 주변) · 내 위치 · S-01-5 오프라인(지도 대신 안내) · S-01-B1 안내 중 배너.
 * 시트는 3단(접힘 · 중간 · 펼침, 처음엔 중간, UI 설계서 2장). 탭을 옮겼다 돌아와도 높이 유지.
 * 지도 핀 → 장소 상세(S-09) ☆ → 즐겨찾기 이름 입력 → 저장 결과 배너.
 */
@Composable
fun HomeScreen(
    onPlaceSelected: (Place) -> Unit,
    onFavoriteClick: (Favorite) -> Unit,
    onGuideBannerClick: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onLoginRequired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val guideSession by viewModel.guideSession.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val savingPlace by viewModel.savingPlace.collectAsStateWithLifecycle()
    val recenterRequest by viewModel.recenterRequest.collectAsStateWithLifecycle()

    // S-01-D1 위치 권한 요청. 정확한 위치(FINE)를 받아야 안내가 가능(NFR-08) → 대략적 위치만이면 S-08-P1
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.locate() else onLocationPermissionDenied()
    }

    ThreeStepSheetScaffold(
        state = rememberThreeStepSheetState(SheetStep.Half),
        sheetContent = {
            HomeSheet(isLoggedIn = isLoggedIn, favorites = favorites, onFavoriteClick = onFavoriteClick)
        },
        // [내 위치 보기]는 시트 바로 위에 붙어 시트 높이를 따라 움직인다
        aboveSheet = {
            ExtendedFloatingActionButton(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
                icon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                text = { Text(if (locating) "현재 위치를 찾는 중…" else "내 위치 보기") },
                modifier = Modifier.padding(end = BloomDimens.ScreenHorizontal, bottom = 16.dp),
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isOnline) {
                KakaoMapView(
                    center = myLocation ?: LatLng.SeoulCityHall,
                    myLocation = myLocation,
                    recenterRequest = recenterRequest,
                    // TODO(트랙 A): 기본 장소 핀은 서버 연동 전까지 예시 장소(홍대 주변). 핀을 누르면 S-09
                    markers = SamplePlaces.all.map { MapMarker(it.id, it.location, selected = it.id == selectedPlace?.id) },
                    onMarkerClick = viewModel::onMapPlaceClick,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(BloomTheme.colors.skeleton), contentAlignment = Alignment.Center) {
                    Text("지도는 온라인에서 표시됩니다", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Column(
                Modifier.statusBarsPadding().padding(horizontal = BloomDimens.ScreenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // TODO(트랙 A): 검색 상태 화면(S-01 검색) 연결. 지금은 예시 장소로 경로 후보(S-02)를 연다.
                SearchBarStub(onClick = { onPlaceSelected(SamplePlaces.HongdaeStreet) })
                if (!isOnline) NoticeBanner("인터넷 연결이 필요합니다 · 즐겨찾기는 볼 수 있어요", tone = BannerTone.Warning)
                saveMessage?.let { NoticeBanner(it, actionLabel = "닫기", onAction = viewModel::dismissSaveMessage) }
                guideSession?.let { session ->
                    NoticeBanner(
                        message = "안내 중 · ${session.currentStep.title}",
                        actionLabel = "안내 보기",
                        onAction = onGuideBannerClick,
                    )
                }
            }
        }
    }

    selectedPlace?.let { place ->
        PlaceDetailSheet(
            place = place,
            isLoggedIn = isLoggedIn,
            onDirections = { viewModel.dismissPlace(); onPlaceSelected(it) },
            onSave = { viewModel.startSaving(it); viewModel.dismissPlace() },
            onLoginClick = { viewModel.dismissPlace(); onLoginRequired() },
            onDismiss = viewModel::dismissPlace,
        )
    }

    savingPlace?.let { place ->
        SaveFavoriteDialog(
            onSave = { alias -> viewModel.save(place, alias) },
            onDismiss = viewModel::cancelSaving,
        )
    }
}

/** 하단 시트: 즐겨찾기 칩 · 최근 목적지. TODO(트랙 A): 최근 목적지 20개(Room) */
@Composable
private fun HomeSheet(
    isLoggedIn: Boolean,
    favorites: List<Favorite>,
    onFavoriteClick: (Favorite) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = BloomDimens.ScreenHorizontal).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("즐겨찾기", style = MaterialTheme.typography.titleMedium)
        if (isLoggedIn) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                favorites.forEach { fav ->
                    AssistChip(
                        onClick = { onFavoriteClick(fav) },
                        label = { Text(fav.alias) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    )
                }
            }
        } else {
            Text("로그인하면 즐겨찾기를 여기서 바로 쓸 수 있어요", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
        }
        Text("최근 목적지", style = MaterialTheme.typography.titleMedium)
        Text("최근에 찾은 곳이 여기에 보여요", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
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
                "장소·주소·역 검색",
                style = MaterialTheme.typography.bodyMedium,
                color = BloomTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
