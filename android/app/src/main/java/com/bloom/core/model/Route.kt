package com.bloom.core.model

import java.time.LocalTime

enum class TransitMode { WALK, BUS, SUBWAY }

/**
 * 경로의 한 구간.
 * @param label 막대 안 글자 — 도보는 "도보", 버스는 노선 번호("7016"), 지하철은 호선("2호선")
 * @param path 이 구간의 경로선 좌표(카카오 응답의 구간 경로 좌표). 순서대로 이으면 구간의 선이 되고,
 *  앞 구간의 마지막 점과 다음 구간의 첫 점이 이어진다. 지도에서는 구간마다 [mode] 별 색으로 칠한다.
 *  경로 데이터라 안내 종료 시 함께 폐기한다(NFR-11).
 *  버스·지하철 구간은 첫 점 = 승차 지점, 마지막 점 = 하차 지점으로 쓰므로 이동 순서대로 두 끝점이 반드시 있어야 한다
 *  (분석서 표 2-1). 비어 있으면 길안내가 그 구간을 위치로 넘기지 못하고 '다음 단계' 버튼으로만 넘긴다.
 */
data class Segment(
    val mode: TransitMode,
    val durationMinutes: Int,
    val label: String,
    val startName: String,
    val endName: String,
    val stopCount: Int = 0,
    val distanceMeters: Int = 0,
    val path: List<LatLng> = emptyList(),
)

/** 경로 후보(S-02 카드 한 장). 안내 중에는 메모리에만 보관한다(NFR-11). */
data class RouteCandidate(
    val id: String,
    val destination: Place,
    val totalMinutes: Int,
    val arrivalTime: LocalTime,
    val transferCount: Int,
    val walkMeters: Int,
    val segments: List<Segment>,
)

enum class RouteSortType(val label: String) {
    FASTEST("최소 시간"),
    FEWEST_TRANSFERS("최소 환승"),
    LEAST_WALK("최소 도보"),
}

/** 정렬 3종. 기준값이 같으면 나머지 두 값(시간 → 환승 → 도보 순)으로 이어서 비교한다(동점 규칙은 팀 확정 전 잠정). */
fun List<RouteCandidate>.sortedFor(type: RouteSortType): List<RouteCandidate> = when (type) {
    RouteSortType.FASTEST -> sortedWith(compareBy({ it.totalMinutes }, { it.transferCount }, { it.walkMeters }))
    RouteSortType.FEWEST_TRANSFERS -> sortedWith(compareBy({ it.transferCount }, { it.totalMinutes }, { it.walkMeters }))
    RouteSortType.LEAST_WALK -> sortedWith(compareBy({ it.walkMeters }, { it.totalMinutes }, { it.transferCount }))
}

object RouteLimits {
    const val MAX_CANDIDATES = 5
    const val MAX_TRANSFERS = 4
    const val MAX_GUIDE_STEPS = 27
    const val WALK_ONLY_DISTANCE_METERS = 700
}
