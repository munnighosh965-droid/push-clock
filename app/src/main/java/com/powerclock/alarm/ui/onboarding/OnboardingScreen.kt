package com.powerclock.alarm.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.prefs.FitnessLevel
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.prefs.SleeperType
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.components.Wordmark
import com.powerclock.alarm.ui.editor.WeekdaySelector
import com.powerclock.alarm.ui.home.missionShortName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    private val _draft = MutableStateFlow(UserSettings())
    val draft: StateFlow<UserSettings> = _draft.asStateFlow()

    fun update(transform: (UserSettings) -> UserSettings) {
        _draft.value = transform(_draft.value)
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.update { _draft.value.copy(onboardingComplete = true) }
            scheduler.rescheduleBedtimeReminder()
            onDone()
        }
    }

    fun exactAlarmAllowed(): Boolean = scheduler.canScheduleExactAlarms()
}

private const val PAGE_COUNT = 7

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var pageIndex by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableIntStateOf(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        LinearProgressIndicator(
            progress = { (pageIndex + 1f) / PAGE_COUNT },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        when (pageIndex) {
            0 -> WelcomePage()
            1 -> NamePage(draft, viewModel::update)
            2 -> SchedulePage(draft, viewModel::update)
            3 -> SleepProfilePage(draft, viewModel::update)
            4 -> SafetyPage(draft, viewModel::update)
            5 -> MissionPrefsPage(draft, viewModel::update)
            6 -> PermissionsPage(viewModel)
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (pageIndex > 0) {
                OutlinedButton(
                    onClick = { pageIndex-- },
                    modifier = Modifier
                        .weight(1f)
                        .sizeIn(minHeight = 56.dp),
                ) { Text("Back") }
            }
            Button(
                onClick = {
                    if (pageIndex < PAGE_COUNT - 1) pageIndex++ else viewModel.complete(onDone)
                },
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 56.dp),
            ) { Text(if (pageIndex < PAGE_COUNT - 1) "Continue" else "Let's go") }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(40.dp))
        Wordmark(big = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "WAKE. MOVE. WIN.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "Power Clock rings until you complete a wake-up mission — a few squats, a math puzzle, or scanning a QR card across the room.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Completely free. No ads, no accounts, no tracking. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NamePage(draft: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    Column {
        Text("What should we call you?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Just for friendly greetings — it never leaves this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = draft.name,
            onValueChange = { v -> update { it.copy(name = v.take(40)) } },
            placeholder = { Text("Name or nickname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SchedulePage(draft: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    Column {
        Text("Your mornings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        TimeSliderRow(
            label = "I usually wake at",
            minutes = draft.usualWakeMinutes,
            onChange = { m -> update { it.copy(usualWakeMinutes = m) } },
        )
        TimeSliderRow(
            label = "I want to wake at",
            minutes = draft.targetWakeMinutes,
            onChange = { m -> update { it.copy(targetWakeMinutes = m) } },
        )
        TimeSliderRow(
            label = "Bedtime goal",
            minutes = draft.bedtimeMinutes,
            onChange = { m -> update { it.copy(bedtimeMinutes = m) } },
        )
        Spacer(Modifier.height(8.dp))
        Text("Work or school days", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        WeekdaySelector(
            mask = draft.workDaysMask,
            onMaskChange = { mask -> update { it.copy(workDaysMask = mask) } },
        )
    }
}

@Composable
internal fun TimeSliderRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label  ${TimeFormat.minutesAsClock(minutes)}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChange((it.toInt() / 5) * 5) },
            valueRange = 0f..(24f * 60f - 5f),
        )
    }
}

@Composable
private fun SleepProfilePage(draft: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    Column {
        Text("How do you sleep?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Text("Typical snoozes per morning: ${draft.typicalSnoozes}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = draft.typicalSnoozes.toFloat(),
            onValueChange = { v -> update { it.copy(typicalSnoozes = v.toInt().coerceIn(0, 10)) } },
            valueRange = 0f..10f,
            steps = 9,
        )
        Spacer(Modifier.height(8.dp))

        Text("Sleeper type", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleeperType.entries.forEach { type ->
                FilterChip(
                    selected = draft.sleeperType == type,
                    onClick = { update { it.copy(sleeperType = type) } },
                    label = { Text(type.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Text("Preferred sound intensity: ${draft.soundIntensity}%", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = draft.soundIntensity.toFloat(),
            onValueChange = { v -> update { it.copy(soundIntensity = v.toInt().coerceIn(20, 100)) } },
            valueRange = 20f..100f,
        )
        Spacer(Modifier.height(8.dp))

        Text("Gradual early-wake program", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.earlyRiseEnabled,
                onClick = { update { it.copy(earlyRiseEnabled = true) } },
                label = { Text("Yes, ease me earlier") },
            )
            FilterChip(
                selected = !draft.earlyRiseEnabled,
                onClick = { update { it.copy(earlyRiseEnabled = false) } },
                label = { Text("Not now") },
            )
        }
    }
}

@Composable
private fun SafetyPage(draft: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    Column {
        Text("Your safety first", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Is there any reason to avoid exercise right now — an injury, limited mobility, pregnancy-related guidance, dizziness, or anything else? We don't need details.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.cannotExercise,
                onClick = { update { it.copy(cannotExercise = true) } },
                label = { Text("Yes — skip workouts") },
            )
            FilterChip(
                selected = !draft.cannotExercise,
                onClick = { update { it.copy(cannotExercise = false) } },
                label = { Text("No — workouts are fine") },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Fitness comfort level", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitnessLevel.entries.forEach { level ->
                FilterChip(
                    selected = draft.fitnessLevel == level,
                    onClick = { update { it.copy(fitnessLevel = level) } },
                    label = { Text(level.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Power Clock is a wellness tool, not medical advice. Alarms always include a workout mission — unless you tell us exercise isn't safe, in which case non-physical missions are used instead. If a mission ever can't run, it is swapped for a safe fallback so you are never trapped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissionPrefsPage(draft: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    Column {
        Text("Favourite wake-up missions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick any that sound good — you can mix and stack them per alarm later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        val selected = draft.preferredMissions.split(",").filter { it.isNotBlank() }.toSet()
        val types = if (draft.cannotExercise) {
            MissionType.entries.filter { !it.isWorkout }
        } else {
            MissionType.entries.toList()
        }
        types.chunked(2).forEach { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowTypes.forEach { type ->
                    FilterChip(
                        selected = type.name in selected,
                        onClick = {
                            val next = if (type.name in selected) selected - type.name else selected + type.name
                            update { it.copy(preferredMissions = next.joinToString(",")) }
                        },
                        label = { Text(missionShortName(type.name)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PermissionsPage(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    var notifGranted by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }
    var cameraGranted by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    Column {
        Text("Make alarms unstoppable", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "These permissions matter most for reliable wake-ups and camera-counted workouts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            if (notifGranted) "✓ Notifications enabled" else "Notifications — shows the ringing alarm screen",
            style = MaterialTheme.typography.titleSmall,
            color = if (notifGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!notifGranted && Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Allow notifications")
            }
        }
        Spacer(Modifier.height(16.dp))

        val exactOk = viewModel.exactAlarmAllowed()
        Text(
            if (exactOk) "✓ Exact alarms allowed" else "Exact alarms — rings at the precise minute, even in Doze",
            style = MaterialTheme.typography.titleSmall,
            color = if (exactOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (_: Exception) {
                }
            }) { Text("Allow exact alarms") }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            if (cameraGranted) "✓ Camera enabled" else "Camera — counts your wake-up workout on-device; nothing is stored or uploaded",
            style = MaterialTheme.typography.titleSmall,
            color = if (cameraGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (!cameraGranted) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Heads-up: if Android \"force stops\" an app, the system blocks its alarms until it's opened again — that applies to every third-party alarm clock. The Reliability Check screen in Settings verifies everything anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
