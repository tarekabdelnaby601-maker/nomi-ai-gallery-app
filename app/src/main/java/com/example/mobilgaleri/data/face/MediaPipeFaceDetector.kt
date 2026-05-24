//com/example/mobilgaleri/data/face/MediaPipeFaceDetector.kt
package com.example.mobilgaleri.data.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.components.containers.Category

data class MediaPipeFaceResult(
    val boundingBox: Rect,
    val rightEye: Pair<Float, Float>? = null,
    val leftEye: Pair<Float, Float>? = null,
    val noseTip: Pair<Float, Float>? = null,
    val mouthCenter: Pair<Float, Float>? = null,
    val rightEarTragion: Pair<Float, Float>? = null,
    val leftEarTragion: Pair<Float, Float>? = null,
    val detectionConfidence: Float? = null
)

class MediaPipeFaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "MediaPipeFaceDetector"
        private const val MODEL_ASSET_PATH = "face_detection_short_range.tflite"

        private const val RIGHT_EYE_INDEX = 0
        private const val LEFT_EYE_INDEX = 1
        private const val NOSE_TIP_INDEX = 2
        private const val MOUTH_CENTER_INDEX = 3
        private const val RIGHT_EAR_TRAGION_INDEX = 4
        private const val LEFT_EAR_TRAGION_INDEX = 5
    }

    private val faceDetector: FaceDetector by lazy {
        Log.d(TAG, "Initializing Face Detector...")

        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()

        val opts = FaceDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(0.35f)    // küçük yüzleri kaçırmaz
            .setMinSuppressionThreshold(0.2f)    // very small tweak, accuracy same
            .build()

        FaceDetector.createFromOptions(context, opts)
    }

    suspend fun detect(bitmap: Bitmap): List<MediaPipeFaceResult> =
        withContext(Dispatchers.Default) {
            try {
                val mp = BitmapImageBuilder(bitmap).build()
                val res = faceDetector.detect(mp)
                mapResultsFast(res)
            } catch (e: Throwable) {
                Log.e(TAG, "detect() failed: ${e.localizedMessage}")
                emptyList()
            }
        }

    /**
     * Turbo mapper — minimum GC, minimum check.
     */
    private fun mapResultsFast(result: FaceDetectorResult?): List<MediaPipeFaceResult> {
        if (result == null) return emptyList()

        val out = ArrayList<MediaPipeFaceResult>(result.detections().size)

        for (det in result.detections()) {
            val b = det.boundingBox() ?: continue
            val rect = Rect(
                b.left.toInt().coerceAtLeast(0),
                b.top.toInt().coerceAtLeast(0),
                b.right.toInt(),
                b.bottom.toInt()
            )
            if (rect.width() <= 0 || rect.height() <= 0) continue

            val keypoints = det.keypoints().orElse(emptyList())

            fun kp(i: Int): Pair<Float, Float>? {
                if (i >= keypoints.size) return null
                val p = keypoints[i]
                return Pair(p.x(), p.y())
            }

            val res = MediaPipeFaceResult(
                boundingBox = rect,
                rightEye = kp(RIGHT_EYE_INDEX),
                leftEye = kp(LEFT_EYE_INDEX),
                noseTip = kp(NOSE_TIP_INDEX),
                mouthCenter = kp(MOUTH_CENTER_INDEX),
                rightEarTragion = kp(RIGHT_EAR_TRAGION_INDEX),
                leftEarTragion = kp(LEFT_EAR_TRAGION_INDEX),
                detectionConfidence = det.categories()?.firstOrNull()?.score()
            )
            out.add(res)
        }

        return out
    }
    fun close() {
        runCatching { faceDetector.close() }
    }

}
