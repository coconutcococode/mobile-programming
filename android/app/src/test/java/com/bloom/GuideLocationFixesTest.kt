package com.bloom

import com.bloom.core.location.LocationTracker
import com.bloom.core.model.LatLng
import com.bloom.core.model.LocationFix
import com.bloom.feature.guide.locationFixes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** 위치 서비스가 위치를 받는 흐름: 등록이 실패해도 안내 중에는 다시 등록한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideLocationFixesTest {

    private val here = LocationFix(LatLng(37.55, 126.92), 10f, 0L)

    /** 처음 [failures]번은 등록에 실패하고, 그다음부터 위치 1건을 준다. */
    private class FlakyTracker(private var failures: Int, private val fix: LocationFix) : LocationTracker {
        val requestedIntervals = mutableListOf<Long>()
        override fun hasPreciseLocationPermission() = true
        override suspend fun currentLocation(): LatLng? = fix.position
        override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> = flow {
            requestedIntervals += intervalMillis
            if (failures > 0) {
                failures--
                throw IOException("등록 실패")
            }
            emit(fix)
        }
    }

    @Test
    fun `위치 등록이 실패하면 잠시 뒤 같은 간격으로 다시 등록해 위치를 받는다`() = runTest {
        val tracker = FlakyTracker(failures = 2, fix = here)

        val received = flowOf<Long?>(5_000L).locationFixes(tracker, retryDelayMillis = 10_000L).first()

        assertEquals(here, received)
        assertEquals(listOf(5_000L, 5_000L, 5_000L), tracker.requestedIntervals)
        assertEquals(20_000L, currentTime) // 실패 2번 × 10초
    }

    @Test
    fun `안내가 없으면 위치를 등록하지 않는다`() = runTest {
        val tracker = FlakyTracker(failures = 0, fix = here)
        val intervals = MutableStateFlow<Long?>(null)
        backgroundScope.launch { intervals.locationFixes(tracker).collect { } }
        testScheduler.runCurrent()
        assertEquals(emptyList<Long>(), tracker.requestedIntervals)

        intervals.value = 5_000L                                  // 안내가 시작되면 등록
        testScheduler.runCurrent()
        assertEquals(listOf(5_000L), tracker.requestedIntervals)
    }
}
