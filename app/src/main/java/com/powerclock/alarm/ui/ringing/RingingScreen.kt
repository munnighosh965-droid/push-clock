package com.powerclock.alarm.ui.ringing

import android.Manifest
import android.content.pm.PackageManager
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
import com.powerclock.alarm.ui.components.HoldToConfirmButton
import com.powerclock.alarm.ui.components.ProgressRing
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.components.rememberReducedMotion
import com.powerclock.alarm.ui.home.missionShortName
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
    LaunchedEffect(session, run.phase) {
        if (session == null && run.phase != RingingPhase.SUCCESS) {
            delay(500)
            onFinished()
        }
    }

    when (run.phase) {
        RingingPhase.RINGING -> {
            val s = session ?: return
            RingingFace(
                label = s.alarm.label,
                queuedCount = s.queuedCount,
                hasMissions = s.alarm.missions.isNotEmpty(),
                reduceMotion = rememberReducedMotion(settings.reduceMotion),
                onStart = viewModel::beginMissions,
                onEmergency = viewModel::emergencyDismiss,
            )
        }

        RingingPhase.MISSION -> MissionHost(viewModel)

        RingingPhase.SUCCESS -> SuccessScreen(
            emergency = run.emergencyUsed,
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
    hasMissions: Boolean,
    reduceMotion: Boolean,
    onStart: () -> Unit,
    onEmergency: () -> Unit,
) {
    var confirmEmergency by remember { mutableStateOf(false) }
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
                    if (hasMissions) "Start mission" else "I'm up — dismiss",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(12.dp))
            HoldToConfirmButton(
                label = "Emergency dismiss (hold 10s)",
                holdSeconds = 10,
                onConfirmed = { confirmEmergency = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmEmergency) {
        AlertDialog(
            onDismissRequest = { confirmEmergency = false },
            title = { Text("Emergency dismiss?") },
            text = {
                Text(
                    "This skips your mission and is recorded in your history. Use it when you can't safely complete the mission — no judgement.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmergency = false
                    onEmergency()
                }) { Text("Dismiss alarm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmergency = false }) { Text("Back to mission") }
            },
        )
    }
}

@Composable
private fun MissionHost(viewModel: RingingViewModel) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mission = run.current ?: return
    var confirmEmergency by remember { mutableStateOf(false) }

    // Camera-dependent missions are replaced automatically when permission
    // is missing; the alarm must never be impossible to dismiss.
    LaunchedEffect(mission) {
        if (mission.type.needsCamera) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                viewModel.replaceCurrentMission(FallbackSelector.FailureReason.CAMERA_UNAVAILABLE)
            } else if (mission.type == MissionType.QR_SCAN && settings.qrCardId.isBlank()) {
                viewModel.replaceCurrentMission(FallbackSelector.FailureReason.SENSOR_UNAVAILABLE)
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
            OutlinedButton(onClick = { confirmEmergency = true }) { Text("Help") }
        }

        val content: @Composable () -> Unit = {
            when (mission.type) {
                MissionType.MATH -> MathMissionScreen(mission) { viewModel.onMissionCompleted() }
                MissionType.MEMORY -> MemoryMissionScreen(mission) { viewModel.onMissionCompleted() }
                MissionType.TYPING -> TypingMissionScreen(mission) { viewModel.onMissionCompleted() }
                MissionType.SHAKE -> ShakeMissionScreen(
                    config = mission,
                    onComplete = { viewModel.onMissionCompleted() },
                    onSensorUnavailable = {
                        viewModel.replaceCurrentMission(FallbackSelector.FailureReason.SENSOR_UNAVAILABLE)
                    },
                )
                MissionType.QR_SCAN -> QrMissionScreen(
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

    if (confirmEmergency) {
        AlertDialog(
            onDismissRequest = { confirmEmergency = false },
            title = { Text("Need a way out?") },
            text = {
                Column {
                    Text("If this mission isn't working, you have safe options:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            confirmEmergency = false
                            viewModel.cannotSafelyExercise()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("I cannot safely exercise — switch mission") }
                    Spacer(Modifier.height(8.dp))
                    HoldToConfirmButton(
                        label = "Emergency dismiss (hold 10s)",
                        holdSeconds = 10,
                        onConfirmed = {
                            confirmEmergency = false
                            viewModel.emergencyDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmEmergency = false }) { Text("Back to mission") }
            },
        )
    }
}

@Composable
private fun SuccessScreen(
    emergency: Boolean,
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
            Text("✓", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (emergency) "You're awake. That's the win." else "Mission complete${if (name.isBlank()) "" else ", $name"}!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                emergency -> "Emergency dismiss was recorded. Small steps still count."
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
