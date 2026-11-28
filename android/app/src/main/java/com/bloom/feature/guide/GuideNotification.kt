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
import com.bloom.core.model.GuideStepType
import com.bloom.core.model.TransitMode

/**
 * S-05 잠금화면 진행 중 알림. 잠금 해제 없이 단계 버튼(승차·하차 완료·다음 단계·재탐색·종료)을 누를 수 있다.
 * 알림을 누르면 안내 진행 화면(S-04)으로 간다.
 */
object GuideNotification {
    /**
     * 처음에는 IMPORTANCE_LOW 채널(guide_progress)이었는데, 낮은 중요도는 '무음' 칸에 접힌 채로 들어가 버튼이 안 보이고
     * 많은 기기에서 잠금화면에 무음 알림을 숨긴다. 채널 중요도는 만든 뒤 바꿀 수 없어 새 ID로 다시 만든다.
     */
    const val CHANNEL_ID = "guide_navigation"
    private const val OLD_CHANNEL_ID = "guide_progress"
    const val NOTIFICATION_ID = 1001

    /** 알림에 보이는 버튼은 최대 3개라 단계 버튼 2개 + 종료로 맞춘다. */
    private const val MAX_STEP_ACTIONS = 2

    fun createChannel(context: Context) {
        // 기본 중요도: 잠금화면·알림 칸에 펼쳐진 채로 보인다. 소리·진동은 끄고(하차 진동은 앱이 직접, FR-07) 단계마다 울리지 않게 한다.
        val channel = NotificationChannel(CHANNEL_ID, "길안내 진행", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "진행 중인 길안내를 잠금화면에 표시합니다"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, session: GuideSession?): Notification {
        val openGuide = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_GUIDE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guide)
            .setContentTitle(session?.currentStep?.title ?: "길안내 준비 중")
            .setContentText(session?.let(::contentText).orEmpty())
            .setSubText(session?.let { "${it.currentStepIndex + 1} / ${it.steps.size}단계 · ${it.route.arrivalTime} 도착 예정" })
            .setContentIntent(openGuide)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (session != null) {
            // 펼친 알림에서는 단계 안내와 도착 정보를 두 줄로
            if (session.arrival != null) builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText(session, separator = "\n")))
            stepActions(session).take(MAX_STEP_ACTIONS).forEach { (label, action) ->
                builder.addAction(0, label, serviceIntent(context, action))
            }
            builder.addAction(0, "종료", serviceIntent(context, GuideService.ACTION_END))
        }
        return builder.build()
    }

    /** 알림 본문: 단계 안내 + 실시간 도착 정보(FR-03, 승차 전 단계에서만). */
    internal fun contentText(session: GuideSession, separator: String = " · "): String =
        listOfNotNull(statusText(session), session.arrival?.let(::arrivalNotificationLine))
            .filter { it.isNotEmpty() }
            .joinToString(separator)

    /** 지금 상황에서 가장 먼저 알려야 할 한 줄 (S-05). */
    internal fun statusText(session: GuideSession): String {
        val step = session.currentStep
        val segment = step.segment
        val unit = if (segment?.mode == TransitMode.SUBWAY) "개 역" else "정거장"
        return when {
            session.isOffRoute -> "경로를 벗어났습니다"
            session.isLocationInaccurate -> "위치가 부정확합니다 · '다음 단계'로 넘길 수 있어요"
            session.isAtAlightingStation && segment != null -> "${segment.endName}에서 내리세요"
            step.type == GuideStepType.RIDING && session.remainingStops != null -> "남은 ${session.remainingStops}$unit"
            else -> step.detail
        }
    }

    /** 단계 버튼. 앞에 있을수록 먼저 보인다. */
    internal fun stepActions(session: GuideSession): List<Pair<String, String>> = buildList {
        if (session.isOffRoute) add("재탐색" to GuideService.ACTION_REROUTE)
        when (session.currentStep.type) {
            GuideStepType.WAIT_BOARDING -> add("승차" to GuideService.ACTION_BOARD)
            GuideStepType.WAIT_ALIGHTING -> add("하차 완료" to GuideService.ACTION_ALIGHT)
            else -> Unit
        }
        if (session.showNextButton && !session.isLastStep) add("다음 단계" to GuideService.ACTION_NEXT)
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, GuideService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
