package com.bloom

import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.RouteLimits
import com.bloom.core.model.RouteSortType
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import com.bloom.core.model.sortedFor
import com.bloom.core.model.toGuideSteps
import com.bloom.data.fake.FakeRouteRepository
import com.bloom.data.session.InMemoryGuideSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class RouteAndGuideTest {

    private val destination = Place("p1", "목적지", "주소", LatLng(0.0, 0.0))

    private fun candidate(id: String, minutes: Int, transfers: Int, walk: Int, segments: List<Segment> = emptyList()) =
        RouteCandidate(id, destination, minutes, LocalTime.NOON, transfers, walk, segments)

    @Test
    fun `정렬 3종이 각각 기준값 오름차순으로 정렬된다`() {
        val list = listOf(
            candidate("a", minutes = 24, transfers = 0, walk = 700),
            candidate("b", minutes = 18, transfers = 2, walk = 300),
            candidate("c", minutes = 21, transfers = 1, walk = 100),
        )
        assertEquals(listOf("b", "c", "a"), list.sortedFor(RouteSortType.FASTEST).map { it.id })
        assertEquals(listOf("a", "c", "b"), list.sortedFor(RouteSortType.FEWEST_TRANSFERS).map { it.id })
        assertEquals(listOf("c", "b", "a"), list.sortedFor(RouteSortType.LEAST_WALK).map { it.id })
    }

    @Test
    fun `최소 도보에서 도보와 시간이 같으면 환승이 적은 경로가 먼저다`() {
        val list = listOf(
            candidate("two", minutes = 20, transfers = 2, walk = 300),
            candidate("zero", minutes = 20, transfers = 0, walk = 300),
        )
        assertEquals(listOf("zero", "two"), list.sortedFor(RouteSortType.LEAST_WALK).map { it.id })
    }

    @Test
    fun `대중교통 구간은 승차대기-이동-하차대기-하차완료 4단계, 마지막은 도착`() {
        val route = candidate(
            "r", 18, 0, 780,
            listOf(
                Segment(TransitMode.WALK, 6, "도보", "", "이대역", distanceMeters = 380),
                Segment(TransitMode.SUBWAY, 6, "2호선", "이대역", "홍대입구역", stopCount = 2),
                Segment(TransitMode.WALK, 6, "도보", "", "목적지", distanceMeters = 400),
            ),
        )
        val types = route.toGuideSteps().map { it.type }
        assertEquals(
            listOf(
                GuideStepType.WALK,
                GuideStepType.WAIT_BOARDING, GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING, GuideStepType.ALIGHTED,
                GuideStepType.WALK,
                GuideStepType.ARRIVED,
            ),
            types,
        )
    }

    @Test
    fun `환승 4회 경로도 최대 27단계를 넘지 않는다`() {
        val walk = Segment(TransitMode.WALK, 3, "도보", "", "역", distanceMeters = 200)
        val bus = Segment(TransitMode.BUS, 5, "7016", "A", "B", stopCount = 3)
        // 대중교통 5구간(환승 4회) + 사이사이 도보 6구간
        val segments = buildList { add(walk); repeat(RouteLimits.MAX_TRANSFERS + 1) { add(bus); add(walk) } }
        val steps = candidate("r", 60, 4, 1200, segments).toGuideSteps()
        assertTrue("단계 수 ${steps.size}", steps.size <= RouteLimits.MAX_GUIDE_STEPS)
    }

    @Test
    fun `안내 세션은 끝 단계를 넘지 않고, 종료하면 폐기된다`() {
        val repo = InMemoryGuideSessionRepository(FakeRouteRepository())
        val route = candidate("r", 10, 0, 500, listOf(Segment(TransitMode.WALK, 10, "도보", "", "목적지", distanceMeters = 500)))
        repo.start(route)
        repeat(10) { repo.next() }
        assertEquals(repo.session.value!!.steps.lastIndex, repo.session.value!!.currentStepIndex)
        repo.end()
        assertNull(repo.session.value)
    }
}
