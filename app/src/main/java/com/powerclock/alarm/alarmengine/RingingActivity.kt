package com.powerclock.alarm.alarmengine

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.powerclock.alarm.ui.ringing.RingingRoot
import com.powerclock.alarm.ui.theme.PowerClockTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen ringing UI. Shown over the lock screen with the screen turned
 * on; back navigation is disabled so the only exit is mission completion.
 */
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .requestDismissKeyguard(this, null)

        // If the ringing service could not be started from the background
        // (OEM restrictions), this activity was opened by the fallback
        // notification instead. Starting the service from a foreground
        // activity is always permitted; the service dedupes repeated ids.
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId > 0L) {
            try {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, AlarmRingingService::class.java).apply {
                        action = AlarmRingingService.ACTION_RING
                        putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                    },
                )
            } catch (_: Throwable) {
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Deliberately ignored while ringing.
                }
            },
        )

        setContent {
            PowerClockTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                        RingingRoot(onFinished = { finish() })
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
