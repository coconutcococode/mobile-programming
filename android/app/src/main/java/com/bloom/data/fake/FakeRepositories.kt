package com.bloom.data.fake

import android.content.Context
import com.bloom.core.model.ArrivalInfo
import com.bloom.core.model.ArrivalStatus
import com.bloom.core.model.Favorite
import com.bloom.core.model.LatLng
import com.bloom.core.model.NearbySort
import com.bloom.core.model.Place
import com.bloom.core.model.PlaceCategory
import com.bloom.core.model.PlaceLimits
import com.bloom.core.model.RouteCandidate
import com.bloom.core.model.Segment
import com.bloom.core.model.TransitMode
import com.bloom.core.model.UserAccount
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.data.repository.PlaceRepository
import com.bloom.data.repository.RouteRepository
import com.bloom.data.repository.TransitRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 로딩 스켈레톤이 보이도록 살짝 기다린다. */
private const val FAKE_LATENCY_MILLIS = 600L

private class NotFoundException : RuntimeException()

@Singleton
class FakePlaceRepository @Inject constructor() : PlaceRepository {
    override suspend fun search(query: String, near: LatLng?): Result<List<Place>> {
        delay(FAKE_LATENCY_MILLIS)
        return Result.success(
            SamplePlaces.all.filter { it.name.contains(query.trim()) }.take(PlaceLimits.MAX_AUTOCOMPLETE),
        )
    }

    override suspend fun getPlace(placeId: String): Result<Place> {
        delay(FAKE_LATENCY_MILLIS)
        return SamplePlaces.all.firstOrNull { it.id == placeId }
            ?.let { Result.success(it) }
            ?: Result.failure(NotFoundException())
    }

    override suspend fun nearby(center: LatLng, category: PlaceCategory, sort: NearbySort): Result<List<Place>> {
        delay(FAKE_LATENCY_MILLIS)
        val places = SamplePlaces.all.filter { it.category == category }
        return Result.success(
            if (sort == NearbySort.DISTANCE) places.sortedBy { it.distanceMeters } else places,
        )
    }
}

/** 가짜 경로선 좌표용 지점(신촌·홍대 일대 대략 위치). 실제 정류장 좌표가 아님. */
private object FakeStops {
    val EwhaStation = LatLng(37.5567, 126.9460)
    val SinchonStation = LatLng(37.5552, 126.9369)
    val HongikStation = LatLng(37.5572, 126.9245)
    val HapjeongStation = LatLng(37.5495, 126.9139)
    val SinchonOgeori = LatLng(37.5551, 126.9368)
    val SeogyoDong = LatLng(37.5530, 126.9215)
    val NearbyStop = LatLng(37.5585, 126.9405)
}

@Singleton
class FakeRouteRepository @Inject constructor() : RouteRepository {
    private var lastCandidates: List<RouteCandidate> = emptyList()

