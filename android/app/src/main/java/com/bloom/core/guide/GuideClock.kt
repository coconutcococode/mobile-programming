package com.bloom.core.guide

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 길안내 시각 기준. 위치의 측정 시각([com.bloom.core.model.LocationFix.elapsedRealtimeMillis])과 같은
 * '기기 부팅 후 경과 시간'을 쓴다. 테스트에서는 가짜 시계로 바꾼다.
 */
fun interface GuideClock {
    fun now(): Long
}

@Singleton
class SystemGuideClock @Inject constructor() : GuideClock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GuideClockModule {
    @Binds
    abstract fun bindGuideClock(impl: SystemGuideClock): GuideClock
}
