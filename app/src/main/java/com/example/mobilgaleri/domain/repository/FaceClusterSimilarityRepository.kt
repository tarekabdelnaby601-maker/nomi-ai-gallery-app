package com.example.mobilgaleri.domain.repository

import com.example.mobilgaleri.data.local.entity.FaceClusterSimilarityEntity

interface FaceClusterSimilarityRepository {
    suspend fun insert(entity: FaceClusterSimilarityEntity)
    suspend fun getFaceScores(faceId: Long): List<FaceClusterSimilarityEntity>
    suspend fun getClusterScores(clusterId: Long): List<FaceClusterSimilarityEntity>
}
