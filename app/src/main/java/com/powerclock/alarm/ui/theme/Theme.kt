package com.powerclock.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand palette — deep ocean blues.
val Midnight = Color(0xFF021024)      // near-black navy background
val DeepSurface = Color(0xFF052659)   // deep navy surfaces
val Horizon = Color(0xFF5483B3)       // medium steel blue
val Mist = Color(0xFF7DA0CA)          // soft powder blue
val Glacier = Color(0xFFC1E8FF)       // pale ice blue highlight
val WarmWhite = Color(0xFFF4F9FF)
val AlertRed = Color(0xFFFF5A5F)

private val DarkColors = darkColorScheme(
    primary = Glacier,
    onPrimary = Midnight,
    primaryContainer = Color(0xFF0A3A73),
    onPrimaryContainer = Glacier,
    secondary = Mist,
    onSecondary = Midnight,
    secondaryContainer = Color(0xFF073066),
    onSecondaryContainer = Mist,
    tertiary = Horizon,
    background = Midnight,
    onBackground = WarmWhite,
    surface = DeepSurface,
    onSurface = WarmWhite,
    surfaceVariant = Color(0xFF0A3161),
    onSurfaceVariant = Mist,
    outline = Horizon,
    error = AlertRed,
    onError = WarmWhite,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF052659),
    onPrimary = Glacier,
    primaryContainer = Glacier,
    onPrimaryContainer = Midnight,
    secondary = Horizon,
    onSecondary = WarmWhite,
    secondaryContainer = Color(0xFFDCEFFE),
    onSecondaryContainer = Color(0xFF06264F),
    tertiary = Mist,
    background = Color(0xFFF2F8FF),
    onBackground = Color(0xFF0B1626),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1626),
    surfaceVariant = Color(0xFFE1EDF9),
    onSurfaceVariant = Color(0xFF3D5876),
    outline = Color(0xFF8FA9C4),
    error = Color(0xFFC0353A),
    onError = WarmWhite,
)

val PowerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val PowerTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PowerClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PowerTypography,
        shapes = PowerShapes,
        content = content,
    )
}
