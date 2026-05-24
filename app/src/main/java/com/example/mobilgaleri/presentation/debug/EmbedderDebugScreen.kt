// com/example/mobilgaleri/presentation/debug/EmbedderDebugScreen.kt
package com.example.mobilgaleri.presentation.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobilgaleri.data.face.MediaPipeFaceLandmarker
import com.example.mobilgaleri.data.face.TfliteEmbedder
import com.example.mobilgaleri.util.alignFace112FiveOrThree
import com.example.mobilgaleri.util.averageEmbeddings
import com.example.mobilgaleri.util.cosSim
import com.example.mobilgaleri.util.flipX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "EmbedderDebugScreen"
private const val THRESHOLD = 0.60f

@Composable
fun EmbedderDebugScreen() {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Hazır") }
    var lastCos by remember { mutableFloatStateOf(0f) }
    var verdict by remember { mutableStateOf("?") }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(16.dp)) {
        Text(status)
        Text("cosSim: $lastCos  →  karar: $verdict (T=$THRESHOLD)")

        Button(onClick = {
            scope.launch(Dispatchers.Default) {

                fun say(step: String) { status = step; Log.d(TAG, step) }

                try {
                    // 0) Asset kontrolü
                    val assets = ctx.assets.list("")!!.toList()
                    require("adaface_ir18.tflite" in assets)
                    require("face_landmarker.task" in assets)
                    require("test_face1.jpg" in assets)
                    require("test_face2.jpg" in assets)

                    say("Resimler yükleniyor…")
                    val b1 = loadBitmap(ctx, "test_face1.jpg")
                    val b2 = loadBitmap(ctx, "test_face2.jpg")

                    say("Landmark (5/3 otomatik)…")
                    val lm = MediaPipeFaceLandmarker(ctx)
                    val (five1, three1) = lm.detectPoints(b1)
                    val (five2, three2) = lm.detectPoints(b2)
                    lm.close()

                    require(five1 != null || three1 != null) { "test_face1: landmark yok" }
                    require(five2 != null || three2 != null) { "test_face2: landmark yok" }

                    say("Hizalama 112×112…")

                    // 🔥 1. foto hizalama
                    val a1 = when {
                        five1 != null -> alignFace112FiveOrThree(
                            b1,
                            leftEye = five1.leftEye.x to five1.leftEye.y,
                            rightEye = five1.rightEye.x to five1.rightEye.y,
                            nose = five1.noseTip.x to five1.noseTip.y,
                            mouthLeft = five1.mouthLeft.x to five1.mouthLeft.y,
                            mouthRight = five1.mouthRight.x to five1.mouthRight.y,
                            margin = 1.10f
                        ).bitmap

                        three1 != null -> alignFace112FiveOrThree(
                            b1,
                            leftEye = three1.leftEye.x to three1.leftEye.y,
                            rightEye = three1.rightEye.x to three1.rightEye.y,
                            nose = three1.noseTip.x to three1.noseTip.y,
                            mouthLeft = null,
                            mouthRight = null,
                            margin = 1.10f
                        ).bitmap

                        else -> Bitmap.createScaledBitmap(b1, 112, 112, true)
                    }

                    // 🔥 2. foto hizalama
                    val a2 = when {
                        five2 != null -> alignFace112FiveOrThree(
                            b2,
                            leftEye = five2.leftEye.x to five2.leftEye.y,
                            rightEye = five2.rightEye.x to five2.rightEye.y,
                            nose = five2.noseTip.x to five2.noseTip.y,
                            mouthLeft = five2.mouthLeft.x to five2.mouthLeft.y,
                            mouthRight = five2.mouthRight.x to five2.mouthRight.y,
                            margin = 1.10f
                        ).bitmap

                        three2 != null -> alignFace112FiveOrThree(
                            b2,
                            leftEye = three2.leftEye.x to three2.leftEye.y,
                            rightEye = three2.rightEye.x to three2.rightEye.y,
                            nose = three2.noseTip.x to three2.noseTip.y,
                            mouthLeft = null,
                            mouthRight = null,
                            margin = 1.10f
                        ).bitmap

                        else -> Bitmap.createScaledBitmap(b2, 112, 112, true)
                    }

                    say("AdaFace çalışıyor…")
                    val emb = TfliteEmbedder(ctx, "adaface_ir18.tflite")

                    // 🔥 flip-average
                    val avg1 = averageEmbeddings(
                        emb.embedAligned112(a1),
                        emb.embedAligned112(a1.flipX())
                    )

                    val avg2 = averageEmbeddings(
                        emb.embedAligned112(a2),
                        emb.embedAligned112(a2.flipX())
                    )

                    lastCos = cosSim(avg1, avg2)
                    verdict = if (lastCos >= THRESHOLD) "Aynı kişi ✅" else "Farklı kişi ❌"

                    say("Bitti ✓")

                } catch (t: Throwable) {
                    val msg = t.message ?: "Hata"
                    status = "Hata: $msg"
                    verdict = "?"
                    lastCos = 0f
                    Log.e(TAG, "ERR", t)
                }
            }
        }) { Text("Embed Testi Çalıştır") }
    }
}

private fun loadBitmap(ctx: Context, name: String): Bitmap {
    ctx.assets.open(name).use { inp ->
        val bytes = inp.readBytes()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Decode edilemedi: $name")
    }
}
