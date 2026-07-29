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

// Brand palette.
val Midnight = Color(0xFF080B12)
val DeepSurface = Color(0xFF111827)
val ElectricLime = Color(0xFFC7FF45)
val PowerBlue = Color(0xFF4DA3FF)
val WarmWhite = Color(0xFFF7F9FC)
val AlertRed = Color(0xFFFF5A5F)

private val DarkColors = darkColorScheme(
    primary = ElectricLime,
    onPrimary = Midnight,
    primaryContainer = Color(0xFF2A3320),
    onPrimaryContainer = ElectricLime,
    secondary = PowerBlue,
    onSecondary = Midnight,
    secondaryContainer = Color(0xFF16283E),
    onSecondaryContainer = PowerBlue,
    tertiary = WarmWhite,
    background = Midnight,
    onBackground = WarmWhite,
    surface = DeepSurface,
    onSurface = WarmWhite,
    surfaceVariant = Color(0xFF1B2436),
    onSurfaceVariant = Color(0xFFB9C2D0),
    outline = Color(0xFF3A4557),
    error = AlertRed,
    onError = WarmWhite,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6B00),
    onPrimary = WarmWhite,
    primaryContainer = Color(0xFFE4FFAD),
    onPrimaryContainer = Color(0xFF1A2600),
    secondary = Color(0xFF1E62A8),
    onSecondary = WarmWhite,
    secondaryContainer = Color(0xFFD5E7FB),
    onSecondaryContainer = Color(0xFF0B2A47),
    background = WarmWhite,
    onBackground = Color(0xFF15181E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFE8ECF3),
    onSurfaceVariant = Color(0xFF454C57),
    outline = Color(0xFF9AA3B0),
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
