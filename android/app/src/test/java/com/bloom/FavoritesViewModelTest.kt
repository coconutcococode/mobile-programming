package com.bloom

import android.content.Context
import com.bloom.core.designsystem.UiState
import com.bloom.core.model.Favorite
import com.bloom.core.model.Place
import com.bloom.core.model.UserAccount
import com.bloom.data.repository.AuthRepository
import com.bloom.data.repository.FavoriteRepository
import com.bloom.feature.favorites.FavoritesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 즐겨찾기(S-07): 불러오는 중, 비어 있음, 별칭 수정, 삭제와 실패 시 다시 시도 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val school = Favorite("f1", "p1", "학교", "홍익대학교", address = "")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private object SignedIn : AuthRepository {
        override val currentUser: StateFlow<UserAccount?> = MutableStateFlow(UserAccount("u1", "test@example.com", null))
        override suspend fun signInWithGoogle(activityContext: Context) = Result.success(currentUser.value!!)
        override suspend fun signOut() = Unit
        override suspend fun idToken(forceRefresh: Boolean): String? = null
    }

    /** 목록은 [list] 로 직접 내보내고, [fail] 이 참이면 수정 · 삭제 실패 */
    private class FakeFavorites : FavoriteRepository {
        val list = MutableSharedFlow<List<Favorite>>(replay = 1)
        var fail = true
        val renamed = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        override fun observeFavorites(): Flow<List<Favorite>> = list
        override suspend fun add(place: Place, alias: String): Result<Favorite> = error("사용 안 함")
        override suspend fun rename(favoriteId: String, alias: String): Result<Unit> {
            renamed += favoriteId to alias
            return if (fail) Result.failure(IOException("offline")) else Result.success(Unit)
        }
        override suspend fun delete(favoriteId: String): Result<Unit> {
            deleted += favoriteId
            return if (fail) Result.failure(IllegalStateException()) else Result.success(Unit)
        }
    }

    @Test
    fun `첫 목록을 받기 전에는 불러오는 중이고 0건이면 비어 있음이다`() = runTest(dispatcher) {
        val repo = FakeFavorites()
        val vm = FavoritesViewModel(SignedIn, repo, flowOf(true))
        backgroundScope.launch { vm.favorites.collect {} }
        advanceUntilIdle()
        assertEquals(UiState.Loading, vm.favorites.value)

        repo.list.emit(emptyList())
        advanceUntilIdle()
        assertEquals(UiState.Empty, vm.favorites.value)

        repo.list.emit(listOf(school))
        advanceUntilIdle()
        assertEquals(UiState.Success(listOf(school)), vm.favorites.value)
    }

    @Test
    fun `별칭 수정에 실패하면 안내를 띄우고 다시 시도하면 같은 값으로 다시 수정한다`() = runTest(dispatcher) {
        val repo = FakeFavorites()
        val vm = FavoritesViewModel(SignedIn, repo, flowOf(true))

        vm.startEdit(school)
        vm.rename(school, "  학교 정문  ")
        advanceUntilIdle()
        assertNull(vm.editing.value)
        assertEquals(listOf("f1" to "학교 정문"), repo.renamed)
        val error = vm.actionError.value!!
        assertEquals("별칭을 바꾸지 못했어요", error.message)
        // 실패 원인을 함께 안내(연결 끊김 → 인터넷 연결 필요)
        assertEquals("인터넷 연결이 필요합니다", error.cause)

        repo.fail = false
        error.retry()
        advanceUntilIdle()
        assertEquals(listOf("f1" to "학교 정문", "f1" to "학교 정문"), repo.renamed)
        assertNull(vm.actionError.value)
    }

    @Test
    fun `삭제 확인 후 삭제하고 실패하면 다시 시도할 수 있다`() = runTest(dispatcher) {
        val repo = FakeFavorites()
        val vm = FavoritesViewModel(SignedIn, repo, flowOf(true))

        vm.startDelete(school)
        assertEquals(school, vm.deleting.value)
        vm.delete(school)
        advanceUntilIdle()
        assertNull(vm.deleting.value)
        assertEquals("삭제하지 못했어요", vm.actionError.value?.message)

        repo.fail = false
        vm.actionError.value!!.retry()
        advanceUntilIdle()
        assertEquals(listOf("f1", "f1"), repo.deleted)
        assertNull(vm.actionError.value)
    }

    @Test
    fun `연결 상태를 그대로 전달한다`() = runTest(dispatcher) {
        val vm = FavoritesViewModel(SignedIn, FakeFavorites(), flowOf(false))
        backgroundScope.launch { vm.isOnline.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.isOnline.value)
    }
}
