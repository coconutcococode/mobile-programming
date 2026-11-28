package com.bloom.core.guide

/**
 * 길안내 단계 전환 기준값. 값은 이 한 곳에서만 관리한다.
 * 근거: 분석서 표 2-1(단계 전환), UC-01 A5(정확도), FR-06(경로 이탈), FR-03·NFR-03(실시간 조회), NFR-04(갱신 간격).
 * '잠정' 표시는 분석서 표 4-4의 잠정값으로, 현장 테스트 후 확정한다.
 */
object GuideThresholds {
    /** 도보 이동 → 승차 대기: 승차 지점(역) 50m 이내 */
    const val BOARDING_APPROACH_METERS = 50.0

    /** 버스 승차 대기 → 차량 이동: 승차 지점에서 100m 이상 멀어짐 */
    const val BUS_DEPARTED_METERS = 100.0

    /** 버스 차량 이동 → 하차 대기: 하차 지점 400m 이내 */
    const val BUS_ALIGHT_SOON_METERS = 400.0

    /** 버스 하차 대기 → 하차 완료: 하차 지점 50m 이내 */
    const val BUS_ALIGHTED_METERS = 50.0

    /** 마지막 도보 → 도착: 목적지 30m 이내 */
    const val DESTINATION_ARRIVED_METERS = 30.0

    /** 지하철 하차 자동 확인 반경 (잠정 200m) */
    const val SUBWAY_ALIGHTED_METERS = 200.0

    /** 지하철 하차역 추정 도착 후 하차 완료로 처리할 때까지 대기 (잠정 5분) */
    const val SUBWAY_ALIGHT_TIMEOUT_MILLIS = 5 * 60_000L

    /** 이보다 오차가 크거나 정확도를 모르는 위치는 단계 전환에 쓰지 않는다 */
    const val MAX_ACCURACY_METERS = 50f

    /** 부정확한 위치만 이 시간 이상 계속되면 '위치가 부정확합니다' + '다음 단계' 버튼 (UC-01 A5) */
    const val INACCURATE_ALERT_MILLIS = 30_000L

    /** 위치 등록이 실패했을 때 다시 등록하기까지 기다리는 시간 */
    const val LOCATION_RETRY_DELAY_MILLIS = 10_000L

    /** 도보 중 경로선에서 이 거리 이상 떨어진 상태가 */
    const val OFF_ROUTE_METERS = 50.0

    /** 이 시간 이상 계속되면 경로 이탈 (FR-06) */
    const val OFF_ROUTE_MILLIS = 20_000L

    /** 승차 지점 이 거리 이내의 승차 전 단계에서만 실시간 도착 정보를 조회 (잠정 500m) */
    const val ARRIVAL_INFO_START_METERS = 500.0

    /** 실시간 도착 정보 조회 간격 (NFR-03, 30초 ±2초) */
    const val ARRIVAL_POLL_INTERVAL_MILLIS = 30_000L

    /** 도착 정보 응답이 이보다 늦으면 실패로 센다 (NFR-06) */
    const val ARRIVAL_TIMEOUT_MILLIS = 3_000L

    /** 이만큼 연달아 실패하면 오류 카드(S-04-4)로 바꾼다 (NFR-06) */
    const val ARRIVAL_MAX_FAILURES = 3

    /** 연결이 끊긴 뒤 마지막 도착 정보를 보여 주는 시간. 지나면 숨긴다 (분석서 표 4-3) */
    const val ARRIVAL_STALE_MILLIS = 2 * 60_000L

    /** 기본 위치 갱신 간격 (UC-01 4단계) */
    const val LOCATION_INTERVAL_MILLIS = 5_000L

    /** 지하철 차량 이동 중 위치 갱신 간격 (잠정 30초, NFR-04) */
    const val SUBWAY_RIDING_LOCATION_INTERVAL_MILLIS = 30_000L

    /** '도착' 표시 후 알림을 지울 때까지 */
    const val ARRIVED_NOTIFICATION_CLEAR_MILLIS = 60_000L

    /**
     * '하차 완료' 문구를 보여 준 뒤 다음 단계로 넘어갈 때까지.
     * 분석서에 값이 없어 위치 갱신 1회 간격으로 잠정 설정 → UI 설계서·현장 테스트로 확정 필요.
     */
    const val ALIGHTED_HOLD_MILLIS = 5_000L
}
