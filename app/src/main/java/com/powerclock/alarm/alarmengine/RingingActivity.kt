package com.powerclock.alarm.alarmengine

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.powerclock.alarm.ui.theme.PowerClockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .requestDismissKeyguard(this, null)
        setContent {
            PowerClockTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Ringing")
                }
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
