package com.bloom.data.repository

import com.bloom.core.model.RecentDestination
import kotlinx.coroutines.flow.Flow

/** 최근 목적지(S-01 시트). 최신순 최대 20개(PlaceLimits.MAX_RECENT_DESTINATIONS). */
interface RecentDestinationRepository {
    fun observe(): Flow<List<RecentDestination>>
    suspend fun save(placeId: String, query: String)
}
