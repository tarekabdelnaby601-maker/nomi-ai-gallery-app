package com.example.mobilgaleri.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.sqrt

/** ArcFace 112×112 canonical 5-nokta (px) */
private val ARC112 = floatArrayOf(
    38.2946f, 51.6963f,   // left eye
    73.5318f, 51.5014f,   // right eye
    56.0252f, 71.7366f,   // nose
    41.5493f, 92.3655f,   // mouth left
    70.7299f, 92.2041f    // mouth right
)

/** Umeyama: 5 karşılık noktadan similarity (s,R,t) -> Android Matrix. */
private fun estimateSimilarity5(src5: FloatArray, dst5: FloatArray): Matrix? {
    if (src5.size != 10 || dst5.size != 10) return null

    var mxs = 0f; var mys = 0f; var mxd = 0f; var myd = 0f
    for (k in 0 until 5) {
        mxs += src5[2*k]; mys += src5[2*k+1]
        mxd += dst5[2*k]; myd += dst5[2*k+1]
    }
    mxs /= 5f; mys /= 5f; mxd /= 5f; myd /= 5f

    val xs = FloatArray(5); val ys = FloatArray(5)
    val xd = FloatArray(5); val yd = FloatArray(5)
    for (k in 0 until 5) {
        xs[k] = src5[2*k] - mxs
        ys[k] = src5[2*k+1] - mys
        xd[k] = dst5[2*k] - mxd
        yd[k] = dst5[2*k+1] - myd
    }

    var sxx = 0f; var sxy = 0f; var syx = 0f; var syy = 0f
    var ns = 0f; var nd = 0f
    for (k in 0 until 5) {
        sxx += xs[k]*xd[k]; sxy += xs[k]*yd[k]
        syx += ys[k]*xd[k]; syy += ys[k]*yd[k]
        ns += xs[k]*xs[k] + ys[k]*ys[k]
        nd += xd[k]*xd[k] + yd[k]*yd[k]
    }
    if (ns < 1e-6f) return null

    val a = (sxx + syy) / ns
    val b = (sxy - syx) / ns
    val ra = a
    val rb = b
    val tx = mxd - (ra*mxs - rb*mys)
    val ty = myd - (rb*mxs + ra*mys)

    return Matrix().apply {
        setValues(
            floatArrayOf(
                ra, -rb, tx,
                rb,  ra, ty,
                0f,  0f, 1f
            )
        )
    }
}

/** Gözler + burun ile 3 nokta affine hizalama */
private fun alignFace112Three(
    src: Bitmap,
    leftEye: Pair<Float, Float>,
    rightEye: Pair<Float, Float>,
    nose: Pair<Float, Float>,
    margin: Float
): Bitmap {
    val dst = floatArrayOf(
        ARC112[0], ARC112[1],   // left eye
        ARC112[2], ARC112[3],   // right eye
        ARC112[4], ARC112[5]    // nose
    )
    val frm = floatArrayOf(
        leftEye.first, leftEye.second,
        rightEye.first, rightEye.second,
        nose.first, nose.second
    )
    val M = Matrix().apply { setPolyToPoly(frm, 0, dst, 0, 3) }
    if (margin != 1.0f) M.postScale(margin, margin, 56f, 56f)

    val out = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, M, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** Sonuç objesi (5-nokta mı 3-nokta mı kullanıldı?) */
data class AlignResult(val bitmap: Bitmap, val usedFivePoint: Boolean)

/**
 * 🔹 Genel hizalama fonksiyonu:
 *  - 5 nokta varsa similarity ile hizalar.
 *  - 5 nokta tahmini başarısızsa, 3 nokta (göz + burun) affine’e düşer.
 *  - mouthLeft / mouthRight null olabilir.
 */
fun alignFace112FiveOrThree(
    src: Bitmap,
    leftEye: Pair<Float, Float>,
    rightEye: Pair<Float, Float>,
    nose: Pair<Float, Float>,
    mouthLeft: Pair<Float, Float>? = null,
    mouthRight: Pair<Float, Float>? = null,
    margin: Float = 1.0f
): AlignResult {
    return if (mouthLeft != null && mouthRight != null) {
        // --- 5 nokta hizalama ---
        val srcPts = floatArrayOf(
            leftEye.first, leftEye.second,
            rightEye.first, rightEye.second,
            nose.first, nose.second,
            mouthLeft.first, mouthLeft.second,
            mouthRight.first, mouthRight.second
        )
        val dstPts = ARC112.copyOf()

        val M = estimateSimilarity5(srcPts, dstPts)
        if (M != null) {
            if (margin != 1.0f) M.postScale(margin, margin, 56f, 56f)
            val out = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(src, M, Paint(Paint.FILTER_BITMAP_FLAG))
            AlignResult(out, true)
        } else {
            AlignResult(
                alignFace112Three(src, leftEye, rightEye, nose, margin),
                false
            )
        }
    } else {
        // --- Ağız noktaları yoksa doğrudan 3 nokta hizalama ---
        AlignResult(
            alignFace112Three(src, leftEye, rightEye, nose, margin),
            false
        )
    }
}
