package com.bloom

import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.data.fake.FakeRouteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRouteRepositoryTest {

    @Test
    fun `가짜 경로의 구간 좌표는 출발지에서 목적지까지 끊김 없이 이어진다`() = runTest {
        val origin = LatLng.SeoulCityHall
        val destination = Place("p1", "목적지", "주소", LatLng(37.5551, 126.9228))

        val candidates = FakeRouteRepository().searchRoutes(origin, destination).getOrThrow()

        candidates.forEach { route ->
            val paths = route.segments.map { it.path }
            assertTrue("${route.id}: 좌표 없는 구간", paths.all { it.size >= 2 })
            assertEquals(origin, paths.first().first())
            assertEquals(destination.location, paths.last().last())
            paths.zipWithNext { prev, next -> assertEquals(prev.last(), next.first()) }
        }
    }
}
