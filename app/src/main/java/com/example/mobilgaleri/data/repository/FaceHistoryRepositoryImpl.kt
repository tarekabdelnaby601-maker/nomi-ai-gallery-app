package com.example.mobilgaleri.data.repository

import com.example.mobilgaleri.data.local.dao.FaceHistoryDao
import com.example.mobilgaleri.data.local.entity.FaceHistoryEntity
import com.example.mobilgaleri.domain.repository.FaceHistoryRepository

class FaceHistoryRepositoryImpl(
    private val dao: FaceHistoryDao
) : FaceHistoryRepository {

    override suspend fun insert(history: FaceHistoryEntity) {
        dao.insert(history)
    }

    override suspend fun getHistoryForFace(faceId: Long): List<FaceHistoryEntity> {
        return dao.getHistoryForFace(faceId)
    }
}
