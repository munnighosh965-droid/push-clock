package com.powerclock.alarm.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The animated analog clock at the top of the dashboard: a breathing accent
 * aura, a slowly rotating rim highlight, an elapsed-seconds arc, and a
 * smoothly sweeping second hand. Everything holds still when reduced motion
 * is requested, so the dial stays readable without any movement.
 */
@Composable
fun HeroClock(
    now: ZonedDateTime,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 224.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val hands = MaterialTheme.colorScheme.onBackground
    val hub = MaterialTheme.colorScheme.surface
    // Dial furniture is a dimmed version of the text colour rather than the
    // cool secondary text tone, so it stays in key with the warm accent.
    val dial = hands

    // One high-resolution time source, so the three hands can never disagree
    // with each other by a fraction of a second.
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        while (true) {
            val t = LocalTime.now()
            seconds = t.second + t.nano / 1_000_000_000f
            delay(if (reduceMotion) 1000L else 40L)
        }
    }

    val rimSweep = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "rim")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
            label = "rimSweep",
        )
        v
    }
    val breathe = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "aura")
        val v by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "auraBreath",
        )
        v
    }

    val minuteOfHour = now.minute
    val hourOfDay = now.hour

    Box(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Analog clock showing ${TimeFormat.clock(now)}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = min(size.width, size.height) / 2f
            val secondAngle = seconds / 60f * 360f
            val minuteAngle = (minuteOfHour + seconds / 60f) / 60f * 360f
            val hourAngle = ((hourOfDay % 12) + minuteOfHour / 60f) / 12f * 360f

            // Soft breathing aura behind the dial.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    center = c,
                    radius = r * breathe,
                ),
                radius = r * breathe,
                center = c,
            )

            // Glass-like dial face.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(hub.copy(alpha = 0.85f), hub.copy(alpha = 0.25f)),
                    center = Offset(c.x, c.y - r * 0.25f),
                    radius = r,
                ),
                radius = r * 0.94f,
                center = c,
            )

            // Rim: a static hairline plus a rotating light sweep on top.
            drawCircle(
                color = dial.copy(alpha = 0.22f),
                radius = r * 0.96f,
                center = c,
                style = Stroke(width = r * 0.012f),
            )
            rotate(degrees = rimSweep, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.75f),
                            secondary.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Transparent,
                        ),
                        center = c,
                    ),
                    radius = r * 0.96f,
                    center = c,
                    style = Stroke(width = r * 0.02f),
                )
            }

            // Minute ticks, with the hour marks longer and brighter.
            for (i in 0 until 60) {
                val isHour = i % 5 == 0
                val rad = Math.toRadians((i * 6f - 90f).toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                val outer = r * 0.89f
                val inner = if (isHour) r * 0.78f else r * 0.84f
                drawLine(
                    color = if (isHour) dial.copy(alpha = 0.6f) else dial.copy(alpha = 0.2f),
                    start = Offset(c.x + cosA * inner, c.y + sinA * inner),
                    end = Offset(c.x + cosA * outer, c.y + sinA * outer),
                    strokeWidth = if (isHour) r * 0.022f else r * 0.01f,
                    cap = StrokeCap.Round,
                )
            }

            // Elapsed seconds of the current minute, as a thin accent arc.
            drawArc(
                color = accent.copy(alpha = 0.5f),
                startAngle = -90f,
                sweepAngle = secondAngle,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.96f, c.y - r * 0.96f),
                size = Size(r * 1.92f, r * 1.92f),
                style = Stroke(width = r * 0.016f, cap = StrokeCap.Round),
            )

            rotate(degrees = hourAngle, pivot = c) {
                drawLine(
                    color = hands,
                    start = Offset(c.x, c.y + r * 0.11f),
                    end = Offset(c.x, c.y - r * 0.46f),
                    strokeWidth = r * 0.055f,
                    cap = StrokeCap.Round,
                )
            }
            rotate(degrees = minuteAngle, pivot = c) {
                drawLine(
                    color = accent,
                    start = Offset(c.x, c.y + r * 0.13f),
                    end = Offset(c.x, c.y - r * 0.68f),
                    strokeWidth = r * 0.038f,
                    cap = StrokeCap.Round,
                )
            }
            rotate(degrees = secondAngle, pivot = c) {
                drawLine(
                    color = secondary,
                    start = Offset(c.x, c.y + r * 0.18f),
                    end = Offset(c.x, c.y - r * 0.8f),
                    strokeWidth = r * 0.014f,
                    cap = StrokeCap.Round,
                )
            }

            // Glowing marker riding the rim with the second hand.
            val secRad = Math.toRadians((secondAngle - 90f).toDouble())
            val markerCenter = Offset(
                c.x + cos(secRad).toFloat() * r * 0.96f,
                c.y + sin(secRad).toFloat() * r * 0.96f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.55f), Color.Transparent),
                    center = markerCenter,
                    radius = r * 0.1f,
                ),
                radius = r * 0.1f,
                center = markerCenter,
            )
            drawCircle(color = secondary, radius = r * 0.022f, center = markerCenter)

            drawCircle(color = accent, radius = r * 0.05f, center = c)
            drawCircle(color = hub, radius = r * 0.022f, center = c)
        }
    }
}

/**
 * The large digital time. Each character animates on its own, so only the
 * digits that actually changed roll over — minutes glide up while the hour
 * stays put.
 */
@Composable
fun AnimatedClockText(
    time: ZonedDateTime,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val digits = TimeFormat.clockDigits(time)
    val meridiem = TimeFormat.meridiem(time)
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onBackground,
            MaterialTheme.colorScheme.primary,
        ),
    )
    val digitStyle = MaterialTheme.typography.displayLarge.merge(TextStyle(brush = gradient))

    Row(
        modifier = modifier.semantics { contentDescription = "Current time ${TimeFormat.clock(time)}" },
        verticalAlignment = Alignment.Bottom,
    ) {
        digits.forEachIndexed { index, char ->
            if (reduceMotion) {
                Text(char.toString(), style = digitStyle)
            } else {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (
                            slideInVertically { height -> -height / 2 } +
                                fadeIn(animationSpec = tween(220))
                            ) togetherWith (
                            slideOutVertically { height -> height / 2 } +
                                fadeOut(animationSpec = tween(180))
                            )
                    },
                    label = "digit$index",
                ) { target ->
                    Text(target.toString(), style = digitStyle)
                }
            }
        }
        Text(
            meridiem,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
    }
}

/**
 * Fades and lifts its content into place once, [delayMillis] after first
 * composition. Used to stagger the dashboard so it assembles itself instead
 * of appearing all at once. A no-op under reduced motion.
 */
@Composable
fun RevealOnAppear(
    reduceMotion: Boolean,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (reduceMotion) {
        Box(modifier) { content() }
        return
    }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        revealed = true
    }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "reveal",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 36.dp.toPx()
        },
    ) { content() }
}
