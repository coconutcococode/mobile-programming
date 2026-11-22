package com.bloom.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap

/**
 * 지도 핀 그림을 코드로 그린다(PNG 파일을 따로 만들 필요 없음).
 * 색: 일반 핀 = 네이비, 선택 핀 = 더 크고 진한 네이비 + 하늘색 테두리, 내 위치 = 파란 점(UI 설계서 3장 색).
 * 비트맵은 한 번만 만들고 재사용한다(지도를 다시 그릴 때마다 만들면 느려짐).
 */
object MapPins {
    private val cache = mutableMapOf<String, Bitmap>()

    private fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density

    fun pin(context: Context, selected: Boolean): Bitmap = cache.getOrPut(if (selected) "pinSelected" else "pin") {
        val w = dp(context, if (selected) 34f else 26f)
        val h = w * 1.33f
        val bmp = createBitmap(w.toInt(), h.toInt())
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (selected) 0xFF1E2F42.toInt() else 0xFF2F4B6B.toInt() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 2f)
            color = if (selected) 0xFFB7D6F2.toInt() else 0xFFFFFFFF.toInt()
        }
        val r = w / 2
        val tail = Path().apply {
            moveTo(r * 0.35f, r * 1.5f)
            lineTo(r * 1.65f, r * 1.5f)
            lineTo(r, h)
            close()
        }
        canvas.drawPath(tail, fill)
        canvas.drawCircle(r, r, r - ring.strokeWidth, fill)
        canvas.drawCircle(r, r, r - ring.strokeWidth, ring)
        bmp
    }

    fun myLocation(context: Context): Bitmap = cache.getOrPut("me") {
        val s = dp(context, 22f)
        val bmp = createBitmap(s.toInt(), s.toInt())
        val canvas = Canvas(bmp)
        val r = s / 2
        canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x506A93C5 })
        canvas.drawCircle(r, r, r * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F4B6B.toInt() })
        canvas.drawCircle(
            r, r, r * 0.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(context, 2f)
                color = 0xFFFFFFFF.toInt()
            },
        )
        bmp
    }
}
