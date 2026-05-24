package com.example.mobilgaleri.domain.usecase

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.example.mobilgaleri.data.face.MediaPipeFaceDetector
import com.example.mobilgaleri.data.face.MediaPipeFaceLandmarker
import com.example.mobilgaleri.data.face.OnnxEdgeFaceEmbedder
import com.example.mobilgaleri.data.local.dao.PhotoScanDao
import com.example.mobilgaleri.data.local.entity.FaceClusterSimilarityEntity
import com.example.mobilgaleri.data.local.entity.FaceEntity
import com.example.mobilgaleri.data.local.entity.PersonEntity
import com.example.mobilgaleri.data.local.entity.PhotoScanEntity
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.domain.repository.*
import com.example.mobilgaleri.domain.sync.ScanCoordinator
import com.example.mobilgaleri.presentation.gallery.ClusteringUpdate
import com.example.mobilgaleri.util.BitmapUtils
import com.example.mobilgaleri.util.alignFace112FiveOrThree
import com.example.mobilgaleri.util.averageEmbeddings
import com.example.mobilgaleri.util.flipX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.math3.ml.clustering.Clusterable
import org.apache.commons.math3.ml.clustering.DBSCANClusterer
import org.apache.commons.math3.ml.distance.DistanceMeasure
import kotlin.math.sqrt

