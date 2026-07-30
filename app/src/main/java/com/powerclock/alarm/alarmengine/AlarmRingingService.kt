package com.powerclock.alarm.alarmengine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.powerclock.alarm.data.audio.CustomAudioStore
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.data.repo.HistoryRepository
import com.powerclock.alarm.domain.audio.SoundCatalog
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Foreground service that owns everything audible/tactile while an alarm
 * rings: Media3 playback on the alarm stream, vibration, optional torch
 * pulses, a short wake lock, the ringing notification with its full-screen
 * intent, the overlap queue, and the safety auto-silence timeout.
 *
 * Hard rules:
 *  - [android.app.Service.startForeground] is called synchronously in
 *    [onStartCommand], before any I/O, so the 5-second foreground window can
 *    never be missed on slow or throttled devices.
 *  - No exception may kill the process while an alarm should be ringing:
 *    the coroutine scope carries a [CoroutineExceptionHandler] that falls
 *    back to a minimal "panic ring" (vibration + fallback tone) instead of
 *    crashing.
 *  - Every resource acquired here is released in [stopRingingInternal],
 *    which runs on every exit path including [onDestroy].
 */
@AndroidEntryPoint
class AlarmRingingService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var historyRepository: HistoryRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notifications: AlarmNotifications
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var stateHolder: RingingStateHolder
    @Inject lateinit var customAudioStore: CustomAudioStore

    private val crashGuard = CoroutineExceptionHandler { _, _ ->
        // Never let a ringing alarm die silently with the process: fall back
        // to the simplest possible ring.
        panicRing()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + crashGuard)

    private var player: ExoPlayer? = null
    private var panicPlayer: MediaPlayer? = null
    private var fallbackToneActive = false
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var torchJob: Job? = null
    private var volumeRampJob: Job? = null
    private var autoSilenceJob: Job? = null
    private var torchCameraId: String? = null
    private var foregroundStarted = false

    private var activeAlarm: Alarm? = null
    private var activeEventId: Long = -1L
    private var missionStartedAtMs: Long? = null
    private var previousAlarmVolume: Int = -1

    /** Overlap/dedupe queue for simultaneous alarms. */
    private val ringQueue = RingQueue()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Enter the foreground state immediately — before any database or
        // player work — so the system's foreground deadline is always met.
        ensureForeground()
        when (intent?.action) {
            ACTION_RING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                if (alarmId > 0L) onRingRequested(alarmId)
            }
            ACTION_MISSION_STARTED -> onMissionStarted()
            ACTION_DISMISS_COMPLETED -> onDismiss(
                WakeOutcome.COMPLETED,
                intent.getIntExtra(EXTRA_TOTAL_REPS, 0),
                intent.getStringExtra(EXTRA_MISSION_SUMMARY) ?: "",
            )
            ACTION_DISMISS_EMERGENCY -> onDismiss(
                WakeOutcome.EMERGENCY,
                intent.getIntExtra(EXTRA_TOTAL_REPS, 0),
                intent.getStringExtra(EXTRA_MISSION_SUMMARY) ?: "",
            )
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground(alarm: Alarm? = null) {
        try {
            val notification = if (alarm != null) {
                notifications.ringingNotification(alarm)
            } else {
                notifications.genericRingingNotification()
            }
            ServiceCompat.startForeground(
                this,
                AlarmNotifications.NOTIFICATION_ID_RINGING,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            foregroundStarted = true
        } catch (_: Throwable) {
            // Even if foreground promotion fails (OEM restrictions), keep
            // running as long as the system allows and still ring.
        }
    }

    // ------------------------------------------------------------------ ring

    private fun onRingRequested(alarmId: Long) {
        // The queue guarantees a single audio pipeline and ignores duplicate
        // deliveries of the same alarm id.
        val startNow = ringQueue.requestRing(alarmId)
        stateHolder.updateQueuedCount(ringQueue.queuedCount)
        if (!startNow) return
        scope.launch {
            val alarm = try {
                alarmRepository.getById(alarmId)
            } catch (_: Throwable) {
                null
            }
            if (alarm == null || !alarm.enabled) {
                // Deleted or disabled after the trigger was armed.
                advanceQueueOrStop()
                return@launch
            }
            try {
                startRinging(alarm)
            } catch (_: Throwable) {
                panicRing()
            }
        }
    }

    /**
     * Opens the full-screen ringing UI directly. Failures are survivable:
     * the ringing notification carries the same intent as a full-screen
     * intent, so the alarm still reaches the user.
     */
    private fun launchRingingActivity(alarmId: Long) {
        try {
            startActivity(
                Intent(this, RingingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    putExtra(RingingActivity.EXTRA_ALARM_ID, alarmId)
                },
            )
        } catch (_: Throwable) {
        }
    }

    /** Moves to the next ringable queued alarm, or winds the service down. */
    private suspend fun advanceQueueOrStop() {
        while (true) {
            val nextId = ringQueue.finishActive() ?: break
            val alarm = try {
                alarmRepository.getById(nextId)
            } catch (_: Throwable) {
                null
            }
            if (alarm != null && alarm.enabled) {
                stateHolder.updateQueuedCount(ringQueue.queuedCount)
                try {
                    startRinging(alarm)
                    return
                } catch (_: Throwable) {
                    panicRing()
                    return
                }
            }
        }
        stateHolder.set(null)
        stopSelfIfIdle()
    }

    private suspend fun startRinging(alarm: Alarm) {
        val now = System.currentTimeMillis()
        activeAlarm = alarm
        missionStartedAtMs = null
        fallbackToneActive = false

        // Upgrade the provisional notification with the alarm's label.
        ensureForeground(alarm)

        // Take the user straight to the full-screen mission view. The
        // notification's full-screen intent is only a backup: since Android
        // 14 it needs a permission the user can revoke, and when it is
        // missing the system silently degrades it to a heads-up notification.
        // Alarms armed with setAlarmClock() come with a temporary
        // background-activity-start allowance, which is what makes this work.
        launchRingingActivity(alarm.id)

        // History row is created immediately so even a crash records the ring.
        activeEventId = try {
            historyRepository.insert(
                WakeEvent(
                    alarmId = alarm.id,
                    alarmLabel = alarm.label,
                    scheduledAtMs = now,
                    rangAtMs = now,
                    outcome = WakeOutcome.MISSED,
                ),
            )
        } catch (_: Throwable) {
            -1L
        }
        stateHolder.set(RingingSession(alarm, activeEventId, now, ringQueue.queuedCount))

        // Keep the next occurrence armed even before this one is dismissed.
        try {
            if (alarm.isRepeating) {
                scheduler.schedule(alarm, ZonedDateTime.now().plusSeconds(1))
            } else {
                alarmRepository.setEnabled(alarm.id, enabled = false)
            }
        } catch (_: Throwable) {
        }

        acquireWakeLock()
        try {
            startAudio(alarm)
        } catch (_: Throwable) {
            startPanicAudio()
        }
        if (alarm.vibrate) startVibration(alarm.vibrationPatternId)
        if (alarm.flashlight) startTorchPulses()

        val autoSilenceMinutes = try {
            settingsRepository.current().autoSilenceMinutes
        } catch (_: Throwable) {
            15
        }
        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(autoSilenceMinutes.coerceIn(5, 30) * 60_000L)
            onDismiss(WakeOutcome.MISSED, 0, "auto-silenced")
        }
    }

    /**
     * Minimal unbreakable ring used when anything in the normal pipeline
     * fails: notification (already posted), vibration, and a plain
     * MediaPlayer looping the bundled tone on the alarm stream.
     */
    private fun panicRing() {
        try {
            if (vibrator == null) startVibration(0)
        } catch (_: Throwable) {
        }
        startPanicAudio()
        if (autoSilenceJob == null || autoSilenceJob?.isActive != true) {
            autoSilenceJob = scope.launch {
                delay(15 * 60_000L)
                onDismiss(WakeOutcome.MISSED, 0, "auto-silenced(panic)")
            }
        }
    }

    private fun startPanicAudio() {
        if (panicPlayer != null) return
        try {
            val mp = MediaPlayer()
            panicPlayer = mp
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            resources.openRawResourceFd(SoundResources.resIdFor(SoundResources.FALLBACK_RES_ID_NAME))
                .use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            mp.isLooping = true
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepare()
            mp.start()
        } catch (_: Throwable) {
            panicPlayer = null
            // Vibration and the notification remain; the alarm is still
            // visible and dismissable.
        }
    }

    // ----------------------------------------------------------------- audio

    private suspend fun startAudio(alarm: Alarm) {
        val settings = settingsRepository.current()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Heavy Sleeper mode may temporarily raise the alarm stream, with
        // the user's explicit prior consent, remembering the old value.
        if (alarm.heavySleeper && settings.allowVolumeOverride) {
            try {
                previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            } catch (_: Throwable) {
                previousAlarmVolume = -1
            }
        }

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_ALARM)
                .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
                .build(),
            /* handleAudioFocus = */ true,
        )
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Damaged file, revoked URI, decoder issues... whatever
                // happened, the guaranteed bundled tone takes over.
                playFallbackTone()
            }
        })

        val mediaItem = resolveMediaItem(alarm)
        exo.setMediaItem(mediaItem)
        exo.prepare()

        val targetVolume = (alarm.volumePercent.coerceIn(0, 100) / 100f).coerceAtLeast(0.05f)
        if (alarm.gradualVolume) {
            exo.volume = 0.1f
            volumeRampJob?.cancel()
            volumeRampJob = scope.launch {
                val rampSeconds = if (alarm.heavySleeper) 20 else 45
                val steps = 20
                for (step in 1..steps) {
                    if (!isActive) return@launch
                    delay(rampSeconds * 1000L / steps)
                    player?.volume = (0.1f + (targetVolume - 0.1f) * step / steps)
                }
            }
        } else {
            exo.volume = targetVolume
        }
        exo.play()
    }

    private suspend fun resolveMediaItem(alarm: Alarm): MediaItem {
        // Custom track, only when it is still readable right now.
        val customUri = alarm.customSoundUri
        if (alarm.soundId == SoundCatalog.CUSTOM_ID && customUri != null) {
            val playable = try {
                customAudioStore.isPlayable(Uri.parse(customUri))
            } catch (_: Throwable) {
                false
            }
            if (playable) {
                val clip = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(alarm.customSoundStartMs.coerceAtLeast(0L))
                    .build()
                return MediaItem.Builder()
                    .setUri(Uri.parse(customUri))
                    .setClippingConfiguration(clip)
                    .build()
            }
        }
        val sound = if (alarm.randomSound) {
            SoundCatalog.randomSound()
        } else {
            SoundCatalog.byId(alarm.soundId)
        }
        return MediaItem.fromUri(rawUri(sound.rawResName))
    }

    private fun playFallbackTone() {
        // Guard against error loops: if the fallback itself fails, hand the
        // job to the bullet-proof MediaPlayer path exactly once.
        if (fallbackToneActive) {
            startPanicAudio()
            return
        }
        fallbackToneActive = true
        val exo = player
        if (exo == null) {
            startPanicAudio()
            return
        }
        try {
            exo.setMediaItem(MediaItem.fromUri(rawUri(SoundResources.FALLBACK_RES_ID_NAME)))
            exo.prepare()
            exo.volume = 0.9f
            exo.play()
        } catch (_: Throwable) {
            startPanicAudio()
        }
    }

    private fun rawUri(rawName: String): Uri {
        val resId = SoundResources.resIdFor(rawName)
        return Uri.parse("android.resource://$packageName/$resId")
    }

    // ------------------------------------------------------------- vibration

    private fun startVibration(patternId: Int) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = v
            if (!v.hasVibrator()) return
            val pattern = when (patternId) {
                1 -> longArrayOf(0, 250, 250, 250, 250, 800) // triple knock
                2 -> longArrayOf(0, 1200, 300)               // long steady
                else -> longArrayOf(0, 500, 500)             // classic pulse
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------ flashlight

    private fun startTorchPulses() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Throwable) {
            null
        } ?: return
        torchCameraId = cameraId
        torchJob?.cancel()
        torchJob = scope.launch {
            var on = false
            while (isActive) {
                on = !on
                try {
                    cameraManager.setTorchMode(cameraId, on)
                } catch (_: Throwable) {
                    // Camera busy (e.g. workout mission is using it): stop pulsing.
                    return@launch
                }
                delay(600)
            }
        }
    }

    private fun stopTorch() {
        torchJob?.cancel()
        torchJob = null
        val id = torchCameraId ?: return
        torchCameraId = null
        try {
            (getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, false)
        } catch (_: Throwable) {
        }
    }

    // -------------------------------------------------------------- wake lock

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "powerclock:ringing").apply {
                // Safety timeout slightly above the longest auto-silence window.
                acquire(31 * 60_000L)
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    // --------------------------------------------------------------- dismiss

    private fun onMissionStarted() {
        if (missionStartedAtMs != null) return
        missionStartedAtMs = System.currentTimeMillis()
        val eventId = activeEventId
        if (eventId > 0) {
            scope.launch {
                try {
                    historyRepository.getById(eventId)?.let {
                        historyRepository.update(it.copy(missionStartedAtMs = missionStartedAtMs))
                    }
                } catch (_: Throwable) {
                }
            }
        }
        // Torch pulses would fight the workout camera; stop them once the
        // user is engaged.
        stopTorch()
    }

    private fun onDismiss(outcome: WakeOutcome, totalReps: Int, summary: String) {
        val alarm = activeAlarm ?: return
        val eventId = activeEventId
        scope.launch {
            try {
                if (eventId > 0) {
                    historyRepository.getById(eventId)?.let {
                        historyRepository.update(
                            it.copy(
                                dismissedAtMs = System.currentTimeMillis(),
                                outcome = outcome,
                                totalReps = totalReps,
                                missionSummary = summary,
                            ),
                        )
                    }
                }
                // Re-assert the next occurrence (defensive; also covers edits
                // made while ringing).
                alarmRepository.getById(alarm.id)?.let { fresh ->
                    if (fresh.enabled) scheduler.schedule(fresh)
                }
            } catch (_: Throwable) {
            }
            stopRingingInternal()
            advanceQueueOrStop()
        }
    }

    /** Releases every acquired resource. Safe to call multiple times. */
    private fun stopRingingInternal() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        volumeRampJob?.cancel()
        volumeRampJob = null

        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {
        }
        player = null

        try {
            panicPlayer?.stop()
            panicPlayer?.release()
        } catch (_: Throwable) {
        }
        panicPlayer = null
        fallbackToneActive = false

        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
        vibrator = null

        stopTorch()
        restoreAlarmVolume()
        releaseWakeLock()
        activeAlarm = null
        activeEventId = -1L
        missionStartedAtMs = null
    }

    private fun restoreAlarmVolume() {
        if (previousAlarmVolume < 0) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
        } catch (_: Throwable) {
        }
        previousAlarmVolume = -1
    }

    private fun stopSelfIfIdle() {
        if (activeAlarm == null && ringQueue.activeId == null && ringQueue.queuedCount == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopRingingInternal()
        stateHolder.set(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.powerclock.alarm.service.RING"
        const val ACTION_MISSION_STARTED = "com.powerclock.alarm.service.MISSION_STARTED"
        const val ACTION_DISMISS_COMPLETED = "com.powerclock.alarm.service.DISMISS_COMPLETED"
        const val ACTION_DISMISS_EMERGENCY = "com.powerclock.alarm.service.DISMISS_EMERGENCY"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TOTAL_REPS = "total_reps"
        const val EXTRA_MISSION_SUMMARY = "mission_summary"

        fun missionStarted(context: Context) {
            try {
                context.startService(
                    Intent(context, AlarmRingingService::class.java).apply {
                        action = ACTION_MISSION_STARTED
                    },
                )
            } catch (_: Throwable) {
            }
        }

        fun dismiss(context: Context, emergency: Boolean, totalReps: Int, summary: String) {
            try {
                context.startService(
                    Intent(context, AlarmRingingService::class.java).apply {
                        action = if (emergency) ACTION_DISMISS_EMERGENCY else ACTION_DISMISS_COMPLETED
                        putExtra(EXTRA_TOTAL_REPS, totalReps)
                        putExtra(EXTRA_MISSION_SUMMARY, summary)
                    },
                )
            } catch (_: Throwable) {
            }
        }
    }
}
