package com.powerclock.alarm.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.domain.model.Sensitivity
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.SectionTitle
import com.powerclock.alarm.ui.home.missionShortName
import com.powerclock.alarm.ui.workout.WorkoutLiveView

internal fun missionDescription(type: MissionType): String = when (type) {
    MissionType.PUSH_UPS -> "Camera counts full push-ups. Phone propped on the floor, body side-on."
    MissionType.KNEE_PUSH_UPS -> "Gentler push-up variant on the knees, camera-counted."
    MissionType.SQUATS -> "Camera counts squats. Stand back so your whole body is visible."
    MissionType.JUMPING_JACKS -> "Camera counts jumping jacks. Great space-friendly heart starter."
    MissionType.MATH -> "Solve fresh math problems. Difficulty is up to you."
    MissionType.MEMORY -> "Memorize and repeat growing light sequences."
    MissionType.TYPING -> "Type a wake-up phrase exactly. No autopilot allowed."
    MissionType.QR_SCAN -> "Scan your Power Clock QR card placed away from the bed."
    MissionType.SHAKE -> "Shake the phone firmly until the meter fills."
}

internal fun tutorialText(type: MissionType): String = when (type) {
    MissionType.PUSH_UPS ->
        "Prop your phone on the floor or a low shelf, 1.5–2 m away, so your whole body is visible from the side. " +
            "Lower until your elbows clearly bend, then push all the way back up. The counter needs the full cycle."
    MissionType.KNEE_PUSH_UPS ->
        "Kneel on a mat with the phone 1.5 m away at floor level, body angled to the camera. " +
            "Bend your elbows fully, then extend. Keep shoulders and hips in view."
    MissionType.SQUATS ->
        "Stand 2 m from the phone, full body in frame, facing the camera. " +
            "Bend knees and drop your hips, then stand fully upright. The rep counts on the return."
    MissionType.JUMPING_JACKS ->
        "Stand 2 m back with space around you. Jump feet apart while raising both arms, " +
            "then return feet together with arms down. Both halves are required."
    else -> ""
}

