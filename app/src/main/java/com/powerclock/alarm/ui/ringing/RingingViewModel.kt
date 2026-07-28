package com.powerclock.alarm.ui.ringing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerclock.alarm.alarmengine.AlarmRingingService
import com.powerclock.alarm.alarmengine.RingingSession
import com.powerclock.alarm.alarmengine.RingingStateHolder
import com.powerclock.alarm.data.prefs.FitnessLevel
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.prefs.UserSettings
import com.powerclock.alarm.data.repo.HistoryRepository
import com.powerclock.alarm.domain.missions.FallbackSelector
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RingingPhase { RINGING, MISSION, SUCCESS }

data class MissionRunState(
    val phase: RingingPhase = RingingPhase.RINGING,
    val missions: List<MissionConfig> = emptyList(),
    val index: Int = 0,
    val totalReps: Int = 0,
    val summary: List<String> = emptyList(),
    val emergencyUsed: Boolean = false,
) {
    val current: MissionConfig? get() = missions.getOrNull(index)
}

@HiltViewModel
class RingingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    stateHolder: RingingStateHolder,
    settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val session: StateFlow<RingingSession?> = stateHolder.session

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val _run = MutableStateFlow(MissionRunState())
    val run: StateFlow<MissionRunState> = _run.asStateFlow()

    /** Kept after dismissal so the success screen can attach a rating. */
    private var completedEventId: Long = -1L

    /**
     * Strict workout mode is on and the user has not told us that exercise is
     * unsafe: the alarm can only be dismissed by finishing a workout.
     */
    val workoutRequired: Boolean
        get() = settings.value.strictWorkoutMode && !settings.value.cannotExercise

    fun beginMissions() {
        val alarm = session.value?.alarm ?: return
        val missions = enforceWorkout(alarm.missions)
        AlarmRingingService.missionStarted(appContext)
        if (missions.isEmpty()) {
            finishAll()
        } else {
            _run.value = _run.value.copy(phase = RingingPhase.MISSION, missions = missions, index = 0)
        }
    }

    /**
     * In strict mode every mission stack ends with a workout. Alarms with no
     * missions at all get one built from the user's fitness level, so "just
     * swipe to dismiss" is never an option.
     */
    private fun enforceWorkout(configured: List<MissionConfig>): List<MissionConfig> {
        if (!workoutRequired) return configured
        if (configured.any { it.type.isWorkout }) return configured
        return configured + defaultWorkout()
    }

    private fun defaultWorkout(): MissionConfig {
        val s = settings.value
        val type = when (s.fitnessLevel) {
            FitnessLevel.GENTLE -> MissionType.KNEE_PUSH_UPS
            FitnessLevel.MODERATE -> MissionType.SQUATS
            FitnessLevel.ACTIVE -> MissionType.PUSH_UPS
        }
        val target = when (s.fitnessLevel) {
            FitnessLevel.GENTLE -> 3
            FitnessLevel.MODERATE -> 5
            FitnessLevel.ACTIVE -> 8
        }
        return MissionConfig(type = type, target = target)
    }

    fun onMissionCompleted(reps: Int = 0) {
        val state = _run.value
        val current = state.current ?: return
        val newSummary = state.summary + "${current.type.name}:${current.target}"
        val next = state.index + 1
        if (next >= state.missions.size) {
            _run.value = state.copy(
                totalReps = state.totalReps + reps,
                summary = newSummary,
                index = next,
            )
            finishAll()
        } else {
            _run.value = state.copy(
                index = next,
                totalReps = state.totalReps + reps,
                summary = newSummary,
            )
        }
    }

    /**
     * The active mission cannot run (camera denied/busy, sensor missing,
     * QR card not set up…). Only this mission is replaced with the alarm's
     * configured fallback; the rest of the stack continues unchanged.
     */
    fun replaceCurrentMission(reason: FallbackSelector.FailureReason) {
        val state = _run.value
        val current = state.current ?: return
        val alarm = session.value?.alarm ?: return
        val replacement = FallbackSelector.replacementFor(current, alarm.fallbackMissionType, reason)
        val missions = state.missions.toMutableList()
        missions[state.index] = replacement
        _run.value = state.copy(missions = missions)
    }

    /** The user tapped "I cannot safely exercise": swap in a brain mission. */
    fun cannotSafelyExercise() {
        replaceCurrentMission(FallbackSelector.FailureReason.POSE_MODEL_UNAVAILABLE)
    }

    fun emergencyDismiss() {
        val s = session.value ?: return
        completedEventId = s.wakeEventId
        _run.value = _run.value.copy(phase = RingingPhase.SUCCESS, emergencyUsed = true)
        AlarmRingingService.dismiss(
            appContext,
            emergency = true,
            totalReps = _run.value.totalReps,
            summary = (_run.value.summary + "EMERGENCY").joinToString("|"),
        )
    }

    private fun finishAll() {
        val s = session.value ?: return
        completedEventId = s.wakeEventId
        _run.value = _run.value.copy(phase = RingingPhase.SUCCESS)
        AlarmRingingService.dismiss(
            appContext,
            emergency = false,
            totalReps = _run.value.totalReps,
            summary = _run.value.summary.joinToString("|"),
        )
    }

    fun rateMorning(rating: Int) {
        val eventId = completedEventId
        if (eventId <= 0) return
        viewModelScope.launch {
            historyRepository.getById(eventId)?.let {
                historyRepository.update(it.copy(energyRating = rating.coerceIn(1, 5)))
            }
        }
    }
}
