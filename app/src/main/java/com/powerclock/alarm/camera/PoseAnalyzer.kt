package com.powerclock.alarm.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.powerclock.alarm.domain.pose.BodyPoint
import com.powerclock.alarm.domain.pose.PosePoint
import com.powerclock.alarm.domain.pose.PoseSample

/**
 * Bridges CameraX frames to the on-device MediaPipe Pose Landmarker and
 * converts results into Power Clock's [PoseSample] model.
 *
 * Frames are processed in memory only: nothing is saved, recorded, or
 * uploaded, and no face recognition of any kind is performed.
 */
class PoseAnalyzer(
    context: Context,
    private val onSample: (PoseSample) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ImageAnalysis.Analyzer {

    // MediaPipe pose landmark indices for the joints we track.
    private val indexMap = mapOf(
        BodyPoint.NOSE to 0,
        BodyPoint.LEFT_SHOULDER to 11,
        BodyPoint.RIGHT_SHOULDER to 12,
        BodyPoint.LEFT_ELBOW to 13,
        BodyPoint.RIGHT_ELBOW to 14,
        BodyPoint.LEFT_WRIST to 15,
        BodyPoint.RIGHT_WRIST to 16,
        BodyPoint.LEFT_HIP to 23,
        BodyPoint.RIGHT_HIP to 24,
        BodyPoint.LEFT_KNEE to 25,
        BodyPoint.RIGHT_KNEE to 26,
        BodyPoint.LEFT_ANKLE to 27,
        BodyPoint.RIGHT_ANKLE to 28,
    )

    private val appContext = context.applicationContext

    @Volatile
    private var closed = false

    @Volatile
    private var landmarker: PoseLandmarker? = null

    @Volatile
    var initError: String? = null
        private set

    val isReady: Boolean get() = landmarker != null

    /**
     * Loads the pose model. Blocking (model load + TFLite init); call from a
     * background thread. Returns true on success. Safe to call repeatedly.
     */
    fun initialize(): Boolean {
        if (landmarker != null) return true
        if (closed) return false
        return try {
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("pose_landmarker_lite.task")
                        .build(),
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumPoses(1)
                .setResultListener { result, _ -> deliver(result) }
                .setErrorListener { e -> onError(e) }
                .build()
            landmarker = PoseLandmarker.createFromOptions(appContext, options)
            initError = null
            true
        } catch (e: Throwable) {
            initError = e.message ?: e.javaClass.simpleName
            onError(e)
            false
        }
    }

    override fun analyze(image: ImageProxy) {
        val lm = landmarker
        if (lm == null || closed) {
            // Model still loading (or failed): drop the frame quietly.
            image.close()
            return
        }
        try {
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
            lm.detectAsync(BitmapImageBuilder(upright).build(), SystemClock.uptimeMillis())
        } catch (e: Throwable) {
            onError(e)
        } finally {
            image.close()
        }
    }

    private fun deliver(result: PoseLandmarkerResult) {
        if (closed) return
        val landmarks = result.landmarks().firstOrNull()
        val points = mutableMapOf<BodyPoint, PosePoint>()
        if (landmarks != null) {
            for ((body, index) in indexMap) {
                val lm = landmarks.getOrNull(index) ?: continue
                points[body] = PosePoint(
                    x = lm.x(),
                    y = lm.y(),
                    visibility = lm.visibility().orElse(0f),
                )
            }
        }
        onSample(PoseSample(SystemClock.uptimeMillis(), points))
    }

    fun close() {
        closed = true
        try {
            landmarker?.close()
        } catch (_: Throwable) {
        }
    }
}
