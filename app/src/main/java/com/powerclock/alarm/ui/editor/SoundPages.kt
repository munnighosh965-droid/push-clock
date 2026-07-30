package com.powerclock.alarm.ui.editor

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powerclock.alarm.domain.audio.SoundCatalog
import com.powerclock.alarm.ui.components.PowerCard

/**
 * Launcher for Android's own ringtone picker, so any alarm sound already on
 * the phone can be used. Returns through [EditorViewModel.onDeviceSoundPicked].
 */
@Composable
private fun rememberDeviceSoundPicker(
    viewModel: EditorViewModel,
    title: String,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) viewModel.onDeviceSoundPicked(uri)
        }
    }
    val existing = viewModel.currentCustomSoundUri
    return {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // A handful of stripped-down ROMs ship no picker; the bundled
            // tones and My music still cover every alarm.
        }
    }
}

@Composable
internal fun SoundLibraryPage(
    viewModel: EditorViewModel,
    state: EditorUiState,
    onBack: () -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }
    val pickDeviceSound = rememberDeviceSoundPicker(viewModel, "Choose an alarm sound")
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
            Text("Alarm sounds", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Eight original Power Clock tones, synthesized just for this app. Tap to select, press play to preview.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        val deviceSoundSelected = state.alarm.soundId == SoundCatalog.CUSTOM_ID
        PowerCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClickLabel = "Choose a sound from this device") { pickDeviceSound() },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sounds on this device", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (deviceSoundSelected && state.alarm.customSoundTitle != null) {
                            state.alarm.customSoundTitle!!
                        } else {
                            "Pick any alarm, ringtone or notification sound already on your phone"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (deviceSoundSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        state.copyResult?.takeIf { deviceSoundSelected || state.alarm.customSoundUri == null }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        SoundCatalog.sounds.forEach { sound ->
            val selected = state.alarm.soundId == sound.id
            PowerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(onClickLabel = "Select ${sound.displayName}") {
                        viewModel.update { it.copy(soundId = sound.id, customSoundUri = null, customSoundTitle = null) }
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sound.displayName, style = MaterialTheme.typography.titleMedium)
                        if (sound.id == SoundCatalog.FALLBACK_ID) {
                            Text(
                                "Guaranteed fallback tone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { viewModel.previewSound(sound.id) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Preview ${sound.displayName}")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = viewModel::stopPreview, modifier = Modifier.fillMaxWidth()) {
            Text("Stop preview")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun CustomMusicPage(
    viewModel: EditorViewModel,
    state: EditorUiState,
    onBack: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onCustomTrackPicked) }
    val pickDeviceSound = rememberDeviceSoundPicker(viewModel, "Choose an alarm sound")

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
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
            Text("My music", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Wake up to your own track. The file is picked through Android's document picker — Power Clock never needs broad storage access and never uploads your music. You are responsible for the music you select.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        val track = state.customTrack
        if (track != null && state.alarm.soundId == SoundCatalog.CUSTOM_ID) {
            PowerCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${track.artist} · ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.customTrackPlayable) {
                        Text(
                            "This file can't be opened right now. The bundled fallback tone will play instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    var startFraction by remember {
                        mutableFloatStateOf(
                            if (track.durationMs > 0) {
                                state.alarm.customSoundStartMs.toFloat() / track.durationMs
                            } else {
                                0f
                            },
                        )
                    }
                    Text(
                        "Start playback at ${formatDuration((startFraction * track.durationMs).toLong())}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = startFraction,
                        onValueChange = { startFraction = it.coerceIn(0f, 0.95f) },
                        onValueChangeFinished = {
                            viewModel.setMissionStartPosition((startFraction * track.durationMs).toLong())
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::previewCustom, modifier = Modifier.weight(1f)) {
                            Text("Preview")
                        }
                        OutlinedButton(onClick = viewModel::stopPreview, modifier = Modifier.weight(1f)) {
                            Text("Pause")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Replace") }
                        OutlinedButton(
                            onClick = viewModel::removeCustomTrack,
                            modifier = Modifier.weight(1f),
                        ) { Text("Remove") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (viewModel.customTrackIsLocalCopy) {
                        Text(
                            "Saved inside Power Clock — this alarm plays even if the original file is moved or deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Button(
                            onClick = viewModel::copyTrackForReliability,
                            enabled = !state.copyingTrack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.copyingTrack) "Copying…" else "Copy into Power Clock for reliability")
                        }
                    }
                    state.copyResult?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = { picker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Choose a track") }
            state.copyResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = pickDeviceSound, modifier = Modifier.fillMaxWidth()) {
            Text("Or pick a sound already on this device")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "If the track is ever deleted, moved, or unreadable at ring time, Power Clock automatically plays its bundled fallback tone — an alarm will never be silent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
