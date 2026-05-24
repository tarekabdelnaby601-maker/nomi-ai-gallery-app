//com/example/mobilgaleri/util/Embeddings.kt
package com.example.mobilgaleri.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import kotlin.math.sqrt

/** L2 normalize (in-place). */
fun l2norm(v: FloatArray) {
    var n = 0f
    for (x in v) n += x * x
    val inv = 1f / (sqrt(n) + 1e-12f)
    for (i in v.indices) v[i] *= inv
}

/** Kozinüs benzerlik [-1,1]. */
fun cosSim(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vector dims must match" }
    var dot = 0f; var na = 0f; var nb = 0f
    for (i in a.indices) {
        val ai = a[i]; val bi = b[i]
        dot += ai * bi; na += ai * ai; nb += bi * bi
    }
    return dot / (sqrt(na) * sqrt(nb) + 1e-12f)
}

/** e = L2norm(e_orig + e_flip) */
fun averageEmbeddings(e1: FloatArray, e2: FloatArray): FloatArray {
    require(e1.size == e2.size)
    val out = FloatArray(e1.size) { i -> 0.5f*(e1[i] + e2[i]) }
    l2norm(out)
    return out
}

/** Bitmap’i yatay çevir. */
fun Bitmap.flipX(): Bitmap {
    val m = Matrix().apply {
        preScale(-1f, 1f)
        postTranslate(this@flipX.width.toFloat(), 0f)
    }
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(this, m, null)
    return out
}
