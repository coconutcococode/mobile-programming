package com.bloom

import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.TransitMode
import com.bloom.data.fake.FakeRouteRepository
import com.bloom.feature.route.routeCenter
import com.bloom.feature.route.routeDestinationMarker
import com.bloom.feature.route.routeFitPoints
import com.bloom.feature.route.routeMapSegments
import com.bloom.feature.route.routeOrigin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 경로 상세(S-03) 지도에 올릴 경로선·핀·카메라 범위. */
class RouteMapTest {

    private val origin = LatLng.SeoulCityHall
    private val destination = Place("p1", "목적지", "주소", LatLng(37.5551, 126.9228))

    private suspend fun firstRoute() = FakeRouteRepository().searchRoutes(origin, destination).getOrThrow().first()

    @Test
    fun `경로선은 구간마다 하나씩, 구간의 이동 수단과 좌표를 그대로 쓴다`() = runTest {
        val route = firstRoute()
        val lines = routeMapSegments(route)
        assertEquals(route.segments.size, lines.size)
        assertEquals(route.segments.map { it.mode }, lines.map { it.mode })
        assertEquals(route.segments.map { it.path }, lines.map { it.points })
        assertTrue(lines.any { it.mode == TransitMode.WALK } && lines.any { it.mode != TransitMode.WALK })
    }

    @Test
    fun `출발점은 첫 구간 첫 좌표, 목적지 핀은 목적지 위치에 강조로 찍힌다`() = runTest {
        val route = firstRoute()
        assertEquals(origin, routeOrigin(route))
        val pin = routeDestinationMarker(route)
        assertEquals(destination.location, pin.point)
        assertTrue(pin.selected)
    }

    @Test
    fun `카메라는 모든 경로 좌표와 목적지를 감싸고, 처음 중심은 그 범위의 가운데`() = runTest {
        val route = firstRoute()
        val points = routeFitPoints(route)
        assertTrue(points.containsAll(route.segments.flatMap { it.path }))
        assertTrue(destination.location in points)

        val center = routeCenter(route)
        assertEquals((points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2, center.latitude, 1e-9)
        assertEquals((points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2, center.longitude, 1e-9)
    }

    @Test
    fun `좌표가 하나도 없는 경로는 출발점이 없고 카메라는 목적지만 본다`() = runTest {
        val route = firstRoute().let { r -> r.copy(segments = r.segments.map { it.copy(path = emptyList()) }) }
        assertEquals(null, routeOrigin(route))
        assertEquals(listOf(destination.location), routeFitPoints(route))
        assertEquals(destination.location, routeCenter(route))
    }
}
