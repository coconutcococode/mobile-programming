package com.bloom

import android.content.Context
import com.bloom.core.model.LatLng
import com.bloom.core.model.Place
import com.bloom.core.model.UserAccount
import com.bloom.data.fake.FakeFavoriteRepository
import com.bloom.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FakeFavoriteRepositoryTest {

    /** 로그인된 상태만 흉내 낸다. */
    private class SignedInAuth : AuthRepository {
        override val currentUser: StateFlow<UserAccount?> =
            MutableStateFlow(UserAccount(uid = "u1", email = "test@example.com", displayName = "테스트"))
        override suspend fun signInWithGoogle(activityContext: Context) = Result.success(currentUser.value!!)
        override suspend fun signOut() = Unit
        override suspend fun idToken(forceRefresh: Boolean): String? = null
    }

    @Test
    fun `즐겨찾기 저장 시 카카오 장소 이름을 검색어로 저장하지 않는다`() = runTest {
        val place = Place("kakao-1", "카카오 장소 이름", "주소", LatLng(37.55, 126.92))

        val saved = FakeFavoriteRepository(SignedInAuth()).add(place, alias = "동아리방").getOrThrow()

        assertEquals("kakao-1", saved.placeId)
        assertEquals("동아리방", saved.alias)
        assertNotEquals(place.name, saved.searchKeyword)
        assertEquals("", saved.searchKeyword)
    }
}
