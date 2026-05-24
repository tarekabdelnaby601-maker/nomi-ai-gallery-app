package com.example.mobilgaleri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mobilgaleri.data.local.entity.FaceHistoryEntity

@Dao
interface FaceHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: FaceHistoryEntity)

    @Query("SELECT * FROM face_history WHERE faceId = :faceId ORDER BY changedAt ASC")
    suspend fun getHistoryForFace(faceId: Long): List<FaceHistoryEntity>
}
