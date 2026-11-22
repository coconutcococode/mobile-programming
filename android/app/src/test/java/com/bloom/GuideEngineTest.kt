package com.bloom

import com.bloom.core.guide.Geo
import com.bloom.core.guide.GuideEngine
import com.bloom.core.guide.GuideProgress
import com.bloom.core.guide.GuideThresholds
import com.bloom.core.model.GuideStep
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import com.bloom.core.model.toGuideSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.math.cos

class GuideEngineTest {

    // ---- 좌표 도우미: 기준점에서 동·북으로 몇 m 떨어진 지점 ----
    private val base = LatLng(37.55, 126.92)
    private val metersPerDegree = Math.toRadians(1.0) * 6_371_000.0 // Geo 와 같은 지구 반지름
    private fun at(eastMeters: Double, northMeters: Double) = LatLng(
        base.latitude + northMeters / metersPerDegree,
        base.longitude + eastMeters / (metersPerDegree * cos(Math.toRadians(base.latitude))),
    )

    private val origin = at(-300.0, 0.0)
    private val stopA = at(0.0, 0.0)          // 승차 지점
    private val stopB = at(2000.0, 0.0)       // 하차 지점
    private val dest = at(2000.0, 300.0)      // 목적지

    private fun walk(from: LatLng, to: LatLng, name: String) =
        Segment(TransitMode.WALK, 4, "도보", "", name, distanceMeters = 300, path = listOf(from, to))

    private fun route(vararg segments: Segment) = RouteCandidate(
        "r", Place("p", "목적지", "주소", dest), 20, LocalTime.NOON, 0, 600, segments.toList(),
    )

    private val busRoute = route(
        walk(origin, stopA, "A정류장"),
        Segment(TransitMode.BUS, 10, "7016", "A정류장", "B정류장", stopCount = 5, path = listOf(stopA, at(1000.0, 0.0), stopB)),
        walk(stopB, dest, "목적지"),
    )

    private val subwayRoute = route(
        walk(origin, stopA, "A역"),
        Segment(TransitMode.SUBWAY, 8, "2호선", "A역", "B역", stopCount = 4, path = listOf(stopA, stopB)),
        walk(stopB, dest, "목적지"),
    )

    private fun engineFor(route: RouteCandidate) = GuideEngine(route.toGuideSteps(), route.destination.location)
    private fun fix(position: LatLng, atMillis: Long, accuracy: Float? = 10f) = LocationFix(position, accuracy, atMillis)
    private fun GuideEngine.type(p: GuideProgress) = currentStep(p).type

