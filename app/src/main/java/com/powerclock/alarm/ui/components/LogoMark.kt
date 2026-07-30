package com.powerclock.alarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.min

/**
 * The Power Clock mark, running as a real clock: the champagne-gold ring
 * opened at twelve with its power stem is the app icon, and the hands inside
 * it show the actual time. Used anywhere the logo appears.
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    diameter: Dp = 28.dp,
    showSeconds: Boolean = true,
    reduceMotion: Boolean = false,
) {
    val reduced = rememberReducedMotion(reduceMotion)
    val gold = MaterialTheme.colorScheme.primary
    val hands = MaterialTheme.colorScheme.onBackground
    val secondHand = MaterialTheme.colorScheme.secondary

    var hour by remember { mutableIntStateOf(0) }
    var minute by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced, showSeconds) {
        while (true) {
            val t = LocalTime.now()
            hour = t.hour
            minute = t.minute
            seconds = t.second + t.nano / 1_000_000_000f
            delay(if (reduced || !showSeconds) 1000L else 40L)
        }
    }

    val label = "Power Clock logo showing ${TimeFormat.hourMinute(hour, minute)}"
    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = label },
    ) {
        val c = center
        val r = min(size.width, size.height) / 2f
        // Ring geometry mirrors the app icon: radius 40 and stroke 6.2 of a
        // 100-unit mark, with a 19-degree opening at twelve.
        val ringR = r * 0.80f
        val stroke = r * 0.124f
        val gapHalf = 9.5f

        drawArc(
            color = gold,
            startAngle = -90f + gapHalf,
            sweepAngle = 360f - gapHalf * 2f,
            useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR),
            size = Size(ringR * 2f, ringR * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Power stem, from the ring inwards.
        drawLine(
            color = gold,
            start = Offset(c.x, c.y - ringR),
            end = Offset(c.x, c.y - ringR * 0.6f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        val hourAngle = ((hour % 12) + minute / 60f) / 12f * 360f
        val minuteAngle = (minute + seconds / 60f) / 60f * 360f
        rotate(degrees = hourAngle, pivot = c) {
            drawLine(
                color = hands,
                start = c,
                end = Offset(c.x, c.y - ringR * 0.42f),
                strokeWidth = r * 0.1f,
                cap = StrokeCap.Round,
            )
        }
        rotate(degrees = minuteAngle, pivot = c) {
            drawLine(
                color = hands,
                start = c,
                end = Offset(c.x, c.y - ringR * 0.58f),
                strokeWidth = r * 0.07f,
                cap = StrokeCap.Round,
            )
        }
        if (showSeconds) {
            rotate(degrees = seconds / 60f * 360f, pivot = c) {
                drawLine(
                    color = secondHand,
                    start = c,
                    end = Offset(c.x, c.y - ringR * 0.56f),
                    strokeWidth = r * 0.032f,
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(color = gold, radius = r * 0.07f, center = c)
    }
}
