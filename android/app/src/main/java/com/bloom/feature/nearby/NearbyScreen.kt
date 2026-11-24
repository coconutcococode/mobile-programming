package com.bloom.feature.nearby

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bloom.core.designsystem.UiState
import com.bloom.core.designsystem.component.BannerTone
import com.bloom.core.designsystem.component.BloomChip
import com.bloom.core.designsystem.component.EmptyState
import com.bloom.core.designsystem.component.ErrorState
import com.bloom.core.designsystem.component.NoticeBanner
import com.bloom.core.designsystem.component.SheetStep
import com.bloom.core.designsystem.component.SkeletonList
import com.bloom.core.designsystem.component.ThreeStepSheetScaffold
import com.bloom.core.designsystem.component.rememberThreeStepSheetState
import com.bloom.core.designsystem.theme.BloomDimens
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.designsystem.toUserMessage
import com.bloom.core.location.LocationTracker
import com.bloom.core.map.KakaoMapView
import com.bloom.core.map.MapMarker
import com.bloom.core.model.LatLng
import com.bloom.core.model.NearbySort
import com.bloom.core.model.Place
import com.bloom.core.model.PlaceCategory
import com.bloom.core.model.PlaceLimits
import com.bloom.core.network.NetworkMonitor
import com.bloom.core.network.toAppError
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.data.repository.PlaceRepository
import com.bloom.feature.place.PlaceDetailSheet
import com.bloom.feature.place.SaveFavoriteDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    authRepository: AuthRepository,
    networkMonitor: NetworkMonitor,
    private val placeRepository: PlaceRepository,
    private val favoriteRepository: FavoriteRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 오프라인이면 지도 대신 안내 + 경고 배너(부분 상태) */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _category = MutableStateFlow(PlaceCategory.CAFE)
    val category: StateFlow<PlaceCategory> = _category.asStateFlow()

    private val _sort = MutableStateFlow(NearbySort.DISTANCE)
    val sort: StateFlow<NearbySort> = _sort.asStateFlow()

    /** 주변 검색 중심. 위치 권한이 없거나 위치를 못 받으면 서울시청 주변 */
    private val _center = MutableStateFlow(LatLng.SeoulCityHall)
    val center: StateFlow<LatLng> = _center.asStateFlow()

    private val _places = MutableStateFlow<UiState<List<Place>>>(UiState.Loading)
    val places: StateFlow<UiState<List<Place>>> = _places.asStateFlow()

    /** 장소 상세(S-09)에 띄운 장소. 목록에서 받은 장소를 그대로 사용(장소 ID 재조회 없음) */
    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()

    /** ☆를 누른 장소. 값이 있으면 이름 입력 창 표시(화면 회전에도 유지되도록 ViewModel 에 보관) */
    private val _savingPlace = MutableStateFlow<Place?>(null)
    val savingPlace: StateFlow<Place?> = _savingPlace.asStateFlow()

    /** 즐겨찾기 저장 결과 안내(성공, 실패). null 이면 배너 없음 */
    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private var loadJob: Job? = null

    init {
        // 위치는 화면을 열 때 한 번만 요청(첫 실행 권한 요청 0건이라 여기서 권한을 묻지 않음).
        // 위치를 기다리는 동안은 기본 위치로 먼저 조회하고, 위치가 오면 그 위치로 다시 조회
        load()
        viewModelScope.launch {
            locationTracker.currentLocation()?.let {
                _center.value = it
                load()
            }
        }
        // 로그아웃하면 추천순을 거리순으로 되돌림
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user == null && _sort.value == NearbySort.RECOMMENDED) {
                    _sort.value = NearbySort.DISTANCE
                    load()
                }
            }
        }
    }

    fun onCategoryChange(value: PlaceCategory) {
        if (value == _category.value) return
        _category.value = value
        load()
    }

    fun onSortChange(value: NearbySort) {
        if (value == _sort.value) return
        // 비로그인은 거리순만(S-06-B1)
        if (value == NearbySort.RECOMMENDED && !isLoggedIn.value) return
        _sort.value = value
        load()
    }

    /** 반경 500m, 최대 10곳 조회. 카테고리, 정렬을 빠르게 바꾸면 이전 조회 취소(늦게 온 결과의 덮어쓰기 방지) */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _places.value = UiState.Loading
            _selectedPlace.value = null
            _places.value = placeRepository.nearby(_center.value, _category.value, _sort.value).fold(
                onSuccess = { list -> if (list.isEmpty()) UiState.Empty else UiState.Success(list.take(PlaceLimits.MAX_NEARBY)) },
                onFailure = { UiState.Error(it.toAppError()) },
            )
        }
    }

    /** 지도 핀 탭: 핀 ID 로 지금 목록의 장소 선택 */
    fun selectById(placeId: String) {
        (_places.value as? UiState.Success)?.data?.firstOrNull { it.id == placeId }?.let { _selectedPlace.value = it }
    }

    fun select(place: Place) {
        _selectedPlace.value = place
    }

    fun dismissPlace() {
        _selectedPlace.value = null
    }

    fun startSaving(place: Place) {
        _savingPlace.value = place
    }

    fun cancelSaving() {
        _savingPlace.value = null
    }

    /** [alias] 는 사용자가 이름 입력 창에 직접 쓴 이름. 카카오 장소 이름은 저장하지 않음(NFR-11) */
    fun save(place: Place, alias: String) {
        _savingPlace.value = null
        viewModelScope.launch {
            _saveMessage.value = favoriteRepository.add(place, alias = alias)
                .fold(onSuccess = { "즐겨찾기에 저장했어요" }, onFailure = { "저장하지 못했어요. 다시 시도해 주세요" })
        }
    }

    fun dismissSaveMessage() {
        _saveMessage.value = null
    }
}

