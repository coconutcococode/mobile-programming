package com.bloom.core.model

import java.time.LocalTime

/** 안내 단계 종류 — 요구사항 분석서 표 2-1: 도보 이동 → 승차 대기 → 차량 이동 → 하차 대기 → 하차 완료 → … → 도착 */
enum class GuideStepType { WALK, WAIT_BOARDING, RIDING, WAIT_ALIGHTING, ALIGHTED, ARRIVED }

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
 * 경로 → 안내 단계 목록 (분석서 표 2-1).
 * - 도보 구간 1개 = '도보 이동' 1단계. 도보 구간이 연달아 오면 하나로 합친다(합치지 않으면 앞 도보 단계는 넘어갈 목표가 없음)
 * - 대중교통 구간 1개 = 승차 대기 · 차량 이동 · 하차 대기 · 하차 완료 4단계
 * - 지하철 → 지하철 환승은 하차 완료 없이 "갈아타세요" 안내 후 바로 다음 열차의 승차 대기 (UC-01 A8)
 * - 마지막에 '도착' 1단계
 * 환승 4회(대중교통 5구간 + 도보 6구간)여도 6 + 5×4 + 1 = 27단계로 [RouteLimits.MAX_GUIDE_STEPS] 이내.
 */
fun RouteCandidate.toGuideSteps(): List<GuideStep> {
    val steps = mutableListOf<GuideStep>()
    fun add(type: GuideStepType, title: String, detail: String, segment: Segment?) {
        steps += GuideStep(steps.size, type, title, detail, segment)
    }
    val segments = this.segments.mergeConsecutiveWalks() // 아래에서는 합친 목록만 쓴다
    segments.forEachIndexed { i, seg ->
        when (seg.mode) {
            TransitMode.WALK -> add(GuideStepType.WALK, "${seg.endName}까지 걷기", "도보 ${seg.durationMinutes}분 · ${seg.distanceMeters}m", seg)
            TransitMode.BUS, TransitMode.SUBWAY -> {
                val isSubway = seg.mode == TransitMode.SUBWAY
                val transfersFromSubway = isSubway && segments.getOrNull(i - 1)?.mode == TransitMode.SUBWAY
                val transfersToSubway = isSubway && segments.getOrNull(i + 1)?.mode == TransitMode.SUBWAY
                add(
                    GuideStepType.WAIT_BOARDING,
                    if (transfersFromSubway) "${seg.label} 갈아타기" else "${seg.startName}에서 ${seg.label} 승차",
                    "${seg.endName} 방면",
                    seg,
                )
                add(GuideStepType.RIDING, "${seg.label} 이동 중", "${seg.stopCount}개 ${if (isSubway) "역" else "정류장"} · ${seg.durationMinutes}분", seg)
                add(
                    GuideStepType.WAIT_ALIGHTING,
                    when {
                        transfersToSubway -> "다음 역에서 환승"
                        isSubway -> "다음 역에서 하차"
                        else -> "다음 정류장에서 하차"
                    },
                    "${seg.endName}에서 내리세요",
                    seg,
                )
                if (!transfersToSubway) add(GuideStepType.ALIGHTED, "${seg.endName} 하차 완료", "", seg)
            }
        }
    }
    add(GuideStepType.ARRIVED, "${destination.name} 도착", destination.address, null)
    return steps
}

/** 연달아 오는 도보 구간을 하나로 합친다: 시간·거리는 더하고, 경로선은 이어 붙이고, 도착지는 마지막 구간 것. */
private fun List<Segment>.mergeConsecutiveWalks(): List<Segment> = fold(mutableListOf()) { merged, seg ->
    val last = merged.lastOrNull()
    if (last != null && last.mode == TransitMode.WALK && seg.mode == TransitMode.WALK) {
        merged[merged.lastIndex] = last.copy(
            durationMinutes = last.durationMinutes + seg.durationMinutes,
            endName = seg.endName,
            distanceMeters = last.distanceMeters + seg.distanceMeters,
            // 앞 구간 끝점과 뒤 구간 첫 점이 같으면 한 번만 넣는다
            path = last.path + if (last.path.lastOrNull() == seg.path.firstOrNull()) seg.path.drop(1) else seg.path,
        )
    } else {
        merged += seg
    }
    merged
}
