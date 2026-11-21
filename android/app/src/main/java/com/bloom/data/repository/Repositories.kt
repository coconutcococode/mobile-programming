package com.bloom.data.repository

import android.content.Context
import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.Favorite
import com.bloom.core.model.GuideSession
import com.bloom.core.model.LatLng
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
    fun start(route: RouteCandidate)
    fun moveToStep(index: Int)
    fun next()
    fun updateArrival(arrival: ArrivalInfo?)
    fun setOffRoute(offRoute: Boolean)

    /** 안내 종료 — 경로 데이터를 메모리에서 폐기한다(NFR-11). */
    fun end()
}
