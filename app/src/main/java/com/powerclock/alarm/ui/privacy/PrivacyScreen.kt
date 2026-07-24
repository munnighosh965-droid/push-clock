package com.powerclock.alarm.ui.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.audio.CustomAudioStore
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.data.repo.HistoryRepository
import com.powerclock.alarm.ui.components.PowerCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val customAudioStore: CustomAudioStore,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun exportCsv(writeTo: (String) -> Boolean) {
        viewModelScope.launch {
            val csv = historyRepository.exportCsv()
            _message.value = if (writeTo(csv)) {
                "History exported. The file went exactly where you chose — nowhere else."
            } else {
                "Export failed — the file could not be written."
            }
        }
    }

    fun resetHistory() {
        viewModelScope.launch {
            historyRepository.deleteAll()
            _message.value = "Wake history cleared. Alarms and settings kept."
        }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            alarmRepository.getAll().forEach { scheduler.cancel(it.id) }
            alarmRepository.deleteAll()
            historyRepository.deleteAll()
            customAudioStore.clearPrivateCopies()
            settingsRepository.wipeAll()
            _message.value = "All data deleted. Power Clock is back to a fresh install state."
        }
    }
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray())
                }
            } catch (_: Exception) {
            }
        }
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
            Text("Privacy & local data", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))

        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                Text("How Power Clock treats your data", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Everything lives on this phone. There is no account, no cloud, no sync.",
                    "The app does not even request internet permission — it cannot upload anything.",
                    "Camera frames for workouts and QR scans are processed in memory and immediately discarded. Nothing is recorded. No face recognition.",
                    "No analytics, no advertising identifiers, no tracking SDKs.",
                    "Your custom music stays where it is; an optional private copy stays inside the app's own storage.",
                    "Exports happen only through Android's file picker, to a location you choose.",
                ).forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                viewModel.exportCsv { csv ->
                    pendingCsv = csv
                    try {
                        exportLauncher.launch("powerclock-history.csv")
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export wake history (CSV)") }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = viewModel::resetHistory, modifier = Modifier.fillMaxWidth()) {
            Text("Reset wake history")
        }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete ALL data") }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = {
                Text("Alarms, history, settings, the QR card id, and any private music copies will be permanently removed from this device. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteEverything()
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
