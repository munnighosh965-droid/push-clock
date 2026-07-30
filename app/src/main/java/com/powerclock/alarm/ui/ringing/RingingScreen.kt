package com.powerclock.alarm.ui.ringing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.domain.missions.FallbackSelector
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.ui.components.ProgressRing
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.components.rememberReducedMotion
import com.powerclock.alarm.ui.home.missionShortName
import com.powerclock.alarm.ui.theme.Inter
import com.powerclock.alarm.ui.workout.WorkoutLiveView
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

@Composable
fun RingingRoot(
    onFinished: () -> Unit,
    viewModel: RingingViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // If the service session disappears while we are not on the success
    // screen, the alarm ended elsewhere (auto-silence or a queued switch).
    // A generous grace period covers slow service cold starts on throttled
    // devices, where this activity may open before the session exists.
    LaunchedEffect(session, run.phase) {
        if (session == null && run.phase != RingingPhase.SUCCESS) {
            delay(8000)
            onFinished()
        }
    }

    if (session == null && run.phase == RingingPhase.RINGING) {
        // Waiting for the ringing service to publish its session.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Starting alarm…", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    when (run.phase) {
        RingingPhase.RINGING -> {
            val s = session ?: return
            RingingFace(
                label = s.alarm.label,
                queuedCount = s.queuedCount,
                reduceMotion = rememberReducedMotion(settings.reduceMotion),
                onStart = viewModel::beginMissions,
            )
        }

        RingingPhase.MISSION -> MissionHost(viewModel)

        RingingPhase.SUCCESS -> SuccessScreen(
            totalReps = run.totalReps,
            name = settings.name,
            reduceMotion = rememberReducedMotion(settings.reduceMotion),
            onRate = viewModel::rateMorning,
            onClose = onFinished,
        )
    }
}

@Composable
private fun RingingFace(
    label: String,
    queuedCount: Int,
    reduceMotion: Boolean,
    onStart: () -> Unit,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }

    val pulse = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "pulse")
        val v by transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                tween(700, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "pulseValue",
        )
        v
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "WAKE UP",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (label.isNotBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (queuedCount > 0) {
                Text(
                    "$queuedCount more alarm(s) waiting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        ProgressRing(
            progress = 1f,
            ringSize = 240.dp,
            modifier = Modifier.scale(pulse),
        ) {
            Text(
                TimeFormat.clock(now),
                style = MaterialTheme.typography.displayMedium,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 64.dp),
            ) {
                Text(
                    "Start wake-up mission",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "The alarm stops once your mission is complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MissionHost(viewModel: RingingViewModel) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mission = run.current ?: return
    var helpOpen by remember { mutableStateOf(false) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            // Denied: swap in the fallback so the alarm stays dismissable.
            viewModel.replaceCurrentMission(FallbackSelector.FailureReason.CAMERA_UNAVAILABLE)
        }
    }

    // Camera permission is requested right here, at ring time, instead of
    // silently replacing the workout with a fallback mission.
    LaunchedEffect(mission) {
        if (mission.type.needsCamera) {
            if (mission.type == MissionType.QR_SCAN && settings.qrCardId.isBlank()) {
                viewModel.replaceCurrentMission(FallbackSelector.FailureReason.SENSOR_UNAVAILABLE)
            } else if (!cameraGranted) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Mission ${run.index + 1} of ${run.missions.size}: ${missionShortName(mission.type.name)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { helpOpen = true }) { Text("Help") }
        }

        val content: @Composable () -> Unit = {
            when {
                mission.type.needsCamera && !cameraGranted -> CameraPermissionWait(
                    onAllow = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onFallback = {
                        viewModel.replaceCurrentMission(FallbackSelector.FailureReason.CAMERA_UNAVAILABLE)
                    },
                )
                mission.type == MissionType.MATH ->
                    MathMissionScreen(mission) { viewModel.onMissionCompleted() }
                mission.type == MissionType.MEMORY ->
                    MemoryMissionScreen(mission) { viewModel.onMissionCompleted() }
                mission.type == MissionType.TYPING ->
                    TypingMissionScreen(mission) { viewModel.onMissionCompleted() }
                mission.type == MissionType.SHAKE -> ShakeMissionScreen(
                    config = mission,
                    onComplete = { viewModel.onMissionCompleted() },
                    onSensorUnavailable = {
                        viewModel.replaceCurrentMission(FallbackSelector.FailureReason.SENSOR_UNAVAILABLE)
                    },
                )
                mission.type == MissionType.QR_SCAN -> QrMissionScreen(
                    expectedContent = settings.qrCardId,
                    onComplete = { viewModel.onMissionCompleted() },
                    onCameraFailed = { reason -> viewModel.replaceCurrentMission(reason) },
                )
                else -> WorkoutLiveView(
                    config = mission,
                    spokenCues = settings.spokenCues,
                    testMode = false,
                    onComplete = { viewModel.onMissionCompleted(mission.target) },
                    onCannotRun = {
                        viewModel.replaceCurrentMission(FallbackSelector.FailureReason.CAMERA_UNAVAILABLE)
                    },
                    onCannotSafelyExercise = viewModel::cannotSafelyExercise,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { content() }
    }

    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text("Mission not working?") },
            text = {
                Column {
                    Text(
                        "The alarm can only be dismissed by completing a mission. " +
                            "If this one can't run right now, switch to a safe non-camera mission:",
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            helpOpen = false
                            viewModel.cannotSafelyExercise()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("I cannot safely exercise — switch mission") }
                }
            },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) { Text("Back to mission") }
            },
        )
    }
}

/** Shown while the ring-time camera permission dialog is on screen. */
@Composable
private fun CameraPermissionWait(
    onAllow: () -> Unit,
    onFallback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Camera permission needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The camera only counts your movements on this device — nothing is recorded or uploaded.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow camera") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onFallback, modifier = Modifier.fillMaxWidth()) {
            Text("Use a non-camera mission instead")
        }
    }
}

@Composable
private fun SuccessScreen(
    totalReps: Int,
    name: String,
    reduceMotion: Boolean,
    onRate: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var rated by remember { mutableStateOf(0) }
    val pulse = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "successPulse")
        val v by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "successPulseValue",
        )
        v
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressRing(progress = 1f, ringSize = 160.dp, modifier = Modifier.scale(pulse)) {
            // The display face carries no dingbats, so symbols use the text face.
            Text(
                "✓",
                style = MaterialTheme.typography.displayMedium,
                fontFamily = Inter,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Mission complete${if (name.isBlank()) "" else ", $name"}!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                totalReps > 0 -> "$totalReps reps before most people opened their eyes. Ready to power up?"
                else -> "Alarm conquered. Ready to power up?"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text("How's your energy?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { star ->
                TextButton(onClick = {
                    rated = star
                    onRate(star)
                }) {
                    Text(
                        if (star <= rated) "★" else "☆",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = Inter,
                        color = if (star <= rated) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 56.dp),
        ) { Text("Start the day") }
    }
}
