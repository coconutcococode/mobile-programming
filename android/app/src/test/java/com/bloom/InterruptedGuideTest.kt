package com.bloom

import com.bloom.core.model.Favorite
import com.bloom.core.model.RecentDestination
import com.bloom.feature.home.ResumeTarget
import com.bloom.feature.home.resumeTargetOf
import com.bloom.feature.home.trackInterruptedGuide
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 이전 안내 중단(S-01-B2): 안내 시작, 종료 기록과 [다시 안내] 목적지 이름, 검색어 */
class InterruptedGuideTest {

    private suspend fun writesFor(vararg ids: String?): List<String?> {
        val writes = mutableListOf<String?>()
        trackInterruptedGuide(flowOf(*ids)) { writes += it }
        return writes
    }

    @Test
    fun `앱을 막 켰을 때 안내가 없으면 이전 실행의 기록을 지우지 않는다`() = runTest {
        assertEquals(emptyList<String?>(), writesFor(null))
    }

    @Test
    fun `안내가 시작되면 목적지를 기록하고 정상 종료되면 지운다`() = runTest {
        assertEquals(listOf("p1", null), writesFor(null, "p1", null))
    }

    @Test
    fun `안내 중 같은 목적지 갱신은 한 번만 기록하고 강제 종료되면 기록이 남는다`() = runTest {
        assertEquals(listOf("p1"), writesFor("p1", "p1", "p1"))
    }

    @Test
    fun `안내를 새로 시작하면 새 목적지로 바꿔 기록한다`() = runTest {
        assertEquals(listOf("p1", null, "p2"), writesFor("p1", null, "p2"))
    }

    @Test
    fun `다시 안내 이름은 최근 목적지 검색어, 즐겨찾기 별칭, 이전 목적지 순으로 고른다`() {
        val recents = listOf(RecentDestination("p1", "홍대입구역"))
        val favorites = listOf(
            Favorite(id = "f1", placeId = "p1", alias = "약속 장소", searchKeyword = "홍대", address = ""),
            Favorite(id = "f2", placeId = "p2", alias = "학교", searchKeyword = "", address = ""),
        )
        assertEquals(ResumeTarget("p1", "홍대입구역", "홍대입구역"), resumeTargetOf("p1", recents, favorites))
        // 검색 없이 저장한 즐겨찾기는 검색어 없음
        assertEquals(ResumeTarget("p2", "학교", null), resumeTargetOf("p2", recents, favorites))
        assertEquals(ResumeTarget("p3", "이전 목적지", null), resumeTargetOf("p3", recents, favorites))
    }
}
