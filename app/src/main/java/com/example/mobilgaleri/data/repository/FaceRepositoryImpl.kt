//com/example/mobilgaleri/data/repository/FaceRepositoryImpl.kt
package com.example.mobilgaleri.data.repository

import com.example.mobilgaleri.data.local.dao.FaceDao
import com.example.mobilgaleri.data.local.entity.FaceEntity
import com.example.mobilgaleri.domain.repository.FaceRepository

class FaceRepositoryImpl(private val faceDao: FaceDao) : FaceRepository {

    override suspend fun insertFace(face: FaceEntity) {
        faceDao.insert(face)
    }

    override suspend fun getAllScannedPhotoIds(): List<Long> {
        return faceDao.getAllScannedPhotoIds()
    }

    override suspend fun getAllFaces(): List<FaceEntity> {
        return faceDao.all()
    }

    // DÜZELTME: Eksik olan fonksiyonlar eklendi.
    override suspend fun updateFaces(faces: List<FaceEntity>) {
        faceDao.updateAll(faces)
    }

    override suspend fun getFacesWithPersonIds(personIds: List<Long>): List<FaceEntity> {
        return faceDao.getFacesWithPersonIds(personIds)
    }
    override suspend fun updateFacePerson(faceId: Long, newPersonId: Long) {
        faceDao.updateFacePerson(faceId, newPersonId)
    }
    override suspend fun getFaceById(faceId: Long): FaceEntity? {
        return faceDao.getById(faceId)
    }

}
