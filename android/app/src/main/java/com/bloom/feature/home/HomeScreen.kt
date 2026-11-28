package com.bloom.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
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
import com.bloom.core.location.LocationPermissions
import com.bloom.core.map.KakaoMapView
import com.bloom.core.map.MapMarker
import com.bloom.core.model.Favorite
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.RecentDestination
import com.bloom.data.fake.SamplePlaces
import com.bloom.feature.place.PlaceDetailSheet
import com.bloom.feature.place.SaveFavoriteDialog

/**
 * S-01 홈(지도). 지도(배경) 위에 상단 검색창 + 하단 시트(즐겨찾기·최근 목적지)를 겹친다.
 * 상태: S-01-0 권한 전(서울시청 주변) · 내 위치 · S-01-5 오프라인(지도 대신 안내) · S-01-B1 안내 중 배너 · S-01-B2 이전 안내 중단 배너.
 * 시트는 3단(접힘 · 중간 · 펼침, 처음엔 중간, UI 설계서 2장). 탭을 옮겼다 돌아와도 높이 유지.
 * 지도 핀 → 장소 상세(S-09) ☆ → 즐겨찾기 이름 입력 → 저장 결과 배너.
 */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onFavoriteClick: (Favorite) -> Unit,
    onRecentClick: (RecentDestination) -> Unit,
    onGuideBannerClick: () -> Unit,
    onResumeInterrupted: (ResumeTarget) -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onLoginRequired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val guideSession by viewModel.guideSession.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
    val selectedPlaceId by viewModel.selectedPlaceId.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val interruptedTarget by viewModel.interruptedTarget.collectAsStateWithLifecycle()
    val savingPlace by viewModel.savingPlace.collectAsStateWithLifecycle()
    val recenterRequest by viewModel.recenterRequest.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    // 권한 창이 떠 있는 동안 고른 최근 목적지. null 이면 [내 위치 보기]에서 연 권한 창
    // rememberSaveable: 권한 창이 떠 있는 동안 화면이 다시 만들어져도(회전 등) 고른 목적지 유지
    var pendingRecent by rememberSaveable(stateSaver = RecentDestinationSaver) { mutableStateOf<RecentDestination?>(null) }
    // 권한 창이 떠 있는 동안 [다시 안내]로 고른 목적지. 화면이 다시 만들어져도 목적지 유지
    var pendingResume by rememberSaveable(stateSaver = ResumeTargetSaver) { mutableStateOf<ResumeTarget?>(null) }

    // S-01-D1 위치 권한 요청. 정확한 위치(FINE)를 받아야 안내가 가능(NFR-08) → 대략적 위치만이면 S-08-P1
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val recent = pendingRecent
        val resume = pendingResume
        pendingRecent = null
        pendingResume = null
        when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] != true -> onLocationPermissionDenied()
            recent != null -> onRecentClick(recent)
            resume != null -> onResumeInterrupted(resume)
            else -> viewModel.locate()
        }
    }

    // 최근 목적지: 목록 맨 위로 올림 → 위치 권한 확인(S-01-D1) → S-02 (검색 화면과 같은 순서)
    val openRecent: (RecentDestination) -> Unit = { recent ->
        viewModel.reuseRecent(recent)
        if (viewModel.hasLocationPermission()) {
            onRecentClick(recent)
        } else {
            pendingRecent = recent
            permissionLauncher.launch(LocationPermissions.REQUIRED)
        }
    }

    // S-01-B2 [다시 안내]: 위치 권한 확인 후 같은 목적지로 S-02 (최근 목적지와 같은 순서)
    val resumeInterrupted: (ResumeTarget) -> Unit = { target ->
        if (viewModel.hasLocationPermission()) {
            onResumeInterrupted(target)
        } else {
            pendingResume = target
            permissionLauncher.launch(LocationPermissions.REQUIRED)
        }
    }

    ThreeStepSheetScaffold(
        state = rememberThreeStepSheetState(SheetStep.Half),
        sheetContent = {
            HomeSheet(
                isLoggedIn = isLoggedIn,
                favorites = favorites,
                recents = recents,
                onFavoriteClick = onFavoriteClick,
                onRecentClick = openRecent,
            )
        },
        // [내 위치 보기]는 시트 바로 위에 붙어 시트 높이를 따라 움직인다
        aboveSheet = {
            ExtendedFloatingActionButton(
                onClick = { permissionLauncher.launch(LocationPermissions.REQUIRED) },
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
                    markers = SamplePlaces.all.map { MapMarker(it.id, it.location, selected = it.id == selectedPlaceId) },
                    onMarkerClick = viewModel::onMapPlaceClick,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 지도 칸 아래쪽은 시트가 덮으므로 글자는 위쪽 30% 지점에 배치
                Box(Modifier.fillMaxSize().background(BloomTheme.colors.skeleton), contentAlignment = BiasAlignment(0f, -0.4f)) {
                    Text("지도는 온라인에서 표시됩니다", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Column(
                Modifier.statusBarsPadding().padding(horizontal = BloomDimens.ScreenHorizontal, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchBarStub(onClick = onSearchClick)
                if (!isOnline) NoticeBanner("인터넷 연결이 필요합니다 · 즐겨찾기는 볼 수 있어요", tone = BannerTone.Warning)
                saveMessage?.let { NoticeBanner(it, actionLabel = "닫기", onAction = viewModel::dismissSaveMessage) }
                guideSession?.let { session ->
                    NoticeBanner(
                        message = "안내 중 · ${session.currentStep.title}",
                        actionLabel = "안내 보기",
                        onAction = onGuideBannerClick,
                    )
                }
                // S-01-B2 이전 안내 중단(강제 종료 후 재실행). 경고가 아닌 안내라 기본 색
                interruptedTarget?.let { target ->
                    NoticeBanner(
                        message = "이전 안내가 중단되었습니다",
                        actionLabel = "다시 안내",
                        onAction = { resumeInterrupted(target) },
                        onDismiss = viewModel::dismissInterrupted,
                    )
                }
            }
        }
    }

    selectedPlace?.let { state ->
        PlaceDetailSheet(
            state = state,
            isLoggedIn = isLoggedIn,
            onDirections = { viewModel.dismissPlace(); onPlaceSelected(it) },
            onSave = { viewModel.startSaving(it); viewModel.dismissPlace() },
            onLoginClick = { viewModel.dismissPlace(); onLoginRequired() },
            onDismiss = viewModel::dismissPlace,
            onRetry = viewModel::retryPlace,
        )
    }

    savingPlace?.let { place ->
        SaveFavoriteDialog(
            onSave = { alias -> viewModel.save(place, alias) },
            onDismiss = viewModel::cancelSaving,
        )
    }
}

/** 하단 시트: 즐겨찾기 칩 · 최근 목적지(최대 20개, 최신순) */
@Composable
private fun HomeSheet(
    isLoggedIn: Boolean,
    favorites: List<Favorite>,
    recents: List<RecentDestination>,
    onFavoriteClick: (Favorite) -> Unit,
    onRecentClick: (RecentDestination) -> Unit,
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
        if (recents.isEmpty()) {
            Text("최근에 찾은 곳이 여기에 보여요", style = MaterialTheme.typography.bodySmall, color = BloomTheme.colors.textSecondary)
        }
        recents.forEach { recent ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onRecentClick(recent) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = BloomTheme.colors.textSecondary)
                // 장소명은 저장 불가(카카오 정책)라 사용자가 입력한 검색어 표시
                Text(recent.query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
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

/** rememberSaveable 이 [RecentDestination] 을 문자열 2개로 저장했다가 되살리는 규칙. null 은 저장하지 않음 */
private val RecentDestinationSaver = listSaver<RecentDestination?, String>(
    save = { r -> if (r == null) emptyList() else listOf(r.placeId, r.query) },
    restore = { if (it.size == 2) RecentDestination(it[0], it[1]) else null },
)

/** [ResumeTarget] 저장용. 검색어가 없으면 빈 문자열로 저장하고 복원 시 null */
private val ResumeTargetSaver = listSaver<ResumeTarget?, String>(
    save = { t -> if (t == null) emptyList() else listOf(t.placeId, t.name, t.searchQuery.orEmpty()) },
    restore = { if (it.size == 3) ResumeTarget(it[0], it[1], it[2].ifEmpty { null }) else null },
)
