package com.powerclock.alarm.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.data.repo.HistoryRepository
import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome
import com.powerclock.alarm.domain.stats.InsightEngine
import com.powerclock.alarm.domain.stats.WakeStats
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.ProgressRing
import com.powerclock.alarm.ui.components.SectionTitle
import com.powerclock.alarm.ui.components.WeekDots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ProgressUiState(
    val stats: WakeStats = WakeStats.EMPTY,
    val events: List<WakeEvent> = emptyList(),
    val insights: List<InsightEngine.Suggestion> = emptyList(),
    val badges: List<Badge> = emptyList(),
)

data class Badge(val title: String, val earned: Boolean, val description: String)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    alarmRepository: AlarmRepository,
) : ViewModel() {

    val state: StateFlow<ProgressUiState> = combine(
        historyRepository.observeAll(),
        alarmRepository.observeAll(),
    ) { events, alarms ->
        val zone = ZoneId.systemDefault()
        val stats = WakeStats.compute(events, zone, LocalDate.now())
        val cutoff = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
        val recent = events.filter { it.rangAtMs >= cutoff }.sortedBy { it.rangAtMs }
        val workoutTarget = alarms
            .flatMap { it.missions }
            .filter { it.type.isWorkout }
            .maxOfOrNull { it.target }
        ProgressUiState(
            stats = stats,
            events = events.sortedByDescending { it.rangAtMs }.take(60),
            insights = InsightEngine.suggestions(recent, workoutTarget),
            badges = badges(stats),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())

    private fun badges(stats: WakeStats): List<Badge> = listOf(
        Badge("First win", stats.bestStreak >= 1, "Complete your first wake-up mission"),
        Badge("Three mornings stronger", stats.bestStreak >= 3, "A 3-day wake streak"),
        Badge("One solid week", stats.bestStreak >= 7, "A 7-day wake streak"),
        Badge("Habit forged", stats.bestStreak >= 21, "A 21-day wake streak"),
        Badge("Half-century mover", stats.totalReps >= 50, "50 total safe reps"),
        Badge("Two-fifty club", stats.totalReps >= 250, "250 total safe reps"),
        Badge("Powered up", stats.powerScore >= 80, "Reach a Power Score of 80"),
    )
}

@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    onOpenEarlyRise: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PowerCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    ProgressRing(
                        progress = stats.powerScore / 100f,
                        ringSize = 120.dp,
                        stroke = 10.dp,
                    ) {
                        Text("${stats.powerScore}", style = MaterialTheme.typography.headlineLarge)
                    }
                    Spacer(Modifier.padding(8.dp))
                    Column {
                        Text("Power Score", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "40 × on-time rate + 30 × completion rate + streak bonus (max 20) + rep bonus (max 10). All computed on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Current streak", "${stats.currentStreak}d", Modifier.weight(1f))
                StatCard("Best streak", "${stats.bestStreak}d", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("On-time (30d)", "${(stats.onTimeRate30d * 100).toInt()}%", Modifier.weight(1f))
                StatCard("Total reps", "${stats.totalReps}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Wakes (7d)", "${stats.wakes7d}", Modifier.weight(1f))
                StatCard("Avg mission", "${stats.avgMissionSeconds}s", Modifier.weight(1f))
            }
        }
        item {
            PowerCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    WeekDots(stats.last7Days)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Days without alarms are rest days — they never break your streak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.insights.isNotEmpty()) {
            item { SectionTitle("Suggestions (always your call)") }
            items(state.insights) { suggestion ->
                PowerCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(titleFor(suggestion.kind), style = MaterialTheme.typography.titleMedium)
                        Text(
                            suggestion.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Apply it from the alarm editor whenever you're ready — nothing changes automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onOpenEarlyRise, modifier = Modifier.fillMaxWidth()) {
                Text("Early Rise Plan")
            }
        }

        item { SectionTitle("Badges") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.badges.forEach { badge ->
                    PowerCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                if (badge.earned) "★" else "☆",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (badge.earned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(Modifier.padding(6.dp))
                            Column {
                                Text(badge.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    badge.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("History") }
        if (state.events.isEmpty()) {
            item {
                Text(
                    "No wake-ups recorded yet. Your first completed mission will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.events, key = { it.id }) { event ->
            HistoryRow(event)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    PowerCard(modifier = modifier) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(event: WakeEvent) {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.US)
    val time = Instant.ofEpochMilli(event.rangAtMs).atZone(ZoneId.systemDefault())
    PowerCard(Modifier.fillMaxWidth()) {
        Column {
            Row {
                Text(
                    formatter.format(time),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (event.outcome) {
                        WakeOutcome.COMPLETED -> "Completed"
                        WakeOutcome.EMERGENCY -> "Emergency dismiss"
                        WakeOutcome.MISSED -> "Missed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (event.outcome) {
                        WakeOutcome.COMPLETED -> MaterialTheme.colorScheme.primary
                        WakeOutcome.EMERGENCY -> MaterialTheme.colorScheme.secondary
                        WakeOutcome.MISSED -> MaterialTheme.colorScheme.error
                    },
                )
            }
            val details = buildList {
                if (event.alarmLabel.isNotBlank()) add(event.alarmLabel)
                if (event.totalReps > 0) add("${event.totalReps} reps")
                event.dismissedAtMs?.let { add("${(it - event.rangAtMs) / 1000}s to dismiss") }
                event.energyRating?.let { add("energy $it/5") }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun titleFor(kind: InsightEngine.Kind): String = when (kind) {
    InsightEngine.Kind.STRONGER_SOUND -> "Try a stronger sound"
    InsightEngine.Kind.DIFFERENT_MISSION -> "Try a different mission"
    InsightEngine.Kind.LOWER_TARGET -> "Lower your rep target"
    InsightEngine.Kind.RAISE_TARGET -> "Ready for more?"
    InsightEngine.Kind.EARLIER_BEDTIME -> "Earlier bedtime reminder"
    InsightEngine.Kind.STACK_MISSIONS -> "Stack a get-out-of-bed mission"
}
