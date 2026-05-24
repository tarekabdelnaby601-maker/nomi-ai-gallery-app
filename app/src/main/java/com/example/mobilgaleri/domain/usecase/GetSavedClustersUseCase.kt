//com/example/mobilgaleri/domain/usecase/GetSavedClustersUseCase.kt
package com.example.mobilgaleri.domain.usecase

import com.example.mobilgaleri.domain.model.PersonDetails
import com.example.mobilgaleri.domain.model.Photo
import com.example.mobilgaleri.domain.repository.FaceRepository
import com.example.mobilgaleri.domain.repository.GalleryRepository
import com.example.mobilgaleri.domain.repository.PersonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetSavedClustersUseCase(
    private val faceRepository: FaceRepository,
    private val galleryRepository: GalleryRepository,
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(): Pair<Map<Int, List<Photo>>, Map<Int, PersonDetails>> = withContext(Dispatchers.IO) {
        val allFaces = faceRepository.getAllFaces()
        val allPhotosMap = galleryRepository.getAllPhotos().associateBy { it.id }
        val allPersonsMap = personRepository.getAllPersons().associateBy { it.personId }

        val clustersMap = mutableMapOf<Int, MutableList<Photo>>()
        val detailsMap = mutableMapOf<Int, PersonDetails>()

        val groupedByPerson = allFaces.groupBy { it.personOwnerId }

        groupedByPerson.forEach { (personId, faces) ->
            val photosInCluster = faces.mapNotNull { face ->
                val base = allPhotosMap[face.photoOwnerId]
                base?.copy(faceId = face.faceId)   // Photo içine yüzün ID’sini ekliyoruz
            }.distinct()
            val personEntity = allPersonsMap[personId]
            val personIdInt = personId.toInt()
            clustersMap[personIdInt] = photosInCluster.toMutableList()

            // --- DÜZELTME BURADA ---
            // PersonDetails nesnesini oluştururken Elvis operatörü (?:) ile varsayılan değerler atıyoruz.
            detailsMap[personIdInt] = PersonDetails(
                displayName = personEntity?.name ?: "Kişi ${personIdInt + 1}", // Eğer isim null ise, "Kişi X" yaz.
                matchInfo = personEntity?.contactMatchConfidence?.let { confidence ->
                    "Rehberle %${(confidence * 100).toInt()} oranında eşleşiyor."
                } ?: "" // Eğer eşleşme bilgisi null ise, boş bir string "" ata.
            )
            // --- DÜZELTME SONU ---
        }
        return@withContext Pair(clustersMap, detailsMap)
    }
}