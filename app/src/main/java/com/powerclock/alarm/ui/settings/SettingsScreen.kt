package com.powerclock.alarm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.prefs.ThemeMode
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.SectionTitle
import com.powerclock.alarm.ui.editor.ToggleRow
import com.powerclock.alarm.ui.onboarding.TimeSliderRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            scheduler.rescheduleBedtimeReminder()
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenReliability: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenEarlyRise: () -> Unit,
    onOpenQrCard: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        SectionTitle("Profile")
        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                OutlinedTextField(
                    value = settings.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v.take(40)) } },
                    label = { Text("Name / nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TimeSliderRow(
                    label = "Bedtime goal",
                    minutes = settings.bedtimeMinutes,
                    onChange = { m -> viewModel.update { it.copy(bedtimeMinutes = m) } },
                )
                ToggleRow("Bedtime reminder notification", settings.bedtimeReminderEnabled) { v ->
                    viewModel.update { it.copy(bedtimeReminderEnabled = v) }
                }
                ToggleRow("Exercise isn't safe for me right now", settings.cannotExercise) { v ->
                    viewModel.update { it.copy(cannotExercise = v) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("Appearance & motion")
        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.update { it.copy(themeMode = mode) } },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.LIGHT -> "Light"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    "System follows Android's night mode, so evenings default to dark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow("Reduce motion (fewer animations)", settings.reduceMotion) { v ->
                    viewModel.update { it.copy(reduceMotion = v) }
                }
                ToggleRow("Haptic feedback", settings.hapticsEnabled) { v ->
                    viewModel.update { it.copy(hapticsEnabled = v) }
                }
                ToggleRow("Spoken rep counting (workouts)", settings.spokenCues) { v ->
                    viewModel.update { it.copy(spokenCues = v) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("Alarm behaviour")
        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow(
                    "Allow Heavy Sleeper mode to raise alarm volume (restored afterwards)",
                    settings.allowVolumeOverride,
                ) { v -> viewModel.update { it.copy(allowVolumeOverride = v) } }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Safety auto-silence after ${settings.autoSilenceMinutes} minutes",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.autoSilenceMinutes.toFloat(),
                    onValueChange = { v ->
                        viewModel.update { it.copy(autoSilenceMinutes = v.toInt().coerceIn(5, 30)) }
                    },
                    valueRange = 5f..30f,
                    steps = 24,
                )
                Text(
                    "If a mission is never finished, the alarm stops itself and is recorded as missed — no endless ringing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("More")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenReliability, modifier = Modifier.fillMaxWidth()) {
                Text("Alarm Reliability Check")
            }
            OutlinedButton(onClick = onOpenEarlyRise, modifier = Modifier.fillMaxWidth()) {
                Text("Early Rise Plan")
            }
            OutlinedButton(onClick = onOpenQrCard, modifier = Modifier.fillMaxWidth()) {
                Text("Power Clock QR card")
            }
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                Text("Privacy & local data")
            }
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text("About & open-source licenses")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
