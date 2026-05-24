package com.example.mobilgaleri.domain.usecase

import kotlin.math.sqrt

/**
 * Doğruluk pipeline'ı birebir korunmuş,
 * sadece similarity değerini dışarı döndürmek için
 * assignWithSim() eklenmiştir.
 */
data class ClusterAssignResult(
    val clusterId: Int,
    val similarity: Float
)

class ClusterFacesUseCase(
    private val threshold: Float = 0.82f,
    private val debug: Boolean = true
) {

    private val centroids = mutableListOf<FloatArray>()
    private val sizes = mutableListOf<Int>()

    // -------------------------------
    // L2 Normalizasyon
    // -------------------------------
    private fun l2norm(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val inv = if (s == 0f) 1f else 1f / sqrt(s)
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] * inv
        return out
    }

    private fun l2normInPlace(v: FloatArray) {
        var s = 0f
        for (x in v) s += x * x
        val inv = if (s == 0f) 1f else 1f / sqrt(s)
        for (i in v.indices) v[i] *= inv
    }

    // -------------------------------
    // Cosine Similarity (Turbo)
    // -------------------------------
    private fun cosineSim(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        val size = a.size
        var i = 0
        while (i < size) {
            val ax = a[i]
            val bx = b[i]
            dot += ax * bx
            na += ax * ax
            nb += bx * bx
            i++
        }
        val den = sqrt(na) * sqrt(nb)
        return if (den == 0f) 0f else dot / den
    }

    // ---------------------------------------------------
    // ORIGINAL assign() FONKSİYONU (Geriye dönük uyumluluk)
    // ---------------------------------------------------
    fun assign(embedding: FloatArray): Int {
        return assignWithSim(embedding).clusterId
    }

    // ---------------------------------------------------
    // YENİ: assignWithSim() → similarity + clusterId döner
    // ---------------------------------------------------
    fun assignWithSim(embedding: FloatArray): ClusterAssignResult {
        val x = l2norm(embedding)

        // İlk cluster
        if (centroids.isEmpty()) {
            if (debug) println("CLUSTER_DEBUG: İlk küme '0' oluşturuldu.")
            centroids.add(x.copyOf())
            sizes.add(1)
            return ClusterAssignResult(0, 1f)
        }

        // En yakın centroid'i bul
        var bestIdx = 0
        var bestSim = -1f
        val last = centroids.lastIndex

        for (i in 0..last) {
            val sim = cosineSim(x, centroids[i])
            if (sim > bestSim) {
                bestSim = sim
                bestIdx = i
            }
        }

        // Eşik geçildiyse mevcut kümeye ekle
        if (bestSim >= threshold) {
            if (debug) {
                println(
                    "CLUSTER_DEBUG: Bir yüz, mevcut küme $bestIdx'ye %.4f benzerlik skoruyla eklendi. (Eşik: $threshold)"
                        .format(bestSim)
                )
            }
            updateCentroid(bestIdx, x)
            return ClusterAssignResult(bestIdx, bestSim)
        }

        // Eşik geçilmediyse yeni cluster
        val newClusterId = centroids.size
        if (debug) {
            println(
                "CLUSTER_DEBUG: En iyi benzerlik (%.4f) eşiği geçemedi. Yeni küme $newClusterId oluşturuldu."
                    .format(bestSim)
            )
        }

        centroids.add(x.copyOf())
        sizes.add(1)
        return ClusterAssignResult(newClusterId, bestSim)
    }

    // ---------------------------------------------------
    // Çoklu assign (Hızlı)
    // ---------------------------------------------------
    fun assignAll(embeddings: List<FloatArray>): List<Int> {
        return embeddings.map { assign(it) }
    }

    // ---------------------------------------------------
    // Centroid güncelleme
    // ---------------------------------------------------
    private fun updateCentroid(idx: Int, x: FloatArray) {
        val oldSize = sizes[idx]
        val newSize = oldSize + 1
        val c = centroids[idx]

        // Weighted average
        for (k in c.indices) {
            c[k] = (c[k] * oldSize + x[k]) / newSize
        }

        // Normalize
        l2normInPlace(c)

        sizes[idx] = newSize
    }

    fun getCentroid(personId: Int): FloatArray? {
        return centroids.getOrNull(personId)
    }
}
