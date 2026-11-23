package com.bloom.core.guide

import com.bloom.core.model.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** 길안내용 거리 계산. 도시 안 짧은 거리(수 km 이내)를 전제로 한다. */
object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** 두 지점 사이 거리(m). */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** 점에서 경로선(점을 순서대로 이은 선)까지 가장 가까운 거리(m). 경로선이 비어 있으면 null. */
    fun distanceToPathMeters(point: LatLng, path: List<LatLng>): Double? = when (path.size) {
        0 -> null
        1 -> distanceMeters(point, path[0])
        else -> path.zipWithNext().minOf { (a, b) -> distanceToSegmentMeters(point, a, b) }
    }

    /** 점을 원점으로 하는 평면에 펼쳐(짧은 거리에서 오차 작음) 선분까지의 거리를 구한다. */
    private fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val cosLat = cos(Math.toRadians(p.latitude))
        fun x(q: LatLng) = Math.toRadians(q.longitude - p.longitude) * cosLat * EARTH_RADIUS_METERS
        fun y(q: LatLng) = Math.toRadians(q.latitude - p.latitude) * EARTH_RADIUS_METERS
        val ax = x(a)
        val ay = y(a)
        val dx = x(b) - ax
        val dy = y(b) - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }
}
