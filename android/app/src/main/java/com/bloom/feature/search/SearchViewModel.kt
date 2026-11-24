package com.bloom.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bloom.core.designsystem.UiState
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.PlaceLimits
import com.bloom.core.model.RecentDestination
import com.bloom.core.network.toAppError
import com.bloom.data.repository.PlaceRepository
import com.bloom.data.repository.RecentDestinationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S-01 검색 상태. 입력이 멈추고 0.3초 뒤 자동완성(최대 10개) 조회.
 * 결과를 고르면 최근 목적지에 '장소 ID + 입력한 검색어'만 저장(NFR-11).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val recentDestinationRepository: RecentDestinationRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** [다시 시도]를 누를 때마다 1씩 증가. 같은 검색어도 다시 조회하기 위한 값 */
    private val retryCount = MutableStateFlow(0)

    /**
     * 검색 기준 위치. 화면을 열 때 한 번만 요청(사용자가 입력하는 동안 미리 받아 둠).
     * 새 위치를 처음 잡을 때는 수 초가 걸릴 수 있어서, 검색은 기다리지 않고 그 시점의 값 사용(없으면 위치 없이 검색)
     */
    private val searchCenter = MutableStateFlow<LatLng?>(null)

    init {
        viewModelScope.launch { searchCenter.value = locationTracker.currentLocation() }
    }

    /** null = 검색어가 비어 있음(최근 목적지를 보여 줌) */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<UiState<SearchResults>?> = combine(
        _query.debounce(PlaceLimits.SEARCH_DEBOUNCE_MILLIS),
        retryCount,
    ) { q, _ -> q.trim() }
        // transformLatest: 새 검색어가 오면 이전 조회를 취소하고 새로 시작(늦게 온 결과의 덮어쓰기 방지)
        .transformLatest { q ->
            if (q.isEmpty()) {
                emit(null)
                return@transformLatest
            }
            emit(UiState.Loading)
            // 위치를 기다리지 않음: 입력이 멈추고 0.3초 뒤 바로 검색(늦게 도착한 위치는 다음 검색부터 사용)
            val result = placeRepository.search(q, searchCenter.value).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) UiState.Empty else UiState.Success(SearchResults(q, list.take(PlaceLimits.MAX_AUTOCOMPLETE)))
                },
                onFailure = { UiState.Error(it.toAppError()) },
            )
            emit(result)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recents: StateFlow<List<RecentDestination>> = recentDestinationRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() {
        retryCount.value++
    }

    fun hasLocationPermission(): Boolean = locationTracker.hasPreciseLocationPermission()

    /**
     * 고른 목적지를 최근 목적지 맨 위에 저장. 화면에는 장소명 대신 이 검색어 표시.
     * suspend: 화면이 저장 완료를 기다린 뒤 이동. 검색 화면이 닫히면 viewModelScope 가 취소되므로
     * 여기서 launch 하면 저장이 중간에 취소될 수 있음.
     */
    suspend fun rememberChoice(placeId: String, typedQuery: String) {
        if (typedQuery.isBlank()) return
        recentDestinationRepository.save(placeId, typedQuery)
    }
}

/** 자동완성 결과와 그 결과를 받아 온 검색어. 고른 장소는 입력창의 지금 글자가 아니라 이 검색어와 짝지어 저장 */
data class SearchResults(val query: String, val places: List<Place>)