class SyncAndClusterNewFacesUseCase(
    private val galleryRepo: GalleryRepository,
    private val faceRepo: FaceRepository,
    private val personRepo: PersonRepository,
    private val cr: ContentResolver,
    private val detector: MediaPipeFaceDetector,
    private val embedder: OnnxEdgeFaceEmbedder,
    private val onlineClusterer: ClusterFacesUseCase,
    private val landmarker: MediaPipeFaceLandmarker,
    private val faceClusterSimilarityRepo: FaceClusterSimilarityRepository,
    private val photoScanDao: PhotoScanDao,
    private val scanCoordinator: ScanCoordinator
) {

    private class CentroidWrapper(val personEntity: PersonEntity) : Clusterable {
        override fun getPoint(): DoubleArray =
            personEntity.centroid.map { it.toDouble() }.toDoubleArray()
    }

    private object CosineDistance : DistanceMeasure {
        override fun compute(a: DoubleArray, b: DoubleArray): Double {
            var dot = 0.0
            var na = 0.0
            var nb = 0.0
            val size = a.size
            var i = 0
            while (i < size) {
                val va = a[i]
                val vb = b[i]
                dot += va * vb
                na += va * va
                nb += vb * vb
                i++
            }
            val den = sqrt(na) * sqrt(nb)
            val sim = if (den == 0.0) 0.0 else dot / den
            return 1.0 - sim
        }
    }

    fun run(): Flow<ClusteringUpdate> = flow {
        val newPhotosToScan = scanCoordinator.getUnscannedPhotos()

        if (newPhotosToScan.isEmpty()) {
            emit(ClusteringUpdate.Completed(0))
            return@flow
        }

        val totalPhotos = newPhotosToScan.size
        var processedCount = 0
        var newFacesScanned = 0

        for (photo in newPhotosToScan) {
            try {
                val hasFace = processPhoto(photo)
                if (hasFace) {
                    newFacesScanned++
                }
            } catch (e: Exception) {
                Log.e("SyncUseCase", "Failed to process photo ${photo.id}", e)
            }

            processedCount++

            if (processedCount % 20 == 0 || processedCount == totalPhotos) {
                emit(ClusteringUpdate.Progress(processedCount, totalPhotos))
            }
        }

        val roughClusters = personRepo.getAllPersons()
        if (roughClusters.size > 1) {
            val dbscan = DBSCANClusterer<CentroidWrapper>(0.45, 1, CosineDistance)
            val finalClusters = dbscan.cluster(roughClusters.map { CentroidWrapper(it) })

            val faceUpdates = mutableListOf<FaceEntity>()
            val personsToDelete = mutableListOf<PersonEntity>()

            for (cluster in finalClusters) {
                val members = cluster.points
                if (members.size > 1) {
                    val main = members.first().personEntity
                    val merged = members.drop(1).map { it.personEntity }
                    personsToDelete.addAll(merged)

                    val mergedIds = merged.map { it.personId }
                    val faces = faceRepo.getFacesWithPersonIds(mergedIds)
                    faces.forEach { it.personOwnerId = main.personId }
                    faceUpdates.addAll(faces)
                }
            }

            if (faceUpdates.isNotEmpty()) faceRepo.updateFaces(faceUpdates)
            if (personsToDelete.isNotEmpty()) personRepo.deletePersons(personsToDelete)
        }

        renumberPersonsSequentially()

        emit(ClusteringUpdate.Completed(newFacesScanned))
    }.flowOn(Dispatchers.IO)


    // --- YENİ FONKSİYON: VERİTABANINDAKİ İSİMLERİ DÜZELTİR ---
    private suspend fun renumberPersonsSequentially() {
        // Tüm kişileri ID'lerine göre sıralı çek
        val allPersons = personRepo.getAllPersons().sortedBy { it.personId }

        var counter = 1
        for (person in allPersons) {
            val currentName = person.name ?: ""

            // Eğer isim boşsa veya "Kişi" ile başlıyorsa (yani özel isim değilse)
            // Özel isimler (örn: "Ahmet", "Brad Pitt") korunur.
            if (currentName.isEmpty() || currentName.startsWith("Kişi")) {
                val newName = "Kişi $counter"

                // Gereksiz update yapmamak için kontrol et
                if (currentName != newName) {
                    // Sadece ismi güncelle
                    personRepo.upsert(person.copy(name = newName))
                }
                counter++
            } else {
                // Özel isim varsa (Ahmet), sıradaki kişi (Mehmet) "Kişi 5" mi olsun yoksa "Kişi 2" mi?
                // Genelde özel isimler araya girse bile sayacı artırmak mantıklıdır.
                // Eğer özel isimleri sayaca dahil etmek istemezsen burayı boş bırak.
                counter++
            }
        }
    }

    // ... (Mevcut embedFaceHighQualityFromDetectionBitmap ve processPhoto fonksiyonları değişmeden kalacak) ...
    // Dosya boyutunu şişirmemek için buraya tekrar kopyalamıyorum, önceki versiyonla aynı kalacaklar.
    // Ancak tam bir dosya oluşturmak için aşağıya processPhoto'yu ekliyorum.

    private fun embedFaceHighQualityFromDetectionBitmap(
        detectionBitmap: Bitmap,
        detBox: Rect,
        landmarker: MediaPipeFaceLandmarker,
        embedder: OnnxEdgeFaceEmbedder,
        alignMargin: Float = 1.10f,
        flipAverage: Boolean = true
    ): FloatArray? {
        // (Bu fonksiyonun içeriği önceki kodun aynısıdır)
        val padX = (detBox.width() * 0.12f).toInt()
        val padY = (detBox.height() * 0.12f).toInt()
        val l = (detBox.left - padX).coerceAtLeast(0)
        val t = (detBox.top - padY).coerceAtLeast(0)
        val r = (detBox.right + padX).coerceAtMost(detectionBitmap.width)
        val b = (detBox.bottom + padY).coerceAtMost(detectionBitmap.height)
        if (l >= r || t >= b) return null

        var roi: Bitmap? = null
        var aligned112: Bitmap? = null

        try {
            roi = Bitmap.createBitmap(detectionBitmap, l, t, r - l, b - t)
            val (five, three) = landmarker.detectPoints(roi)

            aligned112 = when {
                five != null -> alignFace112FiveOrThree(roi, five.leftEye.x to five.leftEye.y, five.rightEye.x to five.rightEye.y, five.noseTip.x to five.noseTip.y, five.mouthLeft.x to five.mouthLeft.y, five.mouthRight.x to five.mouthRight.y, alignMargin).bitmap
                three != null -> alignFace112FiveOrThree(roi, three.leftEye.x to three.leftEye.y, three.rightEye.x to three.rightEye.y, three.noseTip.x to three.noseTip.y, null, null, alignMargin).bitmap
                else -> Bitmap.createScaledBitmap(roi, 112, 112, true)
            }

            val aligned = aligned112 ?: return null
            val e = embedder.embedAligned112(aligned)
            if (!flipAverage) return e

            var flipped: Bitmap? = null
            return try {
                flipped = aligned.flipX()
                val ef = embedder.embedAligned112(flipped)
                averageEmbeddings(e, ef)
            } finally {
                flipped?.recycle()
            }
        } catch (t: Throwable) {
            return null
        } finally {
            aligned112?.recycle(); roi?.recycle()
        }
    }

    private suspend fun processPhoto(photo: Photo): Boolean {
        val photoUri = Uri.parse(photo.contentUri)
        val loaded = BitmapUtils.loadScaledBitmapForDetection(cr, photoUri, 640)
        val bitmap = loaded.first ?: return false

        try {
            val faces = detector.detect(bitmap)

            if (faces.isEmpty()) {
                photoScanDao.insert(
                    PhotoScanEntity(
                        photoId = photo.id,
                        scannedAt = System.currentTimeMillis(),
                        hasFace = false
                    )
                )
                return false
            }

            val face = faces.first()

            val embedding = embedFaceHighQualityFromDetectionBitmap(
                bitmap,
                face.boundingBox,
                landmarker,
                embedder,
                1.10f,
                face.boundingBox.width() >= 80
            ) ?: run {
                photoScanDao.insert(
                    PhotoScanEntity(
                        photoId = photo.id,
                        scannedAt = System.currentTimeMillis(),
                        hasFace = false
                    )
                )
                return false
            }

            val result = onlineClusterer.assignWithSim(embedding)
            val personId = result.clusterId.toLong()

            faceClusterSimilarityRepo.insert(
                FaceClusterSimilarityEntity(
                    faceId = photo.id,
                    clusterId = personId,
                    similarity = result.similarity
                )
            )

            faceRepo.insertFace(
                FaceEntity(
                    photoOwnerId = photo.id,
                    personOwnerId = personId,
                    embedding = embedding,
                    left = face.boundingBox.left,
                    top = face.boundingBox.top,
                    right = face.boundingBox.right,
                    bottom = face.boundingBox.bottom
                )
            )

            personRepo.upsert(
                PersonEntity(
                    personId,
                    null,
                    onlineClusterer.getCentroid(personId.toInt()) ?: embedding
                )
            )

            photoScanDao.insert(
                PhotoScanEntity(
                    photoId = photo.id,
                    scannedAt = System.currentTimeMillis(),
                    hasFace = true
                )
            )

            return true
        } catch (e: Exception) {
            Log.e("SyncUseCase", "processPhoto failed for photo ${photo.id}", e)
            return false
        } finally {
            bitmap.recycle()
        }
    }
}