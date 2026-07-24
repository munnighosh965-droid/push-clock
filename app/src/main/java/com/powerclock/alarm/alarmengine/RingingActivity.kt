package com.powerclock.alarm.alarmengine

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
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
 * on; back navigation is disabled so the only exits are mission completion
 * or the deliberate emergency dismiss.
 */
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .requestDismissKeyguard(this, null)

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
