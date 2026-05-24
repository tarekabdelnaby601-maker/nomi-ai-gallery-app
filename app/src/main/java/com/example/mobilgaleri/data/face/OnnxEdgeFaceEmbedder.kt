package com.example.mobilgaleri.data.face

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * EdgeFace-XS (ArcFace) için ONNX embedder.
 * - NCHW, [-1,1] normalize, L2 norm
 * - Flip-ortalama seçeneği mevcut
 * - Bu sürümde ONNX CPU thread sayısı = 2 (mobil CPU için optimum)
 */
class OnnxEdgeFaceEmbedder(
    context: Context,
    private val inputSize: Int = 112
) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val sessionOptions: OrtSession.SessionOptions = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        // ✅ Android'de thread sayısını buradan kontrol ediyoruz
        setIntraOpNumThreads(2)   // model içi işlemlerde 2 çekirdek
        setInterOpNumThreads(1)   // modeller arası işlemler 1 thread
    }

    private val session: OrtSession
    private val inputName: String

    init {
        // Model yükle
        val modelBytes = context.assets.open("edgeface_xs_gamma_06_op18_single.onnx").use { it.readBytes() }
        Log.d("OnnxEdgeFace", "Loading ONNX (${modelBytes.size} bytes)")

        // Session başlat
        session = env.createSession(modelBytes, sessionOptions)
        inputName = session.inputNames.firstOrNull() ?: "input"
        Log.d("OnnxEdgeFace", "ONNX initialized | threads=2 | input=$inputName, outputs=${session.outputNames}")

        // Warm-up
        try {
            warmupOnce()
        } catch (t: Throwable) {
            Log.w("OnnxEdgeFace", "Warm-up failed (non-fatal): ${t.message}")
        }
    }

    fun embeddingDim(): Int = 512

    /** Zaten 112×112 hizalı yüz bekler. */
    fun embedAligned112(aligned112: Bitmap): FloatArray {
        val bmp = ensureArgbAndSize(aligned112, inputSize, inputSize)
        val input = toNCHWMinus1to1(bmp)

        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input),
            longArrayOf(1L, 3L, inputSize.toLong(), inputSize.toLong())
        ).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { results ->
                @Suppress("UNCHECKED_CAST")
                val emb = (results[0].value as Array<FloatArray>)[0]
                if (emb.size != 512) {
                    Log.w("OnnxEdgeFace", "Unexpected embedding size: ${emb.size} (expected 512)")
                }
                l2normInPlace(emb)
                return emb
            }
        }
    }

    /** Flip-averaging varyantı (opsiyonel). */
    fun embedAligned112FlipAvg(aligned112: Bitmap): FloatArray {
        val e = embedAligned112(aligned112)
        val flipped = aligned112.copy(Config.ARGB_8888, true).also { it.flipXInPlace() }
        val ef = try { embedAligned112(flipped) } finally { if (!flipped.isRecycled) flipped.recycle() }
        return averageEmbeddings(e, ef)
    }

    /** Kutu verildiğinde kırp + resize + embed. */
    fun embed(bitmap: Bitmap, faceBox: android.graphics.Rect): FloatArray {
        val face = safeCrop(bitmap, faceBox, padding = 0.15f)
        val resized = Bitmap.createScaledBitmap(face, inputSize, inputSize, true)
        val e = embedAligned112(resized)
        if (resized !== face && !resized.isRecycled) resized.recycle()
        if (!face.isRecycled) face.recycle()
        return e
    }

    // =========================
    //  Yardımcılar
    // =========================

    private fun toNCHWMinus1to1(bmp: Bitmap): FloatArray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val plane = w * h
        val out = FloatArray(3 * plane)

        // R
        var idx = 0
        for (i in 0 until h) {
            for (j in 0 until w) {
                val p = pixels[i * w + j]
                out[idx++] = ((p ushr 16) and 0xFF) / 127.5f - 1f
            }
        }
        // G
        var base = plane
        for (i in 0 until h) {
            for (j in 0 until w) {
                val p = pixels[i * w + j]
                out[base++] = ((p ushr 8) and 0xFF) / 127.5f - 1f
            }
        }
        // B
        base = 2 * plane
        for (i in 0 until h) {
            for (j in 0 until w) {
                val p = pixels[i * w + j]
                out[base++] = (p and 0xFF) / 127.5f - 1f
            }
        }
        return out
    }

    private fun l2normInPlace(v: FloatArray) {
        var s = 0f; for (x in v) s += x * x
        val inv = 1f / (sqrt(s) + 1e-12f)
        for (i in v.indices) v[i] *= inv
    }

    private fun safeCrop(src: Bitmap, box: android.graphics.Rect, padding: Float): Bitmap {
        val padX = (box.width() * padding).toInt()
        val padY = (box.height() * padding).toInt()
        val l = (box.left - padX).coerceAtLeast(0)
        val t = (box.top - padY).coerceAtLeast(0)
        val r = (box.right + padX).coerceAtMost(src.width)
        val b = (box.bottom + padY).coerceAtMost(src.height)
        return Bitmap.createBitmap(src, l, t, r - l, b - t)
    }

    private fun ensureArgbAndSize(src: Bitmap, w: Int, h: Int): Bitmap {
        val base = if (src.config == Config.ARGB_8888) src else src.copy(Config.ARGB_8888, false)
        return if (base.width == w && base.height == h) base
        else Bitmap.createScaledBitmap(base, w, h, true)
    }

    private fun Bitmap.flipXInPlace() {
        val w = width; val h = height
        val line = IntArray(w)
        for (y in 0 until h) {
            getPixels(line, 0, w, 0, y, w, 1)
            for (x in 0 until w / 2) {
                val t = line[x]
                line[x] = line[w - 1 - x]
                line[w - 1 - x] = t
            }
            setPixels(line, 0, w, 0, y, w, 1)
        }
    }

    private fun averageEmbeddings(a: FloatArray, b: FloatArray): FloatArray {
        val n = minOf(a.size, b.size)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = 0.5f * (a[i] + b[i])
        l2normInPlace(out)
        return out
    }

    private fun warmupOnce() {
        val dummy = Bitmap.createBitmap(inputSize, inputSize, Config.ARGB_8888)
        try {
            val e = embedAligned112(dummy)
            Log.d("OnnxEdgeFace", "Warm-up ok: dim=${e.size}")
        } finally {
            if (!dummy.isRecycled) dummy.recycle()
        }
    }

    override fun close() {
        try { session.close() } catch (_: Throwable) {}
        try { sessionOptions.close() } catch (_: Throwable) {}
    }
}
