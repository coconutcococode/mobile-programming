package com.bloom

import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import com.bloom.core.model.toGuideSteps
import com.bloom.feature.guide.GuideBanner
import com.bloom.feature.guide.GuidePrimaryAction
import com.bloom.feature.guide.canCorrectStation
import com.bloom.feature.guide.estimatedPassedStops
import com.bloom.feature.guide.guideBanners
import com.bloom.feature.guide.primaryAction
import com.bloom.feature.guide.primaryHint
import com.bloom.feature.guide.stationChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 안내 진행 화면(S-04)에 어떤 배너·버튼·현재 역 고치기를 보여 줄지. */
class GuideScreenStateTest {

    private val a = LatLng(37.55, 126.92)
    private val b = LatLng(37.56, 126.93)
    private val subway = Segment(TransitMode.SUBWAY, 6, "2호선", "이대역", "홍대입구역", stopCount = 3, path = listOf(a, b))
    private val bus = Segment(TransitMode.BUS, 10, "7016", "A정류장", "B정류장", stopCount = 5, path = listOf(a, b))
    private val walk = Segment(TransitMode.WALK, 4, "도보", "", "이대역", distanceMeters = 300, path = listOf(a, a))

    private fun session(vararg segments: Segment, at: GuideStepType, nth: Int = 0): GuideSession {
        val route = RouteCandidate("r", Place("p", "목적지", "주소", b), 20, LocalTime.NOON, 0, 600, segments.toList())
        val steps = route.toGuideSteps()
        val index = steps.withIndex().filter { it.value.type == at }[nth].index
        return GuideSession(route, steps, index)
    }

    @Test
    fun `배너는 경로 이탈 - 위치 부정확 - 알림 꺼짐 순으로 해당하는 것만 쌓는다`() {
        val s = session(walk, subway, at = GuideStepType.WALK)
        assertEquals(emptyList<GuideBanner>(), guideBanners(s, notificationsEnabled = true))
        assertEquals(listOf(GuideBanner.NOTIFICATIONS_OFF), guideBanners(s, notificationsEnabled = false))
        assertEquals(
            listOf(GuideBanner.OFF_ROUTE, GuideBanner.LOCATION_INACCURATE, GuideBanner.NOTIFICATIONS_OFF),
            guideBanners(s.copy(isOffRoute = true, isLocationInaccurate = true), notificationsEnabled = false),
        )
    }

    @Test
    fun `주 버튼은 단계에 맞게 바뀌고 경로 이탈이면 재탐색, 마지막 단계면 안내 종료`() {
        assertEquals(GuidePrimaryAction.NEXT, primaryAction(session(walk, subway, at = GuideStepType.WALK)))
        assertEquals(GuidePrimaryAction.REROUTE, primaryAction(session(walk, subway, at = GuideStepType.WALK).copy(isOffRoute = true)))
        assertEquals(GuidePrimaryAction.BOARD, primaryAction(session(walk, subway, at = GuideStepType.WAIT_BOARDING)))
        assertEquals(GuidePrimaryAction.ALIGHT, primaryAction(session(walk, subway, at = GuideStepType.WAIT_ALIGHTING)))
        assertEquals(GuidePrimaryAction.END, primaryAction(session(walk, subway, at = GuideStepType.ARRIVED).copy(isOffRoute = true)))
    }

    @Test
    fun `승차 버튼 위 설명은 버스와 열차를 구분한다`() {
        assertEquals("열차에 탔다면 눌러 주세요", primaryHint(session(subway, at = GuideStepType.WAIT_BOARDING)))
        assertEquals("버스에 탔다면 눌러 주세요", primaryHint(session(bus, at = GuideStepType.WAIT_BOARDING)))
        assertEquals("지금 위치에서 경로를 다시 찾아요", primaryHint(session(walk, subway, at = GuideStepType.WALK).copy(isOffRoute = true)))
        assertNull(primaryHint(session(walk, subway, at = GuideStepType.WALK)))
    }

    @Test
    fun `현재 역 고치기는 지하철 차량 이동과 하차 대기에서만 보인다`() {
        assertTrue(canCorrectStation(session(subway, at = GuideStepType.RIDING)))
        assertTrue(canCorrectStation(session(subway, at = GuideStepType.WAIT_ALIGHTING)))
        assertFalse(canCorrectStation(session(subway, at = GuideStepType.WAIT_BOARDING)))
        assertFalse(canCorrectStation(session(bus, at = GuideStepType.RIDING)))
        assertFalse(canCorrectStation(session(subway.copy(durationMinutes = 0), at = GuideStepType.RIDING))) // 시간 추정을 안 함
    }

    @Test
    fun `역 목록은 승차역부터 하차역까지이고 순번이 지나온 역 수, 지금 추정한 역을 알려 준다`() {
        assertEquals(
            listOf("이대역 (승차역)", "승차역에서 1번째 역", "승차역에서 2번째 역", "홍대입구역 (하차역)"),
            stationChoices(subway),
        )
        val riding = session(subway, at = GuideStepType.RIDING)
        assertEquals(1, estimatedPassedStops(riding.copy(remainingStops = 2)))
        assertEquals(3, estimatedPassedStops(riding.copy(remainingStops = 1, isAtAlightingStation = true)))
        assertNull(estimatedPassedStops(riding.copy(remainingStops = null)))
    }
}
