package com.bloom.core.model

/**
 * 최근 목적지(최대 20개, 기기에만 저장). 카카오 정책상 장소 ID 와 사용자가 입력한 검색어만 저장한다(NFR-11).
 * 화면에는 장소명 대신 검색어를 보여 주고, 누르면 장소 ID 로 다시 조회한다.
 */
data class RecentDestination(
    val placeId: String,
    val query: String,
)
