package com.exhibition.smartdoorlock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = OffWhite,
    background = GraphiteBlack,
    onBackground = OffWhite,
    surface = SurfaceDark,
    onSurface = OffWhite,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = MutedGrayDark,
    outline = BorderDark,
    error = DangerRed,
    onError = OffWhite
)

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = OffWhite,
    background = OffWhiteBg,
    onBackground = GraphiteText,
    surface = SurfaceLight,
    onSurface = GraphiteText,
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = MutedGrayLight,
    outline = BorderLight,
    error = DangerRed,
    onError = OffWhite
)

@Composable
fun SmartDoorLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
