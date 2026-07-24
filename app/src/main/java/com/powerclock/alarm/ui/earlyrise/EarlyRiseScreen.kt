package com.powerclock.alarm.ui.earlyrise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.editor.ToggleRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * A proposed (never automatic) shift of one alarm a few minutes earlier.
 */
data class EarlyRiseProposal(
    val alarm: Alarm,
    val newHour: Int,
    val newMinute: Int,
)

@HiltViewModel
class EarlyRiseViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val alarmRepository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val _proposal = MutableStateFlow<EarlyRiseProposal?>(null)
    val proposal: StateFlow<EarlyRiseProposal?> = _proposal.asStateFlow()

    private val _applied = MutableStateFlow<String?>(null)
    val applied: StateFlow<String?> = _applied.asStateFlow()

    init {
        viewModelScope.launch { computeProposal() }
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            computeProposal()
        }
    }

    private suspend fun computeProposal() {
        val s = settingsRepository.current()
        _proposal.value = null
        if (!s.earlyRiseEnabled) return

        val today = LocalDate.now().toEpochDay()
        val daysSinceLast = today - s.earlyRiseLastAppliedEpochDay
        if (s.earlyRiseLastAppliedEpochDay != 0L && daysSinceLast < s.earlyRiseEveryDays) return

        // Propose shifting the earliest enabled alarm that is still later
        // than the target wake time.
        val target = s.targetWakeMinutes
        val candidate = alarmRepository.getAll()
            .filter { it.enabled && (it.hour * 60 + it.minute) > target }
            .minByOrNull { it.hour * 60 + it.minute }
            ?: return
        val current = candidate.hour * 60 + candidate.minute
        val shifted = maxOf(target, current - s.earlyRiseStepMinutes)
        if (shifted >= current) return
        _proposal.value = EarlyRiseProposal(candidate, shifted / 60, shifted % 60)
    }

    /** Applies the proposal — only ever called from the user's confirm tap. */
    fun applyProposal() {
        val p = _proposal.value ?: return
        viewModelScope.launch {
            val updated = p.alarm.copy(hour = p.newHour, minute = p.newMinute)
            alarmRepository.upsert(updated)
            scheduler.schedule(updated)
            settingsRepository.update {
                it.copy(earlyRiseLastAppliedEpochDay = LocalDate.now().toEpochDay())
            }
            _applied.value =
                "Alarm moved to %02d:%02d. Next nudge in ${settings.value.earlyRiseEveryDays} days.".format(p.newHour, p.newMinute)
            _proposal.value = null
        }
    }
}

@Composable
fun EarlyRiseScreen(
    onBack: () -> Unit,
    viewModel: EarlyRiseViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    val applied by viewModel.applied.collectAsStateWithLifecycle()

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
            Text("Early Rise Plan", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Shift your wake time earlier gradually — a few minutes every few days — instead of one brutal jump. Power Clock only ever proposes a change; you confirm every step.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("Early Rise Plan enabled", settings.earlyRiseEnabled) { v ->
                    viewModel.update { it.copy(earlyRiseEnabled = v) }
                }
                if (settings.earlyRiseEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Step size", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { step ->
                            FilterChip(
                                selected = settings.earlyRiseStepMinutes == step,
                                onClick = { viewModel.update { it.copy(earlyRiseStepMinutes = step) } },
                                label = { Text("$step min") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Every", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 5, 7).forEach { days ->
                            FilterChip(
                                selected = settings.earlyRiseEveryDays == days,
                                onClick = { viewModel.update { it.copy(earlyRiseEveryDays = days) } },
                                label = { Text("$days days") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Goal wake time: ${TimeFormat.minutesAsClock(settings.targetWakeMinutes)} (change it in onboarding answers via Settings profile).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        val currentProposal = proposal
        if (currentProposal != null) {
            PowerCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Today's proposal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Move the %02d:%02d alarm to %02d:%02d?".format(
                            currentProposal.alarm.hour, currentProposal.alarm.minute,
                            currentProposal.newHour, currentProposal.newMinute,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::applyProposal, modifier = Modifier.fillMaxWidth()) {
                        Text("Yes, move it ${settings.earlyRiseStepMinutes} minutes earlier")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Skipping is fine — the proposal simply reappears when you're ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (settings.earlyRiseEnabled) {
            PowerCard(Modifier.fillMaxWidth()) {
                Text(
                    applied
                        ?: "No proposal right now. Either your alarms already match your goal, or the next nudge isn't due yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
