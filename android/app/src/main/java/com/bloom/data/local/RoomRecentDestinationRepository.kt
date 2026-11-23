package com.bloom.data.local

import com.bloom.core.database.RecentDestinationDao
import com.bloom.core.model.PlaceLimits
import com.bloom.core.model.RecentDestination
import com.bloom.data.repository.RecentDestinationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 최근 목적지의 기기 DB(Room) 저장. 같은 장소를 다시 고르면 맨 위로 올림, 20개 초과 시 오래된 것부터 삭제 */
@Singleton
class RoomRecentDestinationRepository @Inject constructor(
    private val dao: RecentDestinationDao,
) : RecentDestinationRepository {

    override fun observe(): Flow<List<RecentDestination>> =
        dao.observe(PlaceLimits.MAX_RECENT_DESTINATIONS).map { list -> list.map { RecentDestination(it.placeId, it.query) } }

    override suspend fun save(placeId: String, query: String) {
        dao.saveOnTop(placeId, query, now = System.currentTimeMillis(), keep = PlaceLimits.MAX_RECENT_DESTINATIONS)
    }
}
