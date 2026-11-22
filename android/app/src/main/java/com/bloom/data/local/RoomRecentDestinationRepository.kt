package com.bloom.data.local

import com.bloom.core.database.RecentDestinationDao
import com.bloom.core.database.RecentDestinationEntity
import com.bloom.core.model.PlaceLimits
import com.bloom.core.model.RecentDestination
import com.bloom.data.repository.RecentDestinationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 최근 목적지를 기기 DB(Room)에 저장한다. 같은 장소를 다시 고르면 맨 위로 올라가고, 20개를 넘으면 오래된 것부터 지운다. */
@Singleton
class RoomRecentDestinationRepository @Inject constructor(
    private val dao: RecentDestinationDao,
) : RecentDestinationRepository {

    override fun observe(): Flow<List<RecentDestination>> =
        dao.observe(PlaceLimits.MAX_RECENT_DESTINATIONS).map { list -> list.map { RecentDestination(it.placeId, it.query) } }

    override suspend fun save(placeId: String, query: String) {
        dao.upsert(RecentDestinationEntity(placeId, query, System.currentTimeMillis()))
        dao.trim(PlaceLimits.MAX_RECENT_DESTINATIONS)
    }
}
