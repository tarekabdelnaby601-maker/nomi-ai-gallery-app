// app/src/main/java/com/example/mobilgaleri/presentation/gallery/GalleryPresenter.kt
package com.example.mobilgaleri.presentation.gallery

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import com.example.mobilgaleri.data.face.MediaPipeFaceDetector
import com.example.mobilgaleri.di.AppModule
import com.example.mobilgaleri.domain.repository.ContactsRepository
import com.example.mobilgaleri.domain.repository.FaceRepository
import com.example.mobilgaleri.domain.repository.GalleryRepository
import com.example.mobilgaleri.domain.repository.PersonRepository
import com.example.mobilgaleri.domain.usecase.GetSavedClustersUseCase
import com.example.mobilgaleri.domain.usecase.MatchContactsUseCase
import com.example.mobilgaleri.domain.sync.ScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

// (ÖNEMLİ) ONNX embedder tipi:
import com.example.mobilgaleri.data.face.OnnxEdgeFaceEmbedder

class GalleryPresenter(
    private val galleryRepository: GalleryRepository,
    private val faceRepository: FaceRepository,
    private val personRepository: PersonRepository,
    private val contactsRepository: ContactsRepository,
    private val contentResolver: ContentResolver,
    private val detector: MediaPipeFaceDetector,
    // Not: embedder burada kullanılmıyor ama mevcut imzayı bozmayalım diye tipini ONNX yaptık.
    private val embedder: OnnxEdgeFaceEmbedder,
    private val appContext: Context,
    private val scanCoordinator: ScanCoordinator
) : GalleryContract.Presenter {

    private var view: GalleryContract.View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val reassignFaceUseCase = AppModule.provideReassignFaceToPersonUseCase(appContext)
    private val getAllPersonsUseCase = AppModule.provideGetAllPersonsUseCase(appContext)
    override fun attach(view: GalleryContract.View) { this.view = view }

    override fun detach() {
        this.view = null
        scope.coroutineContext.cancelChildren()
    }


    override fun loadCachedClusters() {
        scope.launch {
            try {
                view?.showLoading(true)

                val getSaved = GetSavedClustersUseCase(
                    faceRepository,
                    galleryRepository,
                    personRepository
                )

                val (clusters, details) = getSaved()
                view?.displayClusters(clusters, details)

            } catch (e: Exception) {
                view?.showError("Kayıtlı veriler yüklenirken hata: ${e.message}")
            } finally {
                view?.showLoading(false)
            }
        }
    }

    override fun syncIfNeeded() {
        scope.launch {
            try {
                val shouldSync = scanCoordinator.shouldSync()
                if (!shouldSync) return@launch

                val syncUseCase = AppModule.provideSyncAndClusterNewFacesUseCase(appContext)
                val startNs = System.nanoTime()

                syncUseCase.run()
                    .catch { e ->
                        view?.hideProgress()
                        view?.showError("Tarama sırasında hata: ${e.message}")
                    }
                    .onCompletion {
                        view?.hideProgress()
                    }
                    .collect { update ->
                        when (update) {
                            is ClusteringUpdate.Progress -> {
                                view?.updateProgress(update.processed, update.total)
                            }

                            is ClusteringUpdate.Completed -> {
                                val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                                val secText = String.format(java.util.Locale("tr"), "%.2f", elapsedSec)

                                val getSaved = GetSavedClustersUseCase(
                                    faceRepository,
                                    galleryRepository,
                                    personRepository
                                )
                                val (finalClusters, finalDetails) = getSaved()

                                view?.displayClusters(finalClusters, finalDetails)

                                view?.showSummaryToast(
                                    "Galerinizde yüz olan fotoğraflar $secText saniyede tespit edildi"
                                )

                                if (update.newFacesScanned > 0) {
                                    view?.showSummaryToast("${update.newFacesScanned} yeni yüz tarandı ve eklendi.")
                                }

                                view?.showContactsMatchPrompt()
                            }
                        }
                    }
            } catch (e: Exception) {
                view?.showError("Senkron kontrolü sırasında hata: ${e.message}")
            }
        }
    }

    override fun onWrongPersonClicked(faceId: Long) {
        scope.launch {
            val persons = getAllPersonsUseCase()
            view?.showSelectPersonDialog(faceId, persons)
        }
    }

    override fun onPersonSelectedForFace(faceId: Long, newPersonId: Long) {
        scope.launch {
            reassignFaceUseCase(faceId, newPersonId)

            // Kümeleri tekrar yükle
            val (clusters, details) =
                GetSavedClustersUseCase(faceRepository, galleryRepository, personRepository).invoke()

            view?.displayClusters(clusters, details)
            view?.refreshAfterFaceReassigned()
        }
    }

    override fun matchFacesWithContacts() {
        scope.launch {
            try {
                // FaceLandmarker'ı ve ONNX embedder'ı AppModule'dan al
                val matchUseCase = MatchContactsUseCase(
                    personRepository = personRepository,
                    contactsRepository = contactsRepository,
                    embedder = AppModule.provideEmbedder(appContext), // OnnxEdgeFaceEmbedder döner
                    contentResolver = contentResolver,
                    landmarker = AppModule.provideFaceLandmarker(appContext)
                    // Eğer MatchContactsUseCase imzan faceDetector bekliyorsa şunu da ekleyebilirsin:
                    // faceDetector = detector
                )

                matchUseCase.invoke()

                val (finalClusters, finalDetails) =
                    GetSavedClustersUseCase(faceRepository, galleryRepository, personRepository).invoke()
                view?.displayClusters(finalClusters, finalDetails)

                view?.showSummaryToast("Rehber eşleştirmesi tamamlandı.")
            } catch (e: Exception) {
                view?.showError("Rehber eşleştirme sırasında hata: ${e.message}")
            }
        }
    }
}