    override suspend fun searchRoutes(origin: LatLng, destination: Place): Result<List<RouteCandidate>> {
        delay(FAKE_LATENCY_MILLIS)
        val now = LocalTime.now().withSecond(0).withNano(0)
        val end = destination.location

        // 구간 좌표는 앞 구간의 마지막 점과 다음 구간의 첫 점이 이어지게 만든다.
        fun walk(min: Int, to: String, meters: Int, from: LatLng, toPos: LatLng) =
            Segment(TransitMode.WALK, min, "도보", "", to, distanceMeters = meters, path = listOf(from, toPos))

        fun candidate(total: Int, transfers: Int, walkMeters: Int, segments: List<Segment>) = RouteCandidate(
            id = UUID.randomUUID().toString(),
            destination = destination,
            totalMinutes = total,
            arrivalTime = now.plusMinutes(total.toLong()),
            transferCount = transfers,
            walkMeters = walkMeters,
            segments = segments,
        )
        with(FakeStops) {
            lastCandidates = listOf(
                candidate(
                    18, 0, 780,
                    listOf(
                        walk(6, "이대역", 380, origin, EwhaStation),
                        Segment(
                            TransitMode.SUBWAY, 6, "2호선", "이대역", "홍대입구역", stopCount = 2,
                            path = listOf(EwhaStation, SinchonStation, HongikStation),
                        ),
                        walk(6, destination.name, 400, HongikStation, end),
                    ),
                ),
                candidate(
                    21, 0, 560,
                    listOf(
                        walk(3, "신촌오거리", 200, origin, SinchonOgeori),
                        Segment(
                            TransitMode.BUS, 12, "7612", "신촌오거리", "서교동", stopCount = 5,
                            path = listOf(SinchonOgeori, LatLng(37.5545, 126.9300), SeogyoDong),
                        ),
                        walk(6, destination.name, 360, SeogyoDong, end),
                    ),
                ),
                candidate(
                    24, 1, 470,
                    listOf(
                        walk(4, "이대역", 250, origin, EwhaStation),
                        Segment(
                            TransitMode.SUBWAY, 9, "2호선", "이대역", "합정역", stopCount = 3,
                            path = listOf(EwhaStation, SinchonStation, HongikStation, HapjeongStation),
                        ),
                        Segment(
                            TransitMode.BUS, 8, "7016", "합정역", "서교동", stopCount = 3,
                            path = listOf(HapjeongStation, LatLng(37.5510, 126.9175), SeogyoDong),
                        ),
                        walk(3, destination.name, 220, SeogyoDong, end),
                    ),
                ),
                candidate(
                    27, 0, 690,
                    listOf(
                        walk(5, "정류장", 300, origin, NearbyStop),
                        Segment(
                            TransitMode.BUS, 17, "7016", "정류장", "서교동", stopCount = 9,
                            path = listOf(NearbyStop, LatLng(37.5560, 126.9330), LatLng(37.5545, 126.9260), SeogyoDong),
                        ),
                        walk(5, destination.name, 390, SeogyoDong, end),
                    ),
                ),
            )
        }
        return Result.success(lastCandidates)
    }

    override fun findCandidate(candidateId: String): RouteCandidate? =
        lastCandidates.firstOrNull { it.id == candidateId }

    override fun clearCandidates() {
        lastCandidates = emptyList()
    }
}

@Singleton
class FakeTransitRepository @Inject constructor() : TransitRepository {
    override suspend fun arrivals(segment: Segment): Result<ArrivalInfo> {
        delay(FAKE_LATENCY_MILLIS)
        return Result.success(
            ArrivalInfo(
                routeLabel = segment.label,
                firstMinutes = 2,
                secondMinutes = 7,
                baseTime = LocalTime.now().withSecond(0).withNano(0),
                status = ArrivalStatus.OK,
            ),
        )
    }
}

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    override val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    override suspend fun signInWithGoogle(activityContext: Context): Result<UserAccount> {
        delay(FAKE_LATENCY_MILLIS)
        val user = UserAccount(uid = "fake-uid", email = "test@example.com", displayName = "테스트 사용자")
        _currentUser.value = user
        return Result.success(user)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    override suspend fun idToken(forceRefresh: Boolean): String? =
        if (_currentUser.value != null) "fake-firebase-id-token" else null
}

@Singleton
class FakeFavoriteRepository @Inject constructor(
    private val authRepository: AuthRepository,
) : FavoriteRepository {
    private val favorites = MutableStateFlow(
        listOf(
            Favorite("fav-1", SamplePlaces.School.id, "학교", "홍익대학교", SamplePlaces.School.address),
            Favorite("fav-2", SamplePlaces.HongdaeStreet.id, "알바", "홍대 걷고싶은거리", SamplePlaces.HongdaeStreet.address),
            Favorite("fav-3", SamplePlaces.Culture.id, "동아리방", "연남 소극장", SamplePlaces.Culture.address),
        ),
    )

    override fun observeFavorites(): Flow<List<Favorite>> =
        combine(authRepository.currentUser, favorites) { user, list -> if (user == null) emptyList() else list }

    override suspend fun add(place: Place, alias: String): Result<Favorite> {
        if (authRepository.currentUser.value == null) return Result.failure(IllegalStateException("로그인이 필요해요"))
        val favorite = Favorite(UUID.randomUUID().toString(), place.id, alias, place.name, place.address)
        favorites.update { it + favorite }
        return Result.success(favorite)
    }

    override suspend fun rename(favoriteId: String, alias: String): Result<Unit> {
        favorites.update { list -> list.map { if (it.id == favoriteId) it.copy(alias = alias) else it } }
        return Result.success(Unit)
    }

    override suspend fun delete(favoriteId: String): Result<Unit> {
        favorites.update { list -> list.filterNot { it.id == favoriteId } }
        return Result.success(Unit)
    }
}
