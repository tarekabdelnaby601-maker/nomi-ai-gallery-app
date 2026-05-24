package com.example.mobilgaleri.bench

import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.example.mobilgaleri.data.face.TfliteEmbedder
import com.example.mobilgaleri.data.face.MediaPipeFaceDetector              // MediaPipe DETECT
import com.example.mobilgaleri.data.face.MediaPipeFaceLandmarker          // MediaPipe 5-POINT
import com.example.mobilgaleri.util.alignFace112FiveOrThree               // ArcFace 112 hizalama
import com.example.mobilgaleri.util.averageEmbeddings                     // flip-ortalama
import com.example.mobilgaleri.util.flipX                                 // yatay çevir
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

// ===================== Kullanıcı ayarları =====================
private object EvalCfg {
    // Decode/resize guard (OOM koruması)
    const val CAP_SHORT = 1280
    const val CAP_LONG  = 2560
    const val CAP_MAX_MP = 3_000_000

    // AdaFace cosine eşiği (UI’dan değiştirilir)
    var SIM_THR = 0.80f    // AdaFace + hizalama + flip için iyi başlangıç
    var MIN_DEG = 1        // düğüm derecesi < MIN_DEG ⇒ “noise”
}

// ===================== Yardımcılar =====================
private fun comb2(n: Long): Long = if (n >= 2) n * (n - 1) / 2 else 0
private fun copyToDownloads(context: Context, src: File, displayName: String, mimeType: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BenchReports")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        } else {
            val publicDl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outDir = File(publicDl, "BenchReports").apply { mkdirs() }
            val outFile = File(outDir, displayName)
            try {
                src.inputStream().use { it.copyTo(outFile.outputStream()) }
            } catch (_: Exception) {
                val appDir = File(context.getExternalFilesDir(null), "BenchReports").apply { mkdirs() }
                val appOut = File(appDir, displayName)
                src.copyTo(appOut, overwrite = true)
            }
        }
    } catch (_: Throwable) { /* no-op */ }
}
private fun defaultInitialTree(): Uri =
    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")

private fun isRestrictedTree(uri: Uri): Boolean {
    val auth = uri.authority ?: return false
    val docId = DocumentsContract.getTreeDocumentId(uri) ?: return false
    if (auth == "com.android.providers.downloads.documents") return true
    if (docId.startsWith("primary:Download") || docId.startsWith("home:Downloads")) return true
    if (docId.startsWith("primary:Android/obb") || docId.startsWith("primary:Android/data")) return true
    if (!docId.contains(":")) return true
    return false
}

private fun ContentResolver.listImagesInTree(tree: Uri): List<Pair<Uri, String>> {
    val out = mutableListOf<Pair<Uri, String>>()
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(
        tree, DocumentsContract.getTreeDocumentId(tree)
    )
    val proj = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    query(children, proj, null, null, null)?.use { c ->
        val iId = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val iName = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val iMime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (c.moveToNext()) {
            val id = c.getString(iId)
            val name = c.getString(iName) ?: id
            val mime = c.getString(iMime) ?: ""
            if (mime.startsWith("image/") ||
                name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true)
            ) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                out += uri to name
            }
        }
    }
    return out.sortedBy { it.second.lowercase() }
}

private fun parseGtLabelFromName(name: String): String {
    // "Zac Efron_76.jpg" -> "Zac Efron"
    val base = name.substringBeforeLast('.', name)
    val k = base.lastIndexOf('_')
    return if (k <= 0) base.trim() else base.substring(0, k).trim()
}

