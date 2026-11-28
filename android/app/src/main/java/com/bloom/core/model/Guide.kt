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

/** 안내 화면의 실시간 도착 카드(S-04)와 잠금화면 알림 한 줄(S-05)이 보여 줄 상태. UI 설계서 4.2 S-04-1~5 */
sealed interface ArrivalState {
    /** S-04-2 승차 지점 500m에 들어와 첫 조회 중 */
    data object Loading : ArrivalState

    /** S-04-1 실시간 값 수신 */
    data class Live(val info: ArrivalInfo) : ArrivalState

    /** S-04-3 서울 밖 정류장([ArrivalStatus.UNSUPPORTED_AREA]) 또는 정류소 대조 실패([ArrivalStatus.MATCH_FAILED]). 안내는 그대로 계속 */
    data class Unavailable(val status: ArrivalStatus) : ArrivalState

    /** S-04-4 조회가 연달아 실패. 30초마다 계속 다시 시도한다 */
    data class Failed(val failures: Int) : ArrivalState

    /** S-04-5 연결 끊김. 마지막 값을 흐리게 보여 주고, 받은 지 2분이 지나면 [last] 를 비운다 */
    data class Offline(val last: ArrivalInfo?) : ArrivalState
}

/**
 * 진행 중인 안내 1건. 동시에 하나만 존재한다(FR-02).
 * 앱 화면(S-04)과 잠금화면 알림(S-05)이 이 상태 하나를 같이 본다.
 */
data class GuideSession(
    val route: RouteCandidate,
    val steps: List<GuideStep>,
    val currentStepIndex: Int = 0,
    /** 실시간 도착 카드. 조회 대상이 아닌 단계(차량 이동 이후, 승차 지점 500m 밖)면 null */
    val arrival: ArrivalState? = null,
    /** 도보 중 경로선 50m 이상 20초 → '재탐색' 버튼 (FR-06) */
    val isOffRoute: Boolean = false,
    /** 부정확한 위치만 30초 이상 → '위치가 부정확합니다' + '다음 단계' 버튼 (UC-01 A5) */
    val isLocationInaccurate: Boolean = false,
    /** 좌표가 없어 이 단계는 자동으로 못 넘어감 → '다음 단계' 버튼 */
    val needsManualAdvance: Boolean = false,
    /** 남은 정거장(경과 시간 추정). 차량 이동·하차 대기 단계에서만 값이 있다 */
    val remainingStops: Int? = null,
    /** 지하철 하차역 추정 도착 시각이 지남 → "○○역에서 내리세요" */
    val isAtAlightingStation: Boolean = false,
    /** 위치 서비스가 다음에 쓸 위치 갱신 간격 */
    val locationIntervalMillis: Long = 5_000L,
    /** 안내를 시작(재탐색 포함)할 때마다 바뀌는 번호. 같은 경로여도 새로 시작한 안내인지 구분한다 */
    val guideId: Long = 0,
) {
    val currentStep: GuideStep get() = steps[currentStepIndex]
    val isLastStep: Boolean get() = currentStepIndex == steps.lastIndex

    /** '다음 단계' 버튼을 보여 줄지 */
    val showNextButton: Boolean get() = isLocationInaccurate || needsManualAdvance
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
            path = when {
                // 한쪽이라도 경로선이 없으면 비운다. 일부만 남기면 엔진이 그걸 전체 경로로 믿고 경로 이탈을 잘못 판단한다
                last.path.isEmpty() || seg.path.isEmpty() -> emptyList()
                // 앞 구간 끝점과 뒤 구간 첫 점이 같으면 한 번만 넣는다
                last.path.last() == seg.path.first() -> last.path + seg.path.drop(1)
                else -> last.path + seg.path
            },
        )
    } else {
        merged += seg
    }
    merged
}
