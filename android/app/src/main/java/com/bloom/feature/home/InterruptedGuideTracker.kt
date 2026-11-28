package com.bloom.feature.home

import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.model.Favorite
import com.bloom.core.model.RecentDestination
import com.bloom.data.repository.GuideSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S-01-B2 이전 안내 중단 기록 담당. 안내 상태(GuideSessionRepository)는 읽기만 함.
 * 안내 시작 시 목적지 장소 ID 기록, 정상 종료 시 삭제. 강제 종료되면 기록이 남아 다음 실행 때 홈 배너 표시(NFR-07).
 * 앱이 켜져 있는 동안 계속 지켜봐야 해서 화면이 아닌 BloomApplication 에서 한 번 시작
 */
@Singleton
class InterruptedGuideTracker @Inject constructor(
    private val guideSessionRepository: GuideSessionRepository,
    private val settingsDataStore: SettingsDataStore,
) {
    // 앱 프로세스가 살아 있는 동안만 도는 작업 공간. SupervisorJob: 작업 하나가 실패해도 나머지는 계속 동작
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            trackInterruptedGuide(
                destinationIds = guideSessionRepository.session.map { it?.route?.destination?.id },
                write = settingsDataStore::setInterruptedGuide,
            )
        }
    }
}

/**
 * 목적지 장소 ID 흐름(안내 없으면 null)을 기록으로 옮김.
 * 앱을 막 켰을 때의 '안내 없음'은 이전 실행의 기록을 지우지 않고, 이번 실행 안에서 안내가 끝났을 때만 삭제
 */
internal suspend fun trackInterruptedGuide(destinationIds: Flow<String?>, write: suspend (String?) -> Unit) {
    var hadSession = false
    destinationIds
        .distinctUntilChanged() // 위치 갱신마다 오는 같은 목적지는 무시, 목적지가 바뀔 때만 기록
        .collect { placeId ->
            if (placeId != null) {
                write(placeId)
                hadSession = true
            } else if (hadSession) {
                write(null)
                hadSession = false
            }
        }
}

/** S-01-B2 [다시 안내]로 경로 후보(S-02)를 열 때 넘기는 값 */
data class ResumeTarget(val placeId: String, val name: String, val searchQuery: String?)

/**
 * 장소 이름은 저장할 수 없어(NFR-11) 표시 이름은 최근 목적지 검색어 → 즐겨찾기 별칭 → '이전 목적지' 순.
 * 검색어는 사용자가 입력한 글자(최근 목적지 검색어, 즐겨찾기 검색어)만 사용
 */
internal fun resumeTargetOf(placeId: String, recents: List<RecentDestination>, favorites: List<Favorite>): ResumeTarget {
    val recent = recents.firstOrNull { it.placeId == placeId }
    val favorite = favorites.firstOrNull { it.placeId == placeId }
    return ResumeTarget(
        placeId = placeId,
        name = recent?.query ?: favorite?.alias ?: "이전 목적지",
        searchQuery = recent?.query ?: favorite?.searchKeyword?.ifBlank { null },
    )
}
