package com.bloom.core.model

/** Google 로그인 계정. 이메일은 표시용이며 식별자는 Firebase UID 다. */
data class UserAccount(
    val uid: String,
    val email: String,
    val displayName: String?,
)

enum class SyncState { SYNCED, PENDING }

/** 즐겨찾기(FR-05). 로그인 사용자 전용, 계정 기준 저장. 별칭과 재검색용 검색어를 분리한다. */
data class Favorite(
    val id: String,
    val placeId: String,
    val alias: String,
    val searchKeyword: String,
    val address: String,
    val syncState: SyncState = SyncState.SYNCED,
)

object FavoriteLimits {
    const val MAX_COUNT = 50
}
