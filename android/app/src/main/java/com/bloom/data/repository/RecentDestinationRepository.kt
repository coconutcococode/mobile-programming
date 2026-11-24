package com.bloom.data.repository

import com.bloom.core.model.RecentDestination
import kotlinx.coroutines.flow.Flow

/** 최근 목적지(S-01 시트). 최신순 최대 20개(PlaceLimits.MAX_RECENT_DESTINATIONS). */
interface RecentDestinationRepository {
    fun observe(): Flow<List<RecentDestination>>

    /** 맨 위에 저장. 기기 저장 공간 부족 등으로 실패하면 예외 없이 기록만 생략(길찾기 흐름은 계속) */
    suspend fun save(placeId: String, query: String)
}
