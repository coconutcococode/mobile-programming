package com.bloom.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * UI 설계서 [그림 3-1] 글자 크기(sp).
 * - 현재 단계 21 Bold → headlineSmall
 * - 화면 제목 18 Bold → titleLarge
 * - 목록 항목 15 SemiBold → titleMedium
 * - 본문 13~14.5 Regular → bodyLarge(14.5) / bodyMedium(14) / bodySmall(13)
 * - 보조·캡션 11.5 (최소 10.5) → labelMedium / labelSmall
 * 모든 글자는 sp 단위 — 시스템 글꼴 크기 설정을 따른다(6.3).
 */
val BloomTypography = Typography(
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), // 버튼
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)
