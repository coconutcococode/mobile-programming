package com.bloom.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.Favorite
import com.bloom.core.model.GuideSession
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.RecentDestination
import com.bloom.core.network.NetworkMonitor
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.PlaceRepository
import com.bloom.data.repository.RecentDestinationRepository
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
class HomeViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val favoriteRepository: FavoriteRepository,
    private val locationTracker: LocationTracker,
    private val recentDestinationRepository: RecentDestinationRepository,
    authRepository: AuthRepository,
    guideSessionRepository: GuideSessionRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    /** S-01-B1 '안내 중' 배너 */
    val guideSession: StateFlow<GuideSession?> = guideSessionRepository.session

    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favorites: StateFlow<List<Favorite>> = favoriteRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 시트의 최근 목적지(최신순, 최대 20개). 장소 ID와 사용자가 입력한 검색어만 있다 */
    val recents: StateFlow<List<RecentDestination>> = recentDestinationRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** S-01-5 오프라인: 지도 대신 안내, 즐겨찾기 목록은 그대로 */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 내 위치. 권한 전이거나 아직 못 받았으면 null → 지도는 서울시청 주변(S-01-0) */
    private val _myLocation = MutableStateFlow<LatLng?>(null)
    val myLocation: StateFlow<LatLng?> = _myLocation.asStateFlow()

    /** '현재 위치를 찾는 중…' 표시 */
    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    /** [내 위치 보기]를 누를 때마다 1씩 오른다. 위치 값이 그대로여도 지도가 내 위치로 돌아오게 하는 신호 */
    private val _recenterRequest = MutableStateFlow(0)
    val recenterRequest: StateFlow<Int> = _recenterRequest.asStateFlow()

    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()

    private var placeLookupJob: Job? = null

    init {
        // 첫 실행에는 권한을 묻지 않는다(권한 요청 0건). 이미 허용된 경우에만 바로 내 위치로 이동.
        if (locationTracker.hasPreciseLocationPermission()) locate()
    }

    /** [내 위치 보기] — 권한 허용 후 호출. 위치를 못 받으면 서울시청 그대로 */
    fun locate() {
        viewModelScope.launch {
            _locating.value = true
            _myLocation.value = locationTracker.currentLocation() ?: _myLocation.value
            _recenterRequest.value++
            _locating.value = false
        }
    }

    fun hasLocationPermission(): Boolean = locationTracker.hasPreciseLocationPermission()

    /** 최근 목적지를 다시 고르면 목록 맨 위로 올린다(검색 화면과 같은 규칙) */
    fun reuseRecent(recent: RecentDestination) {
        viewModelScope.launch { recentDestinationRepository.save(recent.placeId, recent.query) }
    }

    /** 지도 핀 탭 → S-09 시트. 이전 조회가 남아 있으면 취소해서 늦게 온 결과가 시트를 덮지 않게 한다. */
    fun onMapPlaceClick(placeId: String) {
        placeLookupJob?.cancel()
        placeLookupJob = viewModelScope.launch {
            _selectedPlace.value = placeRepository.getPlace(placeId).getOrNull()
        }
    }

    fun dismissPlace() {
        placeLookupJob?.cancel()
        placeLookupJob = null
        _selectedPlace.value = null
    }

    /** 즐겨찾기 저장 결과 안내(성공·실패). null 이면 배너 없음 */
    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    /**
     * ☆를 누른 장소. 값이 있으면 이름 입력 창을 띄운다.
     * 화면 회전 때 화면(Composable)의 remember 값은 사라지지만 ViewModel 은 남으므로 여기에 둔다.
     */
    private val _savingPlace = MutableStateFlow<Place?>(null)
    val savingPlace: StateFlow<Place?> = _savingPlace.asStateFlow()

    fun startSaving(place: Place) {
        _savingPlace.value = place
    }

    fun cancelSaving() {
        _savingPlace.value = null
    }

    /** [alias] 는 사용자가 저장 창에 직접 입력한 이름. 카카오 장소 이름은 저장하지 않는다(NFR-11). */
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