private fun rotateIfNeeded(bmp: Bitmap, exif: ExifInterface?): Bitmap {
    if (exif == null) return bmp
    val rot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    if (rot == 0) return bmp
    val m = Matrix().apply { postRotate(rot.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

private fun resizeForDetect(bmp: Bitmap): Bitmap {
    val w = bmp.width
    val h = bmp.height
    var scale = 1.0
    scale = min(scale, EvalCfg.CAP_SHORT.toDouble() / min(w, h))
    scale = min(scale, EvalCfg.CAP_LONG.toDouble()  / max(w, h))
    val mp = w.toLong() * h.toLong().toDouble()
    scale = min(scale, sqrt(EvalCfg.CAP_MAX_MP.toDouble() / mp))
    if (scale >= 1.0) return bmp
    val nw = max(1, (w * scale).toInt())
    val nh = max(1, (h * scale).toInt())
    return Bitmap.createScaledBitmap(bmp, nw, nh, true)
}

private fun safeDecodeBitmap(cr: ContentResolver, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(cr, uri)
        val decoded = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.setOnPartialImageListener { true }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val exif = cr.openInputStream(uri)?.use { ExifInterface(it) }
        rotateIfNeeded(decoded, exif)
    } else {
        cr.openInputStream(uri)?.use { s ->
            val raw = BitmapFactory.decodeStream(s) ?: return@use null
            val exif = cr.openInputStream(uri)?.use { ExifInterface(it) }
            rotateIfNeeded(raw, exif)
        }
    }
} catch (_: Throwable) { null }

// ===================== Kümeleme: Cosine-threshold + Union-Find =====================
private class DSU(n: Int) {
    private val p = IntArray(n) { it }
    private val r = IntArray(n)
    fun find(x: Int): Int {
        var a = x
        while (p[a] != a) { p[a] = p[p[a]]; a = p[a] }
        return a
    }
    fun union(a0: Int, b0: Int) {
        var a = find(a0); var b = find(b0)
        if (a == b) return
        if (r[a] < r[b]) { val t = a; a = b; b = t }
        p[b] = a
        if (r[a] == r[b]) r[a]++
    }
}

// ===================== Metrikler =====================
private data class Metrics(
    val b3P: Double, val b3R: Double, val b3F1: Double,
    val pwP: Double, val pwR: Double, val pwF1: Double,
    val ari: Double, val nmi: Double,
    val majAcc: Double,
    val kPred: Int, val kTrue: Int, val n: Int
)

private fun computeMetrics(gt: IntArray, pred: IntArray, noiseClusterId: Int? = null): Metrics {
    val n = gt.size
    require(pred.size == n)

    val use = BooleanArray(n) { true }
    if (noiseClusterId != null) for (i in 0 until n) if (pred[i] == noiseClusterId) use[i] = false
    val idx = IntArray(n); var m = 0
    for (i in 0 until n) if (use[i]) { idx[m++] = i }
    if (m == 0) return Metrics(0.0,0.0,0.0, 0.0,0.0,0.0, 0.0,0.0, 0.0, 0, 0, 0)

    fun remap(a: IntArray, takeIdx: IntArray, m: Int): Pair<IntArray, Int> {
        val map = HashMap<Int, Int>()
        val out = IntArray(m)
        var k = 0
        for (t in 0 until m) {
            val v = a[takeIdx[t]]
            val id = map.getOrPut(v) { k++ }
            out[t] = id
        }
        return out to k
    }

    val (gtR, L) = remap(gt, idx, m)
    val (pdR, K) = remap(pred, idx, m)

    val cont = Array(L) { LongArray(K) }
    val row = LongArray(L)
    val col = LongArray(K)
    for (t in 0 until m) {
        val i = gtR[t]; val j = pdR[t]
        cont[i][j]++; row[i]++; col[j]++
    }

    var b3P = 0.0; var b3R = 0.0
    for (i in 0 until L) for (j in 0 until K) {
        val nij = cont[i][j].toDouble()
        if (nij > 0) {
            b3P += nij * (nij / col[j])
            b3R += nij * (nij / row[i])
        }
    }
    b3P /= m; b3R /= m
    val b3F1 = if (b3P + b3R == 0.0) 0.0 else 2 * b3P * b3R / (b3P + b3R)

    val sumCij2 = cont.sumOf { r -> r.sumOf { comb2(it) } }
    val sumRow2 = row.sumOf { comb2(it) }
    val sumCol2 = col.sumOf { comb2(it) }
    val totalPairs = comb2(m.toLong())
    val TP = sumCij2.toDouble()
    val FP = (sumCol2 - sumCij2).toDouble()
    val FN = (sumRow2 - sumCij2).toDouble()

    val pwP = if (TP + FP == 0.0) 0.0 else TP / (TP + FP)
    val pwR = if (TP + FN == 0.0) 0.0 else TP / (TP + FN)
    val pwF1 = if (pwP + pwR == 0.0) 0.0 else 2 * pwP * pwR / (pwP + pwR)

    val ariNum = TP - (sumRow2.toDouble() * sumCol2.toDouble()) / totalPairs
    val ariDen = 0.5 * (sumRow2 + sumCol2).toDouble() - (sumRow2.toDouble() * sumCol2.toDouble()) / totalPairs
    val ari = if (ariDen == 0.0) 0.0 else ariNum / ariDen

    val N = m.toDouble()
    var mi = 0.0
    var hT = 0.0
    var hP = 0.0
    for (i in 0 until L) if (row[i] > 0) { val pi = row[i] / N; hT -= pi * ln(pi) }
    for (j in 0 until K) if (col[j] > 0) { val pj = col[j] / N; hP -= pj * ln(pj) }
    for (i in 0 until L) for (j in 0 until K) {
        val nij = cont[i][j].toDouble()
        if (nij > 0) {
            val p = nij / N
            val pi = row[i] / N
            val pj = col[j] / N
            mi += p * ln(p / (pi * pj))
        }
    }
    val nmi = if (hT == 0.0 || hP == 0.0) 0.0 else mi / sqrt(hT * hP)

    var match = 0L
    for (j in 0 until K) {
        var best = 0L
        for (i in 0 until L) best = max(best, cont[i][j])
        match += best
    }
    val majAcc = match.toDouble() / m

    return Metrics(b3P,b3R,b3F1, pwP,pwR,pwF1, ari, nmi, majAcc, K, L, m)
}

// ===================== Veri yapıları =====================
private data class Sample(val uri: Uri, val name: String, val gt: String)
private data class Item(val name: String, val gtIdx: Int, val vec: FloatArray)

// ===================== Activity & UI =====================
class ClusterEvalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClusterEvalScreen(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClusterEvalScreen(host: ComponentActivity) {
    val ctx = host
    var tree by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf("Hazır") }
    var simThr by remember { mutableStateOf(EvalCfg.SIM_THR) }
    var minDeg by remember { mutableStateOf(EvalCfg.MIN_DEG) }
    val scope = rememberCoroutineScope()

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (isRestrictedTree(uri)) {
                status = "Bu klasör SAF ile kullanılamaz. Lütfen Documents/Pictures altında bir klasör seçin."
                return@rememberLauncherForActivityResult
            }
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { ctx.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Throwable) {}
            tree = uri; status = "Klasör seçildi ✓"
        }
    }

    MaterialTheme {
        Column(Modifier.padding(16.dp)) {
            Text("Cluster Evaluation", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Row {
                Button(onClick = { pickTree.launch(defaultInitialTree()) }) {
                    Text(if (tree == null) "Veri klasörünü seç" else "Klasör ✓")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sim ≥ "); Spacer(Modifier.width(8.dp))
                Slider(
                    value = simThr, onValueChange = { simThr = it },
                    // AdaFace için daha dar ve gerçekçi aralık
                    valueRange = 0.70f..0.90f, steps = 40, modifier = Modifier.weight(1f)
                )
                Text(String.format("%.2f", simThr))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MinDegree"); Spacer(Modifier.width(8.dp))
                Slider(
                    value = minDeg.toFloat(),
                    onValueChange = { minDeg = it.roundToInt() },
                    valueRange = 0f..5f, steps = 4, modifier = Modifier.weight(1f)
                )
                Text("$minDeg")
            }

            Spacer(Modifier.height(12.dp))

            Button(enabled = tree != null, onClick = {
                scope.launch {
                    EvalCfg.SIM_THR = simThr
                    EvalCfg.MIN_DEG = minDeg
                    status = "Çalışıyor…"
                    try {
                        val reportPath = withContext(Dispatchers.Default) {
                            runEval(ctx, tree!!)
                        }
                        status = "Bitti ✓  Rapor: $reportPath\n(Assignments CSV aynı klasörde)"
                    } catch (t: Throwable) {
                        status = "Hata: ${t.message}"
                    }
                }
            }) { Text("Başlat") }

            Spacer(Modifier.height(12.dp))
            Text(status)
        }
    }
}

// ===================== Çekirdek değerlendirme =====================
private suspend fun runEval(context: Context, tree: Uri): String = withContext(Dispatchers.IO) {
    val cr = context.contentResolver

    // MediaPipe + AdaFace (debug hattıyla birebir)
    val detector = MediaPipeFaceDetector(context)
    val landmarker = MediaPipeFaceLandmarker(context)
    val embedder = TfliteEmbedder(context, "adaface_ir18.tflite")

    // 1) Dosyaları oku + GT etiketleri
    val files = cr.listImagesInTree(tree)
    require(files.isNotEmpty()) { "Klasör boş." }
    val samples = files.map { Sample(it.first, it.second, parseGtLabelFromName(it.second)) }

    val gtToIdx = LinkedHashMap<String, Int>()
    fun gtIndex(s: String): Int = gtToIdx.getOrPut(s) { gtToIdx.size }

    // 2) Görsel → embedding
    val items = ArrayList<Item>(samples.size)
    var noFace = 0; var decodeErr = 0
    for ((_, s) in samples.withIndex()) {
        val bmp0 = safeDecodeBitmap(cr, s.uri)
        if (bmp0 == null) { decodeErr++; continue }

        val bmp = resizeForDetect(bmp0)

        // MediaPipe detect
        val mpFaces = try { detector.detect(bmp) } catch (_: Throwable) {
            if (bmp !== bmp0) bmp.recycle(); bmp0.recycle(); noFace++; continue
        }
        val largest = mpFaces.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height().toLong() }

        if (largest == null) {
            noFace++
            if (bmp !== bmp0) bmp.recycle(); bmp0.recycle()
            continue
        }

        // ROI (bbox + küçük padding)
        val detBox = largest.boundingBox
        val padX = (detBox.width() * 0.12f).toInt()
        val padY = (detBox.height() * 0.12f).toInt()
        val l = (detBox.left - padX).coerceAtLeast(0)
        val t = (detBox.top - padY).coerceAtLeast(0)
        val r = (detBox.right + padX).coerceAtMost(bmp.width)
        val b = (detBox.bottom + padY).coerceAtMost(bmp.height)
        if (r <= l || b <= t) {
            noFace++
            if (bmp !== bmp0) bmp.recycle(); bmp0.recycle()
            continue
        }
        val roi = Bitmap.createBitmap(bmp, l, t, r - l, b - t)

        // 5-nokta landmark → 112×112 hizalama (fallback: basit crop)
        val (five, three) = landmarker.detectPoints(roi)

        val aligned112 = when {
            five != null -> {
                alignFace112FiveOrThree(
                    src = roi,
                    leftEye    = five.leftEye.x    to five.leftEye.y,
                    rightEye   = five.rightEye.x   to five.rightEye.y,
                    nose       = five.noseTip.x    to five.noseTip.y,
                    mouthLeft  = five.mouthLeft.x  to five.mouthLeft.y,
                    mouthRight = five.mouthRight.x to five.mouthRight.y,
                    margin     = 1.10f
                ).bitmap
            }

            three != null -> {
                alignFace112FiveOrThree(
                    src = roi,
                    leftEye    = three.leftEye.x  to three.leftEye.y,
                    rightEye   = three.rightEye.x to three.rightEye.y,
                    nose       = three.noseTip.x  to three.noseTip.y,
                    mouthLeft  = null,
                    mouthRight = null,
                    margin     = 1.10f
                ).bitmap
            }

            else -> {
                // landmark yok → basic crop
                Bitmap.createScaledBitmap(roi, 112, 112, true)
            }
        }


        // AdaFace + flip-ortalama
        val e  = embedder.embedAligned112(aligned112)
        val ef = embedder.embedAligned112(aligned112.flipX())
        val v  = averageEmbeddings(e, ef)

        if (bmp !== bmp0) bmp.recycle()
        bmp0.recycle()
        roi.recycle()
        aligned112.recycle()

        items += Item(s.name, gtIndex(s.gt), v)
    }

    val N = items.size
    require(N > 0) { "Hiç örnek işlenemedi (decodeErr=$decodeErr, noFace=$noFace)." }

    // 3) Cosine-threshold + Union-Find
    val dsu = DSU(N)
    val deg = IntArray(N)
    val thr = EvalCfg.SIM_THR
    for (i in 0 until N) {
        val vi = items[i].vec
        for (j in i + 1 until N) {
            val sim = dot(vi, items[j].vec) // L2-normalize ⇒ cosine = dot
            if (sim >= thr) {
                dsu.union(i, j)
                deg[i]++; deg[j]++
            }
        }
    }
    val rootToId = HashMap<Int, Int>()
    var k = 0
    val cluster = IntArray(N)
    for (i in 0 until N) {
        val r = dsu.find(i)
        val id = rootToId.getOrPut(r) { k++ }
        cluster[i] = id
    }
    val NOISE = -1
    if (EvalCfg.MIN_DEG > 0) for (i in 0 until N) if (deg[i] < EvalCfg.MIN_DEG) cluster[i] = NOISE

    // 4) Metrikler
    val gtIdx = IntArray(N) { items[it].gtIdx }
    val mAll = computeMetrics(gtIdx, cluster, noiseClusterId = null)
    val mNoNoise = computeMetrics(gtIdx, cluster, noiseClusterId = NOISE)

    // 5) Rapor yaz
    val dir = File(context.getExternalFilesDir("bench"), "")
    dir.mkdirs()

    val assignFile = File(dir, "cluster_eval_assignments.csv")
    assignFile.printWriter().use { pw ->
        pw.println("name,gt,pred_cluster,degree")
        for (i in 0 until N) {
            val name = items[i].name
            val gt = gtToIdx.entries.first { it.value == items[i].gtIdx }.key
            pw.println("$name,$gt,${cluster[i]},${deg[i]}")
        }
        pw.println("# decode_errors=$decodeErr, no_face=$noFace, total_input=${samples.size}, used=$N")
        pw.println("# params: sim>=$thr, minDegree=${EvalCfg.MIN_DEG}")
    }

    val sumFile = File(dir, "cluster_eval_summary.txt")
    fun fmt(x: Double) = String.format("%.4f", x)
    sumFile.printWriter().use { pw ->
        pw.println("Cluster Evaluation Summary")
        pw.println("--------------------------------------------------")
        pw.println("N(used)=$N, GT persons=${gtToIdx.size}, Pred clusters(all)=${mAll.kPred}")
        pw.println("Params: sim≥${EvalCfg.SIM_THR}, minDegree=${EvalCfg.MIN_DEG}")
        pw.println("Dropped: decode_err=$decodeErr, no_face=$noFace")
        pw.println()
        pw.println("== Including noise ==")
        pw.println("B3   P/R/F1 : ${fmt(mAll.b3P)} / ${fmt(mAll.b3R)} / ${fmt(mAll.b3F1)}")
        pw.println("Pair P/R/F1 : ${fmt(mAll.pwP)} / ${fmt(mAll.pwR)} / ${fmt(mAll.pwF1)}")
        pw.println("ARI / NMI   : ${fmt(mAll.ari)} / ${fmt(mAll.nmi)}")
        pw.println("Majority ACC: ${fmt(mAll.majAcc)}")
        pw.println()
        pw.println("== Excluding noise ==")
        pw.println("B3   P/R/F1 : ${fmt(mNoNoise.b3P)} / ${fmt(mNoNoise.b3R)} / ${fmt(mNoNoise.b3F1)}")
        pw.println("Pair P/R/F1 : ${fmt(mNoNoise.pwP)} / ${fmt(mNoNoise.pwR)} / ${fmt(mNoNoise.pwF1)}")
        pw.println("ARI / NMI   : ${fmt(mNoNoise.ari)} / ${fmt(mNoNoise.nmi)}")
        pw.println("Majority ACC: ${fmt(mNoNoise.majAcc)}")
    }
    copyToDownloads(context, sumFile, "cluster_eval_summary.txt", "text/plain")
    copyToDownloads(context, assignFile, "cluster_eval_assignments.csv", "text/csv")

    return@withContext sumFile.absolutePath
}

// Dot product (L2-normalize vektörlerde cosine ile aynıdır)
private fun dot(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}
