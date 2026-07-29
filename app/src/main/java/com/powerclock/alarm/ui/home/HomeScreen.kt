package com.powerclock.alarm.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.data.repo.HistoryRepository
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.scheduling.NextOccurrenceCalculator
import com.powerclock.alarm.domain.stats.WakeStats
import com.powerclock.alarm.ui.components.AnimatedClockText
import com.powerclock.alarm.ui.components.HeroClock
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.RevealOnAppear
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.components.WeekDots
import com.powerclock.alarm.ui.components.Wordmark
import com.powerclock.alarm.ui.components.rememberReducedMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val settings: UserSettings = UserSettings(),
    val alarms: List<Alarm> = emptyList(),
    val nextAlarm: Alarm? = null,
    val nextTrigger: ZonedDateTime? = null,
    val stats: WakeStats = WakeStats.EMPTY,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    alarmRepository: AlarmRepository,
    historyRepository: HistoryRepository,
    private val scheduler: AlarmScheduler,
    private val alarmRepo: AlarmRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        alarmRepository.observeAll(),
        historyRepository.observeAll(),
    ) { settings, alarms, events ->
        val now = ZonedDateTime.now()
        val next = alarms
            .filter { it.enabled }
            .mapNotNull { alarm -> NextOccurrenceCalculator.nextTrigger(alarm, now)?.let { alarm to it } }
            .minByOrNull { it.second }
        HomeUiState(
            settings = settings,
            alarms = alarms,
            nextAlarm = next?.first,
            nextTrigger = next?.second,
            stats = WakeStats.compute(events, ZoneId.systemDefault(), LocalDate.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarmRepo.setEnabled(alarm.id, enabled)
            if (updated != null) {
                if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated.id)
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onCreateAlarm: () -> Unit,
    onOpenAlarms: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }
    val reduceMotion = rememberReducedMotion(state.settings.reduceMotion)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        RevealOnAppear(reduceMotion = reduceMotion) { Wordmark() }
        Spacer(Modifier.height(12.dp))

        val greetingName = state.settings.name.ifBlank { "there" }
        val greeting = when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> "Rest well"
        }
        RevealOnAppear(
            reduceMotion = reduceMotion,
            delayMillis = 90,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$greeting, $greetingName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                HeroClock(now = now, reduceMotion = reduceMotion)
                Spacer(Modifier.height(14.dp))
                AnimatedClockText(time = now, reduceMotion = reduceMotion)
                Text(
                    TimeFormat.fullDate(now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        RevealOnAppear(reduceMotion = reduceMotion, delayMillis = 180) {
            PowerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val next = state.nextAlarm
                    val trigger = state.nextTrigger
                    if (next != null && trigger != null) {
                        Text(
                            "Next alarm ${TimeFormat.countdown(now, trigger)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    TimeFormat.nextAlarm(trigger),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                val missions = next.missions
                                Text(
                                    if (missions.isEmpty()) {
                                        "Mission: Squats (added automatically)"
                                    } else {
                                        "Mission: " + missions.joinToString(" → ") { missionShortName(it.type.name) }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (next.label.isNotBlank()) {
                                    Text(
                                        next.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = next.enabled,
                                onCheckedChange = { viewModel.toggleAlarm(next, it) },
                                modifier = Modifier.semantics {
                                    contentDescription = "Quick toggle for next alarm"
                                },
                            )
                        }
                    } else {
                        Text("No alarms scheduled", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Set your first Power Clock alarm and win tomorrow morning.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        RevealOnAppear(reduceMotion = reduceMotion, delayMillis = 260) {
            Button(
                onClick = onCreateAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Set alarm", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(16.dp))

        RevealOnAppear(reduceMotion = reduceMotion, delayMillis = 340) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PowerCard(modifier = Modifier.weight(1f)) {
                    Column {
                        CountUpNumber(
                            value = state.stats.currentStreak,
                            reduceMotion = reduceMotion,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (state.stats.currentStreak == 1) "day streak" else "days streak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PowerCard(modifier = Modifier.weight(1f)) {
                    Column {
                        CountUpNumber(
                            value = state.stats.powerScore,
                            reduceMotion = reduceMotion,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            "Power Score",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        RevealOnAppear(reduceMotion = reduceMotion, delayMillis = 420) {
            PowerCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    WeekDots(state.stats.last7Days)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (state.settings.bedtimeReminderEnabled) {
            RevealOnAppear(reduceMotion = reduceMotion, delayMillis = 500) {
                PowerCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Bedtime reminder", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tonight at ${TimeFormat.minutesAsClock(state.settings.bedtimeMinutes)} — winding down early makes the mission easier.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.alarms.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = onOpenAlarms) {
                Text("See all ${state.alarms.size} alarms")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Dashboard stat that counts up to its value the first time it appears. */
@Composable
private fun CountUpNumber(
    value: Int,
    reduceMotion: Boolean,
    color: Color,
) {
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = if (reduceMotion) snap() else tween(900, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    Text(
        "$shown",
        style = MaterialTheme.typography.headlineLarge,
        color = color,
    )
}

internal fun missionShortName(name: String): String = when (name) {
    "PUSH_UPS" -> "Push-ups"
    "KNEE_PUSH_UPS" -> "Knee push-ups"
    "SQUATS" -> "Squats"
    "JUMPING_JACKS" -> "Jumping jacks"
    "MATH" -> "Math"
    "MEMORY" -> "Memory"
    "TYPING" -> "Typing"
    "QR_SCAN" -> "QR scan"
    "SHAKE" -> "Shake"
    else -> name
}
