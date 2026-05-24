package com.example.mobilgaleri.data.repository

import com.example.mobilgaleri.data.local.dao.FaceClusterSimilarityDao
import com.example.mobilgaleri.data.local.entity.FaceClusterSimilarityEntity
import com.example.mobilgaleri.domain.repository.FaceClusterSimilarityRepository

class FaceClusterSimilarityRepositoryImpl(
    private val dao: FaceClusterSimilarityDao
) : FaceClusterSimilarityRepository {

    override suspend fun insert(entity: FaceClusterSimilarityEntity) {
        dao.insert(entity)
    }

    override suspend fun getFaceScores(faceId: Long) =
        dao.getFaceScores(faceId)

    override suspend fun getClusterScores(clusterId: Long) =
        dao.getClusterScores(clusterId)
}
