package com.powerclock.alarm.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerclock.alarm.domain.pose.PoseSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun cameraProvider(context: Context): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

/** True when the device exposes a camera facing the given direction. */
fun hasCamera(provider: ProcessCameraProvider, front: Boolean): Boolean = try {
    provider.hasCamera(
        if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
    )
} catch (_: Exception) {
    false
}

/**
 * Live camera preview feeding frames into the on-device pose detector.
 *
 * The camera is bound **before** the pose model finishes loading, so the user
 * always sees themselves immediately. [onBindFailed] fires only when the
 * camera itself cannot start (missing hardware, permission race, or still
 * busy after several retries). [onPoseUnavailable] fires when the camera works
 * but automatic rep counting cannot (model load failure) — callers can then
 * switch to manual counting instead of losing the workout entirely.
 */
@Composable
fun PoseCameraPreview(
    useFrontCamera: Boolean,
    onSample: (PoseSample) -> Unit,
    onBindFailed: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    onPoseUnavailable: (String) -> Unit = {},
    onCameraReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        PoseAnalyzer(
            context = context,
            onSample = onSample,
            onError = { /* pose errors pause counting; handled by empty samples */ },
        )
    }

    // Bind the camera first. Right after an alarm fires the camera can still
    // be held by another process for a moment, so binding is retried before
    // giving up on it.
    LaunchedEffect(useFrontCamera) {
        var lastError: Throwable? = IllegalStateException("Camera did not start")
        var bound = false
        repeat(4) { attempt ->
            if (bound) return@repeat
            try {
                val provider = cameraProvider(context)
                val wantFront = useFrontCamera && hasCamera(provider, front = true)
                val selector = when {
                    wantFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    hasCamera(provider, front = false) -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasCamera(provider, front = true) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                }
                if (selector == null) {
                    onBindFailed(IllegalStateException("No camera available"))
                    return@LaunchedEffect
                }
                provider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                bound = true
                onCameraReady()
            } catch (e: Throwable) {
                lastError = e
                delay(400L * (attempt + 1))
            }
        }
        if (!bound) {
            onBindFailed(lastError ?: IllegalStateException("Camera did not start"))
            return@LaunchedEffect
        }
        // Heavy model load happens off the main thread while the preview is
        // already live; counting starts as soon as the detector is ready.
        val modelOk = withContext(Dispatchers.Default) { analyzer.initialize() }
        if (!modelOk) {
            onPoseUnavailable(analyzer.initError ?: "Pose detector could not start")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
            // Unbind so the camera is guaranteed to be released for other apps.
            ProcessCameraProvider.getInstance(context).addListener(
                {
                    try {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    } catch (_: Exception) {
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
