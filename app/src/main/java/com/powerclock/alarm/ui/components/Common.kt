package com.powerclock.alarm.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerclock.alarm.domain.stats.WakeStats
import com.powerclock.alarm.ui.theme.AlertRed
import com.powerclock.alarm.ui.theme.ElectricLime
import com.powerclock.alarm.ui.theme.PowerBlue
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PowerCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/** The in-app POWER CLOCK wordmark. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, big: Boolean = false) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "POWER",
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 2.sp,
        )
        Text(
            " CLOCK",
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = ElectricLime,
            letterSpacing = 2.sp,
        )
    }
}

/** Animated ring used for countdowns and workout progress. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 180.dp,
    stroke: Dp = 12.dp,
    color: Color = ElectricLime,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Seven-day consistency dots; shape + letter make status color-independent. */
@Composable
fun WeekDots(results: List<WakeStats.DayResult>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        results.forEach { r ->
            val (color, letter) = when (r) {
                WakeStats.DayResult.SUCCESS -> ElectricLime to "✓"
                WakeStats.DayResult.EMERGENCY -> PowerBlue to "E"
                WakeStats.DayResult.MISSED -> AlertRed to "×"
                WakeStats.DayResult.NO_ALARM -> MaterialTheme.colorScheme.surfaceVariant to "·"
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (r == WakeStats.DayResult.NO_ALARM) 1f else 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    color = if (r == WakeStats.DayResult.NO_ALARM) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        color
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Honors both the in-app reduced-motion setting and the system toggle. */
@Composable
fun rememberReducedMotion(userPreference: Boolean): Boolean {
    val context = LocalContext.current
    val systemReduced = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    return userPreference || systemReduced
}

object TimeFormat {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dateFull = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
    private val dateShort = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.US)

    fun clock(time: ZonedDateTime): String = clock.format(time)
    fun fullDate(time: ZonedDateTime): String = dateFull.format(time)
    fun nextAlarm(time: ZonedDateTime): String = dateShort.format(time)

    fun minutesAsClock(minutesOfDay: Int): String =
        "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

    fun countdown(from: ZonedDateTime, to: ZonedDateTime): String {
        val d = Duration.between(from, to)
        if (d.isNegative) return "now"
        val days = d.toDays()
        val hours = d.toHours() % 24
        val minutes = d.toMinutes() % 60 + 1
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${minutes.coerceAtMost(59)}m"
            else -> "in ${minutes.coerceAtMost(59)}m"
        }
    }
}
