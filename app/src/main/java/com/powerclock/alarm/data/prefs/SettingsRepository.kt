package com.powerclock.alarm.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "powerclock_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class SleeperType { LIGHT, MEDIUM, HEAVY }
enum class FitnessLevel { GENTLE, MODERATE, ACTIVE }

/**
 * Full local user profile + app settings. Everything lives on-device in
 * DataStore; there is no account and no sync.
 */
data class UserSettings(
    val onboardingComplete: Boolean = false,
    val name: String = "",
    val usualWakeMinutes: Int = 7 * 60,
    val targetWakeMinutes: Int = 6 * 60 + 30,
    val workDaysMask: Int = 0b0011111,
    val typicalSnoozes: Int = 2,
    val sleeperType: SleeperType = SleeperType.MEDIUM,
    val fitnessLevel: FitnessLevel = FitnessLevel.MODERATE,
    val preferredMissions: String = "",
    val soundIntensity: Int = 80,
    val bedtimeMinutes: Int = 23 * 60,
    val bedtimeReminderEnabled: Boolean = false,
    val cannotExercise: Boolean = false,
    val earlyRiseEnabled: Boolean = false,
    val earlyRiseStepMinutes: Int = 10,
    val earlyRiseEveryDays: Int = 3,
    val earlyRiseLastAppliedEpochDay: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val spokenCues: Boolean = false,
    val allowVolumeOverride: Boolean = false,
    val autoSilenceMinutes: Int = 15,
    val qrCardId: String = "",
    /**
     * When on (default), every alarm ends with a workout: the mission starts
     * itself, a workout is injected if the alarm has none, and camera trouble
     * falls back to self-counted reps instead of skipping the exercise.
     */
    val strictWorkoutMode: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val NAME = stringPreferencesKey("name")
        val USUAL_WAKE = intPreferencesKey("usual_wake_minutes")
        val TARGET_WAKE = intPreferencesKey("target_wake_minutes")
        val WORK_DAYS = intPreferencesKey("work_days_mask")
        val SNOOZES = intPreferencesKey("typical_snoozes")
        val SLEEPER = stringPreferencesKey("sleeper_type")
        val FITNESS = stringPreferencesKey("fitness_level")
        val PREFERRED_MISSIONS = stringPreferencesKey("preferred_missions")
        val SOUND_INTENSITY = intPreferencesKey("sound_intensity")
        val BEDTIME = intPreferencesKey("bedtime_minutes")
        val BEDTIME_REMINDER = booleanPreferencesKey("bedtime_reminder_enabled")
        val CANNOT_EXERCISE = booleanPreferencesKey("cannot_exercise")
        val EARLY_RISE = booleanPreferencesKey("early_rise_enabled")
        val EARLY_RISE_STEP = intPreferencesKey("early_rise_step_minutes")
        val EARLY_RISE_EVERY = intPreferencesKey("early_rise_every_days")
        val EARLY_RISE_LAST = stringPreferencesKey("early_rise_last_applied_epoch_day")
        val THEME = stringPreferencesKey("theme_mode")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SPOKEN_CUES = booleanPreferencesKey("spoken_cues")
        val VOLUME_OVERRIDE = booleanPreferencesKey("allow_volume_override")
        val AUTO_SILENCE = intPreferencesKey("auto_silence_minutes")
        val QR_CARD_ID = stringPreferencesKey("qr_card_id")
        val STRICT_WORKOUT = booleanPreferencesKey("strict_workout_mode")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            onboardingComplete = p[Keys.ONBOARDING] ?: false,
            name = p[Keys.NAME] ?: "",
            usualWakeMinutes = p[Keys.USUAL_WAKE] ?: 7 * 60,
            targetWakeMinutes = p[Keys.TARGET_WAKE] ?: 6 * 60 + 30,
            workDaysMask = p[Keys.WORK_DAYS] ?: 0b0011111,
            typicalSnoozes = p[Keys.SNOOZES] ?: 2,
            sleeperType = enum(p[Keys.SLEEPER], SleeperType.MEDIUM),
            fitnessLevel = enum(p[Keys.FITNESS], FitnessLevel.MODERATE),
            preferredMissions = p[Keys.PREFERRED_MISSIONS] ?: "",
            soundIntensity = p[Keys.SOUND_INTENSITY] ?: 80,
            bedtimeMinutes = p[Keys.BEDTIME] ?: 23 * 60,
            bedtimeReminderEnabled = p[Keys.BEDTIME_REMINDER] ?: false,
            cannotExercise = p[Keys.CANNOT_EXERCISE] ?: false,
            earlyRiseEnabled = p[Keys.EARLY_RISE] ?: false,
            earlyRiseStepMinutes = p[Keys.EARLY_RISE_STEP] ?: 10,
            earlyRiseEveryDays = p[Keys.EARLY_RISE_EVERY] ?: 3,
            earlyRiseLastAppliedEpochDay = p[Keys.EARLY_RISE_LAST]?.toLongOrNull() ?: 0L,
            themeMode = enum(p[Keys.THEME], ThemeMode.SYSTEM),
            reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            spokenCues = p[Keys.SPOKEN_CUES] ?: false,
            allowVolumeOverride = p[Keys.VOLUME_OVERRIDE] ?: false,
            autoSilenceMinutes = p[Keys.AUTO_SILENCE] ?: 15,
            qrCardId = p[Keys.QR_CARD_ID] ?: "",
            strictWorkoutMode = p[Keys.STRICT_WORKOUT] ?: true,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        context.dataStore.edit { p ->
            p[Keys.ONBOARDING] = next.onboardingComplete
            p[Keys.NAME] = next.name.take(40)
            p[Keys.USUAL_WAKE] = next.usualWakeMinutes
            p[Keys.TARGET_WAKE] = next.targetWakeMinutes
            p[Keys.WORK_DAYS] = next.workDaysMask
            p[Keys.SNOOZES] = next.typicalSnoozes
            p[Keys.SLEEPER] = next.sleeperType.name
            p[Keys.FITNESS] = next.fitnessLevel.name
            p[Keys.PREFERRED_MISSIONS] = next.preferredMissions
            p[Keys.SOUND_INTENSITY] = next.soundIntensity
            p[Keys.BEDTIME] = next.bedtimeMinutes
            p[Keys.BEDTIME_REMINDER] = next.bedtimeReminderEnabled
            p[Keys.CANNOT_EXERCISE] = next.cannotExercise
            p[Keys.EARLY_RISE] = next.earlyRiseEnabled
            p[Keys.EARLY_RISE_STEP] = next.earlyRiseStepMinutes
            p[Keys.EARLY_RISE_EVERY] = next.earlyRiseEveryDays
            p[Keys.EARLY_RISE_LAST] = next.earlyRiseLastAppliedEpochDay.toString()
            p[Keys.THEME] = next.themeMode.name
            p[Keys.REDUCE_MOTION] = next.reduceMotion
            p[Keys.HAPTICS] = next.hapticsEnabled
            p[Keys.SPOKEN_CUES] = next.spokenCues
            p[Keys.VOLUME_OVERRIDE] = next.allowVolumeOverride
            p[Keys.AUTO_SILENCE] = next.autoSilenceMinutes
            p[Keys.QR_CARD_ID] = next.qrCardId
            p[Keys.STRICT_WORKOUT] = next.strictWorkoutMode
        }
    }

    /** Creates (once) and returns the random local QR card identifier. */
    suspend fun ensureQrCardId(): String {
        val existing = current().qrCardId
        if (existing.isNotBlank()) return existing
        val id = "POWERCLOCK-" + UUID.randomUUID().toString().uppercase()
        update { it.copy(qrCardId = id) }
        return id
    }

    suspend fun wipeAll() {
        context.dataStore.edit { it.clear() }
    }

    private inline fun <reified T : Enum<T>> enum(raw: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default
}
