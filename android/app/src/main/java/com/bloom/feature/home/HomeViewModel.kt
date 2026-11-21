package com.bloom.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bloom.core.model.Favorite
import com.bloom.core.model.GuideSession
import com.bloom.core.model.Place
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.PlaceRepository
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
    authRepository: AuthRepository,
    guideSessionRepository: GuideSessionRepository,
) : ViewModel() {

    /** S-01-B1 '안내 중' 배너 */
    val guideSession: StateFlow<GuideSession?> = guideSessionRepository.session

    val isLoggedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favorites: StateFlow<List<Favorite>> = favoriteRepository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()

    private var placeLookupJob: Job? = null

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

    fun save(place: Place) {
        viewModelScope.launch { favoriteRepository.add(place, alias = place.name) }
    }
}
