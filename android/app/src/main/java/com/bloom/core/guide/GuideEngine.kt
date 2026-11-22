package com.bloom.core.guide

import com.bloom.core.model.GuideStep
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode

/**
 * 진행 중인 안내의 상태. 시각은 모두 기기 부팅 후 경과 시간(ms, [LocationFix.elapsedRealtimeMillis]와 같은 기준).
 *
 * @param boardedAt 현재 대중교통 구간에 탄 시각. 지하철 현재 역 추정과 남은 정거장 계산에 쓴다.
 * @param isLocationInaccurate 부정확한 위치만 30초 이상 계속됨 → '위치가 부정확합니다' + '다음 단계' 버튼 (UC-01 A5)
 * @param isOffRoute 도보 중 경로선에서 50m 이상 떨어진 상태가 20초 이상 계속됨 → '재탐색' 버튼 (FR-06)
 */
data class GuideProgress(
    val stepIndex: Int,
    val stepStartedAt: Long,
    val boardedAt: Long? = null,
    val inaccurateSince: Long? = null,
    val isLocationInaccurate: Boolean = false,
    val offRouteSince: Long? = null,
    val isOffRoute: Boolean = false,
)

/**
 * 길안내 단계 전환 규칙(분석서 표 2-1). 화면·서비스와 분리된 순수 로직이라 단위 테스트로 검증한다.
 * 모든 함수는 이전 상태를 받아 새 상태를 돌려준다(상태를 직접 바꾸지 않음).
 *
 * - 도보·버스: 위치로 전환 (승차 50m, 승차 지점에서 100m 멀어짐, 하차 400m, 하차 50m, 목적지 30m)
 * - 지하철: 지하에서는 위치를 못 받으므로 시간으로 추정 (승차 시각 + 구간 소요 시간)
 * - 승차·하차 지점 = 대중교통 구간 경로선의 시작·끝 좌표
 */
