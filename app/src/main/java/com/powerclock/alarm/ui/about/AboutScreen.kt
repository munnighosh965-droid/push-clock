package com.powerclock.alarm.ui.about

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powerclock.alarm.BuildConfig
import com.powerclock.alarm.ui.components.PowerCard
import com.powerclock.alarm.ui.components.SectionTitle
import com.powerclock.alarm.ui.components.Wordmark

private data class License(val library: String, val license: String)

private val LICENSES = listOf(
    License("AndroidX (Core, Lifecycle, Activity, Navigation, Room, DataStore, CameraX, Media3, Compose, AppCompat, SplashScreen)", "Apache License 2.0"),
    License("Kotlin Standard Library & kotlinx.coroutines — JetBrains", "Apache License 2.0"),
    License("Jetpack Compose Material 3 & Material Icons — Google", "Apache License 2.0"),
    License("Dagger / Hilt — Google", "Apache License 2.0"),
    License("MediaPipe Tasks Vision (Pose Landmarker) — Google", "Apache License 2.0"),
    License("Pose Landmarker Lite model (pose_landmarker_lite.task) — Google", "Apache License 2.0"),
    License("ZXing (\"Zebra Crossing\") core — ZXing authors", "Apache License 2.0"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
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
            Text("About", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))

        Wordmark(big = true)
        Text(
            "WAKE. MOVE. WIN.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        PowerCard(Modifier.fillMaxWidth()) {
            Text(
                "Power Clock is a completely free, offline-first alarm clock with wake-up missions. " +
                    "No ads, no subscriptions, no accounts, no tracking — and no internet permission at all. " +
                    "All sounds, artwork, and code are original to this project.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))

        PowerCard(Modifier.fillMaxWidth()) {
            Text(
                "Wellness note: Power Clock encourages gentle morning movement but is not medical advice. " +
                    "Listen to your body, keep targets comfortable, and use the non-physical missions or " +
                    "emergency dismiss whenever exercise isn't right for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("Open-source licenses")
        LICENSES.forEach { entry ->
            PowerCard(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column {
                    Text(entry.library, style = MaterialTheme.typography.titleSmall)
                    Text(
                        entry.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Full license texts are bundled with the source distribution (THIRD_PARTY_LICENSES.md). " +
                "The Apache License 2.0 text is available at apache.org/licenses/LICENSE-2.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }
}
