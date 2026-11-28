package com.bloom.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bloom.core.designsystem.UiState
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.AppError
import com.bloom.core.model.LatLng
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.RouteLimits
import com.bloom.core.model.RouteSortType
import com.bloom.core.model.sortedFor
import com.bloom.core.navigation.RouteCandidatesRoute
import com.bloom.core.network.toAppError
import com.bloom.data.repository.PlaceRepository
import com.bloom.data.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S-02 경로 후보 목록 — ViewModel + UiState 패턴의 기준 예시.
 * 다른 화면도 이 구조(저장소 호출 → UiState 로 변환 → 화면은 상태만 그림)를 따른다.
 */
@HiltViewModel
class RouteCandidatesViewModel internal constructor(
    private val args: RouteCandidatesRoute,
    private val placeRepository: PlaceRepository,
    private val routeRepository: RouteRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    /** Hilt 가 쓰는 생성자. 화면 인자 읽기(toRoute)는 안드로이드 Bundle 이 필요해서, 단위 테스트는 인자를 바로 받는 생성자를 쓴다. */
    @Inject constructor(
        savedStateHandle: SavedStateHandle,
        placeRepository: PlaceRepository,
        routeRepository: RouteRepository,
        locationTracker: LocationTracker,
    ) : this(savedStateHandle.toRoute<RouteCandidatesRoute>(), placeRepository, routeRepository, locationTracker)

    val destinationName: String = args.destinationName

    private val _sort = MutableStateFlow(RouteSortType.FASTEST) // 기본 정렬: 최소 시간
    val sort: StateFlow<RouteSortType> = _sort.asStateFlow()

    /** 정확한 위치 권한이 없어 화면이 권한 요청(S-01-D1)을 띄워야 하는지 */
    private val _needsPermission = MutableStateFlow(false)
    val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()

    private val candidates = MutableStateFlow<UiState<List<RouteCandidate>>>(UiState.Loading)

    /** 카카오맵 길찾기용: 이번 검색에서 받은 현재 위치·목적지 좌표. 메모리에만 두고 다시 검색하면 새로 채운다. */
    private var searchedOrigin: LatLng? = null
    private var searchedDestination: LatLng? = null

    val uiState: StateFlow<UiState<List<RouteCandidate>>> =
        combine(candidates, _sort) { state, sort ->
            if (state is UiState.Success) state.copy(data = state.data.sortedFor(sort)) else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        load()
    }

    /** 진행 중인 검색. 다시 검색하면 이전 검색을 취소해, 늦게 끝난 이전 결과가 새 결과·좌표를 덮어쓰지 않게 한다. */
    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            candidates.value = UiState.Loading
            searchedOrigin = null
            searchedDestination = null
            // TODO(트랙 A): 위치 권한 흐름(S-01-D1 → 거절 시 S-08-P1). 지금은 권한이 없으면 서울시청 기준.
            // 출발지는 항상 현재 위치(NFR-08). 권한이 없으면 검색 대신 권한 요청, 위치를 못 받으면 오류(다시 시도)
            if (!locationTracker.hasPreciseLocationPermission()) {
                _needsPermission.value = true
                return@launch
            }
            val origin = locationTracker.currentLocation() ?: run {
                candidates.value = UiState.Error(AppError.LocationUnavailable)
                return@launch
            }
            searchedOrigin = origin
            val destination = placeRepository.getPlace(args.destinationPlaceId).getOrElse {
                candidates.value = UiState.Error(it.toAppError())
                return@launch
            }
            searchedDestination = destination.location
            candidates.value = routeRepository.searchRoutes(origin, destination).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) UiState.Empty else UiState.Success(list.take(RouteLimits.MAX_CANDIDATES))
                },
                onFailure = { UiState.Error(it.toAppError()) },
            )
        }
    }

    /** S-02-4: 경로를 못 불러왔을 때 카카오맵에서 이어서 찾을 링크. */
    fun kakaoMapDirections(): KakaoMapDirections =
        kakaoMapDirections(args.destinationPlaceId, searchedOrigin, searchedDestination)

    fun onSortChange(type: RouteSortType) {
        _sort.value = type
    }

    /** 권한 창을 띄운 시점에 요청 표시 해제(화면이 다시 만들어져도 창을 두 번 띄우지 않게) */
    fun onPermissionRequested() {
        _needsPermission.value = false
    }

    /** 허용이면 현재 위치로 검색. 거절은 화면이 위치 권한 안내(S-08-P1)로 이동 */
    fun onPermissionResult(granted: Boolean) {
        if (granted) load()
    }
}
