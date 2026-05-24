package com.example.mobilgaleri.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_cluster_similarity")
data class FaceClusterSimilarityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val faceId: Long,
    val clusterId: Long,
    val similarity: Float,
    val timestamp: Long = System.currentTimeMillis()
)
