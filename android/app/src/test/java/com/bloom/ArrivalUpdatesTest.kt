package com.bloom

import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.ArrivalState
import com.bloom.core.model.ArrivalStatus
import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import com.bloom.core.model.toGuideSteps
import com.bloom.feature.guide.ArrivalPolling
import com.bloom.feature.guide.ArrivalTarget
import com.bloom.feature.guide.GuideBanner
import com.bloom.feature.guide.arrivalCardText
import com.bloom.feature.guide.arrivalNotificationLine
import com.bloom.feature.guide.arrivalTarget
import com.bloom.feature.guide.arrivalTargets
import com.bloom.feature.guide.guideBanners
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 실시간 도착 정보 조회(FR-03, NFR-03)와 카드 상태(UI 설계서 4.2 S-04-1~5). */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalUpdatesTest {

    private val station = LatLng(37.5567, 126.9237)
    private val alight = LatLng(37.5600, 126.9300)
    private val subway = Segment(TransitMode.SUBWAY, 6, "2호선", "이대역", "홍대입구역", stopCount = 3, path = listOf(station, alight))
    private val bus = Segment(TransitMode.BUS, 10, "7016", "A정류장", "B정류장", stopCount = 5, path = listOf(station, alight))
    private val walk = Segment(TransitMode.WALK, 8, "도보", "", "이대역", distanceMeters = 600, path = listOf(LatLng(37.55, 126.92), station))
    private val info = ArrivalInfo("2호선", 2, 7, LocalTime.of(14, 2), ArrivalStatus.OK)

    private fun session(vararg segments: Segment, at: GuideStepType, nth: Int = 0): GuideSession {
        val route = RouteCandidate("r", Place("p", "목적지", "주소", alight), 20, LocalTime.NOON, 0, 600, segments.toList())
        val steps = route.toGuideSteps()
        val index = steps.withIndex().filter { it.value.type == at }[nth].index
        return GuideSession(route, steps, index)
    }

    /** 승차 지점에서 북쪽으로 [meters] 떨어진 곳 */
    private fun north(meters: Double) = LatLng(station.latitude + meters / 111_000.0, station.longitude)

    // ── 조회 대상 ─────────────────────────────

    @Test
    fun `도보 이동 중에는 승차 지점 500m 안에 들어와야 조회하고, 승차 대기는 항상 조회한다`() {
        val walking = session(walk, subway, at = GuideStepType.WALK)
        assertNull(arrivalTarget(walking, north(600.0), active = null))
        assertNull(arrivalTarget(walking, here = null, active = null))
        assertEquals(ArrivalTarget(subway, 0), arrivalTarget(walking, north(450.0), active = null))
        assertEquals(ArrivalTarget(subway, 0), arrivalTarget(session(walk, subway, at = GuideStepType.WAIT_BOARDING), here = null, active = null))
    }

    @Test
    fun `한 번 시작하면 500m 언저리에서 위치가 흔들려도 승차 전까지 계속 조회한다`() {
        val walking = session(walk, subway, at = GuideStepType.WALK)
        assertEquals(ArrivalTarget(subway, 0), arrivalTarget(walking, north(520.0), active = ArrivalTarget(subway, 0)))
    }

    @Test
    fun `차량 이동부터, 그리고 승차가 없는 마지막 도보에서는 조회하지 않는다`() {
        assertNull(arrivalTarget(session(walk, subway, at = GuideStepType.RIDING), station, active = ArrivalTarget(subway, 0)))
        assertNull(arrivalTarget(session(walk, subway, at = GuideStepType.WAIT_ALIGHTING), station, active = null))
        assertNull(arrivalTarget(session(subway, walk, at = GuideStepType.WALK), station, active = null))
    }

    // ── 조회 순서와 카드 상태 ─────────────────────

    private class Harness(scope: TestScope, private val responses: MutableList<suspend () -> Result<ArrivalInfo>>) {
        val targets = MutableStateFlow<ArrivalTarget?>(null)
        val online = MutableStateFlow(true)
        val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val published = mutableListOf<ArrivalState?>()
        val callTimes = mutableListOf<Long>()
        val current get() = published.lastOrNull()

        init {
            val polling = ArrivalPolling(
                fetch = {
                    callTimes += scope.testScheduler.currentTime
                    val next = responses.removeFirstOrNull() ?: return@ArrivalPolling Result.failure(IllegalStateException("응답 없음"))
                    next()
                },
                publish = { published += it },
                now = { scope.testScheduler.currentTime },
            )
            scope.backgroundScope.launch { polling.run(targets, online, retries) }
        }
    }

    private fun ok(minutes: Int = 2): suspend () -> Result<ArrivalInfo> = { Result.success(info.copy(firstMinutes = minutes)) }
    private val fail: suspend () -> Result<ArrivalInfo> = { Result.failure(IllegalStateException("서버 오류")) }

    @Test
    fun `대상이 생기면 확인 중을 거쳐 값을 보여 주고 30초마다 조회하며, 차량 이동이 되면 카드를 지우고 멈춘다`() = runTest {
        val h = Harness(this, mutableListOf(ok(2), ok(1), ok(0)))
        runCurrent()
        h.targets.value = ArrivalTarget(subway, 1)
        runCurrent()
        assertEquals(listOf(null, ArrivalState.Loading, ArrivalState.Live(info.copy(firstMinutes = 2))), h.published)

        advanceTimeBy(60_001)
        assertEquals(listOf(0L, 30_000L, 60_000L), h.callTimes)
        assertEquals(ArrivalState.Live(info.copy(firstMinutes = 0)), h.current)

        h.targets.value = null
        runCurrent()
        assertNull(h.current)
        advanceTimeBy(120_000)
        assertEquals(3, h.callTimes.size)
    }

    @Test
    fun `3초 넘는 응답과 실패는 2번까지 확인 중을 유지하고 3번째에 오류 카드, 이후 성공하면 다시 값을 보여 준다`() = runTest {
        val slow: suspend () -> Result<ArrivalInfo> = { delay(3_500); Result.success(info) }
        val h = Harness(this, mutableListOf(slow, fail, fail, ok()))
        h.targets.value = ArrivalTarget(subway, 1)
        advanceTimeBy(30_001)
        assertEquals(ArrivalState.Loading, h.current)
        advanceTimeBy(30_000)
        assertEquals(3, h.callTimes.size)
        assertEquals(ArrivalState.Failed(3), h.current)
        advanceTimeBy(30_000)
        assertEquals(ArrivalState.Live(info), h.current)
    }

    @Test
    fun `오류 카드에서 다시 시도를 누르면 30초를 기다리지 않고 바로 조회한다`() = runTest {
        val h = Harness(this, mutableListOf(fail, fail, fail, ok()))
        h.targets.value = ArrivalTarget(subway, 1)
        advanceTimeBy(60_001)
        assertEquals(ArrivalState.Failed(3), h.current)
        advanceTimeBy(5_000)
        h.retries.tryEmit(Unit)
        runCurrent()
        assertEquals(65_001L, h.callTimes.last())
        assertEquals(ArrivalState.Live(info), h.current)
    }

    @Test
    fun `미제공 지역이면 카드를 바꾸고 조회를 멈춘다`() = runTest {
        val h = Harness(this, mutableListOf({ Result.success(info.copy(status = ArrivalStatus.UNSUPPORTED_AREA)) }))
        h.targets.value = ArrivalTarget(bus, 1)
        advanceTimeBy(120_000)
        assertEquals(ArrivalState.Unavailable(ArrivalStatus.UNSUPPORTED_AREA), h.current)
        assertEquals(1, h.callTimes.size)
    }

    @Test
    fun `연결이 끊기면 마지막 값을 흐리게 두고 받은 지 2분 뒤 숨기며, 연결되면 바로 다시 조회한다`() = runTest {
        val h = Harness(this, mutableListOf(ok(), ok(5)))
        h.targets.value = ArrivalTarget(subway, 1)
        runCurrent()
        advanceTimeBy(10_000)
        h.online.value = false
        runCurrent()
        assertEquals(ArrivalState.Offline(info), h.current)

        advanceTimeBy(110_001) // 받은 지 2분
        assertEquals(ArrivalState.Offline(null), h.current)
        assertEquals(1, h.callTimes.size)

        h.online.value = true
        runCurrent()
        assertEquals(2, h.callTimes.size)
        assertEquals(ArrivalState.Live(info.copy(firstMinutes = 5)), h.current)
    }

    @Test
    fun `재탐색으로 안내가 새로 시작되면 승차 구간이 같아도 조회를 처음부터 다시 한다`() = runTest {
        val h = Harness(this, mutableListOf({ Result.success(info.copy(status = ArrivalStatus.MATCH_FAILED)) }, ok()))
        h.targets.value = ArrivalTarget(subway, 1)
        advanceTimeBy(60_000)
        assertEquals(ArrivalState.Unavailable(ArrivalStatus.MATCH_FAILED), h.current)
        assertEquals(1, h.callTimes.size)

        // 새 안내는 도착 카드가 비어 있는 채로 시작하므로, 같은 구간이어도 다시 확인 중 → 값을 보여 줘야 한다
        h.targets.value = ArrivalTarget(subway, 2)
        runCurrent()
        assertEquals(2, h.callTimes.size)
        assertEquals(listOf(ArrivalState.Loading, ArrivalState.Live(info)), h.published.takeLast(2))
    }

    @Test
    fun `위치·시간 갱신으로 세션이 바뀌어도 대상은 그대로고, 안내 번호가 바뀌면 새 대상이 된다`() = runTest {
        val start = session(walk, subway, at = GuideStepType.WAIT_BOARDING).copy(guideId = 1)
        val sessions = MutableStateFlow<GuideSession?>(start)
        val targets = mutableListOf<ArrivalTarget?>()
        backgroundScope.launch { arrivalTargets(sessions, MutableStateFlow(null)).toList(targets) }
        runCurrent()
        sessions.value = start.copy(remainingStops = 3, arrival = ArrivalState.Loading) // 평소 갱신
        runCurrent()
        sessions.value = start.copy(guideId = 2) // 재탐색
        runCurrent()
        assertEquals(listOf(null, ArrivalTarget(subway, 1), ArrivalTarget(subway, 2)), targets)
    }

    // ── 화면·알림 글자 ─────────────────────────

    @Test
    fun `잠금화면 알림 한 줄은 FR-03 형식이고, 연결이 끊기면 기준 시각만 남긴다`() {
        assertEquals("2호선 2분 후 · 다음 7분 후 (14:02 기준)", arrivalNotificationLine(ArrivalState.Live(info)))
        assertEquals("2호선 곧 도착 (14:02 기준)", arrivalNotificationLine(ArrivalState.Live(info.copy(firstMinutes = 0, secondMinutes = null))))
        assertEquals("연결 끊김 · 14:02 기준", arrivalNotificationLine(ArrivalState.Offline(info)))
        assertEquals("실시간 미제공 지역", arrivalNotificationLine(ArrivalState.Unavailable(ArrivalStatus.UNSUPPORTED_AREA)))
    }

    @Test
    fun `카드는 버스면 다음 버스, 연결 끊김이면 흐리게, 오류면 다시 시도를 보여 준다`() {
        val live = arrivalCardText(ArrivalState.Live(info), TransitMode.BUS)
        assertEquals("14:02 기준", live.baseTime)
        assertEquals("2분 후", live.main)
        assertEquals(listOf("다음 버스 7분 후"), live.body)
        assertTrue(live.showSource)

        val offline = arrivalCardText(ArrivalState.Offline(info), TransitMode.SUBWAY)
        assertEquals("연결 끊김 · 14:02 기준", offline.baseTime)
        assertTrue(offline.dimmed)

        assertTrue(arrivalCardText(ArrivalState.Failed(3), TransitMode.SUBWAY).showRetry)
    }

    @Test
    fun `연결 끊김 배너는 위치 부정확 다음, 알림 꺼짐 앞에 쌓는다`() {
        val s = session(walk, subway, at = GuideStepType.WALK).copy(isLocationInaccurate = true)
        assertEquals(
            listOf(GuideBanner.LOCATION_INACCURATE, GuideBanner.OFFLINE, GuideBanner.NOTIFICATIONS_OFF),
            guideBanners(s, notificationsEnabled = false, online = false),
        )
    }
}
