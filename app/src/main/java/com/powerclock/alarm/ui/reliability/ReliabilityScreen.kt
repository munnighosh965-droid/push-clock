package com.powerclock.alarm.ui.reliability

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.powerclock.alarm.ui.components.PowerCard

private data class Check(
    val title: String,
    val ok: Boolean,
    val critical: Boolean,
    val detail: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-evaluate every time the user returns from a settings screen.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    val checks = remember(refreshTick) { buildChecks(context, notifPermissionLauncher::launch, cameraPermissionLauncher::launch) }
    val criticalOk = checks.filter { it.critical }.all { it.ok }

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
            Text("Alarm Reliability Check", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))

        PowerCard(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    if (criticalOk) "Critical checks passed" else "Attention needed",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (criticalOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (criticalOk) {
                        "Notifications, exact alarms, full-screen alerts, and sound are all ready. Optional items below can improve reliability further."
                    } else {
                        "Some required settings are off, so alarms may be delayed, quiet, or hidden. Power Clock will not pretend an alarm is protected while these are missing."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        checks.forEach { check ->
            PowerCard(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (check.ok) "✓" else if (check.critical) "!" else "○",
                            style = MaterialTheme.typography.headlineSmall,
                            color = when {
                                check.ok -> MaterialTheme.colorScheme.primary
                                check.critical -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.padding(6.dp))
                        Text(check.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!check.ok && check.action != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = check.action) {
                            Text(check.actionLabel ?: "Fix")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PowerCard(Modifier.fillMaxWidth()) {
            Text(
                "Good to know: if you \"force stop\" Power Clock from system settings, Android blocks ALL of its alarms until the app is opened again. This is an operating-system rule that applies to every third-party alarm clock. Swiping the app away from Recents is fine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun buildChecks(
    context: Context,
    requestNotifications: (String) -> Unit,
    requestCamera: (String) -> Unit,
): List<Check> {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun openAppSettings() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    val notifOk = notificationManager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    val fullScreenOk = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()

    val soundOk = audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0

    val batteryOk = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    val cameraOk = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    return listOf(
        Check(
            title = "Notifications",
            ok = notifOk,
            critical = true,
            detail = "Required to show the ringing alarm and its full-screen wake-up view.",
            actionLabel = "Allow",
            action = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifications(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppSettings()
                }
            },
        ),
        Check(
            title = "Exact alarms",
            ok = exactOk,
            critical = true,
            detail = "Lets alarms ring at the precise minute even while the phone sleeps (Doze).",
            actionLabel = "Open settings",
            action = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                    }
                }
            },
        ),
        Check(
            title = "Full-screen alarm view",
            ok = fullScreenOk,
            critical = true,
            detail = "Opens the mission screen over the lock screen when the alarm fires.",
            actionLabel = "Open settings",
            action = {
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                    }
                }
            },
        ),
        Check(
            title = "Alarm sound volume",
            ok = soundOk,
            critical = true,
            detail = if (soundOk) {
                "Alarm stream volume is above zero."
            } else {
                "Alarm stream volume is currently ZERO — alarms would be silent. Raise it with the volume keys or enable Heavy Sleeper volume override in Settings."
            },
        ),
        Check(
            title = "Battery optimization exemption",
            ok = batteryOk,
            critical = false,
            detail = "Optional. Alarms use setAlarmClock() which works in Doze, but excluding Power Clock from battery optimization adds extra headroom on aggressive devices.",
            actionLabel = "Battery settings",
            action = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: Exception) {
                }
            },
        ),
        Check(
            title = "Camera (workout missions)",
            ok = cameraOk,
            critical = false,
            detail = "Only needed for camera-counted workouts and QR scanning. If unavailable at ring time, the mission is automatically replaced with your fallback — alarms can always be dismissed.",
            actionLabel = "Allow camera",
            action = { requestCamera(Manifest.permission.CAMERA) },
        ),
    )
}
