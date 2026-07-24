package com.powerclock.alarm.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.scheduling.NextOccurrenceCalculator
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.TimeFormat
import com.powerclock.alarm.ui.home.missionShortName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = repo.observeAll()
        .map { it.sortedWith(compareBy({ a -> a.hour }, { a -> a.minute })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggle(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = repo.setEnabled(alarm.id, enabled)
            if (updated != null) {
                if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated.id)
            }
        }
    }

    fun duplicate(alarm: Alarm) {
        viewModelScope.launch {
            val newId = repo.duplicate(alarm.copy(label = duplicateLabel(alarm.label)))
            repo.getById(newId)?.let { if (it.enabled) scheduler.schedule(it) }
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            repo.delete(alarm.id)
        }
    }

    private fun duplicateLabel(label: String): String =
        if (label.isBlank()) "Copy" else "$label (copy)"
}

@Composable
fun AlarmListScreen(
    modifier: Modifier = Modifier,
    onCreateAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel(),
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    var deleteCandidate by remember { mutableStateOf<Alarm?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No alarms yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the + button to create your first wake-up mission.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { viewModel.toggle(alarm, it) },
                        onEdit = { onEditAlarm(alarm.id) },
                        onDuplicate = { viewModel.duplicate(alarm) },
                        onDelete = { deleteCandidate = alarm },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onCreateAlarm,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .semantics { contentDescription = "Create new alarm" },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete alarm?") },
            text = {
                Text(
                    "The ${"%02d:%02d".format(candidate.hour, candidate.minute)} alarm will be removed and unscheduled.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(candidate)
                    deleteCandidate = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    PowerCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit alarm") { onEdit() },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "%02d:%02d".format(alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (alarm.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        describeRepeat(alarm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (alarm.label.isNotBlank()) {
                        Text(
                            alarm.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (alarm.enabled) {
                        NextOccurrenceCalculator.nextTrigger(alarm, ZonedDateTime.now())?.let {
                            Text(
                                "Rings ${TimeFormat.nextAlarm(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    val missions = alarm.missions
                    if (missions.isNotEmpty()) {
                        Text(
                            missions.joinToString(" → ") { missionShortName(it.type.name) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = if (alarm.enabled) "Disable alarm" else "Enable alarm"
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Duplicate alarm",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete alarm",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun describeRepeat(alarm: Alarm): String {
    if (!alarm.isRepeating) return "One time"
    if (alarm.repeatDaysMask == Alarm.EVERY_DAY) return "Every day"
    if (alarm.repeatDaysMask == Alarm.WEEKDAYS) return "Weekdays"
    return NextOccurrenceCalculator.describeDays(alarm.repeatDaysMask)
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.US) }
}
