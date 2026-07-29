package com.powerclock.alarm.ui.editor

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.SectionTitle
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.home.missionShortName
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal enum class EditorPage { MAIN, MISSION_LIBRARY, MISSION_CONFIG, SOUND_LIBRARY, CUSTOM_MUSIC, WORKOUT_TEST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    onClose: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(EditorPage.MAIN) }
    // Index of the mission being configured, or -1 when adding a new one.
    var configIndex by rememberSaveable { mutableIntStateOf(-1) }
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var testConfig by remember { mutableStateOf<MissionConfig?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    BackHandler(enabled = page != EditorPage.MAIN) {
        page = when (page) {
            EditorPage.MISSION_CONFIG -> EditorPage.MISSION_LIBRARY
            EditorPage.WORKOUT_TEST -> EditorPage.MISSION_CONFIG
            else -> EditorPage.MAIN
        }
    }

    if (!state.loaded) return

    when (page) {
        EditorPage.MAIN -> EditorMainPage(
            viewModel = viewModel,
            state = state,
            onClose = { viewModel.stopPreview(); onClose() },
            onAddMission = { page = EditorPage.MISSION_LIBRARY },
            onEditMission = { idx ->
                configIndex = idx
                pendingType = null
                page = EditorPage.MISSION_CONFIG
            },
            onOpenSounds = { page = EditorPage.SOUND_LIBRARY },
            onOpenCustomMusic = { page = EditorPage.CUSTOM_MUSIC },
        )

        EditorPage.MISSION_LIBRARY -> MissionLibraryPage(
            availableTypes = viewModel.missionTypesForUser,
            cannotExercise = state.cannotExercise,
            onBack = { page = EditorPage.MAIN },
            onPick = { type ->
                pendingType = type.name
                configIndex = -1
                page = EditorPage.MISSION_CONFIG
            },
        )

        EditorPage.MISSION_CONFIG -> {
            val existing = if (configIndex >= 0) state.missions.getOrNull(configIndex) else null
            val type = existing?.type
                ?: MissionType.entries.firstOrNull { it.name == pendingType }
                ?: MissionType.MATH
            MissionConfigPage(
                type = type,
                initial = existing,
                onBack = {
                    page = if (configIndex >= 0) EditorPage.MAIN else EditorPage.MISSION_LIBRARY
                },
                onConfirm = { config ->
                    if (configIndex >= 0) {
                        viewModel.updateMission(configIndex, config)
                    } else {
                        viewModel.addMission(config)
                    }
                    page = EditorPage.MAIN
                },
                onTest = { config ->
                    testConfig = config
                    page = EditorPage.WORKOUT_TEST
                },
            )
        }

        EditorPage.WORKOUT_TEST -> {
            val config = testConfig
            if (config == null) {
                page = EditorPage.MISSION_CONFIG
            } else {
                WorkoutTestPage(
                    config = config,
                    onBack = { page = EditorPage.MISSION_CONFIG },
                )
            }
        }

        EditorPage.SOUND_LIBRARY -> SoundLibraryPage(
            viewModel = viewModel,
            state = state,
            onBack = { viewModel.stopPreview(); page = EditorPage.MAIN },
        )

        EditorPage.CUSTOM_MUSIC -> CustomMusicPage(
            viewModel = viewModel,
            state = state,
            onBack = { viewModel.stopPreview(); page = EditorPage.MAIN },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorMainPage(
    viewModel: EditorViewModel,
    state: EditorUiState,
    onClose: () -> Unit,
    onAddMission: () -> Unit,
    onEditMission: (Int) -> Unit,
    onOpenSounds: () -> Unit,
    onOpenCustomMusic: () -> Unit,
) {
    val context = LocalContext.current
    val alarm = state.alarm
    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = true,
    )
    LaunchedEffect(timeState) {
        snapshotFlow { timeState.hour to timeState.minute }
            .collect { (h, m) -> viewModel.update { it.copy(hour = h, minute = m) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close editor")
            }
            Text(
                if (alarm.id == 0L) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(8.dp))

        PowerCard(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = timeState)
                state.nextTriggerPreview?.let {
                    Text(
                        "Will ring ${TimeFormat.nextAlarm(it)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Repeat")
        WeekdaySelector(
            mask = alarm.repeatDaysMask,
            onMaskChange = { mask -> viewModel.update { it.copy(repeatDaysMask = mask) } },
        )
        Spacer(Modifier.height(16.dp))

        SectionTitle("Label")
        OutlinedTextField(
            value = alarm.label,
            onValueChange = { v -> viewModel.update { it.copy(label = v.take(60)) } },
            placeholder = { Text("e.g. Morning shift") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        SectionTitle("Wake-up missions")
        MissionStackEditor(
            missions = state.missions,
            cannotExercise = state.cannotExercise,
            onAdd = onAddMission,
            onEdit = onEditMission,
            onRemove = viewModel::removeMission,
            onMove = viewModel::moveMission,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "If a mission can't run (camera denied or busy, sensor missing), only that mission is replaced with your fallback:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MissionType.SAFE_FALLBACKS.forEach { type ->
                FilterChip(
                    selected = alarm.fallbackMissionType == type,
                    onClick = { viewModel.update { it.copy(fallbackMissionType = type) } },
                    label = { Text(missionShortName(type.name)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Sound")
        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    if (alarm.soundId == "custom") {
                        alarm.customSoundTitle ?: "Custom track"
                    } else {
                        com.powerclock.alarm.domain.audio.SoundCatalog.byId(alarm.soundId).displayName
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!state.customTrackPlayable) {
                    Text(
                        "Selected track can't be opened right now — the bundled fallback tone will play instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSounds, modifier = Modifier.weight(1f)) {
                        Text("Sound library")
                    }
                    OutlinedButton(onClick = onOpenCustomMusic, modifier = Modifier.weight(1f)) {
                        Text("My music")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Surprise me (random sound)", Modifier.weight(1f))
                    Switch(
                        checked = alarm.randomSound,
                        onCheckedChange = { v -> viewModel.update { it.copy(randomSound = v) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Volume & intensity")
        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                Text("Alarm volume: ${alarm.volumePercent}%")
                Slider(
                    value = alarm.volumePercent.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(volumePercent = v.toInt().coerceIn(0, 100)) } },
                    valueRange = 0f..100f,
                )
                ToggleRow("Gradual volume increase", alarm.gradualVolume) { v ->
                    viewModel.update { it.copy(gradualVolume = v) }
                }
                ToggleRow("Heavy Sleeper mode (louder, faster ramp)", alarm.heavySleeper) { v ->
                    viewModel.update { it.copy(heavySleeper = v) }
                }
                ToggleRow("Vibration", alarm.vibrate) { v ->
                    viewModel.update { it.copy(vibrate = v) }
                }
                if (alarm.vibrate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Pulse", 1 to "Knock", 2 to "Steady").forEach { (id, name) ->
                            FilterChip(
                                selected = alarm.vibrationPatternId == id,
                                onClick = { viewModel.update { it.copy(vibrationPatternId = id) } },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                ToggleRow("Flashlight pulses", alarm.flashlight) { v ->
                    viewModel.update { it.copy(flashlight = v) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!state.exactAlarmAllowed) {
            PowerCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Exact alarms are turned off",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Android may delay this alarm without the exact-alarm special access. Grant it so Power Clock can ring on time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                                )
                            } catch (_: Exception) {
                            }
                        }
                    }) { Text("Open settings") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 56.dp),
        ) { Text("Save alarm") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun WeekdaySelector(mask: Int, onMaskChange: (Int) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val bit = 1 shl (day.value - 1)
                val selected = mask and bit != 0
                FilterChip(
                    selected = selected,
                    onClick = { onMaskChange(mask xor bit) },
                    label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.US)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMaskChange(0) }) { Text("Once") }
            OutlinedButton(onClick = { onMaskChange(Alarm.WEEKDAYS) }) { Text("Weekdays") }
            OutlinedButton(onClick = { onMaskChange(Alarm.EVERY_DAY) }) { Text("Every day") }
        }
        Text(
            if (mask == 0) "Rings once, then turns itself off." else "Repeats weekly on the selected days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
