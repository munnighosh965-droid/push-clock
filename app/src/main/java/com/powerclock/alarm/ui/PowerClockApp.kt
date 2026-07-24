package com.powerclock.alarm.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powerclock.alarm.data.prefs.UserSettings

@Composable
fun PowerClockApp(settings: UserSettings) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Text("Power Clock")
    }
}
