package com.bloom.feature.guide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bloom.MainActivity
import com.bloom.R
import com.bloom.core.model.GuideSession

/**
 * S-05 잠금화면 진행 중 알림.
 * TODO(트랙 B): 단계 버튼(승차·다음 단계·종료) 액션, 도착 예정·기준 시각, 경로 이탈 시 [재탐색] (S-05 이탈 알림).
 */
object GuideNotification {
    const val CHANNEL_ID = "guide_progress"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "길안내 진행", NotificationManager.IMPORTANCE_LOW).apply {
            description = "진행 중인 길안내를 잠금화면에 표시합니다"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, session: GuideSession?): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val step = session?.currentStep
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guide)
            .setContentTitle(step?.title ?: "길안내 준비 중")
            .setContentText(
                session?.let { "${it.currentStepIndex + 1} / ${it.steps.size}단계 · ${it.route.arrivalTime} 도착 예정" } ?: "",
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
