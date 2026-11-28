package com.bloom.feature.guide

import com.bloom.core.guide.Geo
import com.bloom.core.guide.GuideClock
import com.bloom.core.guide.GuideThresholds
import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.ArrivalState
import com.bloom.core.model.ArrivalStatus
import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.Segment
import com.bloom.core.network.NetworkMonitor
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.TransitRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 실시간 도착 정보 조회(FR-03, NFR-03). 안내 서비스가 [run] 으로 돌리고, 안내 화면의 [다시 시도]가 [retryNow] 를 부른다.
 * 결과는 안내 세션에 넣어 화면(S-04)과 잠금화면 알림(S-05)이 같은 값을 보게 한다.
 */
@Singleton
class ArrivalUpdates @Inject constructor(
    private val transitRepository: TransitRepository,
    private val guideSessionRepository: GuideSessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val clock: GuideClock,
) {
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** S-04-4 [다시 시도]: 30초를 기다리지 않고 바로 조회한다. */
    fun retryNow() {
        retries.tryEmit(Unit)
    }

    /** 안내가 이어지는 동안 계속 돈다. [fixes] 는 안내 서비스가 받은 최신 위치(아직 없으면 null). */
    suspend fun run(fixes: Flow<LocationFix?>) {
        ArrivalPolling(
            fetch = transitRepository::arrivals,
            publish = guideSessionRepository::updateArrival,
            now = clock::now,
        ).run(
            targets = arrivalTargets(guideSessionRepository.session, fixes),
            online = networkMonitor.isOnline,
            retries = retries,
        )
    }
}

/**
 * 지금 도착 정보를 조회할 승차 구간. 없으면 null.
 * - 승차 대기 단계: 그 구간
 * - 도보 이동 단계: 바로 다음이 승차 대기이고 승차 지점까지 500m 이내일 때 그 구간
 * - 차량 이동부터는 조회하지 않는다(NFR-03)
 * [active] 는 지금 조회 중인 구간. 한 번 시작하면 위치가 500m 언저리에서 흔들려도 그 승차 전까지 계속한다
 * (흔들릴 때마다 카드가 사라졌다 나타나고 조회를 새로 하지 않게).
 */
internal fun arrivalTarget(session: GuideSession, here: LatLng?, active: Segment?): Segment? {
    val step = session.currentStep
    return when (step.type) {
        GuideStepType.WAIT_BOARDING -> step.segment
        GuideStepType.WALK -> {
            val next = upcomingBoarding(session) ?: return null
            if (next == active) return next
            val boardingPoint = next.path.firstOrNull() ?: return null // 좌표가 없으면 승차 대기 단계에서만 조회
            next.takeIf { here != null && Geo.distanceMeters(here, boardingPoint) <= GuideThresholds.ARRIVAL_INFO_START_METERS }
        }
        else -> null
    }
}

/** 도보 이동 단계라면 바로 다음 승차 대기 구간, 승차 대기 단계라면 그 구간. 카드의 '다음 열차·다음 버스' 글자에도 쓴다. */
internal fun upcomingBoarding(session: GuideSession): Segment? {
    val step = session.currentStep
    return when (step.type) {
        GuideStepType.WAIT_BOARDING -> step.segment
        GuideStepType.WALK -> session.steps.getOrNull(step.index + 1)?.takeIf { it.type == GuideStepType.WAIT_BOARDING }?.segment
        else -> null
    }
}

internal fun arrivalTargets(sessions: Flow<GuideSession?>, fixes: Flow<LocationFix?>): Flow<Segment?> =
    combine(sessions, fixes, ::Pair)
        .runningFold(null as Segment?) { active, (session, fix) ->
            session?.let { arrivalTarget(it, fix?.position, active) }
        }
        .distinctUntilChanged()

/**
 * 조회 순서와 카드 상태 전환(UI 설계서 4.2 S-04-1~5). 안드로이드와 무관해 가상 시간으로 단위 테스트한다.
 * - 대상이 생기면 '확인 중' → 30초마다 조회. 대상이 바뀌면 처음부터, 없어지면 카드를 지우고 멈춘다
 * - 3초 넘게 걸리거나 실패하면 1회 실패. 3회 연달아 실패하면 오류 카드, 그 뒤에도 30초마다 다시 시도
 * - 미제공 지역·대조 실패는 다시 물어도 같으므로 조회를 멈춘다
 * - 연결이 끊기면 마지막 값을 흐리게 두고 받은 지 2분 뒤 숨김. 연결되면 바로 다시 조회
 */
internal class ArrivalPolling(
    private val fetch: suspend (Segment) -> Result<ArrivalInfo>,
    private val publish: (ArrivalState?) -> Unit,
    private val now: () -> Long,
) {
    private var target: Segment? = null
    private var state: ArrivalState? = null
    private var last: ArrivalInfo? = null
    private var lastReceivedAt = 0L
    private var failures = 0

    suspend fun run(targets: Flow<Segment?>, online: Flow<Boolean>, retries: Flow<Unit>) {
        combine(targets.distinctUntilChanged(), online.distinctUntilChanged(), ::Pair).collectLatest { (segment, isOnline) ->
            if (segment != target) {
                target = segment
                state = null
                last = null
                failures = 0
            }
            when {
                segment == null -> set(null)
                !isOnline -> showOffline()
                else -> poll(segment, retries)
            }
        }
    }

    private suspend fun showOffline() {
        if (state is ArrivalState.Unavailable) return // 정류장이 그대로라 연결과 무관
        val info = last?.takeIf { isFresh() }
        set(ArrivalState.Offline(info))
        if (info != null) {
            delay(lastReceivedAt + GuideThresholds.ARRIVAL_STALE_MILLIS - now())
            set(ArrivalState.Offline(null))
        }
    }

    private suspend fun poll(segment: Segment, retries: Flow<Unit>) {
        when (state) {
            is ArrivalState.Unavailable -> return
            null, is ArrivalState.Offline -> set(last?.takeIf { isFresh() }?.let { ArrivalState.Live(it) } ?: ArrivalState.Loading)
            else -> Unit
        }
        while (true) {
            val startedAt = now()
            val info = withTimeoutOrNull(GuideThresholds.ARRIVAL_TIMEOUT_MILLIS) { fetch(segment).getOrNull() }
            when (info?.status) {
                null, ArrivalStatus.ERROR -> {
                    failures++
                    if (failures >= GuideThresholds.ARRIVAL_MAX_FAILURES) set(ArrivalState.Failed(failures))
                }
                ArrivalStatus.OK -> {
                    failures = 0
                    last = info
                    lastReceivedAt = now()
                    set(ArrivalState.Live(info))
                }
                ArrivalStatus.UNSUPPORTED_AREA, ArrivalStatus.MATCH_FAILED -> {
                    set(ArrivalState.Unavailable(info.status))
                    return
                }
            }
            // 조회 시작 시각 기준으로 30초 간격을 맞춘다. [다시 시도]를 누르면 바로 다음 조회
            withTimeoutOrNull(GuideThresholds.ARRIVAL_POLL_INTERVAL_MILLIS - (now() - startedAt)) { retries.first() }
        }
    }

    private fun isFresh() = now() - lastReceivedAt < GuideThresholds.ARRIVAL_STALE_MILLIS

    private fun set(value: ArrivalState?) {
        state = value
        publish(value)
    }
}
