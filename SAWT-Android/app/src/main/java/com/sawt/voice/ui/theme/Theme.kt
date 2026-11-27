package com.sawt.voice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val SawtColorScheme = darkColorScheme(
    primary = SawtColors.Crimson,
    onPrimary = SawtColors.TextPrimary,
    secondary = SawtColors.Blue,
    onSecondary = SawtColors.TextPrimary,
    tertiary = SawtColors.BlueLight,
    background = SawtColors.Black,
    onBackground = SawtColors.TextPrimary,
    surface = SawtColors.Surface,
    onSurface = SawtColors.TextPrimary,
    surfaceVariant = SawtColors.SurfaceHigh,
    onSurfaceVariant = SawtColors.TextSecondary,
    outline = SawtColors.Outline,
    outlineVariant = SawtColors.Outline,
    error = SawtColors.CrimsonLight,
    onError = SawtColors.Black,
)

/** التطبيق داكن دائمًا (هوية بصرية ثابتة) ومفروض عليه اتجاه RTL. */
@Composable
fun SawtTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = SawtColorScheme,
            typography = SawtTypography,
            shapes = SawtShapes,
            content = content,
        )
    }
}
