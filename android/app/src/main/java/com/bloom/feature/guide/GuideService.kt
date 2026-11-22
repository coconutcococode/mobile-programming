package com.bloom.feature.guide

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.guide.GuideThresholds
import com.bloom.core.location.LocationPermissions
import com.bloom.core.location.LocationTracker
import com.bloom.core.model.GuideSession
import com.bloom.core.model.GuideStepType
import com.bloom.data.repository.GuideSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 화면이 꺼져도 안내를 이어 가는 전경 서비스(location 타입).
 * 받은 위치와 시간을 GuideSessionRepository(단계 전환 엔진)에 넘기고, 바뀐 상태를 잠금화면 알림(S-05)에 그린다.
 * 앱 화면(S-04)도 같은 저장소를 보므로 화면·알림·서비스가 항상 같은 단계를 보여 준다.
 *
 * TODO(트랙 B): 승차 지점 500m 이내 실시간 도착 30초 조회(FR-03, 서버 대조 결과 연결 후).
 */
@AndroidEntryPoint
class GuideService : LifecycleService() {

    @Inject lateinit var guideSessionRepository: GuideSessionRepository
    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private var running = false
    private var arrivedJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_END -> {
                guideSessionRepository.end()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_BOARD -> guideSessionRepository.board()
            ACTION_ALIGHT -> guideSessionRepository.alight()
            ACTION_NEXT -> guideSessionRepository.next()
            ACTION_REROUTE -> lifecycleScope.launch {
                locationTracker.currentLocation()?.let { guideSessionRepository.reroute(it) }
            }
        }

        if (!running) {
            running = true
            ServiceCompat.startForeground(
                this,
                GuideNotification.NOTIFICATION_ID,
                GuideNotification.build(this, guideSessionRepository.session.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            observeSession()
            followLocation()
            tick()
        }
        // 강제 종료되면 재시작하지 않는다. 경로는 디스크에 없으므로 다음 실행 때 S-01-B2 배너로 안내(NFR-07).
        return START_NOT_STICKY
    }

    /** 상태가 바뀔 때마다 알림을 다시 그리고, 단계가 바뀐 순간의 일(진동·도착 처리)을 한다. */
    private fun observeSession() = lifecycleScope.launch {
        var previous: GuideSession? = null
        guideSessionRepository.session.collect { session ->
            if (session == null) {
                stopSelf()
                return@collect
            }
            notify(GuideNotification.build(this@GuideService, session))
            val stepChanged = previous?.route !== session.route || previous?.currentStepIndex != session.currentStepIndex
            if (stepChanged) onStepEntered(session)
            previous = session
        }
    }

    private suspend fun onStepEntered(session: GuideSession) {
        arrivedJob?.cancel()
        when (session.currentStep.type) {
            // 하차 대기로 바뀌면 진동 2회(FR-07). 설정에서 끈 경우는 문구만 바뀐다.
            GuideStepType.WAIT_ALIGHTING ->
                if (settingsDataStore.settings.first().alightVibration) AlightVibration.play(this)
            // '도착' 표시 후 60초 뒤 알림 제거·서비스 종료·경로 데이터 폐기
            GuideStepType.ARRIVED -> arrivedJob = lifecycleScope.launch {
                delay(GuideThresholds.ARRIVED_NOTIFICATION_CLEAR_MILLIS)
                guideSessionRepository.end()
            }
            else -> Unit
        }
    }

    /** 단계에 맞는 간격으로 위치를 받는다. 간격이 바뀌면(지하철 차량 이동 30초 ↔ 5초) 다시 등록한다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun followLocation() = lifecycleScope.launch {
        guideSessionRepository.session
            .map { it?.locationIntervalMillis }
            .distinctUntilChanged()
            .flatMapLatest { interval -> if (interval == null) emptyFlow() else locationTracker.locationUpdates(interval) }
            .catch { /* 위치 등록 실패: 자동 전환 없이 단계 버튼으로 진행 */ }
            .collect { guideSessionRepository.onLocation(it) }
    }

    /** 지하철 추정 전환·남은 정거장처럼 시간만 지나도 바뀌는 상태를 1초마다 갱신한다. */
    private fun tick() = lifecycleScope.launch {
        while (isActive) {
            delay(1_000)
            guideSessionRepository.onTick()
        }
    }

    @SuppressLint("MissingPermission") // 아래에서 알림 권한 확인
    private fun notify(notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) NotificationManagerCompat.from(this).notify(GuideNotification.NOTIFICATION_ID, notification)
    }

    companion object {
        // 잠금화면 알림 버튼 → 이 서비스로 전달
        internal const val ACTION_BOARD = "com.bloom.guide.BOARD"
        internal const val ACTION_ALIGHT = "com.bloom.guide.ALIGHT"
        internal const val ACTION_NEXT = "com.bloom.guide.NEXT"
        internal const val ACTION_REROUTE = "com.bloom.guide.REROUTE"
        internal const val ACTION_END = "com.bloom.guide.END"

        /** 정확한 위치 권한이 없으면 location 타입 전경 서비스를 시작할 수 없다(API 34+). */
        fun start(context: Context): Boolean {
            if (!LocationPermissions.hasPreciseLocation(context)) return false
            ContextCompat.startForegroundService(context, Intent(context, GuideService::class.java))
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuideService::class.java))
        }
    }
}