class GuideEngine(
    val steps: List<GuideStep>,
    private val destination: LatLng,
) {
    init {
        require(steps.isNotEmpty()) { "안내 단계가 비어 있음" }
    }

    fun start(now: Long): GuideProgress = GuideProgress(stepIndex = 0, stepStartedAt = now)

    fun currentStep(progress: GuideProgress): GuideStep = steps[progress.stepIndex]

    /** 새 위치를 받았을 때. 정확도 50m를 넘거나 모르는 위치는 전환에 쓰지 않는다. */
    fun onLocation(progress: GuideProgress, fix: LocationFix): GuideProgress {
        val now = fix.elapsedRealtimeMillis
        val accuracy = fix.accuracyMeters
        if (accuracy == null || accuracy > GuideThresholds.MAX_ACCURACY_METERS) {
            val since = progress.inaccurateSince ?: now
            return progress.copy(
                inaccurateSince = since,
                isLocationInaccurate = now - since >= GuideThresholds.INACCURATE_ALERT_MILLIS,
            )
        }
        val accurate = updateOffRoute(progress.copy(inaccurateSince = null, isLocationInaccurate = false), fix.position, now)
        return if (reachedByLocation(accurate, fix.position, now)) advance(accurate, now) else accurate
    }

    /** 위치와 상관없이 시간으로 바뀌는 전환(지하철 추정, 하차 완료 표시 시간). 서비스가 주기적으로 부른다. */
    fun onTick(progress: GuideProgress, now: Long): GuideProgress {
        val step = currentStep(progress)
        val segment = step.segment
        val boardedAt = progress.boardedAt
        val reached = when (step.type) {
            GuideStepType.RIDING ->
                segment.isSubway() && boardedAt != null && now >= alightSoonAt(segment!!, boardedAt)
            GuideStepType.WAIT_ALIGHTING ->
                segment.isSubway() && boardedAt != null &&
                    now >= estimatedArrivalAt(segment!!, boardedAt) + GuideThresholds.SUBWAY_ALIGHT_TIMEOUT_MILLIS
            GuideStepType.ALIGHTED -> now - progress.stepStartedAt >= GuideThresholds.ALIGHTED_HOLD_MILLIS
            else -> false
        }
        return if (reached) advance(progress, now) else progress
    }

    /** '승차' 버튼. 지하철은 이 시각이 현재 역 추정의 기준이 된다. */
    fun onBoardPressed(progress: GuideProgress, now: Long): GuideProgress =
        if (currentStep(progress).type == GuideStepType.WAIT_BOARDING) advance(progress, now) else progress

    /** '하차 완료' 버튼. */
    fun onAlightPressed(progress: GuideProgress, now: Long): GuideProgress =
        if (currentStep(progress).type == GuideStepType.WAIT_ALIGHTING) advance(progress, now) else progress

    /** 단계 목록에서 특정 단계를 골라 이동. 차량 이동 이후로 옮기면 지금을 승차 시각으로 본다. */
    fun moveTo(progress: GuideProgress, index: Int, now: Long): GuideProgress {
        val target = index.coerceIn(0, steps.lastIndex)
        val boardedAt = when (steps[target].type) {
            GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING, GuideStepType.ALIGHTED -> progress.boardedAt ?: now
            else -> null
        }
        return GuideProgress(stepIndex = target, stepStartedAt = now, boardedAt = boardedAt)
    }

    /** '다음 단계' 버튼(위치가 부정확할 때 등 수동 전환). */
    fun onNextPressed(progress: GuideProgress, now: Long): GuideProgress = advance(progress, now)

    /**
     * 지하철 현재 역 보정(UC-01 A9). [passedStops] = 승차역에서 지나온 역 수.
     * 승차 시각을 다시 계산해 남은 역 추정을 바로잡고, 이미 하차 대기로 넘어갔더라도 아직 이르면 차량 이동으로 되돌린다.
     */
    fun onStationCorrected(progress: GuideProgress, passedStops: Int, now: Long): GuideProgress {
        val step = currentStep(progress)
        val segment = step.segment
        if (!segment.isSubway() || step.type !in setOf(GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING)) return progress
        val stops = segment!!.stopsOrOne()
        val corrected = progress.copy(boardedAt = now - perStopMillis(segment) * passedStops.coerceIn(0, stops))
        val shouldBeRiding = step.type == GuideStepType.WAIT_ALIGHTING && now < alightSoonAt(segment, corrected.boardedAt!!)
        return if (shouldBeRiding) corrected.copy(stepIndex = progress.stepIndex - 1, stepStartedAt = now) else onTick(corrected, now)
    }

    /** 지하철 하차역 추정 도착 시각이 지났으면 true → "○○역에서 내리세요" 표시. */
    fun isAtAlightingStation(progress: GuideProgress, now: Long): Boolean {
        val step = currentStep(progress)
        val boardedAt = progress.boardedAt ?: return false
        return step.type == GuideStepType.WAIT_ALIGHTING && step.segment.isSubway() &&
            now >= estimatedArrivalAt(step.segment!!, boardedAt)
    }

    /**
     * 남은 정거장 수. 지하철은 경과 시간 추정이 기본, 버스는 정류소 대조 실패 시 대체값(표 2-1).
     * 하차 대기는 '다음 정류장(역)에서 하차' 단계라 항상 1이고, 차량 이동 중에는 시간 추정이 늦어도 2 밑으로 내려가지 않는다
     * (버스는 위치로 먼저 하차 대기로 넘어가므로, 시간 추정만 믿으면 단계와 숫자가 어긋난다).
     */
    fun remainingStops(progress: GuideProgress, now: Long): Int? {
        val step = currentStep(progress)
        val segment = step.segment ?: return null
        return when (step.type) {
            GuideStepType.WAIT_ALIGHTING -> 1
            GuideStepType.RIDING -> {
                val boardedAt = progress.boardedAt ?: return null
                val perStop = perStopMillis(segment)
                if (perStop <= 0) return null // 소요 시간을 모르면(0분 등) 추정하지 않는다
                val stops = segment.stopsOrOne()
                val passed = ((now - boardedAt) / perStop).toInt()
                (stops - passed).coerceIn(minOf(2, stops), stops)
            }
            else -> null
        }
    }

    /**
     * 지금 단계가 자동(위치·시간)으로 넘어갈 수 없으면 true → 화면·알림에 '다음 단계' 버튼을 보여 준다.
     * 대중교통 구간 경로선에 승차·하차 좌표가 없을 때 생긴다. 위치가 부정확할 때(A5)와 같은 방식으로 수동 전환하게 한다.
     */
    fun needsManualAdvance(progress: GuideProgress): Boolean {
        val step = currentStep(progress)
        val segment = step.segment
        return when (step.type) {
            GuideStepType.WALK -> {
                val next = steps.getOrNull(progress.stepIndex + 1)
                when (next?.type) {
                    GuideStepType.ARRIVED -> false
                    GuideStepType.WAIT_BOARDING -> next.segment?.boardingPoint() == null
                    else -> true // 도보 다음에 넘어갈 목표가 없는 단계가 오면(정상 단계 목록에선 없음) 수동으로
                }
            }
            // 지하철은 승차 버튼·시간으로 넘어가므로 좌표가 없어도 된다
            GuideStepType.WAIT_BOARDING -> segment.isBus() && segment!!.boardingPoint() == null
            GuideStepType.RIDING, GuideStepType.WAIT_ALIGHTING -> segment.isBus() && segment!!.alightingPoint() == null
            GuideStepType.ALIGHTED, GuideStepType.ARRIVED -> false
        }
    }

    /** 다음 위치 요청 간격. 지하철 차량 이동 중에는 GPS가 안 잡히므로 늘린다(NFR-04). */
    fun locationIntervalMillis(progress: GuideProgress): Long {
        val step = currentStep(progress)
        return if (step.type == GuideStepType.RIDING && step.segment.isSubway()) {
            GuideThresholds.SUBWAY_RIDING_LOCATION_INTERVAL_MILLIS
        } else {
            GuideThresholds.LOCATION_INTERVAL_MILLIS
        }
    }

    /** 실시간 도착 정보를 조회할 구간인지: 승차 전(도보 이동·승차 대기)이고 승차 지점 500m 이내 (FR-03). */
    fun shouldFetchArrivals(progress: GuideProgress, position: LatLng): Boolean {
        val boardingPoint = when (currentStep(progress).type) {
            GuideStepType.WALK -> nextBoardingPoint(progress.stepIndex)
            GuideStepType.WAIT_BOARDING -> currentStep(progress).segment?.boardingPoint()
            else -> null
        } ?: return false
        return Geo.distanceMeters(position, boardingPoint) <= GuideThresholds.ARRIVAL_INFO_START_METERS
    }

    // ---- 내부 규칙 ----

    private fun reachedByLocation(progress: GuideProgress, position: LatLng, now: Long): Boolean {
        val step = currentStep(progress)
        val segment = step.segment
        return when (step.type) {
            GuideStepType.WALK -> {
                val next = steps.getOrNull(progress.stepIndex + 1)
                when (next?.type) {
                    GuideStepType.ARRIVED ->
                        Geo.distanceMeters(position, destination) <= GuideThresholds.DESTINATION_ARRIVED_METERS
                    GuideStepType.WAIT_BOARDING -> next.segment?.boardingPoint()
                        ?.let { Geo.distanceMeters(position, it) <= GuideThresholds.BOARDING_APPROACH_METERS } == true
                    else -> false
                }
            }
            // 지하철 승차는 '승차' 버튼 시각으로 판단 (다음 열차 출발 시각은 서버 정보 연결 후)
            GuideStepType.WAIT_BOARDING -> segment.isBus() && segment!!.boardingPoint()
                ?.let { Geo.distanceMeters(position, it) >= GuideThresholds.BUS_DEPARTED_METERS } == true
            GuideStepType.RIDING -> segment.isBus() && segment!!.alightingPoint()
                ?.let { Geo.distanceMeters(position, it) <= GuideThresholds.BUS_ALIGHT_SOON_METERS } == true
            GuideStepType.WAIT_ALIGHTING -> {
                val point = segment?.alightingPoint() ?: return false
                val distance = Geo.distanceMeters(position, point)
                if (segment.isBus()) {
                    distance <= GuideThresholds.BUS_ALIGHTED_METERS
                } else {
                    // 지하철: 하차역 추정 도착 시각 이후, 정확한 위치가 하차역 200m 이내면 자동 하차 완료
                    isAtAlightingStation(progress, now) && distance <= GuideThresholds.SUBWAY_ALIGHTED_METERS
                }
            }
            GuideStepType.ALIGHTED, GuideStepType.ARRIVED -> false
        }
    }

    /** 도보 이동 중에만 경로 이탈을 본다. 경로선이 없으면 판단하지 않는다. */
    private fun updateOffRoute(progress: GuideProgress, position: LatLng, now: Long): GuideProgress {
        val step = currentStep(progress)
        val distance = step.segment?.path?.let { Geo.distanceToPathMeters(position, it) }
        if (step.type != GuideStepType.WALK || distance == null || distance < GuideThresholds.OFF_ROUTE_METERS) {
            return progress.copy(offRouteSince = null, isOffRoute = false)
        }
        val since = progress.offRouteSince ?: now
        return progress.copy(offRouteSince = since, isOffRoute = now - since >= GuideThresholds.OFF_ROUTE_MILLIS)
    }

    private fun advance(progress: GuideProgress, now: Long): GuideProgress {
        if (progress.stepIndex >= steps.lastIndex) return progress
        val nextIndex = progress.stepIndex + 1
        val boardedAt = when (steps[nextIndex].type) {
            GuideStepType.RIDING -> now // 승차 버튼·승차 지점 이탈·다음 단계 버튼 모두 이 시각을 승차 시각으로 쓴다
            GuideStepType.WAIT_ALIGHTING, GuideStepType.ALIGHTED -> progress.boardedAt
            else -> null
        }
        return progress.copy(
            stepIndex = nextIndex,
            stepStartedAt = now,
            boardedAt = boardedAt,
            offRouteSince = null,
            isOffRoute = false,
        )
    }

    private fun nextBoardingPoint(stepIndex: Int): LatLng? =
        steps.getOrNull(stepIndex + 1)?.takeIf { it.type == GuideStepType.WAIT_BOARDING }?.segment?.boardingPoint()

    private fun perStopMillis(segment: Segment): Long = segment.durationMinutes * 60_000L / segment.stopsOrOne()

    /** 하차역 직전 역 추정 도착 시각 → 하차 대기 */
    private fun alightSoonAt(segment: Segment, boardedAt: Long): Long =
        boardedAt + perStopMillis(segment) * (segment.stopsOrOne() - 1)

    /** 하차역 추정 도착 시각 */
    private fun estimatedArrivalAt(segment: Segment, boardedAt: Long): Long =
        boardedAt + segment.durationMinutes * 60_000L
}

private fun Segment?.isSubway() = this?.mode == TransitMode.SUBWAY
private fun Segment?.isBus() = this?.mode == TransitMode.BUS
private fun Segment.stopsOrOne() = stopCount.coerceAtLeast(1)
private fun Segment.boardingPoint(): LatLng? = path.firstOrNull()
private fun Segment.alightingPoint(): LatLng? = path.lastOrNull()
