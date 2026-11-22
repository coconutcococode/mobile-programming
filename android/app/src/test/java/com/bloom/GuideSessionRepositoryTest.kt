package com.bloom

import com.bloom.core.guide.GuideThresholds
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.data.fake.FakeRouteRepository
import com.bloom.data.repository.RouteRepository
import com.bloom.data.session.InMemoryGuideSessionRepository
import com.bloom.feature.guide.GuideNotification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 안내 세션 저장소 = 엔진 + 화면·알림이 보는 상태. 가짜 경로(신촌·홍대 좌표)와 가짜 시계로 확인한다. */
class GuideSessionRepositoryTest {

    private var now = 0L
    private val routes = FakeRouteRepository()
    private val repo = InMemoryGuideSessionRepository(routes) { now }
    private val destination = Place("p1", "목적지", "주소", LatLng(37.5551, 126.9228))
    private val cityHall = LatLng.SeoulCityHall

    private fun fix(position: LatLng) = LocationFix(position, accuracyMeters = 10f, elapsedRealtimeMillis = now)

    /** 가짜 경로 첫 후보: 도보 → 2호선(이대역→홍대입구역, 2개 역·6분) → 도보 */
    private suspend fun startSubwayRoute() {
        repo.start(routes.searchRoutes(cityHall, destination).getOrThrow().first())
    }

    @Test
    fun `위치를 넘기면 단계가 바뀌고 화면·알림이 볼 값이 함께 갱신된다`() = runTest {
        startSubwayRoute()
        val ewha = repo.session.value!!.steps[1].segment!!.path.first()
        assertEquals(GuideStepType.WALK, repo.session.value!!.currentStep.type)

        now = 5_000; repo.onLocation(fix(ewha))
        assertEquals(GuideStepType.WAIT_BOARDING, repo.session.value!!.currentStep.type)
        assertTrue(GuideNotification.stepActions(repo.session.value!!).any { it.first == "승차" })

        now = 10_000; repo.board()
        val riding = repo.session.value!!
        assertEquals(GuideStepType.RIDING, riding.currentStep.type)
        assertEquals(2, riding.remainingStops)
        assertEquals(GuideThresholds.SUBWAY_RIDING_LOCATION_INTERVAL_MILLIS, riding.locationIntervalMillis)
        assertEquals("남은 2개 역", GuideNotification.statusText(riding))

        // 2개 역 · 6분 → 역당 3분, 직전 역 = 승차 + 3분
        now = 10_000 + 3 * 60_000; repo.onTick()
        assertEquals(GuideStepType.WAIT_ALIGHTING, repo.session.value!!.currentStep.type)
        now = 10_000 + 6 * 60_000; repo.onTick()
        assertTrue(repo.session.value!!.isAtAlightingStation)
        assertEquals("홍대입구역에서 내리세요", GuideNotification.statusText(repo.session.value!!))
    }

    @Test
    fun `부정확한 위치가 30초 이상 계속되면 다음 단계 버튼이 알림에 나온다`() = runTest {
        startSubwayRoute()
        repeat(7) {
            now = it * 5_000L
            repo.onLocation(LocationFix(cityHall, accuracyMeters = 120f, elapsedRealtimeMillis = now))
        }
        val s = repo.session.value!!
        assertTrue(s.isLocationInaccurate)
        assertTrue(GuideNotification.stepActions(s).any { it.first == "다음 단계" })
    }

    @Test
    fun `재탐색은 같은 목적지의 가장 빠른 경로로 안내를 처음부터 다시 시작한다`() = runTest {
        startSubwayRoute()
        repo.next(); repo.next()
        assertTrue(repo.session.value!!.currentStepIndex > 0)

        assertTrue(repo.reroute(cityHall))
        val s = repo.session.value!!
        assertEquals(0, s.currentStepIndex)
        assertEquals(destination, s.route.destination)
        assertEquals(18, s.route.totalMinutes) // 가짜 후보 중 가장 빠른 경로
    }

    @Test
    fun `안내가 없으면 재탐색하지 않고, 종료하면 세션과 경로 후보가 모두 지워진다`() = runTest {
        assertFalse(repo.reroute(cityHall))
        startSubwayRoute()
        val candidateId = repo.session.value!!.route.id
        assertNotNull(routes.findCandidate(candidateId))
        repo.end()
        assertNull(repo.session.value)
        assertNull(routes.findCandidate(candidateId))
        repo.next() // 종료 후 버튼이 눌려도 아무 일 없음
        assertNull(repo.session.value)
    }

    /** 경로 검색 결과를 채우기 직전에 [duringSearch]를 실행한다(검색하는 사이 사용자가 안내를 바꾸는 상황). */
    private class SlowRouteRepository(private val inner: FakeRouteRepository) : RouteRepository by inner {
        var duringSearch: (() -> Unit)? = null
        var lastResult: List<RouteCandidate> = emptyList()
        override suspend fun searchRoutes(origin: LatLng, destination: Place): Result<List<RouteCandidate>> {
            duringSearch?.also { duringSearch = null }?.invoke()
            return inner.searchRoutes(origin, destination).onSuccess { lastResult = it }
        }
    }

    @Test
    fun `재탐색 검색 중에 다른 안내가 시작되면 결과를 버려 새 안내를 덮어쓰지 않는다`() = runTest {
        val slow = SlowRouteRepository(FakeRouteRepository())
        val repo = InMemoryGuideSessionRepository(slow) { now }
        repo.start(slow.searchRoutes(cityHall, destination).getOrThrow().first())

        val otherDestination = Place("p2", "다른 목적지", "주소", LatLng(37.5600, 126.9300))
        val other = slow.searchRoutes(cityHall, otherDestination).getOrThrow().first()
        slow.duringSearch = { repo.end(); repo.start(other) }

        assertFalse(repo.reroute(cityHall))
        assertEquals(otherDestination, repo.session.value!!.route.destination)
    }

    @Test
    fun `재탐색 검색 중에 안내가 끝나면 검색이 다시 채운 경로 후보도 지운다`() = runTest {
        val slow = SlowRouteRepository(FakeRouteRepository())
        val repo = InMemoryGuideSessionRepository(slow) { now }
        repo.start(slow.searchRoutes(cityHall, destination).getOrThrow().first())
        slow.duringSearch = { repo.end() }

        assertFalse(repo.reroute(cityHall))
        assertNull(repo.session.value)
        assertTrue(slow.lastResult.isNotEmpty())
        slow.lastResult.forEach { assertNull(slow.findCandidate(it.id)) }
    }
}
