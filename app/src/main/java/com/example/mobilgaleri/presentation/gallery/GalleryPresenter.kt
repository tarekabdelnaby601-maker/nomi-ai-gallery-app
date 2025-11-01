// app/src/main/java/com/example/mobilgaleri/presentation/gallery/GalleryPresenter.kt
package com.example.mobilgaleri.presentation.gallery

import android.content.ContentResolver
import android.content.Context
import com.example.mobilgaleri.data.face.MediaPipeFaceDetector
import com.example.mobilgaleri.data.face.TfliteEmbedder
import com.example.mobilgaleri.di.AppModule
import com.example.mobilgaleri.domain.repository.ContactsRepository
import com.example.mobilgaleri.domain.repository.FaceRepository
import com.example.mobilgaleri.domain.repository.GalleryRepository
import com.example.mobilgaleri.domain.repository.PersonRepository
import com.example.mobilgaleri.domain.usecase.GetSavedClustersUseCase
import com.example.mobilgaleri.domain.usecase.MatchContactsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

class GalleryPresenter(
    private val galleryRepository: GalleryRepository,
    private val faceRepository: FaceRepository,
    private val personRepository: PersonRepository,
    private val contactsRepository: ContactsRepository,
    private val contentResolver: ContentResolver,
    private val detector: MediaPipeFaceDetector,
    private val embedder: TfliteEmbedder,
    private val appContext: Context
) : GalleryContract.Presenter {

    private var view: GalleryContract.View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun attach(view: GalleryContract.View) { this.view = view }
    override fun detach() {
        this.view = null
        scope.coroutineContext.cancelChildren()
    }

    override fun loadAndSyncClusters() {
        scope.launch {
            // 1) Kayıtlı kümeleri hızlıca göster (UI hemen dolsun)
            try {
                view?.showLoading(true)
                val getSaved = GetSavedClustersUseCase(faceRepository, galleryRepository, personRepository)
                val (initialClusters, initialDetails) = getSaved()
                view?.displayClusters(initialClusters, initialDetails)
            } catch (e: Exception) {
                view?.showError("Kayıtlı veriler yüklenirken hata: ${e.message}")
            } finally {
                view?.showLoading(false)
            }

            // 2) Yeni yüzleri tarayıp cluster'la — SENİN MİMARİN: AppModule üzerinden alıyoruz
            val syncUseCase = AppModule.provideSyncAndClusterNewFacesUseCase(appContext)

            val startNs = System.nanoTime()

            syncUseCase.run()
                .catch { e ->
                    view?.hideProgress()
                    view?.showError("Tarama sırasında hata: ${e.message}")
                }
                .onCompletion {
                    // her durumda overlay’i kapat
                    view?.hideProgress()
                }
                .collect { update ->
                    when (update) {
                        is ClusteringUpdate.Progress -> {
                            view?.updateProgress(update.processed, update.total)
                        }
                        is ClusteringUpdate.Completed -> {
                            val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
                            val secText = String.format(java.util.Locale("tr"), "%.1f", elapsedSec)

                            // En güncel kümeleri çek ve göster
                            val (finalClusters, finalDetails) =
                                GetSavedClustersUseCase(faceRepository, galleryRepository, personRepository).invoke()
                            view?.displayClusters(finalClusters, finalDetails)

                            // Özet toast(lar)
                            view?.showSummaryToast("Galerinizde yüz olan fotoğraflar $secText saniyede tespit edildi")
                            val newFacesCount = update.newFacesScanned
                            if (newFacesCount > 0) {
                                view?.showSummaryToast("$newFacesCount yeni yüz tarandı ve eklendi.")
                            }

                            // UI: eşleştirme onayı için bottom sheet aç
                            view?.showContactsMatchPrompt()
                        }
                    }
                }
        }
    }

    override fun matchFacesWithContacts() {
        scope.launch {
            try {
                // SENİN MİMARİN: FaceLandmarker'ı AppModule'dan alıyoruz
                val matchUseCase = MatchContactsUseCase(
                    personRepository = personRepository,
                    contactsRepository = contactsRepository,
                    embedder = embedder,
                    contentResolver = contentResolver,
                    landmarker = AppModule.provideFaceLandmarker(appContext)
                    // Eğer senin MatchContactsUseCase imzan "faceDetector" bekliyorsa şu satırı kullan:
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
