package com.bloom.feature.guide

import android.app.Notification
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** 길안내 알림이 사용자에게 실제로 보이는지 확인하고, 고칠 수 있는 시스템 설정 화면을 연다(S-04-B1·S-04-S1). */
object GuideSystemSettings {

    /** 앱 알림이 켜져 있고 길안내 채널도 꺼지지 않았으면 true. 권한 거절도 여기서 false 로 잡힌다. */
    fun notificationsEnabled(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(GuideNotification.CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * 잠금화면에 길안내 알림이 숨겨지는 설정이면 true.
     * - 기기 전체에서 잠금화면 알림을 끔
     * - 사용자가 길안내 채널만 잠금화면에서 숨김
     * 알림 채널은 VISIBILITY_PUBLIC 이라 '민감한 내용 숨기기'만 켠 경우에는 내용이 그대로 보인다.
     */
    fun isLockScreenHidden(context: Context): Boolean {
        val channel = NotificationManagerCompat.from(context).getNotificationChannel(GuideNotification.CHANNEL_ID)
        if (channel?.lockscreenVisibility == Notification.VISIBILITY_SECRET) return true
        // 공개 상수가 없는 시스템 설정값이라 못 읽으면 숨김이 아닌 것으로 본다(안내를 괜히 띄우지 않음).
        val showOnLockScreen = runCatching {
            Settings.Secure.getInt(context.contentResolver, LOCK_SCREEN_SHOW_NOTIFICATIONS, 1)
        }.getOrDefault(1)
        return showOnLockScreen == 0
    }

    /** 앱 알림 설정(권한·채널을 한곳에서 켤 수 있음). */
    fun openAppNotificationSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 잠금화면 알림 설정. 기기마다 화면이 달라 열 수 있는 것부터 차례로 시도하고, 마지막엔 앱 알림 설정을 연다. */
    fun openLockScreenNotificationSettings(context: Context) {
        val candidates = listOf(
            Intent(ACTION_LOCK_SCREEN_SETTINGS),
            Intent(ACTION_NOTIFICATION_SETTINGS),
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // 다음 후보
            }
        }
        openAppNotificationSettings(context)
    }

    private const val LOCK_SCREEN_SHOW_NOTIFICATIONS = "lock_screen_show_notifications"
    private const val ACTION_LOCK_SCREEN_SETTINGS = "android.settings.LOCK_SCREEN_SETTINGS"
    private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
}
