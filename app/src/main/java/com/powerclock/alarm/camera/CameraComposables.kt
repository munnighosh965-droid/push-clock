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
 * [onBindFailed] fires when the camera cannot start (busy, missing, or
 * permission race) so callers can fall back to a non-camera mission.
 */
@Composable
fun PoseCameraPreview(
    useFrontCamera: Boolean,
    onSample: (PoseSample) -> Unit,
    onBindFailed: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
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

    LaunchedEffect(useFrontCamera) {
        // Heavy model load happens off the main thread; the camera preview
        // binds regardless so the user immediately sees themselves, and
        // counting starts as soon as the detector is ready.
        val modelOk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            analyzer.initialize()
        }
        if (!modelOk) {
            onBindFailed(
                IllegalStateException(
                    "Pose detector could not start" +
                        (analyzer.initError?.let { ": $it" } ?: ""),
                ),
            )
            return@LaunchedEffect
        }
        try {
            val provider = cameraProvider(context)
            provider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            val wantFront = useFrontCamera && hasCamera(provider, front = true)
            val selector = when {
                wantFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                hasCamera(provider, front = false) -> CameraSelector.DEFAULT_BACK_CAMERA
                hasCamera(provider, front = true) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> {
                    onBindFailed(IllegalStateException("No camera available"))
                    return@LaunchedEffect
                }
            }
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (e: Throwable) {
            onBindFailed(e)
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
