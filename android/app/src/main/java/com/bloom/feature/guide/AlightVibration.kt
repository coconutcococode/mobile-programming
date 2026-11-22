package com.bloom.feature.guide

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 하차 대기 진동 2회 (FR-07). */
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
        if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }
}
