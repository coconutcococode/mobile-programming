package com.bloom

import com.bloom.core.designsystem.UiState
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.AppError
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.core.navigation.RouteCandidatesRoute
import com.bloom.data.fake.FakePlaceRepository
import com.bloom.data.fake.FakeRouteRepository
import com.bloom.data.fake.SamplePlaces
import com.bloom.data.repository.RouteRepository
import com.bloom.feature.route.RouteCandidatesViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S-02 경로 후보 위치 권한 흐름: 권한이 없으면 검색 대신 요청, 위치를 못 받으면 다른 출발지로 검색하지 않고 오류 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCandidatesPermissionTest {

    private val dispatcher = StandardTestDispatcher()
    private val here = LatLng(37.556, 126.923)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeLocation(var permitted: Boolean, var location: LatLng?) : LocationTracker {
        override fun hasPreciseLocationPermission() = permitted
        override suspend fun currentLocation(): LatLng? = if (permitted) location else null
        override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> = emptyFlow()
    }

    /** 검색에 쓴 출발지를 기록하는 경로 저장소 */
    private class RecordingRoutes(private val delegate: FakeRouteRepository = FakeRouteRepository()) : RouteRepository by delegate {
        val origins = mutableListOf<LatLng>()
        override suspend fun searchRoutes(origin: LatLng, destination: Place): Result<List<RouteCandidate>> {
            origins += origin
            return delegate.searchRoutes(origin, destination)
        }
    }

    private fun viewModel(tracker: LocationTracker, routes: RecordingRoutes) = RouteCandidatesViewModel(
        RouteCandidatesRoute(SamplePlaces.HongdaeStreet.id, SamplePlaces.HongdaeStreet.name),
        FakePlaceRepository(),
        routes,
        tracker,
    )

    @Test
    fun `권한이 없으면 검색하지 않고 권한 요청을 띄운다`() = runTest(dispatcher) {
        val routes = RecordingRoutes()
        val vm = viewModel(FakeLocation(permitted = false, location = here), routes)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.needsPermission.value)
        assertTrue(routes.origins.isEmpty())
        vm.onPermissionRequested()
        assertFalse(vm.needsPermission.value)
    }

    @Test
    fun `권한을 허용하면 현재 위치를 출발지로 검색한다`() = runTest(dispatcher) {
        val routes = RecordingRoutes()
        val tracker = FakeLocation(permitted = false, location = here)
        val vm = viewModel(tracker, routes)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        tracker.permitted = true
        vm.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(listOf(here), routes.origins)
        assertTrue(vm.uiState.value is UiState.Success)
    }

    @Test
    fun `권한을 거절하면 검색하지 않는다`() = runTest(dispatcher) {
        val routes = RecordingRoutes()
        val vm = viewModel(FakeLocation(permitted = false, location = here), routes)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onPermissionResult(granted = false)
        advanceUntilIdle()

        assertTrue(routes.origins.isEmpty())
    }

    @Test
    fun `권한은 있지만 위치를 받지 못하면 서울시청으로 검색하지 않고 위치 오류를 보인다`() = runTest(dispatcher) {
        val routes = RecordingRoutes()
        val tracker = FakeLocation(permitted = true, location = null)
        val vm = viewModel(tracker, routes)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(routes.origins.isEmpty())
        assertEquals(UiState.Error(AppError.LocationUnavailable), vm.uiState.value)

        // [다시 시도]: 위치를 받으면 현재 위치로 검색
        tracker.location = here
        vm.load()
        advanceUntilIdle()
        assertEquals(listOf(here), routes.origins)
    }
}
