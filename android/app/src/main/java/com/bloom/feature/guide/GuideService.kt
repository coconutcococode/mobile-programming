package com.bloom.feature.guide

import android.Manifest
import android.annotation.SuppressLint
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
import com.bloom.core.location.LocationPermissions
import com.bloom.data.repository.GuideSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 화면이 꺼져도 안내를 이어 가는 전경 서비스(location 타입).
 * 안내 상태는 GuideSessionRepository 하나를 앱 화면(S-04)과 같이 본다.
 *
 * TODO(트랙 B): 위치 수신 → 단계 자동 전환, 승차 지점 500m 이내 실시간 도착 30초 폴링(NFR-03),
 *  경로 이탈 판정(50m·20초), 하차 진동(FR-07), 지하철 구간 위치 간격 30초(NFR-04).
 */
@AndroidEntryPoint
class GuideService : LifecycleService() {

    @Inject lateinit var guideSessionRepository: GuideSessionRepository

    private var observeJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            GuideNotification.NOTIFICATION_ID,
            GuideNotification.build(this, guideSessionRepository.session.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )

        if (observeJob == null) {
            observeJob = lifecycleScope.launch {
                guideSessionRepository.session.collect { session ->
                    if (session == null) stopSelf() else notifyProgress(GuideNotification.build(this@GuideService, session))
                }
            }
        }
        // 강제 종료되면 재시작하지 않는다. 경로는 디스크에 없으므로 다음 실행 때 S-01-B2 배너로 안내(NFR-07).
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // 아래에서 알림 권한 확인
    private fun notifyProgress(notification: android.app.Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) NotificationManagerCompat.from(this).notify(GuideNotification.NOTIFICATION_ID, notification)
    }

    companion object {
        private const val ACTION_STOP = "com.bloom.guide.STOP"

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
