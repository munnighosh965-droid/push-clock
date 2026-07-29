package com.powerclock.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Brand palette — obsidian, champagne gold, and platinum. Gold carries the
// accents, platinum the secondary information, so the two never compete.
val Obsidian = Color(0xFF0A0C10)      // near-black background
val Graphite = Color(0xFF14181F)      // raised surfaces
val Slate = Color(0xFF1D232C)         // surface variant
val Champagne = Color(0xFFE4C58A)     // warm gold accent
val ChampagneDim = Color(0xFFB89A61)  // gold, one step back
val Platinum = Color(0xFFC3CBD6)      // cool silver secondary
val Ivory = Color(0xFFF4F1EA)         // warm off-white text
val Muted = Color(0xFF9AA3B2)         // secondary text
val AlertRed = Color(0xFFE5544B)

private val DarkColors = darkColorScheme(
    primary = Champagne,
    onPrimary = Obsidian,
    primaryContainer = Color(0xFF2B2318),
    onPrimaryContainer = Color(0xFFF0DCB4),
    secondary = Platinum,
    onSecondary = Obsidian,
    secondaryContainer = Color(0xFF202832),
    onSecondaryContainer = Color(0xFFD6DEE9),
    tertiary = ChampagneDim,
    onTertiary = Obsidian,
    background = Obsidian,
    onBackground = Ivory,
    surface = Graphite,
    onSurface = Ivory,
    surfaceVariant = Slate,
    onSurfaceVariant = Muted,
    outline = Color(0xFF333B47),
    outlineVariant = Color(0xFF262D37),
    error = AlertRed,
    onError = Color(0xFF1A0A08),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFFFD9D4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5C1E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E6C9),
    onPrimaryContainer = Color(0xFF2A1F08),
    secondary = Color(0xFF46505F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E7EE),
    onSecondaryContainer = Color(0xFF1A222D),
    tertiary = Color(0xFF8C6C2A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF12151A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF5A6270),
    outline = Color(0xFFB4B9C2),
    outlineVariant = Color(0xFFDCDDE2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val PowerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
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