@Composable
internal fun MissionLibraryPage(
    availableTypes: List<MissionType>,
    cannotExercise: Boolean,
    onBack: () -> Unit,
    onPick: (MissionType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Mission library", style = MaterialTheme.typography.headlineSmall)
        }
        if (cannotExercise) {
            Text(
                "Workout missions are hidden because you told us exercise isn't safe for you right now. You can change this in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        val workouts = availableTypes.filter { it.isWorkout }
        val brain = availableTypes.filter { it in MissionType.BRAIN }
        val movement = availableTypes.filter { it == MissionType.QR_SCAN || it == MissionType.SHAKE }

        if (workouts.isNotEmpty()) {
            SectionTitle("Camera workouts")
            workouts.forEach { MissionCard(it, onPick) }
        }
        SectionTitle("Brain missions")
        brain.forEach { MissionCard(it, onPick) }
        SectionTitle("Movement & environment")
        movement.forEach { MissionCard(it, onPick) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MissionCard(type: MissionType, onPick: (MissionType) -> Unit) {
    PowerCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClickLabel = "Choose ${missionShortName(type.name)}") { onPick(type) },
    ) {
        Column {
            Text(missionShortName(type.name), style = MaterialTheme.typography.titleMedium)
            Text(
                missionDescription(type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MissionConfigPage(
    type: MissionType,
    initial: MissionConfig?,
    onBack: () -> Unit,
    onConfirm: (MissionConfig) -> Unit,
    onTest: (MissionConfig) -> Unit,
) {
    var target by rememberSaveable {
        mutableIntStateOf(initial?.target ?: MissionConfig.defaultTarget(type))
    }
    var difficulty by rememberSaveable { mutableIntStateOf(initial?.difficulty ?: 1) }
    var sensitivity by rememberSaveable {
        mutableStateOf((initial?.sensitivity ?: Sensitivity.NORMAL).name)
    }

    val minTarget = if (type.isWorkout) MissionConfig.MIN_WORKOUT_TARGET else 1
    val maxTarget = when (type) {
        MissionType.MATH -> 10
        MissionType.MEMORY -> 5
        MissionType.TYPING -> 3
        MissionType.QR_SCAN -> 1
        else -> MissionConfig.MAX_TARGET
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(missionShortName(type.name), style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            missionDescription(type),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (type.isWorkout) {
            PowerCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("How to position your phone", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tutorialText(type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (type != MissionType.QR_SCAN) {
            SectionTitle(
                when {
                    type.isWorkout -> "Repetitions"
                    type == MissionType.MATH -> "Questions"
                    type == MissionType.MEMORY -> "Rounds"
                    type == MissionType.TYPING -> "Phrases"
                    else -> "Shakes"
                },
            )
            Text("$target", style = MaterialTheme.typography.headlineMedium)
            Slider(
                value = target.toFloat(),
                onValueChange = { target = it.toInt().coerceIn(minTarget, maxTarget) },
                valueRange = minTarget.toFloat()..maxTarget.toFloat(),
                steps = (maxTarget - minTarget - 1).coerceAtLeast(0),
            )
            if (type.isWorkout && target >= 15) {
                Text(
                    "That's a solid target. Consider a 20-second stretch before your set — straight out of bed, muscles are cold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (type in MissionType.BRAIN) {
            SectionTitle("Difficulty")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Easy", 2 to "Medium", 3 to "Hard").forEach { (level, name) ->
                    FilterChip(
                        selected = difficulty == level,
                        onClick = { difficulty = level },
                        label = { Text(name) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (type.isWorkout) {
            SectionTitle("Detection sensitivity")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sensitivity.entries.forEach { s ->
                    FilterChip(
                        selected = sensitivity == s.name,
                        onClick = { sensitivity = s.name },
                        label = {
                            Text(
                                when (s) {
                                    Sensitivity.BEGINNER -> "Beginner"
                                    Sensitivity.NORMAL -> "Normal"
                                    Sensitivity.STRICT -> "Strict"
                                },
                            )
                        },
                    )
                }
            }
            Text(
                "Beginner accepts shallower reps; Strict wants textbook form.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    onTest(
                        MissionConfig(
                            type, target, difficulty,
                            Sensitivity.valueOf(sensitivity),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test mission (live camera)") }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                onConfirm(MissionConfig(type, target, difficulty, Sensitivity.valueOf(sensitivity)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 56.dp),
        ) { Text(if (initial != null) "Update mission" else "Add to mission stack") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun MissionStackEditor(
    missions: List<MissionConfig>,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Column {
        if (missions.isEmpty()) {
            Text(
                "No missions yet — the alarm will have a simple dismiss button. Add a mission to make sure you actually get up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        missions.forEachIndexed { index, config ->
            PowerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(onClickLabel = "Edit mission") { onEdit(index) },
                    ) {
                        Text(
                            "${index + 1}. ${missionShortName(config.type.name)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            summarize(config),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move mission earlier")
                    }
                    IconButton(onClick = { onMove(index, 1) }, enabled = index < missions.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move mission later")
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove mission",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Add mission")
        }
    }
}

private fun summarize(config: MissionConfig): String = when (config.type) {
    MissionType.QR_SCAN -> "Scan your Power Clock card"
    MissionType.MATH -> "${config.target} questions · difficulty ${config.difficulty}"
    MissionType.MEMORY -> "${config.target} rounds · difficulty ${config.difficulty}"
    MissionType.TYPING -> "${config.target} phrase(s)"
    MissionType.SHAKE -> "${config.target} shakes"
    else -> "${config.target} reps · ${config.sensitivity.name.lowercase()} sensitivity"
}

@Composable
internal fun WorkoutTestPage(
    config: MissionConfig,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Test: ${missionShortName(config.type.name)}", style = MaterialTheme.typography.headlineSmall)
        }
        if (cameraGranted) {
            WorkoutLiveView(
                config = config,
                spokenCues = false,
                testMode = true,
                onComplete = onBack,
                onCannotRun = { onBack() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Camera access is needed only to count your movements. Frames are processed on this device and never stored or uploaded.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
            }
        }
    }
}
