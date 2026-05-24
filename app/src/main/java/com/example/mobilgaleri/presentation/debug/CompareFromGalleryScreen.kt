//com/example/mobilgaleri/presentation/debug/CompareFromGalleryScreen.kt
package com.example.mobilgaleri.presentation.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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

@Composable
fun CompareFromGalleryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var uri1 by remember { mutableStateOf<Uri?>(null) }
    var uri2 by remember { mutableStateOf<Uri?>(null) }
    var bmp1 by remember { mutableStateOf<Bitmap?>(null) }
    var bmp2 by remember { mutableStateOf<Bitmap?>(null) }

    var status by remember { mutableStateOf("İki foto seçiniz.") }
    var score by remember { mutableFloatStateOf(0f) }
    var verdict by remember { mutableStateOf("?") }

    val pick1 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri1 = uri
        bmp1 = uri?.let { decodeUriToBitmap(ctx, it) }
    }
    val pick2 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri2 = uri
        bmp2 = uri?.let { decodeUriToBitmap(ctx, it) }
    }

    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(160.dp), contentAlignment = Alignment.Center) {
                    if (bmp1 != null) {
                        Image(bmp1!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxHeight())
                    } else Text("Foto 1")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pick1.launch("image/*") }) { Text("Foto 1 Seç") }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(160.dp), contentAlignment = Alignment.Center) {
                    if (bmp2 != null) {
                        Image(bmp2!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxHeight())
                    } else Text("Foto 2")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { pick2.launch("image/*") }) { Text("Foto 2 Seç") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text("cosSim: $score   →   karar: $verdict", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        Button(
            enabled = (bmp1 != null && bmp2 != null),
            onClick = {
                scope.launch(Dispatchers.Default) {
                    try {
                        status = "Landmarks & hizalama…"
                        val lm = MediaPipeFaceLandmarker(ctx)

                        // 🔥 YENİ API: detectPoints()
                        val (five1, three1) = lm.detectPoints(bmp1!!)
                        val (five2, three2) = lm.detectPoints(bmp2!!)
                        lm.close()

                        require(five1 != null || three1 != null) { "Foto 1 yüzü bulunamadı." }
                        require(five2 != null || three2 != null) { "Foto 2 yüzü bulunamadı." }

                        // 🔥 Priority: Five → Three → fallback
                        val a1 = when {
                            five1 != null -> alignFace112FiveOrThree(
                                bmp1!!,
                                leftEye = five1.leftEye.x to five1.leftEye.y,
                                rightEye = five1.rightEye.x to five1.rightEye.y,
                                nose = five1.noseTip.x to five1.noseTip.y,
                                mouthLeft = five1.mouthLeft.x to five1.mouthLeft.y,
                                mouthRight = five1.mouthRight.x to five1.mouthRight.y,
                                margin = 1.10f
                            ).bitmap

                            three1 != null -> alignFace112FiveOrThree(
                                bmp1!!,
                                leftEye = three1.leftEye.x to three1.leftEye.y,
                                rightEye = three1.rightEye.x to three1.rightEye.y,
                                nose = three1.noseTip.x to three1.noseTip.y,
                                mouthLeft = null,
                                mouthRight = null,
                                margin = 1.10f
                            ).bitmap

                            else -> Bitmap.createScaledBitmap(bmp1!!, 112, 112, true)
                        }

                        val a2 = when {
                            five2 != null -> alignFace112FiveOrThree(
                                bmp2!!,
                                leftEye = five2.leftEye.x to five2.leftEye.y,
                                rightEye = five2.rightEye.x to five2.rightEye.y,
                                nose = five2.noseTip.x to five2.noseTip.y,
                                mouthLeft = five2.mouthLeft.x to five2.mouthLeft.y,
                                mouthRight = five2.mouthRight.x to five2.mouthRight.y,
                                margin = 1.10f
                            ).bitmap

                            three2 != null -> alignFace112FiveOrThree(
                                bmp2!!,
                                leftEye = three2.leftEye.x to three2.leftEye.y,
                                rightEye = three2.rightEye.x to three2.rightEye.y,
                                nose = three2.noseTip.x to three2.noseTip.y,
                                mouthLeft = null,
                                mouthRight = null,
                                margin = 1.10f
                            ).bitmap

                            else -> Bitmap.createScaledBitmap(bmp2!!, 112, 112, true)
                        }

                        status = "AdaFace (flip-ortalama)…"
                        val emb = TfliteEmbedder(ctx, "adaface_ir18.tflite")

                        val avg1 = run {
                            val e = emb.embedAligned112(a1)
                            val ef = emb.embedAligned112(a1.flipX())
                            averageEmbeddings(e, ef)
                        }
                        val avg2 = run {
                            val e = emb.embedAligned112(a2)
                            val ef = emb.embedAligned112(a2.flipX())
                            averageEmbeddings(e, ef)
                        }

                        val thr = 0.60f
                        score = cosSim(avg1, avg2)
                        verdict = if (score >= thr) "Aynı kişi ✅" else "Farklı kişi ❌"
                        status = "Bitti ✅ (eşik=$thr)"

                    } catch (t: Throwable) {
                        status = "Hata: ${t.message}"
                        verdict = "?"
                        score = 0f
                    }
                }
            }
        ) { Text("Karşılaştır") }
    }
}

private fun decodeUriToBitmap(ctx: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}
