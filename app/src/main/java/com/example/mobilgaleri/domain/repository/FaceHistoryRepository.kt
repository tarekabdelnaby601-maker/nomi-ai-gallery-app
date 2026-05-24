package com.example.mobilgaleri.domain.repository

import com.example.mobilgaleri.data.local.entity.FaceHistoryEntity

interface FaceHistoryRepository {
    suspend fun insert(history: FaceHistoryEntity)
    suspend fun getHistoryForFace(faceId: Long): List<FaceHistoryEntity>
}
