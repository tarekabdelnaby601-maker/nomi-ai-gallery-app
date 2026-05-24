package com.example.mobilgaleri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mobilgaleri.data.local.entity.FaceClusterSimilarityEntity

@Dao
interface FaceClusterSimilarityDao {

    @Insert
    suspend fun insert(entity: FaceClusterSimilarityEntity)

    @Query("SELECT * FROM face_cluster_similarity WHERE faceId = :faceId")
    suspend fun getFaceScores(faceId: Long): List<FaceClusterSimilarityEntity>

    @Query("SELECT * FROM face_cluster_similarity WHERE clusterId = :clusterId")
    suspend fun getClusterScores(clusterId: Long): List<FaceClusterSimilarityEntity>
}
