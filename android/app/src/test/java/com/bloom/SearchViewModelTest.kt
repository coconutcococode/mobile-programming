package com.bloom

import com.bloom.core.designsystem.UiState
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.PlaceLimits
import com.bloom.core.model.RecentDestination
import com.bloom.data.fake.FakePlaceRepository
import com.bloom.data.repository.RecentDestinationRepository
import com.bloom.feature.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 검색 상태(S-01 검색): 0.3초 대기 후 자동완성, 최근 목적지에는 입력한 검색어만 저장 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val recents = InMemoryRecents()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SearchViewModel(FakePlaceRepository(), recents, NoLocation)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `입력이 멈추고 0_3초가 지나야 검색하고 결과는 최대 10개다`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.results.collect {} }

        viewModel.onQueryChange("홍")
        advanceTimeBy(PlaceLimits.SEARCH_DEBOUNCE_MILLIS - 1)
        assertNull("0.3초 전에는 아직 검색하지 않는다", viewModel.results.value)

        advanceUntilIdle()
        val state = viewModel.results.value
        assertTrue(state is UiState.Success && state.data.places.isNotEmpty() && state.data.places.size <= PlaceLimits.MAX_AUTOCOMPLETE)
    }

    @Test
    fun `결과가 뜬 뒤 검색어를 바꿔도 지금 보이는 결과는 그 결과를 받아 온 검색어와 짝을 이룬다`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.results.collect {} }

        viewModel.onQueryChange("홍")
        advanceUntilIdle()
        // 0.3초가 지나기 전(새 검색 전)에는 '홍' 결과가 그대로 남아 있음
        viewModel.onQueryChange("홍익")

        val state = viewModel.results.value
        assertTrue(state is UiState.Success && state.data.query == "홍")
    }

    @Test
    fun `결과가 없으면 Empty, 검색어를 지우면 최근 목적지 상태로 돌아간다`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.results.collect {} }

        viewModel.onQueryChange("없는장소이름")
        advanceUntilIdle()
        assertEquals(UiState.Empty, viewModel.results.value)

        viewModel.onQueryChange("")
        advanceUntilIdle()
        assertNull(viewModel.results.value)
    }

    @Test
    fun `최근 목적지에는 장소 이름이 아니라 입력한 검색어를 저장하고 빈 검색어는 저장하지 않는다`() = runTest(dispatcher) {
        viewModel.rememberChoice("place-1", "학교")
        viewModel.rememberChoice("place-2", "  ")
        advanceUntilIdle()

        assertEquals(listOf(RecentDestination("place-1", "학교")), recents.items.value)
    }

    @Test
    fun `위치가 끝내 오지 않아도 정해 둔 시간 뒤 위치 없이 검색해 결과를 보여 준다`() = runTest(dispatcher) {
        val vm = SearchViewModel(FakePlaceRepository(), recents, LocationTracking(arrives = false))
        backgroundScope.launch { vm.results.collect {} }

        vm.onQueryChange("홍")
        advanceUntilIdle()

        assertTrue(vm.results.value is UiState.Success)
    }

    @Test
    fun `위치는 화면을 열 때 한 번만 요청하고 이후 검색에서 다시 쓴다`() = runTest(dispatcher) {
        val tracker = LocationTracking(arrives = true)
        val vm = SearchViewModel(FakePlaceRepository(), recents, tracker)
        backgroundScope.launch { vm.results.collect {} }

        vm.onQueryChange("홍")
        advanceUntilIdle()
        vm.onQueryChange("홍익")
        advanceUntilIdle()

        assertEquals(1, tracker.requests)
        assertTrue(vm.results.value is UiState.Success)
    }

    private class InMemoryRecents : RecentDestinationRepository {
        val items = MutableStateFlow<List<RecentDestination>>(emptyList())
        override fun observe(): Flow<List<RecentDestination>> = items
        override suspend fun save(placeId: String, query: String) {
            items.value = listOf(RecentDestination(placeId, query)) + items.value.filterNot { it.placeId == placeId }
        }
    }

    private object NoLocation : LocationTracker {
        override fun hasPreciseLocationPermission() = false
        override suspend fun currentLocation(): LatLng? = null
        override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> = emptyFlow()
    }

    /** 위치 요청 횟수 기록. [arrives] 가 false 면 위치가 끝내 오지 않는 상황 */
    private class LocationTracking(private val arrives: Boolean) : LocationTracker {
        var requests = 0
        override fun hasPreciseLocationPermission() = true
        override suspend fun currentLocation(): LatLng? {
            requests++
            if (!arrives) awaitCancellation()
            return LatLng(37.5563, 126.9246)
        }
        override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> = emptyFlow()
    }
}
