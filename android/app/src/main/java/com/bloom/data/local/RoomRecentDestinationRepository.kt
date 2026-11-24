package com.bloom.data.local

import android.database.sqlite.SQLiteException
import android.util.Log
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
        // 최근 목적지는 부가 기록이라 DB 오류(저장 공간 부족 등)로 검색, 길찾기를 막지 않음. 취소(CancellationException)는 그대로 전달
        try {
            dao.saveOnTop(placeId, query, now = System.currentTimeMillis(), keep = PlaceLimits.MAX_RECENT_DESTINATIONS)
        } catch (e: SQLiteException) {
            Log.w("RecentDestinations", "최근 목적지 저장 실패", e)
        }
    }
}
