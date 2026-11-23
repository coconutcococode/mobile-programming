package com.bloom.feature.guide

import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode

/*
 * 안내 진행 화면(S-04)에서 무엇을 보여 줄지 정하는 규칙. 화면 그리기와 분리해 단위 테스트로 확인한다.
 */

/** S-04 상단 배너. 여러 개면 급한 것부터 이 순서로 위에서 쌓는다. */
enum class GuideBanner(val message: String, val actionLabel: String) {
    /** S-04-B3 경로 이탈(FR-06) */
    OFF_ROUTE("경로를 벗어났습니다", "재탐색"),

    /** S-04-B2 위치 부정확(UC-01 A5): 자동 전환이 멈추므로 손으로 넘기게 한다 */
    LOCATION_INACCURATE("위치가 정확하지 않아요 · 단계를 직접 넘겨 주세요", "다음 단계"),

    /** S-04-B1 알림 권한 거절(UC-01 A1): 앱 화면으로는 계속 안내하지만 잠금화면에는 안 보인다 */
    NOTIFICATIONS_OFF("알림이 꺼져 있어 잠금화면에 안내가 보이지 않아요", "설정 열기"),
}

fun guideBanners(session: GuideSession, notificationsEnabled: Boolean): List<GuideBanner> = buildList {
    if (session.isOffRoute) add(GuideBanner.OFF_ROUTE)
    if (session.isLocationInaccurate) add(GuideBanner.LOCATION_INACCURATE)
    if (!notificationsEnabled) add(GuideBanner.NOTIFICATIONS_OFF)
}

/** 하단 큰 버튼 하나가 할 일. 경로 이탈이면 주 버튼을 재탐색으로 바꾼다(UI 설계 3.2). */
enum class GuidePrimaryAction(val label: String) {
    REROUTE("새 경로로 재탐색"),
    BOARD("승차"),
    ALIGHT("하차 완료"),
    NEXT("다음 단계"),
    END("안내 종료"),
}

fun primaryAction(session: GuideSession): GuidePrimaryAction = when {
    session.isLastStep -> GuidePrimaryAction.END
    session.isOffRoute -> GuidePrimaryAction.REROUTE
    session.currentStep.type == GuideStepType.WAIT_BOARDING -> GuidePrimaryAction.BOARD
    session.currentStep.type == GuideStepType.WAIT_ALIGHTING -> GuidePrimaryAction.ALIGHT
    else -> GuidePrimaryAction.NEXT
}

/** 주 버튼 위 한 줄 설명. */
fun primaryHint(session: GuideSession): String? = when (primaryAction(session)) {
    GuidePrimaryAction.REROUTE -> "지금 위치에서 경로를 다시 찾아요"
    GuidePrimaryAction.BOARD -> when (session.currentStep.segment?.mode) {
        TransitMode.BUS -> "버스에 탔다면 눌러 주세요"
        else -> "열차에 탔다면 눌러 주세요"
    }
    GuidePrimaryAction.ALIGHT -> "내렸다면 눌러 주세요"
    else -> null
}

/** 단계 카드 위 작은 글씨("현재 단계 · 승차 대기"). */
fun stepTypeLabel(type: GuideStepType): String = when (type) {
    GuideStepType.WALK -> "도보 이동"
    GuideStepType.WAIT_BOARDING -> "승차 대기"
    GuideStepType.RIDING -> "차량 이동"
    GuideStepType.WAIT_ALIGHTING -> "하차 대기"
    GuideStepType.ALIGHTED -> "하차 완료"
    GuideStepType.ARRIVED -> "도착"
}

/**
 * '현재 역 고치기'(UC-01 A9)를 보여 줄지. 지하철은 지하에서 위치를 못 받아 시간으로 현재 역을 추정하므로,
 * 차량 이동·하차 대기 단계에서만 사용자가 실제 역으로 바로잡을 수 있게 한다.
 */
fun canCorrectStation(session: GuideSession): Boolean {
    val segment = session.currentStep.segment ?: return false
    // 소요 시간을 모르면 시간 추정 자체를 하지 않으므로(다음 단계 버튼으로 진행) 고칠 것도 없다
    return segment.mode == TransitMode.SUBWAY && segment.durationMinutes > 0 &&
        session.currentStep.type in setOf(GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING)
}

/**
 * 현재 역 고치기에서 고를 수 있는 역. 목록 순번 = 승차역에서 지나온 역 수(0 = 승차역, 마지막 = 하차역).
 * 경로 데이터에 중간 역 이름이 없어 중간 역은 순번으로 보여 준다.
 */
fun stationChoices(segment: Segment): List<String> {
    val stops = segment.stopCount.coerceAtLeast(1)
    return (0..stops).map { passed ->
        when (passed) {
            0 -> "${segment.startName} (승차역)"
            stops -> "${segment.endName} (하차역)"
            else -> "승차역에서 ${passed}번째 역"
        }
    }
}

/** 지금 추정하고 있는 역(지나온 역 수). 목록에서 '지금 표시 중'으로 강조한다. 추정할 수 없으면 null. */
fun estimatedPassedStops(session: GuideSession): Int? {
    val segment = session.currentStep.segment ?: return null
    val stops = segment.stopCount.coerceAtLeast(1)
    if (session.isAtAlightingStation) return stops
    val remaining = session.remainingStops ?: return null
    return (stops - remaining).coerceIn(0, stops)
}
