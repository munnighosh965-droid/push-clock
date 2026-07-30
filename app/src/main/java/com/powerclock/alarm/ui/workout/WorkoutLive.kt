package com.powerclock.alarm.ui.workout

import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.pose.BodyPoint
import com.powerclock.alarm.domain.pose.PoseHint
import com.powerclock.alarm.domain.pose.PoseSample
import com.powerclock.alarm.domain.pose.RepCounter
import com.powerclock.alarm.camera.PoseCameraPreview
import com.powerclock.alarm.ui.theme.Champagne
import com.powerclock.alarm.ui.theme.Platinum
import java.util.Locale

private val SKELETON_EDGES = listOf(
    BodyPoint.LEFT_SHOULDER to BodyPoint.RIGHT_SHOULDER,
    BodyPoint.LEFT_SHOULDER to BodyPoint.LEFT_ELBOW,
    BodyPoint.LEFT_ELBOW to BodyPoint.LEFT_WRIST,
    BodyPoint.RIGHT_SHOULDER to BodyPoint.RIGHT_ELBOW,
    BodyPoint.RIGHT_ELBOW to BodyPoint.RIGHT_WRIST,
    BodyPoint.LEFT_SHOULDER to BodyPoint.LEFT_HIP,
    BodyPoint.RIGHT_SHOULDER to BodyPoint.RIGHT_HIP,
    BodyPoint.LEFT_HIP to BodyPoint.RIGHT_HIP,
    BodyPoint.LEFT_HIP to BodyPoint.LEFT_KNEE,
    BodyPoint.LEFT_KNEE to BodyPoint.LEFT_ANKLE,
    BodyPoint.RIGHT_HIP to BodyPoint.RIGHT_KNEE,
    BodyPoint.RIGHT_KNEE to BodyPoint.RIGHT_ANKLE,
)

/**
 * Live camera workout with on-device pose counting. Shared between the
 * editor's "Test mission" mode and real ringing missions.
 */
@Composable
fun WorkoutLiveView(
    config: MissionConfig,
    spokenCues: Boolean,
    testMode: Boolean,
    onComplete: () -> Unit,
    onCannotRun: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCannotSafelyExercise: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val counter = remember(config) { RepCounter.forType(config.type, config.sensitivity) }

    var reps by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(PoseHint.GET_IN_POSITION) }
    var phaseProgress by remember { mutableStateOf(0f) }
    var latestSample by remember { mutableStateOf<PoseSample?>(null) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }

    val currentOnComplete by rememberUpdatedState(onComplete)

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(spokenCues) {
        if (spokenCues && tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                tts?.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(reps) {
        if (reps > 0) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            tts?.speak("$reps", TextToSpeech.QUEUE_FLUSH, null, "rep$reps")
        }
        if (reps >= config.target && !done) {
            done = true
            currentOnComplete()
        }
    }

    Box(modifier = modifier) {
        PoseCameraPreview(
            useFrontCamera = useFrontCamera,
            onSample = { sample ->
                latestSample = sample
                val update = counter.process(sample)
                reps = update.repCount
                paused = update.paused
                hint = update.hint
                phaseProgress = update.phaseProgress
            },
            onBindFailed = { e ->
                onCannotRun(e.message ?: "Camera unavailable")
            },
            modifier = Modifier.fillMaxSize(),
        )

        SkeletonOverlay(
            sample = latestSample,
            mirrored = useFrontCamera,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$reps / ${config.target}",
                style = MaterialTheme.typography.displayMedium,
                color = Champagne,
            )
            Text(
                "${(config.target - reps).coerceAtLeast(0)} to go",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            LinearProgressIndicator(
                progress = { phaseProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Platinum,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        }

        val hintText = when {
            paused -> "Can't see you clearly — step back so your whole body is in the frame."
            hint == PoseHint.GET_IN_POSITION -> "Get into the start position…"
            hint == PoseHint.GO_LOWER -> "Almost — complete the full movement."
            hint == PoseHint.FULL_RETURN -> "Return fully to the start position."
            else -> ""
        }
        if (hintText.isNotEmpty()) {
            Text(
                hintText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier.weight(1f),
                ) { Text(if (useFrontCamera) "Use rear camera" else "Use front camera") }
                if (testMode) {
                    OutlinedButton(onClick = onComplete, modifier = Modifier.weight(1f)) {
                        Text("End test")
                    }
                }
            }
            if (!testMode && onCannotSafelyExercise != null) {
                OutlinedButton(
                    onClick = onCannotSafelyExercise,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("I cannot safely exercise") }
            }
        }
    }
}

/**
 * Simple alignment skeleton assuming the preview is center-cropped
 * (PreviewView FILL_CENTER); good enough for positioning guidance.
 */
@Composable
private fun SkeletonOverlay(
    sample: PoseSample?,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val s = sample ?: return@Canvas
        fun mapPoint(p: com.powerclock.alarm.domain.pose.PosePoint): Offset {
            val x = if (mirrored) 1f - p.x else p.x
            return Offset(x * size.width, p.y * size.height)
        }
        for ((a, b) in SKELETON_EDGES) {
            val pa = s[a] ?: continue
            val pb = s[b] ?: continue
            if (pa.visibility < 0.4f || pb.visibility < 0.4f) continue
            drawLine(
                color = Champagne.copy(alpha = 0.8f),
                start = mapPoint(pa),
                end = mapPoint(pb),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        for (p in s.points.values) {
            if (p.visibility < 0.4f) continue
            drawCircle(color = Platinum, radius = 9f, center = mapPoint(p))
        }
    }
}
