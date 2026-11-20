package com.bloom.core.model

import java.time.LocalTime

/** 안내 단계 종류 — 요구사항 분석서 표 2-1: 도보 → 승차 대기 → 차량 이동 → 하차 대기 → 하차 완료 */
enum class GuideStepType { WALK, WAIT_BOARDING, RIDING, WAIT_ALIGHTING, ARRIVED }

data class GuideStep(
    val index: Int,
    val type: GuideStepType,
    val title: String,
    val detail: String,
    val segment: Segment?,
)

enum class ArrivalStatus { OK, UNSUPPORTED_AREA, MATCH_FAILED, ERROR }

/** 승차 전 실시간 도착 정보(FR-03). 항상 기준 시각과 함께 표시한다(NFR-03). */
data class ArrivalInfo(
    val routeLabel: String,
    val firstMinutes: Int?,
    val secondMinutes: Int?,
    val baseTime: LocalTime,
    val status: ArrivalStatus,
)

/**
 * 진행 중인 안내 1건. 동시에 하나만 존재한다(FR-02).
 * 앱 화면(S-04)과 잠금화면 알림(S-05)이 이 상태 하나를 같이 본다.
 */
data class GuideSession(
    val route: RouteCandidate,
    val steps: List<GuideStep>,
    val currentStepIndex: Int = 0,
    val arrival: ArrivalInfo? = null,
    val isOffRoute: Boolean = false,
) {
    val currentStep: GuideStep get() = steps[currentStepIndex]
    val isLastStep: Boolean get() = currentStepIndex == steps.lastIndex
}

/**
 * 경로 → 안내 단계 목록. 초안이며 단계 생성 규칙은 트랙 B(길안내)에서 확정한다.
 * 대중교통 구간 1개 = 승차 대기·차량 이동·하차 대기 3단계, 도보 구간 1개 = 1단계, 마지막에 도착 1단계.
 */
fun RouteCandidate.toGuideSteps(): List<GuideStep> {
    val steps = mutableListOf<GuideStep>()
    fun add(type: GuideStepType, title: String, detail: String, segment: Segment?) {
        steps += GuideStep(steps.size, type, title, detail, segment)
    }
    segments.forEach { seg ->
        when (seg.mode) {
            TransitMode.WALK -> add(GuideStepType.WALK, "${seg.endName}까지 걷기", "도보 ${seg.durationMinutes}분 · ${seg.distanceMeters}m", seg)
            TransitMode.BUS, TransitMode.SUBWAY -> {
                add(GuideStepType.WAIT_BOARDING, "${seg.startName}에서 ${seg.label} 승차", "${seg.endName} 방면", seg)
                add(GuideStepType.RIDING, "${seg.label} 이동 중", "${seg.stopCount}개 정류장 · ${seg.durationMinutes}분", seg)
                add(GuideStepType.WAIT_ALIGHTING, "${seg.endName}에서 하차", "하차 준비", seg)
            }
        }
    }
    add(GuideStepType.ARRIVED, "${destination.name} 도착", destination.address, null)
    return steps
}
