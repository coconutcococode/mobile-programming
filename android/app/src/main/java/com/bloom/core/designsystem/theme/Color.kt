package com.bloom.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * UI 설계서 3장 [그림 3-1] 디자인 기준(스톰브레이크 콘트라스트) · 6.1 명암비 · 6.5 다크 모드.
 * 화면 코드에서는 이 값을 직접 쓰지 말고 MaterialTheme.colorScheme / BloomTheme.colors 를 쓴다.
 */
internal object BloomPalette {
    // 라이트
    val Background = Color(0xFFF5FAFF)        // 배경
    val SkyLight = Color(0xFFB7D6F2)          // 보조 하늘: 버스 구간·진행 전
    val BlueMid = Color(0xFF6A93C5)           // 중간 파랑: 테두리·비활성 (글자 X)
    val Navy = Color(0xFF2F4B6B)              // 네이비(주색): 버튼·선택·제목
    val WarningOrange = Color(0xFFF26B4F)     // 경고 주황: 경고·오류 아이콘·테두리 (글자에는 사용 금지)
    val TextPrimary = Color(0xFF1E2F42)       // 본문 글자
    val TextSecondary = Color(0xFF5A6B7E)     // 보조 글자
    val TextError = Color(0xFFB23A22)         // 오류 글자
    val SelectedBackground = Color(0xFFE6F0FA) // 선택 배경
    val WarningBackground = Color(0xFFFFF1EE) // 경고 배경
    val Skeleton = Color(0xFFE3ECF6)          // 스켈레톤
    val Link = Color(0xFF4A6E99)              // 링크 글자
    val White = Color(0xFFFFFFFF)

    // 다크 (6.5)
    val DarkBackground = Color(0xFF121A24)
    val DarkSurface = Color(0xFF1B2633)
    val DarkTextPrimary = Color(0xFFE6EDF5)
    val DarkTextSecondary = Color(0xFFA9B6C4)
    val DarkPrimary = Color(0xFF9CC3EC)       // 다크에서는 밝은 하늘 버튼 + 어두운 글자
    val DarkTextError = Color(0xFFFF9B85)

    // 설계서에 값이 없는 다크 보조색 — 임시값, 디자인 확정 시 교체
    val DarkSkeleton = Color(0xFF26323F)
    val DarkWarningBackground = Color(0xFF3A2622)
    val DarkSelectedBackground = Color(0xFF243447)
}

/** Material ColorScheme 에 없는 Bloom 전용 의미 색. */
@Immutable
data class BloomExtendedColors(
    val warning: Color,
    val warningBackground: Color,
    val textSecondary: Color,
    val textError: Color,
    val skeleton: Color,
    val link: Color,
    val selectedBackground: Color,
    // 구간 막대: 도보=연한 배경 + '도보 n', 버스=하늘 + '번호 n', 지하철=네이비 + '호선 n' (색 외 수단으로 글자 병기)
    val segmentWalk: Color,
    val onSegmentWalk: Color,
    val segmentBus: Color,
    val onSegmentBus: Color,
    val segmentSubway: Color,
    val onSegmentSubway: Color,
)

internal val LightExtendedColors = BloomExtendedColors(
    warning = BloomPalette.WarningOrange,
    warningBackground = BloomPalette.WarningBackground,
    textSecondary = BloomPalette.TextSecondary,
    textError = BloomPalette.TextError,
    skeleton = BloomPalette.Skeleton,
    link = BloomPalette.Link,
    selectedBackground = BloomPalette.SelectedBackground,
    segmentWalk = BloomPalette.SelectedBackground,
    onSegmentWalk = BloomPalette.TextPrimary,
    segmentBus = BloomPalette.SkyLight,
    onSegmentBus = BloomPalette.Navy,
    segmentSubway = BloomPalette.Navy,
    onSegmentSubway = BloomPalette.White,
)

internal val DarkExtendedColors = BloomExtendedColors(
    warning = BloomPalette.WarningOrange, // 경고 주황은 다크에서도 그대로
    warningBackground = BloomPalette.DarkWarningBackground,
    textSecondary = BloomPalette.DarkTextSecondary,
    textError = BloomPalette.DarkTextError,
    skeleton = BloomPalette.DarkSkeleton,
    link = BloomPalette.DarkPrimary,
    selectedBackground = BloomPalette.DarkSelectedBackground,
    segmentWalk = BloomPalette.DarkSelectedBackground,
    onSegmentWalk = BloomPalette.DarkTextPrimary,
    segmentBus = BloomPalette.SkyLight,
    onSegmentBus = BloomPalette.DarkBackground,
    segmentSubway = BloomPalette.DarkPrimary,
    onSegmentSubway = BloomPalette.DarkBackground,
)
