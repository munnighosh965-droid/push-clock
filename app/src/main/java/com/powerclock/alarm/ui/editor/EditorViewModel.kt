package com.powerclock.alarm.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.powerclock.alarm.alarmengine.AlarmScheduler
import com.powerclock.alarm.data.audio.AudioTrackInfo
import com.powerclock.alarm.data.audio.CustomAudioStore
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.domain.audio.SoundCatalog
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

data class EditorUiState(
    val loaded: Boolean = false,
    val alarm: Alarm = Alarm(hour = 7, minute = 0),
    val missions: List<MissionConfig> = emptyList(),
    val customTrack: AudioTrackInfo? = null,
    val customTrackPlayable: Boolean = true,
    val exactAlarmAllowed: Boolean = true,
    val cannotExercise: Boolean = false,
    val saved: Boolean = false,
    val nextTriggerPreview: ZonedDateTime? = null,
    val copyingTrack: Boolean = false,
    val copyResult: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repo: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val customAudioStore: CustomAudioStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val alarmId: Long = savedStateHandle.get<Long>("alarmId") ?: 0L

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var previewPlayer: ExoPlayer? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val alarm = if (alarmId > 0) repo.getById(alarmId) else null
            val base = alarm ?: Alarm(
                hour = settings.targetWakeMinutes / 60,
                minute = settings.targetWakeMinutes % 60,
                volumePercent = settings.soundIntensity,
            )
            _state.value = EditorUiState(
                loaded = true,
                alarm = base,
                missions = base.missions,
                exactAlarmAllowed = scheduler.canScheduleExactAlarms(),
                cannotExercise = settings.cannotExercise,
            )
            refreshCustomTrack()
            refreshPreview()
        }
    }

    fun update(transform: (Alarm) -> Alarm) {
        _state.value = _state.value.copy(alarm = transform(_state.value.alarm))
        refreshPreview()
    }

    private fun refreshPreview() {
        val alarm = _state.value.alarm
        _state.value = _state.value.copy(
            nextTriggerPreview = com.powerclock.alarm.domain.scheduling.NextOccurrenceCalculator
                .nextTrigger(alarm.copy(enabled = true), ZonedDateTime.now()),
        )
    }

    // ------------------------------------------------------------- missions

    fun addMission(config: MissionConfig) {
        val list = _state.value.missions + config
        setMissions(list)
    }

    fun updateMission(index: Int, config: MissionConfig) {
        val list = _state.value.missions.toMutableList()
        if (index in list.indices) {
            list[index] = config
            setMissions(list)
        }
    }

    fun removeMission(index: Int) {
        val list = _state.value.missions.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            setMissions(list)
        }
    }

    fun moveMission(index: Int, delta: Int) {
        val list = _state.value.missions.toMutableList()
        val to = index + delta
        if (index in list.indices && to in list.indices) {
            val item = list.removeAt(index)
            list.add(to, item)
            setMissions(list)
        }
    }

    private fun setMissions(list: List<MissionConfig>) {
        _state.value = _state.value.copy(
            missions = list,
            alarm = _state.value.alarm.copy(missionsEncoded = MissionConfig.encodeStack(list)),
        )
    }

    // --------------------------------------------------------------- sounds

    fun previewSound(soundId: String) {
        stopPreview()
        val sound = SoundCatalog.byId(soundId)
        val resId = appContext.resources.getIdentifier(sound.rawResName, "raw", appContext.packageName)
        if (resId == 0) return
        val player = ExoPlayer.Builder(appContext).build()
        previewPlayer = player
        player.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${appContext.packageName}/$resId")))
        player.prepare()
        player.play()
    }

    fun previewCustom() {
        val uri = _state.value.alarm.customSoundUri ?: return
        stopPreview()
        try {
            val player = ExoPlayer.Builder(appContext).build()
            previewPlayer = player
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(_state.value.alarm.customSoundStartMs)
                            .build(),
                    )
                    .build(),
            )
            player.prepare()
            player.play()
        } catch (_: Exception) {
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
    }

    // --------------------------------------------------------- custom music

    fun onCustomTrackPicked(uri: Uri) {
        viewModelScope.launch {
            customAudioStore.persistPermission(uri)
            val info = customAudioStore.readMetadata(uri)
            if (info == null) {
                _state.value = _state.value.copy(
                    copyResult = "That file could not be read. Please pick a different track.",
                )
                return@launch
            }
            update {
                it.copy(
                    soundId = SoundCatalog.CUSTOM_ID,
                    customSoundUri = uri.toString(),
                    customSoundTitle = "${info.title} — ${info.artist}",
                    customSoundStartMs = 0L,
                )
            }
            refreshCustomTrack()
        }
    }

    fun removeCustomTrack() {
        update {
            it.copy(
                soundId = SoundCatalog.FALLBACK_ID,
                customSoundUri = null,
                customSoundTitle = null,
                customSoundStartMs = 0L,
            )
        }
        _state.value = _state.value.copy(customTrack = null, customTrackPlayable = true)
        viewModelScope.launch { customAudioStore.clearPrivateCopies() }
    }

    fun copyTrackForReliability() {
        val uriStr = _state.value.alarm.customSoundUri ?: return
        _state.value = _state.value.copy(copyingTrack = true, copyResult = null)
        viewModelScope.launch {
            val copied = customAudioStore.copyIntoPrivateStorage(Uri.parse(uriStr))
            if (copied != null) {
                update { it.copy(customSoundUri = copied.toString()) }
                _state.value = _state.value.copy(
                    copyingTrack = false,
                    copyResult = "Copied into Power Clock. The alarm no longer depends on the original file.",
                )
            } else {
                _state.value = _state.value.copy(
                    copyingTrack = false,
                    copyResult = "Could not copy (not enough free space or the file is unreadable).",
                )
            }
            refreshCustomTrack()
        }
    }

    private suspend fun refreshCustomTrack() {
        val uriStr = _state.value.alarm.customSoundUri
        if (uriStr == null) {
            _state.value = _state.value.copy(customTrack = null, customTrackPlayable = true)
            return
        }
        val uri = Uri.parse(uriStr)
        val playable = customAudioStore.isPlayable(uri)
        val info = if (playable) customAudioStore.readMetadata(uri) else null
        _state.value = _state.value.copy(customTrack = info, customTrackPlayable = playable)
    }

    // ----------------------------------------------------------------- save

    fun save() {
        viewModelScope.launch {
            stopPreview()
            var alarm = _state.value.alarm
            // Re-validate the custom track before scheduling; broken tracks
            // silently fall back to the bundled tone at ring time, but we
            // surface it here too.
            if (alarm.soundId == SoundCatalog.CUSTOM_ID && alarm.customSoundUri != null) {
                val ok = customAudioStore.isPlayable(Uri.parse(alarm.customSoundUri))
                _state.value = _state.value.copy(customTrackPlayable = ok)
            }
            alarm = alarm.copy(enabled = true)
            val id = repo.upsert(alarm)
            repo.getById(id)?.let { scheduler.schedule(it) }
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun setMissionStartPosition(ms: Long) {
        update { it.copy(customSoundStartMs = ms.coerceAtLeast(0L)) }
    }

    val exactAlarmAllowedNow: Boolean get() = scheduler.canScheduleExactAlarms()

    val missionTypesForUser: List<MissionType>
        get() = if (_state.value.cannotExercise) {
            MissionType.entries.filter { !it.isWorkout }
        } else {
            MissionType.entries.toList()
        }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }
}
