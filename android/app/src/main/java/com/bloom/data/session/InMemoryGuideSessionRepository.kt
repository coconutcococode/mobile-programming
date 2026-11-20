package com.bloom.data.session

import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.GuideSession
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.toGuideSteps
import com.bloom.data.repository.GuideSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** 경로 데이터는 디스크에 쓰지 않고 메모리에만 둔다(NFR-07·NFR-11). */
@Singleton
class InMemoryGuideSessionRepository @Inject constructor() : GuideSessionRepository {

    private val _session = MutableStateFlow<GuideSession?>(null)
    override val session: StateFlow<GuideSession?> = _session.asStateFlow()

    override fun start(route: RouteCandidate) {
        // 새 경로로 재탐색하면 기존 안내 데이터를 교체한다.
        _session.value = GuideSession(route = route, steps = route.toGuideSteps())
    }

    override fun moveToStep(index: Int) {
        _session.update { s -> s?.copy(currentStepIndex = index.coerceIn(0, s.steps.lastIndex)) }
    }

    override fun next() {
        _session.update { s -> s?.copy(currentStepIndex = (s.currentStepIndex + 1).coerceAtMost(s.steps.lastIndex)) }
    }

    override fun updateArrival(arrival: ArrivalInfo?) {
        _session.update { it?.copy(arrival = arrival) }
    }

    override fun setOffRoute(offRoute: Boolean) {
        _session.update { it?.copy(isOffRoute = offRoute) }
    }

    override fun end() {
        _session.value = null
    }
}
