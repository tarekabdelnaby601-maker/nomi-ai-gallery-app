//com/example/mobilgaleri/domain/repository/FaceRepository.kt
package com.example.mobilgaleri.domain.repository

import com.example.mobilgaleri.data.local.entity.FaceEntity

/**
 * Yüz veritabanı işlemleri için güncel ve temiz sözleşme.
 */
interface FaceRepository {
    suspend fun insertFace(face: FaceEntity)
    suspend fun getAllScannedPhotoIds(): List<Long>
    suspend fun getAllFaces(): List<FaceEntity>
    suspend fun updateFaces(faces: List<FaceEntity>)
    suspend fun getFacesWithPersonIds(personIds: List<Long>): List<FaceEntity>
    suspend fun updateFacePerson(faceId: Long, newPersonId: Long)
    suspend fun getFaceById(faceId: Long): FaceEntity?


}
