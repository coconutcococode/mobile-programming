package com.bloom

import com.bloom.core.designsystem.UiState
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.core.navigation.RouteCandidatesRoute
import com.bloom.data.fake.FakePlaceRepository
import com.bloom.data.fake.SamplePlaces
import com.bloom.data.repository.RouteRepository
import com.bloom.feature.route.RouteCandidatesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** S-02 경로 후보: 다시 검색하면 이전 검색 결과가 새 결과·카카오맵 좌표를 덮어쓰지 않는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCandidatesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val oldLocation = LatLng(37.50, 127.00)
    private val newLocation = LatLng(37.56, 126.92)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `첫 검색이 위치를 기다리는 사이 다시 검색하면 늦게 끝난 첫 검색은 출발 좌표를 덮어쓰지 않는다`() = runTest(dispatcher) {
        val firstLocation = CompletableDeferred<LatLng?>()
        val tracker = SequencedLocation(firstLocation, newLocation)
        val viewModel = RouteCandidatesViewModel(
            RouteCandidatesRoute(SamplePlaces.HongdaeStreet.id, SamplePlaces.HongdaeStreet.name),
            FakePlaceRepository(),
            FailingRoutes,
            tracker,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.runCurrent() // 첫 검색: 위치를 기다리는 중

        viewModel.load() // 다시 검색: 새 위치로 끝까지 진행
        advanceUntilIdle()
        firstLocation.complete(oldLocation) // 첫 검색의 위치가 뒤늦게 도착
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Error)
        val appUri = viewModel.kakaoMapDirections().appUri.orEmpty()
        assertTrue(appUri, appUri.contains("sp=${newLocation.latitude},${newLocation.longitude}&"))
        assertEquals(2, tracker.requests)
    }

    /** 첫 요청은 [first]가 끝날 때까지 기다리고, 그다음부터는 [next]를 바로 준다. */
    private class SequencedLocation(private val first: CompletableDeferred<LatLng?>, private val next: LatLng) : LocationTracker {
        var requests = 0
        override fun hasPreciseLocationPermission() = true
        override suspend fun currentLocation(): LatLng? = if (requests++ == 0) first.await() else next
        override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> = emptyFlow()
    }

    private object FailingRoutes : RouteRepository {
        override suspend fun searchRoutes(origin: LatLng, destination: Place): Result<List<RouteCandidate>> =
            Result.failure(IOException("offline"))
        override fun findCandidate(candidateId: String): RouteCandidate? = null
        override fun clearCandidates() = Unit
    }
}
