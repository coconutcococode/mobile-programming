package com.bloom.core.model

/**
 * 안내 중 받은 위치 1건.
 * 정확도 기준(50m 초과 제외)과 경로 이탈 시간(20초) 판단은 길안내 엔진이 이 값으로 한다.
 *
 * @param accuracyMeters 위치 오차 반경(m). 클수록 부정확하다. 기기가 정확도를 주지 않으면 null(부정확으로 취급).
 * @param elapsedRealtimeMillis 측정 시각. 기기 부팅 후 경과 시간(ms)이라 사용자가 시계를 바꿔도 간격 계산이 틀어지지 않는다.
 */
data class LocationFix(
    val position: LatLng,
    val accuracyMeters: Float?,
    val elapsedRealtimeMillis: Long,
)
