package com.powerclock.alarm.ui.ringing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerclock.alarm.camera.QrAnalyzer
import com.powerclock.alarm.camera.cameraProvider
import com.powerclock.alarm.domain.missions.FallbackSelector
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.ui.components.ProgressRing
import java.util.concurrent.Executors
import kotlin.math.sqrt

@Composable
fun ShakeMissionScreen(
    config: MissionConfig,
    onComplete: () -> Unit,
    onSensorUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    var shakes by remember { mutableIntStateOf(0) }
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentUnavailable by rememberUpdatedState(onSensorUnavailable)

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            currentUnavailable()
            onDispose { }
        } else {
            var lastShakeAt = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val (x, y, z) = event.values
                    val magnitude = sqrt(x * x + y * y + z * z)
                    val now = System.currentTimeMillis()
                    // > ~1.8 g of force, debounced, counts as one shake.
                    if (magnitude > 17f && now - lastShakeAt > 250) {
                        lastShakeAt = now
                        shakes++
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    LaunchedEffect(shakes) {
        if (shakes >= config.target) currentOnComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Shake it off!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Shake the phone firmly until the ring fills.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        ProgressRing(progress = shakes.toFloat() / config.target, ringSize = 200.dp) {
            Text(
                "$shakes / ${config.target}",
                style = MaterialTheme.typography.headlineLarge,
            )
        }
    }
}

@Composable
fun QrMissionScreen(
    expectedContent: String,
    onComplete: () -> Unit,
    onCameraFailed: (FallbackSelector.FailureReason) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scannedWrong by remember { mutableStateOf(false) }
    var matched by remember { mutableStateOf(false) }
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(Unit) {
        try {
            val provider = cameraProvider(context)
            provider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        executor,
                        QrAnalyzer { text ->
                            if (!matched) {
                                if (text == expectedContent) {
                                    matched = true
                                } else {
                                    scannedWrong = true
                                }
                            }
                        },
                    )
                }
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        } catch (_: Exception) {
            onCameraFailed(FallbackSelector.FailureReason.CAMERA_UNAVAILABLE)
        }
    }

    LaunchedEffect(matched) {
        if (matched) currentOnComplete()
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            ContextCompat.getMainExecutor(context).execute {
                try {
                    androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context).get().unbindAll()
                } catch (_: Exception) {
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (scannedWrong) {
                    "That's a different code — find your Power Clock card."
                } else {
                    "Point the camera at your Power Clock QR card."
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                onCameraFailed(FallbackSelector.FailureReason.CAMERA_UNAVAILABLE)
            }) { Text("Camera isn't working — use fallback") }
        }
    }
}
