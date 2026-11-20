package com.bloom.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColorScheme = lightColorScheme(
    primary = BloomPalette.Navy,
    onPrimary = BloomPalette.White,
    primaryContainer = BloomPalette.SelectedBackground,
    onPrimaryContainer = BloomPalette.Navy,
    secondary = BloomPalette.BlueMid,
    // 중간 파랑 위 흰 글자는 3.2:1 로 기준 미달 → 어두운 글자(5.5:1)
    onSecondary = BloomPalette.DarkBackground,
    secondaryContainer = BloomPalette.SkyLight,
    onSecondaryContainer = BloomPalette.TextPrimary,
    background = BloomPalette.Background,
    onBackground = BloomPalette.TextPrimary,
    surface = BloomPalette.White,
    onSurface = BloomPalette.TextPrimary,
    surfaceVariant = BloomPalette.SelectedBackground,
    onSurfaceVariant = BloomPalette.TextSecondary,
    outline = BloomPalette.BlueMid,
    error = BloomPalette.TextError,
    onError = BloomPalette.White,
    errorContainer = BloomPalette.WarningBackground,
    onErrorContainer = BloomPalette.TextError,
)

private val DarkColorScheme = darkColorScheme(
    primary = BloomPalette.DarkPrimary,
    onPrimary = BloomPalette.DarkBackground,
    primaryContainer = BloomPalette.DarkSelectedBackground,
    onPrimaryContainer = BloomPalette.DarkTextPrimary,
    secondary = BloomPalette.BlueMid,
    onSecondary = BloomPalette.DarkBackground,
    secondaryContainer = BloomPalette.DarkSelectedBackground,
    onSecondaryContainer = BloomPalette.DarkTextPrimary,
    background = BloomPalette.DarkBackground,
    onBackground = BloomPalette.DarkTextPrimary,
    surface = BloomPalette.DarkSurface,
    onSurface = BloomPalette.DarkTextPrimary,
    surfaceVariant = BloomPalette.DarkSurface,
    onSurfaceVariant = BloomPalette.DarkTextSecondary,
    outline = BloomPalette.BlueMid,
    error = BloomPalette.DarkTextError,
    onError = BloomPalette.DarkBackground,
    errorContainer = BloomPalette.DarkWarningBackground,
    onErrorContainer = BloomPalette.DarkTextError,
)

private val LocalBloomColors = staticCompositionLocalOf { LightExtendedColors }

@Composable
fun BloomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBloomColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = BloomTypography,
            shapes = BloomShapes,
            content = content,
        )
    }
}

/** 사용 예: `BloomTheme.colors.warning` */
object BloomTheme {
    val colors: BloomExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBloomColors.current
}
