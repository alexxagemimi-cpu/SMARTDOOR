package com.exhibition.smartdoorlock.ui.theme

import androidx.compose.ui.graphics.Color

// Neutral base — a slightly warm graphite rather than pure black, so surfaces
// still read as "material" instead of a flat void.
val GraphiteBlack = Color(0xFF121214)
val SurfaceDark = Color(0xFF1B1B1E)
val SurfaceDarkElevated = Color(0xFF232326)
val BorderDark = Color(0xFF2E2E32)
val OffWhite = Color(0xFFF5F5F4)
val MutedGrayDark = Color(0xFFA0A0A5)

val OffWhiteBg = Color(0xFFFAFAF9)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceLightElevated = Color(0xFFF2F2F1)
val BorderLight = Color(0xFFE2E2E1)
val GraphiteText = Color(0xFF19191B)
val MutedGrayLight = Color(0xFF6C6C70)

// One deliberate accent — a cool steel blue, used only for interactive/primary
// elements. It's kept out of anything decorative so it stays meaningful.
val AccentBlue = Color(0xFF5B8DEF)

// State colors. These are reserved exclusively for door-state / form-validation
// feedback — never used decoratively — so green and red keep a consistent meaning
// everywhere they appear in the app.
val SuccessGreen = Color(0xFF34C77B)
val DangerRed = Color(0xFFE5484D)
