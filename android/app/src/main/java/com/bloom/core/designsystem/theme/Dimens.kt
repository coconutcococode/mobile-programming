package com.bloom.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** UI 설계서 [그림 3-1] 여백·모양 + 5.2 피츠의 법칙 + 6.2 터치 타깃. 기준 화면 360×760dp. */
object BloomDimens {
    val ScreenHorizontal = 16.dp   // 좌우 여백
    val Grid = 8.dp                // 8dp 그리드
    val CardGap = 10.dp            // 카드 간격
    val SectionGap = 24.dp         // 섹션 간격
    val CardRadius = 12.dp         // 카드 모서리
    val ChipRadius = 17.dp         // 칩 모서리(완전 둥글)
    val MinTouchTarget = 48.dp     // 터치 영역 최소 48×48
    val PrimaryButtonHeight = 56.dp        // 주 버튼(S-03·S-04) 56dp
    val PrimaryButtonHeightCompact = 52.dp // 주 버튼(그 외) 52dp
    val PrimaryButtonMaxWidth = 328.dp
    val BottomBarItemHeight = 64.dp
}

val BloomShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(BloomDimens.CardRadius),
    large = RoundedCornerShape(16.dp),
)
