package com.bloom.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bloom.core.designsystem.UiState
import com.bloom.core.location.LocationTracker
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
class RouteCandidatesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val routeRepository: RouteRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<RouteCandidatesRoute>()
    val destinationName: String = args.destinationName

    private val _sort = MutableStateFlow(RouteSortType.FASTEST) // 기본 정렬: 최소 시간
    val sort: StateFlow<RouteSortType> = _sort.asStateFlow()

    private val candidates = MutableStateFlow<UiState<List<RouteCandidate>>>(UiState.Loading)

    val uiState: StateFlow<UiState<List<RouteCandidate>>> =
        combine(candidates, _sort) { state, sort ->
            if (state is UiState.Success) state.copy(data = state.data.sortedFor(sort)) else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            candidates.value = UiState.Loading
            // TODO(트랙 A): 위치 권한 흐름(S-01-D1 → 거절 시 S-08-P1). 지금은 권한이 없으면 서울시청 기준.
            val origin = locationTracker.currentLocation() ?: LatLng.SeoulCityHall
            val destination = placeRepository.getPlace(args.destinationPlaceId).getOrElse {
                candidates.value = UiState.Error(it.toAppError())
                return@launch
            }
            candidates.value = routeRepository.searchRoutes(origin, destination).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) UiState.Empty else UiState.Success(list.take(RouteLimits.MAX_CANDIDATES))
                },
                onFailure = { UiState.Error(it.toAppError()) },
            )
        }
    }

    fun onSortChange(type: RouteSortType) {
        _sort.value = type
    }
}
