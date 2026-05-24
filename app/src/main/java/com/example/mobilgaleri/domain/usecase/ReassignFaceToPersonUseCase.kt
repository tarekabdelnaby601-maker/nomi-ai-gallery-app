package com.example.mobilgaleri.domain.usecase

import com.example.mobilgaleri.data.local.entity.FaceHistoryEntity
import com.example.mobilgaleri.domain.repository.FaceHistoryRepository
import com.example.mobilgaleri.domain.repository.FaceRepository

class ReassignFaceToPersonUseCase(
    private val faceRepository: FaceRepository,
    private val historyRepository: FaceHistoryRepository
) {
    suspend operator fun invoke(faceId: Long, newPersonId: Long) {
        // 1) Mevcut yüzü ve eski kişisini al
        val face = faceRepository.getFaceById(faceId) ?: return
        val oldPersonId = face.personOwnerId

        // 2) History tablosuna kaydet
        val history = FaceHistoryEntity(
            faceId = faceId,
            oldPersonId = oldPersonId,
            newPersonId = newPersonId,
            changedAt = System.currentTimeMillis()
        )
        historyRepository.insert(history)

        // 3) Asıl yüz kaydını güncelle (güncel kişi)
        faceRepository.updateFacePerson(faceId, newPersonId)
    }
}