    @Test
    fun `버스 경로는 위치만으로 도보-승차대기-차량이동-하차대기-하차완료-도보-도착 순서로 바뀐다`() {
        val e = engineFor(busRoute)
        var p = e.start(0)
        var t = 0L
        fun move(east: Double, north: Double) { t += 5_000; p = e.onLocation(p, fix(at(east, north), t)) }

        move(-200.0, 0.0); assertEquals(GuideStepType.WALK, e.type(p))
        move(-40.0, 0.0); assertEquals(GuideStepType.WAIT_BOARDING, e.type(p))       // 승차 지점 50m 이내
        move(60.0, 0.0); assertEquals(GuideStepType.WAIT_BOARDING, e.type(p))        // 아직 100m 안
        move(120.0, 0.0); assertEquals(GuideStepType.RIDING, e.type(p))              // 승차 지점에서 100m 이상
        move(1500.0, 0.0); assertEquals(GuideStepType.RIDING, e.type(p))             // 하차 지점까지 500m
        move(1650.0, 0.0); assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))     // 하차 지점 400m 이내
        move(1960.0, 0.0); assertEquals(GuideStepType.ALIGHTED, e.type(p))           // 하차 지점 50m 이내
        t += GuideThresholds.ALIGHTED_HOLD_MILLIS; p = e.onTick(p, t)
        assertEquals(GuideStepType.WALK, e.type(p))
        move(2000.0, 250.0); assertEquals(GuideStepType.WALK, e.type(p))
        move(2000.0, 280.0); assertEquals(GuideStepType.ARRIVED, e.type(p))         // 목적지 30m 이내
    }

    @Test
    fun `정확도 50m를 넘는 위치는 전환에 쓰지 않고, 30초 이상 계속되면 부정확 표시`() {
        val e = engineFor(busRoute)
        var p = e.start(0)
        p = e.onLocation(p, fix(at(-10.0, 0.0), 1_000, accuracy = 80f))
        assertEquals(GuideStepType.WALK, e.type(p))
        p = e.onLocation(p, fix(at(-10.0, 0.0), 20_000, accuracy = null))
        assertFalse(p.isLocationInaccurate)
        p = e.onLocation(p, fix(at(-10.0, 0.0), 31_000, accuracy = 80f))
        assertTrue(p.isLocationInaccurate)

        p = e.onLocation(p, fix(at(-10.0, 0.0), 36_000, accuracy = 10f))
        assertFalse(p.isLocationInaccurate)
        assertEquals(GuideStepType.WAIT_BOARDING, e.type(p))
    }

    @Test
    fun `도보 중 경로선에서 50m 이상 20초 이상 벗어나면 경로 이탈, 돌아오면 해제`() {
        val e = engineFor(busRoute)
        var p = e.start(0)
        p = e.onLocation(p, fix(at(-200.0, 80.0), 0))
        p = e.onLocation(p, fix(at(-190.0, 80.0), 10_000))
        assertFalse(p.isOffRoute)
        p = e.onLocation(p, fix(at(-180.0, 80.0), 20_000))
        assertTrue(p.isOffRoute)
        p = e.onLocation(p, fix(at(-170.0, 10.0), 25_000))
        assertFalse(p.isOffRoute)
    }

    @Test
    fun `지하철은 승차 버튼 시각부터 시간으로 하차 대기와 하차역 도착을 추정한다`() {
        val e = engineFor(subwayRoute)
        var p = e.onLocation(e.start(0), fix(at(-30.0, 0.0), 1_000))
        assertEquals(GuideStepType.WAIT_BOARDING, e.type(p))

        val boarded = 10_000L
        p = e.onBoardPressed(p, boarded)
        assertEquals(GuideStepType.RIDING, e.type(p))
        assertEquals(4, e.remainingStops(p, boarded))

        // 4개 역 · 8분 → 역당 2분, 직전 역 도착 = 승차 + 6분
        p = e.onTick(p, boarded + 5 * 60_000)
        assertEquals(GuideStepType.RIDING, e.type(p))
        assertEquals(2, e.remainingStops(p, boarded + 5 * 60_000))
        p = e.onTick(p, boarded + 6 * 60_000)
        assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))
        assertEquals(1, e.remainingStops(p, boarded + 6 * 60_000))

        assertFalse(e.isAtAlightingStation(p, boarded + 8 * 60_000 - 1))
        assertTrue(e.isAtAlightingStation(p, boarded + 8 * 60_000))

        // 하차역 추정 도착 후 5분이 지나면 자동으로 하차 완료
        p = e.onTick(p, boarded + 13 * 60_000 - 1)
        assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))
        p = e.onTick(p, boarded + 13 * 60_000)
        assertEquals(GuideStepType.ALIGHTED, e.type(p))
    }

    @Test
    fun `지하철 하차역 200m 자동 확인은 추정 도착 시각 이후에만 한다`() {
        val e = engineFor(subwayRoute)
        var p = e.onLocation(e.start(0), fix(at(-30.0, 0.0), 0))
        p = e.onBoardPressed(p, 0)
        p = e.onTick(p, 6 * 60_000)
        assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))

        p = e.onLocation(p, fix(at(1850.0, 0.0), 7 * 60_000))   // 하차역 150m지만 추정 도착 전
        assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))
        p = e.onLocation(p, fix(at(1850.0, 0.0), 8 * 60_000))   // 추정 도착 후
        assertEquals(GuideStepType.ALIGHTED, e.type(p))
    }

    @Test
    fun `현재 역을 보정하면 하차 대기에서 차량 이동으로 되돌아갈 수 있다`() {
        val e = engineFor(subwayRoute)
        var p = e.onLocation(e.start(0), fix(at(-30.0, 0.0), 0))
        p = e.onBoardPressed(p, 0)
        p = e.onTick(p, 6 * 60_000)
        assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))

        p = e.onStationCorrected(p, passedStops = 1, now = 6 * 60_000)   // 실제로는 1개 역만 지남
        assertEquals(GuideStepType.RIDING, e.type(p))
        assertEquals(3, e.remainingStops(p, 6 * 60_000))
    }

    @Test
    fun `버튼으로 승차·하차 완료·다음 단계를 넘길 수 있고 마지막 단계에서 멈춘다`() {
        val e = engineFor(busRoute)
        var p = e.start(0)
        p = e.onBoardPressed(p, 1)                       // 도보 중엔 승차 버튼 무시
        assertEquals(GuideStepType.WALK, e.type(p))
        p = e.onNextPressed(p, 2)
        p = e.onBoardPressed(p, 3)
        assertEquals(GuideStepType.RIDING, e.type(p))
        p = e.onNextPressed(p, 4)
        p = e.onAlightPressed(p, 5)
        assertEquals(GuideStepType.ALIGHTED, e.type(p))
        repeat(10) { p = e.onNextPressed(p, 6L + it) }
        assertEquals(GuideStepType.ARRIVED, e.type(p))
    }

    @Test
    fun `지하철 차량 이동 중에만 위치 갱신 간격을 30초로 늘린다`() {
        val e = engineFor(subwayRoute)
        var p = e.start(0)
        assertEquals(GuideThresholds.LOCATION_INTERVAL_MILLIS, e.locationIntervalMillis(p))
        p = e.onNextPressed(p, 0)
        p = e.onBoardPressed(p, 0)
        assertEquals(GuideThresholds.SUBWAY_RIDING_LOCATION_INTERVAL_MILLIS, e.locationIntervalMillis(p))
    }

    @Test
    fun `실시간 도착 정보는 승차 전이고 승차 지점 500m 이내일 때만 조회한다`() {
        val e = engineFor(busRoute)
        val p = e.start(0)
        assertFalse(e.shouldFetchArrivals(p, at(-600.0, 0.0)))
        assertTrue(e.shouldFetchArrivals(p, at(-400.0, 0.0)))
        val riding = e.onBoardPressed(e.onNextPressed(p, 0), 0)
        assertFalse(e.shouldFetchArrivals(riding, at(10.0, 0.0)))
    }

    @Test
    fun `지하철 환승은 하차 완료 없이 바로 다음 열차 승차 대기로 이어진다`() {
        val transfer = route(
            walk(origin, stopA, "A역"),
            Segment(TransitMode.SUBWAY, 6, "2호선", "A역", "B역", stopCount = 3, path = listOf(stopA, stopB)),
            Segment(TransitMode.SUBWAY, 4, "6호선", "B역", "C역", stopCount = 2, path = listOf(stopB, dest)),
        )
        val steps = transfer.toGuideSteps()
        assertEquals(
            listOf(
                GuideStepType.WALK,
                GuideStepType.WAIT_BOARDING, GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING,
                GuideStepType.WAIT_BOARDING, GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING, GuideStepType.ALIGHTED,
                GuideStepType.ARRIVED,
            ),
            steps.map { it.type },
        )
        assertEquals("다음 역에서 환승", steps[3].title)
        assertEquals("6호선 갈아타기", steps[4].title)
    }

    @Test
    fun `거리 계산 - 두 지점 거리와 경로선까지 수직 거리`() {
        assertEquals(2000.0, Geo.distanceMeters(stopA, stopB), 2.0)
        assertEquals(80.0, Geo.distanceToPathMeters(at(1000.0, 80.0), listOf(stopA, stopB))!!, 1.0)
        assertEquals(100.0, Geo.distanceToPathMeters(at(-100.0, 0.0), listOf(stopA, stopB))!!, 1.0) // 선분 끝 바깥
        assertEquals(null, Geo.distanceToPathMeters(stopA, emptyList()))
    }

    @Test
    fun `구간 소요 시간이 0분이어도 앱이 꺼지지 않고 남은 정거장은 모름으로 둔다`() {
        val zero = route(
            walk(origin, stopA, "A역"),
            Segment(TransitMode.SUBWAY, 0, "2호선", "A역", "B역", stopCount = 4, path = listOf(stopA, stopB)),
            walk(stopB, dest, "목적지"),
        )
        val e = engineFor(zero)
        val riding = e.onBoardPressed(e.onNextPressed(e.start(0), 0), 0)
        assertEquals(GuideStepType.RIDING, e.type(riding))
        assertEquals(null, e.remainingStops(riding, 60_000))
    }

    @Test
    fun `버스 구간 좌표가 없으면 자동 전환 대신 다음 단계 버튼이 필요하다고 알린다`() {
        val noPath = route(
            walk(origin, stopA, "A정류장"),
            Segment(TransitMode.BUS, 10, "7016", "A정류장", "B정류장", stopCount = 5),
            walk(stopB, dest, "목적지"),
        )
        val e = engineFor(noPath)
        var p = e.start(0)
        assertTrue(e.needsManualAdvance(p))                       // 도보: 다음 승차 지점을 모름
        p = e.onLocation(p, fix(stopA, 5_000))
        assertEquals(GuideStepType.WALK, e.type(p))               // 승차 지점에 와도 자동으로 안 넘어감
        p = e.onNextPressed(p, 6_000)
        assertTrue(e.needsManualAdvance(p))                       // 승차 대기
        p = e.onBoardPressed(p, 7_000)
        assertTrue(e.needsManualAdvance(p))                       // 차량 이동

        val normal = engineFor(busRoute)
        assertFalse(normal.needsManualAdvance(normal.start(0)))
    }

    @Test
    fun `지하철 구간은 좌표가 없어도 승차 버튼과 시간으로 넘어가므로 다음 단계 버튼이 필요 없다`() {
        val noPath = route(
            walk(origin, stopA, "A역"),
            Segment(TransitMode.SUBWAY, 8, "2호선", "A역", "B역", stopCount = 4),
            walk(stopB, dest, "목적지"),
        )
        val e = engineFor(noPath)
        var p = e.start(0)
        assertTrue(e.needsManualAdvance(p))           // 역까지 도보: 역 위치를 모르면 수동
        p = e.onNextPressed(p, 0)
        val transitTypes = listOf(
            GuideStepType.WAIT_BOARDING, GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING, GuideStepType.ALIGHTED,
        )
        transitTypes.forEach { expected ->
            assertEquals(expected, e.type(p))
            assertFalse("$expected", e.needsManualAdvance(p))
            p = e.onNextPressed(p, 0)
        }
        assertFalse(e.needsManualAdvance(p))          // 마지막 도보: 목적지 좌표는 항상 있음
    }

    @Test
    fun `도보 구간이 연달아 오면 한 단계로 합쳐서 승차 지점까지 이어진다`() {
        val mid = at(-150.0, 0.0)
        val twoWalks = route(
            Segment(TransitMode.WALK, 2, "도보", "", "골목", distanceMeters = 150, path = listOf(origin, mid)),
            Segment(TransitMode.WALK, 2, "도보", "", "A정류장", distanceMeters = 150, path = listOf(mid, stopA)),
            Segment(TransitMode.BUS, 10, "7016", "A정류장", "B정류장", stopCount = 5, path = listOf(stopA, stopB)),
            walk(stopB, dest, "목적지"),
        )
        val steps = twoWalks.toGuideSteps()
        val firstWalk = steps.first()
        assertEquals(GuideStepType.WALK, firstWalk.type)
        assertEquals(GuideStepType.WAIT_BOARDING, steps[1].type)
        assertEquals("A정류장까지 걷기", firstWalk.title)
        assertEquals(4, firstWalk.segment!!.durationMinutes)
        assertEquals(300, firstWalk.segment!!.distanceMeters)
        assertEquals(listOf(origin, mid, stopA), firstWalk.segment!!.path) // 이음점은 한 번만

        val e = GuideEngine(steps, dest)
        val p = e.onLocation(e.start(0), fix(at(-30.0, 0.0), 5_000))
        assertEquals(GuideStepType.WAIT_BOARDING, e.type(p))
    }

    @Test
    fun `도보 다음에 넘어갈 목표가 없는 단계가 오면 다음 단계 버튼이 필요하다고 알린다`() {
        val walkSeg = walk(origin, stopA, "A")
        val steps = listOf(
            GuideStep(0, GuideStepType.WALK, "걷기", "", walkSeg),
            GuideStep(1, GuideStepType.WALK, "또 걷기", "", walkSeg),
            GuideStep(2, GuideStepType.ARRIVED, "도착", "", null),
        )
        val e = GuideEngine(steps, dest)
        assertTrue(e.needsManualAdvance(e.start(0)))
    }

    @Test
    fun `연속 도보 중 한쪽 경로선이 없으면 합친 경로선을 비워 경로 이탈을 잘못 판단하지 않는다`() {
        val mid = at(-150.0, 0.0)
        val partial = route(
            Segment(TransitMode.WALK, 2, "도보", "", "골목", distanceMeters = 150, path = listOf(origin, mid)),
            Segment(TransitMode.WALK, 2, "도보", "", "A정류장", distanceMeters = 150), // 경로선 없음
            Segment(TransitMode.BUS, 10, "7016", "A정류장", "B정류장", stopCount = 5, path = listOf(stopA, stopB)),
            walk(stopB, dest, "목적지"),
        )
        val steps = partial.toGuideSteps()
        assertEquals(emptyList<LatLng>(), steps.first().segment!!.path)

        // 두 번째 도보 구간(경로선 없음)을 제대로 걷는 중: 첫 구간 끝에서 60m 떨어져도 이탈이 아니다
        val e = GuideEngine(steps, dest)
        var p = e.onLocation(e.start(0), fix(at(-90.0, 0.0), 0))
        p = e.onLocation(p, fix(at(-90.0, 0.0), 25_000))
        assertFalse(p.isOffRoute)
        assertEquals(GuideStepType.WALK, e.type(p))
    }

    @Test
    fun `버스가 위치로 먼저 하차 대기로 넘어가면 남은 정거장은 시간 추정과 상관없이 1이다`() {
        val e = engineFor(busRoute)
        var p = e.onBoardPressed(e.onNextPressed(e.start(0), 0), 0)
        assertEquals(5, e.remainingStops(p, 10_000))
        assertEquals(2, e.remainingStops(p, 9 * 60_000))          // 시간상 1이어도 차량 이동 중엔 2
        p = e.onLocation(p, fix(at(1700.0, 0.0), 30_000))         // 하차 지점 300m → 하차 대기
        assertEquals(GuideStepType.WAIT_ALIGHTING, e.type(p))
        assertEquals(1, e.remainingStops(p, 30_000))
    }
}
