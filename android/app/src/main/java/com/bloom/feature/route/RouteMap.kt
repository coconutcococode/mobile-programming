package com.bloom.feature.route

import com.bloom.core.map.MapMarker
import com.bloom.core.map.MapRouteSegment
import com.bloom.core.model.LatLng
import com.bloom.core.model.RouteCandidate

/*
 * 경로 상세(S-03) 지도에 올릴 값. 경로 좌표는 화면에 그릴 때만 쓰고 저장하지 않는다(NFR-11).
 */

/** 구간마다 이동 수단 색으로 그릴 경로선(팀 합의 11/22). 좌표가 없는 구간은 지도 부품이 건너뛴다. */
fun routeMapSegments(route: RouteCandidate): List<MapRouteSegment> =
    route.segments.map { MapRouteSegment(it.mode, it.path) }

/** 경로 전체가 지도에 들어오게 맞출 점들: 모든 구간 좌표 + 목적지. */
fun routeFitPoints(route: RouteCandidate): List<LatLng> =
    route.segments.flatMap { it.path } + route.destination.location

/**
 * 출발점 = 첫 구간의 첫 좌표. 첫 구간에 좌표가 없으면 null(출발점 핀을 찍지 않음).
 * 뒤 구간의 첫 좌표(승차 정류장 등)를 쓰면 실제 출발 위치가 아닌 곳에 출발점이 찍힌다.
 */
fun routeOrigin(route: RouteCandidate): LatLng? =
    route.segments.firstOrNull()?.path?.firstOrNull()

/** 목적지 핀(선택 상태 색으로 강조). */
fun routeDestinationMarker(route: RouteCandidate): MapMarker =
    MapMarker(id = DESTINATION_MARKER_ID, point = route.destination.location, selected = true)

/** 지도를 처음 열 때 중심: 경로 전체를 감싸는 사각형의 가운데. */
fun routeCenter(route: RouteCandidate): LatLng {
    val points = routeFitPoints(route)
    return LatLng(
        (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2,
        (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2,
    )
}

private const val DESTINATION_MARKER_ID = "destination"