/**
 * S-06 주변(지도 + 추천 시트).
 * TODO(트랙 A): 지도 번호 핀(1~10) = 목록 번호, 시트 3단(접힘·중간·펼침), 반경 500m 최대 10개,
 *  추천순(로그인·동의 S-06-D1) / 거리순, 상태 S-06-1~5(공공 상가정보 대체 시 출처 표시).
 */
@Composable
fun NearbyScreen(
    onDirections: (Place) -> Unit,
    onLoginClick: () -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val center by viewModel.center.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
    val savingPlace by viewModel.savingPlace.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()

    val list = (places as? UiState.Success)?.data.orEmpty()

    ThreeStepSheetScaffold(
        state = rememberThreeStepSheetState(SheetStep.Half),
        sheetContent = {
            NearbySheet(
                isLoggedIn = isLoggedIn,
                category = category,
                sort = sort,
                places = places,
                onCategoryChange = viewModel::onCategoryChange,
                onSortChange = viewModel::onSortChange,
                onLoginClick = onLoginClick,
                onRetry = viewModel::load,
                onPlaceClick = viewModel::select,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isOnline) {
                KakaoMapView(
                    center = center,
                    // 번호 핀: 목록과 같은 순서로 1~10
                    markers = list.mapIndexed { index, place ->
                        MapMarker(place.id, place.location, label = "${index + 1}", selected = place.id == selectedPlace?.id)
                    },
                    onMarkerClick = viewModel::selectById,
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
                if (!isOnline) NoticeBanner("인터넷 연결이 필요합니다", tone = BannerTone.Warning)
                saveMessage?.let { NoticeBanner(it, actionLabel = "닫기", onAction = viewModel::dismissSaveMessage) }
            }
        }
    }

    selectedPlace?.let { place ->
        PlaceDetailSheet(
            place = place,
            isLoggedIn = isLoggedIn,
            onDirections = { viewModel.dismissPlace(); onDirections(it) },
            onSave = { viewModel.startSaving(it); viewModel.dismissPlace() },
            onLoginClick = { viewModel.dismissPlace(); onLoginClick() },
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

/** 추천 시트: 카테고리 4종, 정렬, 비로그인 배너(S-06-B1), 목록(로딩, 비어 있음, 오류 상태 포함) */
@Composable
private fun NearbySheet(
    isLoggedIn: Boolean,
    category: PlaceCategory,
    sort: NearbySort,
    places: UiState<List<Place>>,
    onCategoryChange: (PlaceCategory) -> Unit,
    onSortChange: (NearbySort) -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onPlaceClick: (Place) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = BloomDimens.ScreenHorizontal).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("주변 추천 · 반경 ${PlaceLimits.NEARBY_RADIUS_METERS}m", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceCategory.entries.forEach { c -> BloomChip(c.label, selected = c == category, onClick = { onCategoryChange(c) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbySort.entries.forEach { s -> BloomChip(s.label, selected = s == sort, onClick = { onSortChange(s) }) }
        }
        if (!isLoggedIn) {
            NoticeBanner("로그인하면 맞춤 추천순을 볼 수 있어요", actionLabel = "로그인", onAction = onLoginClick)
        }
        when (places) {
            UiState.Loading -> SkeletonList(rows = 4, rowHeight = 48.dp)
            UiState.Empty -> EmptyState(
                title = "반경 ${PlaceLimits.NEARBY_RADIUS_METERS}m 안에 ${category.label}이(가) 없어요",
                description = "다른 카테고리를 골라 보세요",
            )
            is UiState.Error -> ErrorState(message = places.error.toUserMessage(), onAction = onRetry)
            is UiState.Success -> places.data.forEachIndexed { index, place ->
                NearbyRow(number = index + 1, place = place, onClick = { onPlaceClick(place) })
            }
        }
    }
}

/** 목록 한 줄. [number] 는 지도 핀 번호와 같음 */
@Composable
private fun NearbyRow(number: Int, place: Place, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = BloomDimens.MinTouchTarget).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
        Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            listOfNotNull(place.distanceMeters?.let { "${it}m" }, place.walkMinutes?.let { "도보 ${it}분" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = BloomTheme.colors.textSecondary,
        )
    }
}
