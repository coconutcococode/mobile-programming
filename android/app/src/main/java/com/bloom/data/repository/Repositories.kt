package com.bloom.data.repository

import android.content.Context
import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.Favorite
import com.bloom.core.model.GuideSession
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.core.model.NearbySort
import com.bloom.core.model.Place
import com.bloom.core.model.PlaceCategory
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.Segment
import com.bloom.core.model.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * 화면(ViewModel)은 이 인터페이스에만 의존한다.
 * 지금은 data/fake 의 가짜 구현이 연결되어 있고, 서버 API 가 확정되면 data/remote 구현으로 바꾼다(DataModule 한 곳만 수정).
 */

interface PlaceRepository {
    /** 자동완성 검색. 최대 10개(PlaceLimits.MAX_AUTOCOMPLETE). */
    suspend fun search(query: String, near: LatLng?): Result<List<Place>>

    /** 장소 상세(S-09). 열 때마다 실시간 조회한다. */
    suspend fun getPlace(placeId: String): Result<Place>

    /** 주변 장소(S-06). 반경 500m, 최대 10개. */
    suspend fun nearby(center: LatLng, category: PlaceCategory, sort: NearbySort): Result<List<Place>>
}

interface RouteRepository {
    /** 경로 후보 검색(S-02). 재탐색도 이 함수를 현재 위치로 다시 부른다. */
    suspend fun searchRoutes(origin: LatLng, destination: Place): Result<List<RouteCandidate>>

    /** 마지막 검색 결과에서 후보 하나를 꺼낸다(메모리 보관만, NFR-11). 프로세스가 죽었으면 null. */
    fun findCandidate(candidateId: String): RouteCandidate?

    /** 보관 중인 경로 후보(출발지·경로선 좌표 포함)를 메모리에서 지운다. 안내 종료 시 호출(NFR-11). */
    fun clearCandidates()
}

interface TransitRepository {
    /** 승차 전 실시간 도착 정보(FR-03). */
    suspend fun arrivals(segment: Segment): Result<ArrivalInfo>
}

interface FavoriteRepository {
    /** 로그인 계정의 즐겨찾기. 비로그인이면 빈 목록. */
    fun observeFavorites(): Flow<List<Favorite>>
    suspend fun add(place: Place, alias: String): Result<Favorite>
    suspend fun rename(favoriteId: String, alias: String): Result<Unit>
    suspend fun delete(favoriteId: String): Result<Unit>
}

interface AuthRepository {
    val currentUser: StateFlow<UserAccount?>

    /** Credential Manager 가 Activity Context 를 요구하므로 화면에서 context 를 넘긴다. */
    suspend fun signInWithGoogle(activityContext: Context): Result<UserAccount>
    suspend fun signOut()

    /** Firebase ID Token. 로그인 전이면 null. */
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

/**
 * 진행 중인 안내 상태 — 트랙 A(홈 배너 S-01-B1, 안내 시작 제한 S-03-B1)와
 * 트랙 B(S-04 화면, S-05 알림, GuideService)가 함께 쓰는 공용 계약.
 */
interface GuideSessionRepository {
    val session: StateFlow<GuideSession?>

    /** 안내 시작. 진행 중인 안내가 있으면 새 경로로 바꾼다(재탐색). */
    fun start(route: RouteCandidate)

    /** 위치 서비스가 받은 위치 → 단계 자동 전환 (분석서 표 2-1) */
    fun onLocation(fix: LocationFix)

    /** 시간으로 바뀌는 전환(지하철 추정·하차 완료 표시). 위치 서비스가 1초마다 부른다 */
    fun onTick()

    /** '승차' 버튼 */
    fun board()

    /** '하차 완료' 버튼 */
    fun alight()

    /** '다음 단계' 버튼 */
    fun next()

    /** 단계 목록에서 특정 단계를 골라 이동 */
    fun moveToStep(index: Int)

    /** 지하철 현재 역 보정 — 승차역에서 지나온 역 수 (UC-01 A9) */
    fun correctStation(passedStops: Int)

    fun updateArrival(arrival: ArrivalInfo?)

    /** '재탐색' — [from]에서 같은 목적지로 다시 검색해 가장 빠른 경로로 안내를 바꾼다(FR-06). 실패하면 false */
    suspend fun reroute(from: LatLng): Boolean

    /** 안내 종료 — 경로 데이터를 메모리에서 폐기한다(NFR-11). */
    fun end()
}
