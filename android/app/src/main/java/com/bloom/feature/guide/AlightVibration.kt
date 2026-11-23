package com.bloom.feature.guide

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 하차 대기 진동 2회 (FR-07).
 * 용도를 '알림'으로 지정해 기기의 알림 진동 설정을 따른다. 지정하지 않으면 미디어 진동 설정을 따라,
 * Android 16에서 미디어 진동만 꺼도 하차 진동이 울리지 않는다.
 */
object AlightVibration {
    // 0.3초 울림 · 0.2초 쉼 · 0.3초 울림
    private val PATTERN = longArrayOf(0, 300, 200, 300)

    fun play(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        val effect = VibrationEffect.createWaveform(PATTERN, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        }
    }
}
