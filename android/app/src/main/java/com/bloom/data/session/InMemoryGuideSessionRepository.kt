package com.bloom.data.session

import com.bloom.core.guide.GuideClock
import com.bloom.core.guide.GuideEngine
import com.bloom.core.guide.GuideProgress
import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.GuideSession
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.RouteSortType
import com.bloom.core.model.sortedFor
import com.bloom.core.model.toGuideSteps
import com.bloom.data.repository.GuideSessionRepository
import com.bloom.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 진행 중인 안내 1건. 단계 전환 규칙은 [GuideEngine]에 맡기고, 결과를 화면·알림이 바로 쓰는 [GuideSession]으로 펼쳐 낸다.
 * 경로 데이터는 디스크에 쓰지 않고 메모리에만 둔다(NFR-07·NFR-11).
 * 위치 서비스(백그라운드)와 화면·알림 버튼(메인)이 동시에 부를 수 있어 상태 변경은 한 번에 하나씩 한다.
 */
@Singleton
class InMemoryGuideSessionRepository @Inject constructor(
    private val routeRepository: RouteRepository,
    private val clock: GuideClock,
) : GuideSessionRepository {

    private val _session = MutableStateFlow<GuideSession?>(null)
    override val session: StateFlow<GuideSession?> = _session.asStateFlow()

    private var engine: GuideEngine? = null
    private var progress: GuideProgress? = null
    private val lock = Any()

    /** 안내를 시작·종료할 때마다 1씩 올린다. 재탐색 검색 중에 안내가 바뀌었는지 알아보는 데 쓴다. */
    private var generation = 0L

    override fun start(route: RouteCandidate) = synchronized(lock) {
        // 새 경로로 재탐색하면 기존 안내 데이터를 교체한다.
        generation++
        val newEngine = GuideEngine(route.toGuideSteps(), route.destination.location)
        engine = newEngine
        publish(route, newEngine, newEngine.start(clock.now()), arrival = null)
    }

    override fun onLocation(fix: LocationFix) = update { e, p -> e.onLocation(p, fix) }

    override fun onTick() = update { e, p -> e.onTick(p, clock.now()) }

    override fun board() = update { e, p -> e.onBoardPressed(p, clock.now()) }

    override fun alight() = update { e, p -> e.onAlightPressed(p, clock.now()) }

    override fun next() = update { e, p -> e.onNextPressed(p, clock.now()) }

    override fun moveToStep(index: Int) = update { e, p -> e.moveTo(p, index, clock.now()) }

    override fun correctStation(passedStops: Int) = update { e, p -> e.onStationCorrected(p, passedStops, clock.now()) }

    override fun updateArrival(arrival: ArrivalInfo?) = synchronized(lock) {
        _session.value = _session.value?.copy(arrival = arrival)
    }

    override suspend fun reroute(from: LatLng): Boolean {
        val (destination, startedGeneration) = synchronized(lock) {
            val destination = _session.value?.route?.destination ?: return false
            destination to generation
        }
        val best = routeRepository.searchRoutes(from, destination).getOrNull()
            ?.sortedFor(RouteSortType.FASTEST)
            ?.firstOrNull()
        synchronized(lock) {
            // 검색하는 사이 안내가 끝났거나 다른 안내가 시작됐으면 이 결과는 버린다(새 안내를 옛 목적지로 덮어쓰지 않음).
            if (generation != startedGeneration) {
                // 안내가 끝난 뒤 검색이 후보를 다시 채웠을 수 있으므로 지운다(NFR-07). 새 안내가 있으면 그 안내가 후보를 관리한다.
                if (_session.value == null) routeRepository.clearCandidates()
                return false
            }
            if (best == null) return false
            start(best)
        }
        return true
    }

    /** 안내 화면·알림·도착 처리 모두 여기를 거치므로, 경로 후보도 이 한 곳에서 함께 지운다. */
    override fun end() = synchronized(lock) {
        generation++
        engine = null
        progress = null
        _session.value = null
        routeRepository.clearCandidates()
    }

    private inline fun update(block: (GuideEngine, GuideProgress) -> GuideProgress) = synchronized(lock) {
        val e = engine ?: return@synchronized
        val p = progress ?: return@synchronized
        val current = _session.value ?: return@synchronized
        // 남은 정거장 등은 시간만 지나도 바뀌므로 매번 다시 펼친다. 값이 같으면 StateFlow 가 알아서 무시한다.
        publish(current.route, e, block(e, p), current.arrival)
    }

    private fun publish(route: RouteCandidate, e: GuideEngine, p: GuideProgress, arrival: ArrivalInfo?) {
        progress = p
        val now = clock.now()
        _session.value = GuideSession(
            route = route,
            steps = e.steps,
            currentStepIndex = p.stepIndex,
            arrival = arrival,
            isOffRoute = p.isOffRoute,
            isLocationInaccurate = p.isLocationInaccurate,
            needsManualAdvance = e.needsManualAdvance(p),
            remainingStops = e.remainingStops(p, now),
            isAtAlightingStation = e.isAtAlightingStation(p, now),
            locationIntervalMillis = e.locationIntervalMillis(p),
        )
    }
}
