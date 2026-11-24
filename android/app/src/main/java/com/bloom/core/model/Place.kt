package com.bloom.core.model

data class LatLng(val latitude: Double, val longitude: Double) {
    companion object {
        /** 위치 권한 전 기본 지역(S-01-0): 서울시청 주변 */
        val SeoulCityHall = LatLng(37.5662952, 126.9779451)
    }
}

/** 주변 장소 카테고리 4종 (FR-04) */
enum class PlaceCategory(val label: String) {
    CAFE("카페"),
    CONVENIENCE_STORE("편의점"),
    RESTAURANT("음식점"),
    CULTURE("문화시설"),
}

enum class NearbySort(val label: String) {
    RECOMMENDED("추천순"),
    DISTANCE("거리순"),
}

/**
 * 장소. 카카오 응답 중 저장 가능한 값은 장소 ID 하나뿐이다(NFR-11).
 * 나머지 필드는 화면 표시용으로만 메모리에 들고 있는다.
 */
data class Place(
    val id: String,
    val name: String,
    val address: String,
    val location: LatLng,
    val category: PlaceCategory? = null,
    val phone: String? = null,
    val placeUrl: String? = null,
    val distanceMeters: Int? = null,
    val walkMinutes: Int? = null,
    val recommendReason: String? = null,
)

object PlaceLimits {
    const val MAX_AUTOCOMPLETE = 10
    const val SEARCH_DEBOUNCE_MILLIS = 300L

    /** 자동완성 한 번이 검색 기준 위치를 기다리는 최대 시간. 넘으면 위치 없이 검색 */
    const val SEARCH_LOCATION_WAIT_MILLIS = 1_000L
    const val MAX_RECENT_DESTINATIONS = 20
    const val NEARBY_RADIUS_METERS = 500
    const val MAX_NEARBY = 10
}
